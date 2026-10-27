package es.vicai.studylab.owner;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respuestas del panel del propietario (F14, ART-003). Solo cifras: ninguna lleva texto de usuario salvo la etiqueta de quien gasta (nombre de un adulto o identificador de un menor). */
public final class OwnerReports {

    private OwnerReports() {}

    // ---------- gasto de IA ----------

    public record Day(String day, BigDecimal usd, long calls) {}

    public record Count(String day, long count) {}

    public record Projection(BigDecimal usd, boolean reliable) {}

    public record GlobalLimit(BigDecimal limitUsd, BigDecimal percent) {}

    public record ModelRow(String provider, String model, BigDecimal usd, long calls, BigDecimal errorPercent, BigDecimal blockedPercent, BigDecimal avgMs, BigDecimal p95Ms,
                           long inputTokens, long outputTokens) {}

    public record ProviderRow(String provider, BigDecimal usd, long calls) {}

    public record TaskRow(String task, BigDecimal usd, long calls) {}

    public record UserSpend(UUID userId, String kind, String label, BigDecimal usd) {}

    public record Totals(long calls, BigDecimal errorPercent, BigDecimal blockedPercent, long inputTokens, long outputTokens, BigDecimal avgMs) {}

    public record Benchmark(long calls, BigDecimal usd) {}

    public record Ai(int range, BigDecimal todayUsd, BigDecimal monthUsd, BigDecimal rangeUsd, Projection projection, GlobalLimit globalLimit, List<Day> daily, List<ModelRow> byModel,
                     List<ProviderRow> byProvider, List<TaskRow> byTask, List<UserSpend> byUser, Totals totals, BigDecimal costPerCourse, BigDecimal costPerActiveUser, Benchmark benchmark) {}

    public record TextPrice(String provider, String model, BigDecimal inputPerMillion, BigDecimal outputPerMillion, String verifiedOn, boolean stale) {}

    public record ImagePriceRow(String provider, String model, BigDecimal perImage, String verifiedOn, boolean stale) {}

    public record Prices(List<TextPrice> models, List<ImagePriceRow> images) {}

    // ---------- cuentas y conexiones ----------

    public record Active(long d1, long d7, long d30) {}

    public record LoginDay(String day, long ok, long failed, long locked) {}

    public record Accounts(int range, List<Count> signupsDaily, long adults, long minors, Map<String, Long> byStatus, Active active, long openSessions, List<LoginDay> logins) {}

    // ---------- uso de la app ----------

    public record CourseUsage(Map<String, Long> byStatus, List<Count> createdDaily) {}

    public record PieceUsage(long done, long failed, long pending) {}

    public record StudyUsage(long lessonsDone, long exerciseAttempts, long examAttempts, long examsPassed) {}

    public record TutorUsage(long questions, long blocked, long filtered) {}

    public record StatusCounts(Map<String, Long> byStatus) {}

    public record Sharing(long created, long redeemed, long revoked) {}

    public record Usage(int range, CourseUsage courses, PieceUsage pieces, StudyUsage study, TutorUsage tutor, StatusCounts documents, StatusCounts illustrations, Sharing sharing,
                        long coursesDeleted) {}

    // ---------- salud ----------

    public record CourseQueue(long pendingGeneration, long generating) {}

    public record PendingFailed(long pending, long failed) {}

    public record AiError(String provider, String errorKind, long count) {}

    public record TableSize(String name, long rows) {}

    public record Health(CourseQueue courses, PendingFailed pieces, PendingFailed documents, PendingFailed illustrations, List<AiError> aiErrors, long contentBlocks24h, long databaseBytes,
                         List<TableSize> tables) {}

    // ---------- lista de usuarios ----------

    /** De cada persona sale solo esto. Nombre y correo solo los adultos; identificador de acceso y tutor solo los menores. */
    public record UserItem(UUID id, String kind, String status,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String displayName,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String email,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String loginId,
                           Instant createdAt, Instant lastActivityAt, BigDecimal monthSpendUsd, BigDecimal limitUsd, boolean limitIsCustom, long courses,
                           @JsonInclude(JsonInclude.Include.NON_NULL) UUID tutorId) {}

    public record UserPage(List<UserItem> items, long total, int page, int size) {}
}
