package es.vicai.studylab.owner;

import es.vicai.studylab.ai.AiImageProperties;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.budget.BudgetService;
import es.vicai.studylab.owner.OwnerReports.Accounts;
import es.vicai.studylab.owner.OwnerReports.Ai;
import es.vicai.studylab.owner.OwnerReports.CourseQueue;
import es.vicai.studylab.owner.OwnerReports.CourseUsage;
import es.vicai.studylab.owner.OwnerReports.GlobalLimit;
import es.vicai.studylab.owner.OwnerReports.Health;
import es.vicai.studylab.owner.OwnerReports.ImagePriceRow;
import es.vicai.studylab.owner.OwnerReports.PendingFailed;
import es.vicai.studylab.owner.OwnerReports.PieceUsage;
import es.vicai.studylab.owner.OwnerReports.Prices;
import es.vicai.studylab.owner.OwnerReports.Projection;
import es.vicai.studylab.owner.OwnerReports.Sharing;
import es.vicai.studylab.owner.OwnerReports.StatusCounts;
import es.vicai.studylab.owner.OwnerReports.StudyUsage;
import es.vicai.studylab.owner.OwnerReports.TextPrice;
import es.vicai.studylab.owner.OwnerReports.TutorUsage;
import es.vicai.studylab.owner.OwnerReports.Usage;
import es.vicai.studylab.owner.OwnerReports.UserSpend;
import es.vicai.studylab.study.StudyProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Compone los informes del panel del propietario (F14) con lo que calcula {@link OwnerStatsRepository}, el tope global de {@link BudgetService} y los precios de la configuración.
 * Solo lee y solo devuelve cifras; no habla con ningún modelo.
 */
@Service
public class OwnerReportService {

    private static final int STALE_DAYS = 90;

    private final OwnerStatsRepository stats;
    private final OwnerUserRepository users;
    private final BudgetService budget;
    private final AiProperties props;
    private final AiImageProperties imageProps;
    private final StudyProperties study;

    public OwnerReportService(OwnerStatsRepository stats, OwnerUserRepository users, BudgetService budget, AiProperties props, AiImageProperties imageProps, StudyProperties study) {
        this.stats = stats;
        this.users = users;
        this.budget = budget;
        this.props = props;
        this.imageProps = imageProps;
        this.study = study;
    }

    public Ai ai(OwnerRange range) {
        int back = range.back();
        var totals = stats.totals(back);
        BigDecimal rangeUsd = stats.rangeUsd(back);
        BigDecimal month = stats.monthUsd();
        long active = stats.activeUsersInRange(back);
        Map<UUID, String> labels = Map.of();
        List<UserSpend> top = stats.topUsers(back);
        labels = users.labels(top.stream().map(UserSpend::userId).toList());
        Map<UUID, String> names = labels;
        List<UserSpend> byUser = top.stream().map(u -> new UserSpend(u.userId(), u.kind(), names.get(u.userId()), u.usd())).toList();
        return new Ai(range.days(), stats.todayUsd(), month, rangeUsd, projection(month), new GlobalLimit(props.budget().effectiveLimit(), budget.status().percent()),
                stats.daily(back), stats.byModel(back), stats.byProvider(back), stats.byTask(back), byUser, totals, stats.costPerCourse(back).setScale(4, RoundingMode.HALF_UP),
                active == 0 ? BigDecimal.ZERO.setScale(4) : rangeUsd.divide(BigDecimal.valueOf(active), 4, RoundingMode.HALF_UP), stats.benchmark(back));
    }

    /** Gasto del mes al ritmo actual; poco fiable mientras haya menos de tres días de datos. */
    private Projection projection(BigDecimal month) {
        var clock = stats.monthClock();
        if (month.signum() == 0 || clock.firstCall() == null) return new Projection(BigDecimal.ZERO.setScale(2), false);
        double days = Math.max(clock.elapsedDays(), 0.5);
        BigDecimal projected = month.multiply(BigDecimal.valueOf(clock.daysInMonth())).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        return new Projection(projected, Duration.between(clock.firstCall(), clock.now()).toDays() >= 3);
    }

    public Prices prices() {
        LocalDate today = LocalDate.now();
        List<TextPrice> text = props.pricing().entrySet().stream().map(e -> {
            String[] key = split(e.getKey());
            AiProperties.Price price = e.getValue();
            return new TextPrice(key[0], key[1], price.inputPerMillion(), price.outputPerMillion(), date(price.verifiedOn()), stale(price.verifiedOn(), today));
        }).sorted(Comparator.comparing(TextPrice::provider).thenComparing(TextPrice::model)).toList();
        List<ImagePriceRow> image = imageProps.imagePricing().entrySet().stream().map(e -> {
            String[] key = split(e.getKey());
            return new ImagePriceRow(key[0], key[1], e.getValue().perImage(), date(e.getValue().verifiedOn()), stale(e.getValue().verifiedOn(), today));
        }).sorted(Comparator.comparing(ImagePriceRow::provider).thenComparing(ImagePriceRow::model)).toList();
        return new Prices(text, image);
    }

    private static String[] split(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? new String[] {key, ""} : new String[] {key.substring(0, slash), key.substring(slash + 1)};
    }

    private static String date(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static boolean stale(LocalDate verifiedOn, LocalDate today) {
        return verifiedOn == null || verifiedOn.isBefore(today.minusDays(STALE_DAYS));
    }

    public Accounts accounts(OwnerRange range) {
        int back = range.back();
        return new Accounts(range.days(), stats.signupsDaily(back), stats.adults(), stats.minors(), stats.usersByStatus(), stats.active(), stats.openSessions(), stats.logins(back));
    }

    public Usage usage(OwnerRange range) {
        int back = range.back();
        return new Usage(range.days(), new CourseUsage(stats.coursesByStatus(), stats.coursesCreatedDaily(back)),
                new PieceUsage(stats.piecesWith("DONE"), stats.piecesWith("FAILED"), stats.piecesWith("PENDING")),
                new StudyUsage(stats.lessonsDone(back), stats.exerciseAttempts(back), stats.examAttempts(back), stats.examsPassed(back, study.passPercent())),
                new TutorUsage(stats.tutorQuestions(back), stats.tutorWith("BLOCKED", back), stats.tutorWith("FILTERED", back)),
                new StatusCounts(stats.documentsByStatus()), new StatusCounts(stats.imagesByStatus()),
                new Sharing(stats.auditEvents("COURSE_SHARE_CREATED", back), stats.auditEvents("COURSE_SHARE_REDEEMED", back), stats.auditEvents("COURSE_SHARE_REVOKED", back)),
                stats.auditEvents("COURSE_DELETED", back));
    }

    public Health health() {
        return new Health(new CourseQueue(stats.coursesWith("PENDING_GENERATION"), stats.coursesWith("GENERATING")),
                new PendingFailed(stats.piecesWith("PENDING"), stats.piecesWith("FAILED")),
                new PendingFailed(stats.documentsIn(List.of("UPLOADED", "PROCESSING")), stats.documentsIn(List.of("ERROR"))),
                new PendingFailed(stats.imagesWith("PENDING"), stats.imagesWith("FAILED")),
                stats.aiErrorsLastDay(), stats.contentBlocksLastDay(), stats.databaseBytes(), stats.bigTables());
    }
}
