package es.vicai.studylab.guardian;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.ai.budget.BudgetService;
import es.vicai.studylab.ai.budget.UsageRepository;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.generation.ExamParser;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.study.ExamGrader;
import es.vicai.studylab.study.StudyProperties;
import es.vicai.studylab.study.StudyService;
import es.vicai.studylab.tutor.TutorRepository;
import es.vicai.studylab.tutor.TutorService;
import es.vicai.studylab.tutor.TutorViews;
import es.vicai.studylab.web.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * El panel del adulto tutor (F08, ADR-59 a 62): lo mismo que ve el alumno, en solo lectura. El llamante ya comprobó el vínculo con {@code AccessGuard}; aquí
 * todo se busca con el id del ALUMNO como propietario, así que un curso o una pieza de otro alumno no existe. Nunca se devuelve el texto de lecciones,
 * ejercicios resueltos ni exámenes. Las pantallas con textos del alumno se auditan con ids (nunca el contenido), fuera de cualquier transacción.
 */
@Service
public class GuardianService {

    static final int MAX_ATTEMPTS_PER_ITEM = 20;
    static final int MAX_ATTEMPTS = 200;
    static final int MAX_ALERTS = 100;
    private static final Duration RECENT_ALERTS = Duration.ofDays(30);

    // ---------- contrato HTTP ----------

    public record StudentView(UUID id, String displayName, boolean active) {}

    public record AlertCount(int last30Days) {}

    public record TaskSpend(String task, BigDecimal spendUsd, long calls) {}

    public record UsageView(String month, BigDecimal spendUsd, BigDecimal limitUsd, boolean reached, List<TaskSpend> byTask) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CourseView(UUID id, String title, String status, Integer percent, int lessonsDone, int lessonsTotal, int examsPassed, int examsTotal, Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Overview(StudentView student, Instant lastActivityAt, AlertCount alerts, UsageView usage, List<CourseView> courses) {}

    public record CourseDetail(UUID id, String title, String status, StudyService.Overview overview) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttemptView(String answer, String selfResult, String aiResult, String aiComment, Instant createdAt) {}

    public record ExerciseView(int item, String statement, List<AttemptView> attempts) {}

    public record ExamView(Instant submittedAt, int correct, int total, int percent, boolean passed, List<String> failed) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PieceAttempts(UUID pieceId, String kind, String title, List<ExerciseView> exercises, List<ExamView> exams) {}

    public record Attempts(boolean truncated, List<PieceAttempts> pieces) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConversationView(UUID courseId, String courseTitle, UUID pieceId, String lessonTitle, int messages, int blocked, int filtered, Instant lastAt) {}

    public record ThreadView(List<TutorService.MessageView> messages) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AlertView(String type, Instant at, UUID courseId, String courseTitle, UUID pieceId, String lessonTitle, String topic, String category) {}

    private final GuardianRepository repo;
    private final TutorRepository tutor;
    private final TutorViews views;
    private final StudyService study;
    private final StudyProperties studyProps;
    private final UsageRepository usage;
    private final BudgetService budget;
    private final AuditService audit;
    private final ObjectMapper json;

    public GuardianService(GuardianRepository repo, TutorRepository tutor, TutorViews views, StudyService study, StudyProperties studyProps, UsageRepository usage,
                           BudgetService budget, AuditService audit, ObjectMapper json) {
        this.repo = repo;
        this.tutor = tutor;
        this.views = views;
        this.study = study;
        this.studyProps = studyProps;
        this.usage = usage;
        this.budget = budget;
        this.audit = audit;
        this.json = json;
    }

    // ---------- resumen y curso (no se auditan: no llevan textos del alumno) ----------

    public Overview overview(UUID studentId) {
        GuardianRepository.StudentRow student = repo.student(studentId).orElseThrow(ApiException::notFound);
        Map<UUID, StudyService.CourseSummary> summaries = study.courseSummaries(studentId);
        List<CourseView> courses = repo.courses(studentId).stream().map(c -> {
            StudyService.CourseSummary s = summaries.get(c.id());
            return new CourseView(c.id(), c.title(), c.status(), s == null ? null : s.percent(), s == null ? 0 : s.lessonsDone(), s == null ? 0 : s.lessonsTotal(),
                    s == null ? 0 : s.examsPassed(), s == null ? 0 : s.examsTotal(), c.createdAt());
        }).toList();

        int recentAlerts = repo.alerts(studentId, Instant.now().minus(RECENT_ALERTS), 1000).size();
        UsageRepository.UserUsage month = usage.userMonth(studentId);
        BudgetService.UserLimit limit = budget.userLimit(usage.isMinor(studentId), month.spendUsd());
        List<TaskSpend> byTask = usage.userMonthByTask(studentId).stream().map(t -> new TaskSpend(t.task(), t.spendUsd(), t.calls())).toList();
        return new Overview(new StudentView(studentId, student.displayName(), student.active()), repo.lastActivity(studentId).orElse(null), new AlertCount(recentAlerts),
                new UsageView(usage.currentMonth(), month.spendUsd(), limit.limitUsd(), limit.reached(), byTask), courses);
    }

    public CourseDetail course(UUID studentId, String rawCourse) {
        UUID courseId = id(rawCourse);
        GuardianRepository.CourseRow course = repo.course(courseId, studentId).orElseThrow(ApiException::notFound);
        return new CourseDetail(course.id(), course.title(), course.status(), study.overviewFor(studentId, courseId));
    }

    // ---------- intentos ----------

    public Attempts attempts(AuthUser adult, UUID studentId, String rawCourse, String ip) {
        UUID courseId = id(rawCourse);
        repo.course(courseId, studentId).orElseThrow(ApiException::notFound);
        List<GuardianRepository.PieceRow> pieces = repo.practicePieces(courseId, studentId);

        Map<UUID, TreeMap<Integer, List<GuardianRepository.ExerciseAttemptRow>>> exercises = new LinkedHashMap<>();
        for (GuardianRepository.ExerciseAttemptRow a : repo.exerciseAttempts(courseId, studentId)) {
            exercises.computeIfAbsent(a.pieceId(), k -> new TreeMap<>()).computeIfAbsent(a.item(), k -> new ArrayList<>()).add(a);
        }
        Map<UUID, List<GuardianRepository.ExamAttemptRow>> exams = new LinkedHashMap<>();
        for (GuardianRepository.ExamAttemptRow a : repo.examAttempts(courseId, studentId)) exams.computeIfAbsent(a.pieceId(), k -> new ArrayList<>()).add(a);

        boolean truncated = false;
        int total = 0;
        List<PieceAttempts> out = new ArrayList<>();
        for (GuardianRepository.PieceRow piece : pieces) {
            if ("EXERCISES".equals(piece.kind())) {
                List<ExerciseView> items = new ArrayList<>();
                List<String> statements = statements(piece);
                for (Map.Entry<Integer, List<GuardianRepository.ExerciseAttemptRow>> entry : exercises.getOrDefault(piece.id(), new TreeMap<>()).entrySet()) {
                    List<AttemptView> attempts = new ArrayList<>();
                    for (GuardianRepository.ExerciseAttemptRow a : entry.getValue()) {
                        if (attempts.size() >= MAX_ATTEMPTS_PER_ITEM || total >= MAX_ATTEMPTS) {
                            truncated = true;
                            break;
                        }
                        attempts.add(new AttemptView(a.answer(), a.selfResult(), a.aiResult(), a.aiComment(), a.createdAt()));
                        total++;
                    }
                    if (!attempts.isEmpty()) items.add(new ExerciseView(entry.getKey(), entry.getKey() < statements.size() ? statements.get(entry.getKey()) : "", attempts));
                }
                if (!items.isEmpty()) out.add(new PieceAttempts(piece.id(), piece.kind(), piece.title(), items, null));
            } else {
                List<ExamParser.Question> questions = questions(piece);
                List<ExamView> done = new ArrayList<>();
                for (GuardianRepository.ExamAttemptRow a : exams.getOrDefault(piece.id(), List.of())) {
                    if (done.size() >= MAX_ATTEMPTS_PER_ITEM || total >= MAX_ATTEMPTS) {
                        truncated = true;
                        break;
                    }
                    done.add(new ExamView(a.submittedAt(), a.correct(), a.total(), ExamGrader.percent(a.correct(), a.total()),
                            ExamGrader.passes(a.correct(), a.total(), studyProps.passPercent()), failed(a, questions)));
                    total++;
                }
                if (!done.isEmpty()) out.add(new PieceAttempts(piece.id(), piece.kind(), piece.title(), null, done));
            }
        }
        view(adult, studentId, ip, "ATTEMPTS", courseId, null);
        return new Attempts(truncated, out);
    }

    // ---------- conversaciones ----------

    public List<ConversationView> conversations(AuthUser adult, UUID studentId, String ip) {
        List<GuardianRepository.ConversationRow> rows = repo.conversations(studentId);
        Map<UUID, String> courses = repo.courseTitles(studentId, rows.stream().map(GuardianRepository.ConversationRow::courseId).collect(java.util.stream.Collectors.toSet()));
        Map<UUID, String> lessons = repo.chapterTitles(studentId, rows.stream().map(GuardianRepository.ConversationRow::pieceId).collect(java.util.stream.Collectors.toSet()));
        List<ConversationView> out = rows.stream().map(r -> new ConversationView(r.courseId(), courses.get(r.courseId()), r.pieceId(), lessons.get(r.pieceId()), r.messages(), r.blocked(),
                r.filtered(), r.lastAt())).toList();
        view(adult, studentId, ip, "CONVERSATIONS", null, null);
        return out;
    }

    public ThreadView thread(AuthUser adult, UUID studentId, String rawCourse, String rawPiece, String ip) {
        UUID courseId = id(rawCourse);
        UUID pieceId = id(rawPiece);
        tutor.lesson(courseId, pieceId, studentId).orElseThrow(ApiException::notFound);
        List<TutorService.MessageView> messages = tutor.thread(pieceId, studentId).stream().map(views::view).toList();
        view(adult, studentId, ip, "THREAD", courseId, pieceId);
        return new ThreadView(messages);
    }

    // ---------- avisos ----------

    public List<AlertView> alerts(AuthUser adult, UUID studentId, String ip) {
        List<GuardianRepository.AlertRow> rows = repo.alerts(studentId, Instant.EPOCH, MAX_ALERTS);
        Set<UUID> courseIds = new HashSet<>();
        Set<UUID> pieceIds = new HashSet<>();
        for (GuardianRepository.AlertRow r : rows) {
            if (r.courseId() != null) courseIds.add(r.courseId());
            if (r.pieceId() != null) pieceIds.add(r.pieceId());
        }
        Map<UUID, String> courses = repo.courseTitles(studentId, courseIds);
        Map<UUID, String> lessons = repo.chapterTitles(studentId, pieceIds);
        List<AlertView> out = rows.stream().map(r -> new AlertView(r.type(), r.at(), r.courseId(), r.courseId() == null ? null : courses.get(r.courseId()), r.pieceId(),
                r.pieceId() == null ? null : lessons.get(r.pieceId()), r.topic(), r.category())).toList();
        view(adult, studentId, ip, "ALERTS", null, null);
        return out;
    }

    // ---------- ayudas ----------

    /** Anota que el adulto miró esta pantalla del alumno: quién, a quién y qué (ids), nunca el contenido. */
    private void view(AuthUser adult, UUID studentId, String ip, String screen, UUID courseId, UUID pieceId) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("screen", screen);
        if (courseId != null) details.put("courseId", courseId.toString());
        if (pieceId != null) details.put("pieceId", pieceId.toString());
        audit.record("GUARDIAN_VIEW", AuditService.OK, adult.id(), studentId, ip, details);
    }

    private List<String> statements(GuardianRepository.PieceRow piece) {
        List<String> out = new ArrayList<>();
        try {
            JsonNode list = json.readTree(piece.contentJson()).path("exercises");
            for (JsonNode exercise : list) out.add(exercise.path("statement").asText(""));
        } catch (JsonProcessingException | RuntimeException e) {
            // un contenido ilegible se muestra sin enunciado
        }
        return out;
    }

    private List<ExamParser.Question> questions(GuardianRepository.PieceRow piece) {
        try {
            JsonNode list = json.readTree(piece.contentJson()).get("questions");
            return list == null ? List.of() : json.convertValue(list, new TypeReference<List<ExamParser.Question>>() {});
        } catch (JsonProcessingException | RuntimeException e) {
            return List.of();
        }
    }

    /** Los textos de las preguntas que el alumno falló o dejó sin contestar (nunca las opciones ni cuál era la correcta). */
    private List<String> failed(GuardianRepository.ExamAttemptRow attempt, List<ExamParser.Question> questions) {
        try {
            List<Integer> answers = json.readValue(attempt.answersJson(), new TypeReference<List<Integer>>() {});
            List<String> out = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                Integer chosen = i < answers.size() ? answers.get(i) : null;
                if (chosen == null || chosen != questions.get(i).correct()) out.add(questions.get(i).text());
            }
            return out;
        } catch (JsonProcessingException | RuntimeException e) {
            return List.of();
        }
    }

    private static UUID id(String raw) {
        try {
            if (raw == null) throw ApiException.notFound();
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound();
        }
    }
}
