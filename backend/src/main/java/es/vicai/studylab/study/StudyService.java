package es.vicai.studylab.study;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.generation.ExamParser;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * El estudio de un curso (F06): lecciones hechas, progreso calculado, intentos de ejercicio y de examen. Todo se filtra por propietario y un curso, pieza o
 * intento ajenos responden igual que uno inexistente (404). La solución de un ejercicio y la opción correcta de un examen no salen hasta que el alumno
 * responde o entrega (ADR-50). Lo que escribe el alumno no se registra en logs ni en auditoría (ADR-53).
 */
@Service
public class StudyService {

    public static final int MAX_ANSWER = 2000;
    private static final Set<String> RESULTS = Set.of("CORRECT", "PARTIAL", "INCORRECT");

    // ---------- contrato HTTP ----------

    public record DoneRequest(Boolean done) {}

    public record AnswerRequest(String answer) {}

    public record ResultRequest(String result) {}

    public record SubmitRequest(List<Integer> answers) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CourseProgress(UUID courseId, Integer percent) {}

    public record LessonState(UUID pieceId, String status, boolean done) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExamState(UUID pieceId, String status, int attempts, Integer best, Integer last, boolean passed) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopicState(UUID id, int percent, List<LessonState> lessons, ExamState exam) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Overview(Integer percent, int lessonsDone, int lessonsTotal, int examsPassed, int examsTotal, List<TopicState> topics) {}

    public record ExerciseAnswered(UUID attemptId, String solution, String explanation) {}

    public record StartedQuestion(String text, List<String> options) {}

    public record ExamStarted(UUID attemptId, List<StartedQuestion> questions) {}

    public record Started(ExamStarted exam, boolean created) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExerciseAttemptView(UUID id, int itemIndex, String answer, String selfResult, String aiResult, String aiComment, Instant createdAt) {}

    public record ExamAttemptView(UUID id, int correct, int total, int percent, boolean passed, Instant submittedAt) {}

    public record Attempts(String kind, List<?> attempts) {}

    private final StudyRepository repo;
    private final StudyProperties props;
    private final ObjectMapper json;

    public StudyService(StudyRepository repo, StudyProperties props, ObjectMapper json) {
        this.repo = repo;
        this.props = props;
        this.json = json;
    }

    // ---------- progreso ----------

    public List<CourseProgress> progress(AuthUser user) {
        Map<UUID, Integer> done = repo.lessonsDoneByCourse(user.id());
        Map<UUID, Integer> passed = repo.examsPassedByCourse(user.id(), props.passPercent());
        List<CourseProgress> out = new ArrayList<>();
        for (StudyRepository.CourseTotals t : repo.courseTotals(user.id())) {
            int total = t.lessons() + t.exams();
            out.add(new CourseProgress(t.courseId(), total == 0 ? null : percent(done.getOrDefault(t.courseId(), 0) + passed.getOrDefault(t.courseId(), 0), total)));
        }
        return out;
    }

    /** Lo que muestra el panel del adulto tutor de cada curso de un alumno (F08): el mismo cálculo que {@link #progress} con los números a la vista. */
    public record CourseSummary(UUID courseId, Integer percent, int lessonsDone, int lessonsTotal, int examsPassed, int examsTotal) {}

    public Map<UUID, CourseSummary> courseSummaries(UUID ownerId) {
        Map<UUID, Integer> done = repo.lessonsDoneByCourse(ownerId);
        Map<UUID, Integer> passed = repo.examsPassedByCourse(ownerId, props.passPercent());
        Map<UUID, CourseSummary> out = new LinkedHashMap<>();
        for (StudyRepository.CourseTotals t : repo.courseTotals(ownerId)) {
            int total = t.lessons() + t.exams();
            int lessonsDone = done.getOrDefault(t.courseId(), 0);
            int examsPassed = passed.getOrDefault(t.courseId(), 0);
            out.put(t.courseId(), new CourseSummary(t.courseId(), total == 0 ? null : percent(lessonsDone + examsPassed, total), lessonsDone, t.lessons(), examsPassed, t.exams()));
        }
        return out;
    }

    public Overview overview(AuthUser user, String rawCourseId) {
        return overviewFor(user.id(), id(rawCourseId));
    }

    /** El avance de un curso de {@code ownerId}; uno que no es suyo no existe. Lo usan el alumno y el panel de su adulto tutor. */
    public Overview overviewFor(UUID ownerId, UUID courseId) {
        if (!repo.courseExists(courseId, ownerId)) throw ApiException.notFound();

        Map<UUID, List<StudyRepository.ExamStat>> attempts = new HashMap<>();
        for (StudyRepository.ExamStat stat : repo.examStats(courseId, ownerId)) attempts.computeIfAbsent(stat.pieceId(), k -> new ArrayList<>()).add(stat);

        Map<UUID, List<StudyRepository.StudyPiece>> byTopic = new LinkedHashMap<>();
        for (StudyRepository.StudyPiece piece : repo.studyPieces(courseId, ownerId)) byTopic.computeIfAbsent(piece.topicId(), k -> new ArrayList<>()).add(piece);

        List<TopicState> topics = new ArrayList<>();
        int lessonsDone = 0, lessonsTotal = 0, examsPassed = 0, examsTotal = 0;
        for (Map.Entry<UUID, List<StudyRepository.StudyPiece>> entry : byTopic.entrySet()) {
            List<LessonState> lessons = new ArrayList<>();
            ExamState exam = null;
            int topicDone = 0, topicTotal = 0;
            for (StudyRepository.StudyPiece piece : entry.getValue()) {
                if ("LESSON".equals(piece.kind())) {
                    boolean done = piece.done() && "DONE".equals(piece.status());
                    lessons.add(new LessonState(piece.id(), piece.status(), done));
                    topicTotal++;
                    if (done) topicDone++;
                } else if ("EXAM".equals(piece.kind())) {
                    exam = examState(piece, attempts.getOrDefault(piece.id(), List.of()));
                    topicTotal++;
                    if (exam.passed()) topicDone++;
                }
            }
            lessonsTotal += lessons.size();
            lessonsDone += (int) lessons.stream().filter(LessonState::done).count();
            if (exam != null) {
                examsTotal++;
                if (exam.passed()) examsPassed++;
            }
            topics.add(new TopicState(entry.getKey(), topicTotal == 0 ? 0 : percent(topicDone, topicTotal), List.copyOf(lessons), exam));
        }
        int total = lessonsTotal + examsTotal;
        return new Overview(total == 0 ? null : percent(lessonsDone + examsPassed, total), lessonsDone, lessonsTotal, examsPassed, examsTotal, List.copyOf(topics));
    }

    private ExamState examState(StudyRepository.StudyPiece piece, List<StudyRepository.ExamStat> stats) {
        if (stats.isEmpty()) return new ExamState(piece.id(), piece.status(), 0, null, null, false);
        int best = 0;
        boolean passed = false;
        for (StudyRepository.ExamStat s : stats) {
            best = Math.max(best, ExamGrader.percent(s.correct(), s.total()));
            passed |= ExamGrader.passes(s.correct(), s.total(), props.passPercent());
        }
        StudyRepository.ExamStat last = stats.get(stats.size() - 1);
        return new ExamState(piece.id(), piece.status(), stats.size(), best, ExamGrader.percent(last.correct(), last.total()), passed);
    }

    private static int percent(int done, int total) {
        return (int) Math.round(done * 100.0 / total);
    }

    // ---------- lecciones ----------

    public void markDone(AuthUser user, String rawCourse, String rawPiece, DoneRequest body) {
        StudyRepository.PieceRow piece = piece(user, rawCourse, rawPiece);
        if (body == null || body.done() == null) throw ApiException.validation("Indica si la lección está hecha");
        if (!"LESSON".equals(piece.kind())) throw ApiException.validation("Solo se pueden marcar las lecciones");
        if (!"DONE".equals(piece.status())) throw StudyException.notReady();
        if (body.done()) repo.markDone(id(rawCourse), piece.id(), user.id());
        else repo.unmarkDone(piece.id(), user.id());
    }

    // ---------- ejercicios ----------

    public ExerciseAnswered sendAnswer(AuthUser user, String rawCourse, String rawPiece, String rawItem, AnswerRequest body) {
        StudyRepository.PieceRow piece = piece(user, rawCourse, rawPiece);
        if (!"EXERCISES".equals(piece.kind())) throw ApiException.notFound();
        int item = item(rawItem);
        if (!"DONE".equals(piece.status())) throw StudyException.notReady();
        JsonNode exercise = exercise(piece, item);
        String answer = validAnswer(body == null ? null : body.answer());
        checkSubmissionBudget(user);

        UUID attempt = repo.insertExerciseAttempt(id(rawCourse), piece.id(), user.id(), item, answer);
        repo.pruneExerciseAttempts(piece.id(), user.id(), props.maxAttemptsPerPiece());
        return new ExerciseAnswered(attempt, exercise.path("solution").asText(""), exercise.path("explanation").asText(""));
    }

    public void setResult(AuthUser user, String rawCourse, String rawPiece, String rawItem, String rawAttempt, ResultRequest body) {
        StudyRepository.PieceRow piece = piece(user, rawCourse, rawPiece);
        if (!"EXERCISES".equals(piece.kind())) throw ApiException.notFound();
        StudyRepository.ExerciseAttemptRow attempt = repo.exerciseAttempt(id(rawAttempt), id(rawCourse), piece.id(), item(rawItem), user.id()).orElseThrow(ApiException::notFound);
        if (body == null || body.result() == null || !RESULTS.contains(body.result())) throw ApiException.validation("El resultado no es válido");
        repo.setSelfResult(attempt.id(), user.id(), body.result());
    }

    private JsonNode exercise(StudyRepository.PieceRow piece, int item) {
        try {
            JsonNode exercises = json.readTree(piece.contentJson()).get("exercises");
            if (exercises == null || item >= exercises.size()) throw ApiException.notFound();
            return exercises.get(item);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dato guardado no válido");
        }
    }

    static String validAnswer(String answer) {
        if (answer == null || answer.strip().isEmpty()) throw ApiException.validation("Escribe tu respuesta");
        if (answer.length() > MAX_ANSWER) throw ApiException.validation("La respuesta es demasiado larga (máximo " + MAX_ANSWER + " caracteres)");
        for (int i = 0; i < answer.length(); i++) {
            char c = answer.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') throw ApiException.validation("La respuesta contiene caracteres no válidos");
        }
        return answer;
    }

    // ---------- exámenes ----------

    public Started startExam(AuthUser user, String rawCourse, String rawPiece) {
        StudyRepository.PieceRow piece = piece(user, rawCourse, rawPiece);
        if (!"EXAM".equals(piece.kind())) throw ApiException.notFound();
        if (!"DONE".equals(piece.status())) throw StudyException.notReady();
        List<ExamParser.Question> questions = questions(piece);

        repo.purgeOldOpenExams(user.id());
        var open = repo.openExam(piece.id(), user.id());
        boolean created = open.isEmpty();
        UUID attempt = open.orElseGet(() -> repo.insertExam(id(rawCourse), piece.id(), user.id()));
        return new Started(new ExamStarted(attempt, questions.stream().map(q -> new StartedQuestion(q.text(), q.options())).toList()), created);
    }

    public ExamGrader.Result submitExam(AuthUser user, String rawCourse, String rawPiece, String rawAttempt, SubmitRequest body) {
        StudyRepository.PieceRow piece = piece(user, rawCourse, rawPiece);
        if (!"EXAM".equals(piece.kind())) throw ApiException.notFound();
        StudyRepository.ExamAttemptRow attempt = repo.examAttempt(id(rawAttempt), piece.id(), user.id()).orElseThrow(ApiException::notFound);
        List<ExamParser.Question> questions = questions(piece);
        if ("SUBMITTED".equals(attempt.status())) return stored(attempt, questions); // entregar otra vez devuelve el mismo resultado (ADR-51)

        List<Integer> answers = validAnswers(body, questions);
        checkSubmissionBudget(user);
        ExamGrader.Result result = ExamGrader.grade(questions, answers, props.passPercent());
        if (repo.submitExam(attempt.id(), user.id(), write(answers), result.correct(), result.total()) == 0) {
            return stored(repo.examAttempt(attempt.id(), piece.id(), user.id()).orElseThrow(ApiException::notFound), questions); // otra pestaña se adelantó
        }
        repo.pruneExamAttempts(piece.id(), user.id(), props.maxAttemptsPerPiece());
        return result;
    }

    public ExamGrader.Result examAttempt(AuthUser user, String rawCourse, String rawPiece, String rawAttempt) {
        StudyRepository.PieceRow piece = piece(user, rawCourse, rawPiece);
        if (!"EXAM".equals(piece.kind())) throw ApiException.notFound();
        StudyRepository.ExamAttemptRow attempt = repo.examAttempt(id(rawAttempt), piece.id(), user.id()).orElseThrow(ApiException::notFound);
        if (!"SUBMITTED".equals(attempt.status())) throw StudyException.notSubmitted();
        return stored(attempt, questions(piece));
    }

    private ExamGrader.Result stored(StudyRepository.ExamAttemptRow attempt, List<ExamParser.Question> questions) {
        try {
            List<Integer> answers = json.readValue(attempt.answersJson(), new TypeReference<List<Integer>>() {});
            return ExamGrader.grade(questions, answers, props.passPercent());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dato guardado no válido");
        }
    }

    private static List<Integer> validAnswers(SubmitRequest body, List<ExamParser.Question> questions) {
        if (body == null || body.answers() == null || body.answers().size() != questions.size()) throw ApiException.validation("Hay que enviar una respuesta por pregunta");
        for (int i = 0; i < questions.size(); i++) {
            Integer chosen = body.answers().get(i);
            if (chosen != null && (chosen < 0 || chosen >= questions.get(i).options().size())) throw ApiException.validation("Hay una opción que no existe");
        }
        return java.util.Collections.unmodifiableList(new ArrayList<>(body.answers())); // con nulos: List.copyOf no los admite
    }

    private List<ExamParser.Question> questions(StudyRepository.PieceRow piece) {
        try {
            return json.convertValue(json.readTree(piece.contentJson()).get("questions"), new TypeReference<List<ExamParser.Question>>() {});
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Dato guardado no válido");
        }
    }

    // ---------- historial ----------

    public Attempts attempts(AuthUser user, String rawCourse, String rawPiece) {
        StudyRepository.PieceRow piece = piece(user, rawCourse, rawPiece);
        if ("EXERCISES".equals(piece.kind())) {
            return new Attempts("EXERCISES", repo.exerciseAttempts(piece.id(), user.id()).stream()
                    .map(a -> new ExerciseAttemptView(a.id(), a.itemIndex(), a.answer(), a.selfResult(), a.aiResult(), a.aiComment(), a.createdAt())).toList());
        }
        if ("EXAM".equals(piece.kind())) {
            return new Attempts("EXAM", repo.submittedExams(piece.id(), user.id()).stream().map(a -> new ExamAttemptView(a.id(), a.correct(), a.total(),
                    ExamGrader.percent(a.correct(), a.total()), ExamGrader.passes(a.correct(), a.total(), props.passPercent()), a.submittedAt())).toList());
        }
        throw ApiException.notFound();
    }

    // ---------- utilidades ----------

    private void checkSubmissionBudget(AuthUser user) {
        if (repo.submissionsLastHour(user.id()) >= props.maxSubmissionsPerHour()) throw StudyException.rateLimited();
    }

    private StudyRepository.PieceRow piece(AuthUser user, String rawCourse, String rawPiece) {
        return repo.piece(id(rawCourse), id(rawPiece), user.id()).orElseThrow(ApiException::notFound);
    }

    static UUID id(String raw) {
        try {
            return raw == null ? invalid() : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return invalid();
        }
    }

    private static UUID invalid() {
        throw ApiException.notFound();
    }

    /** El número de ejercicio de la ruta: un entero entre 0 y 7; cualquier otra cosa es un ejercicio que no existe. */
    static int item(String raw) {
        try {
            int item = Integer.parseInt(raw);
            if (item >= 0 && item <= 7) return item;
        } catch (NumberFormatException e) {
            // se trata abajo como inexistente
        }
        throw ApiException.notFound();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar");
        }
    }
}
