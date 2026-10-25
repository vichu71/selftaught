package es.vicai.studylab.course;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiRequest;
import es.vicai.studylab.ai.AiTask;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.safety.BlockCategory;
import es.vicai.studylab.safety.ContentGuard;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import es.vicai.studylab.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asistente de creación de curso (F04). La consulta se guarda en el servidor con sus preguntas ya validadas (ADR-37); el cliente solo devuelve
 * ids de opciones. Una llamada al modelo con el tema como DATO (ADR-38), filtro de menores en dos capas (ADR-39) y límites por usuario contados
 * en la base de datos (ADR-41). Nunca se envía al modelo texto de un documento: solo el tema.
 */
@Service
public class CourseService {

    public static final int MIN_TOPIC = 3;
    public static final int MAX_TOPIC = 200;
    public static final int MAX_DOCUMENTS = 5;

    // ---------- contrato HTTP ----------

    public record IntakeRequest(String topic, List<String> documentIds) {}

    public record AnswerRequest(String questionId, String optionId) {}

    public record CreateRequest(String intakeId, List<AnswerRequest> answers) {}

    public record OptionView(String id, String label, @JsonProperty("isDefault") boolean isDefault) {}

    public record QuestionView(String id, String text, List<OptionView> options) {}

    public record IntakeView(UUID intakeId, List<QuestionView> questions) {}

    public record CourseView(UUID id, String title, String status, CourseSheet sheet, List<CourseRepository.DocumentRef> documents,
                             Instant createdAt) {}

    public record Created(CourseView course, boolean created) {}

    private final CourseRepository courses;
    private final CourseProperties props;
    private final AiGateway gateway;
    private final IntakeParser parser;
    private final SheetFactory sheets;
    private final ContentGuard guard;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;

    public CourseService(CourseRepository courses, CourseProperties props, AiGateway gateway, IntakeParser parser, SheetFactory sheets,
                         ContentGuard guard, AuditService audit, TransactionTemplate transaction, ObjectMapper json) {
        this.courses = courses;
        this.props = props;
        this.gateway = gateway;
        this.parser = parser;
        this.sheets = sheets;
        this.guard = guard;
        this.audit = audit;
        this.transaction = transaction;
        this.json = json;
    }

    // ---------- consulta ----------

    public IntakeView intake(AuthUser user, IntakeRequest request, String ip) {
        String topic = validTopic(request == null ? null : request.topic());
        List<UUID> documents = validDocuments(request.documentIds());
        if (courses.readyDocuments(user.id(), documents).size() != documents.size()) throw ApiException.notFound();

        if (courses.pendingCourses(user.id()) >= props.maxPending()) throw CourseException.courseLimitReached(props.maxPending());
        if (courses.intakesInTheLastHour(user.id()) >= props.intakesPerHour()) throw CourseException.rateLimited();

        boolean minor = user.kind() == UserKind.MINOR;
        UUID intakeId = UUID.randomUUID();
        if (minor) {
            Optional<BlockCategory> local = guard.localCheck(topic);
            if (local.isPresent()) throw block(user, intakeId, topic, documents, local.get().name(), ip);
        }

        IntakeParser.Result result = askModel(user, topic, minor);
        if (minor && !result.appropriate()) {
            throw block(user, intakeId, topic, documents, result.category().equals("NONE") ? "OTHER" : result.category(), ip);
        }

        courses.insertIntake(intakeId, user.id(), topic, write(documents.stream().map(UUID::toString).toList()), "OPEN",
                write(result.questions()), null, props.intakeTtl().toSeconds());
        audit.record("COURSE_INTAKE_STARTED", AuditService.OK, user.id(), user.id(), ip, Map.of("intakeId", intakeId.toString()));
        return new IntakeView(intakeId, result.questions().stream().map(CourseService::view).toList());
    }

    /** Una llamada, con un reintento si el JSON no es válido; si sigue mal, 0 preguntas (ADR-38). Un fallo de la IA sube tal cual. */
    private IntakeParser.Result askModel(AuthUser user, String topic, boolean minor) {
        AiRequest request = AiRequest.of(AiTask.INTAKE, user.id(), "intake").data("tema", topic).data("audiencia", minor ? "escolar" : "general");
        for (int attempt = 0; attempt < 2; attempt++) {
            Optional<IntakeParser.Result> parsed = parser.parse(gateway.complete(request).text());
            if (parsed.isPresent()) return parsed.get();
        }
        return new IntakeParser.Result(true, "NONE", List.of());
    }

    private ApiException block(AuthUser user, UUID intakeId, String topic, List<UUID> documents, String category, String ip) {
        courses.insertIntake(intakeId, user.id(), topic, write(documents.stream().map(UUID::toString).toList()), "BLOCKED", null, category,
                props.intakeTtl().toSeconds());
        // La auditoría lleva la categoría y el id; el texto del tema queda solo en la consulta, que verá el tutor (F08)
        audit.record("COURSE_TOPIC_BLOCKED", AuditService.DENIED, user.id(), user.id(), ip,
                Map.of("intakeId", intakeId.toString(), "category", category));
        return CourseException.topicNotAllowed();
    }

    // ---------- creación ----------

    public Created create(AuthUser user, CreateRequest request, String ip) {
        UUID intakeId = parse(request == null ? null : request.intakeId());
        if (intakeId == null) throw ApiException.notFound();
        CourseRepository.IntakeRow intake = courses.findIntake(intakeId, user.id()).orElseThrow(ApiException::notFound);
        if ("BLOCKED".equals(intake.status())) throw ApiException.notFound();
        if ("CONSUMED".equals(intake.status())) return new Created(existing(intakeId, user.id()), false);
        if (intake.expired()) throw CourseException.intakeExpired();
        if (courses.pendingCourses(user.id()) >= props.maxPending()) throw CourseException.courseLimitReached(props.maxPending());

        List<IntakeParser.Question> questions = read(intake.questionsJson(), new TypeReference<List<IntakeParser.Question>>() {});
        List<UUID> documents = read(intake.documentIdsJson(), new TypeReference<List<String>>() {}).stream().map(UUID::fromString).toList();
        if (courses.readyDocuments(user.id(), documents).size() != documents.size()) throw ApiException.notFound();

        Map<String, String> answers = new HashMap<>();
        for (AnswerRequest a : request.answers() == null ? List.<AnswerRequest>of() : request.answers()) {
            if (a == null || a.questionId() == null || a.optionId() == null || answers.put(a.questionId(), a.optionId()) != null) {
                throw ApiException.validation("Las respuestas no son válidas");
            }
        }
        CourseSheet sheet;
        try {
            sheet = sheets.build(intake.topic(), questions, answers, documents);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("Las respuestas no son válidas");
        }

        UUID courseId = UUID.randomUUID();
        Boolean inserted = transaction.execute(status -> {
            if (!courses.insertCourse(courseId, user.id(), intakeId, intake.topic(), write(sheet))) return false; // otro «Aceptar» se adelantó
            for (UUID doc : documents) courses.linkDocument(courseId, doc, user.id());
            courses.consumeIntake(intakeId, user.id());
            audit.record("COURSE_CREATED", AuditService.OK, user.id(), user.id(), ip, Map.of("courseId", courseId.toString()));
            return true;
        });
        return new Created(existing(intakeId, user.id()), Boolean.TRUE.equals(inserted));
    }

    // ---------- lectura ----------

    public List<CourseView> list(AuthUser user) {
        return courses.listCourses(user.id()).stream().map(row -> view(row, user.id())).toList();
    }

    public CourseView get(AuthUser user, String rawId) {
        UUID id = parse(rawId);
        if (id == null) throw ApiException.notFound();
        return view(courses.findCourse(id, user.id()).orElseThrow(ApiException::notFound), user.id());
    }

    private CourseView existing(UUID intakeId, UUID ownerId) {
        return view(courses.findCourseByIntake(intakeId, ownerId).orElseThrow(ApiException::notFound), ownerId);
    }

    private CourseView view(CourseRepository.CourseRow row, UUID ownerId) {
        return new CourseView(row.id(), row.title(), row.status(), read(row.sheetJson(), new TypeReference<CourseSheet>() {}),
                courses.courseDocuments(row.id(), ownerId), row.createdAt());
    }

    /** Al cliente solo llegan el id, el texto y las opciones: nunca el campo de la ficha ni los valores, que son internos. */
    private static QuestionView view(IntakeParser.Question q) {
        List<OptionView> options = new ArrayList<>();
        for (IntakeParser.Option o : q.options()) options.add(new OptionView(o.id(), o.label(), o.isDefault()));
        return new QuestionView(q.id(), q.text(), options);
    }

    // ---------- validación ----------

    static String validTopic(String raw) {
        if (raw == null) throw ApiException.validation("Escribe qué quieres estudiar");
        String topic = raw.strip();
        int length = topic.codePointCount(0, topic.length());
        if (length < MIN_TOPIC || length > MAX_TOPIC) throw ApiException.validation("El tema debe tener entre 3 y 200 caracteres");
        if (topic.chars().anyMatch(Character::isISOControl)) throw ApiException.validation("El tema contiene caracteres no válidos");
        if (topic.codePoints().noneMatch(Character::isLetterOrDigit)) throw ApiException.validation("El tema debe tener letras o números");
        return topic;
    }

    private static List<UUID> validDocuments(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        if (raw.size() > MAX_DOCUMENTS) throw ApiException.validation("Puedes elegir como máximo " + MAX_DOCUMENTS + " documentos");
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (String s : raw) {
            UUID id = parse(s);
            if (id == null || !ids.add(id)) throw ApiException.validation("Los documentos elegidos no son válidos");
        }
        return List.copyOf(ids);
    }

    private static UUID parse(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- JSON ----------

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar", e);
        }
    }

    private <T> T read(String text, TypeReference<T> type) {
        try {
            return json.readValue(text == null ? "[]" : text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dato guardado no válido", e);
        }
    }
}
