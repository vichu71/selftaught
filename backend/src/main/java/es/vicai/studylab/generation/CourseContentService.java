package es.vicai.studylab.generation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.course.CourseRepository;
import es.vicai.studylab.course.CourseService;
import es.vicai.studylab.course.CourseSheet;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.web.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lo que el usuario puede leer de un curso ya generado: el detalle con su temario y su progreso, cada pieza, la exportación, y «Reanudar».
 * Todo se filtra por propietario: un curso o una pieza ajenos responden igual que uno inexistente (404). Nada de lo que sale lleva ids de
 * documentos ni nombres: las fuentes se citan como «Fuente n».
 */
@Service
public class CourseContentService {

    public static final int EXPORT_VERSION = 1;

    // ---------- contrato HTTP ----------

    public record Progress(int done, int total) {}

    public record PieceRef(UUID id, String status) {}

    public record ChapterView(UUID id, String title, String objective, PieceRef lesson, PieceRef exercises) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopicView(UUID id, String title, String summary, List<ChapterView> chapters, PieceRef exam) {}

    /** El curso de F04 más lo que añade F05; {@code stopReason} solo aparece si el curso está parado. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CourseDetailView(UUID id, String title, String status, CourseSheet sheet, List<CourseRepository.DocumentRef> documents,
                                   Instant createdAt, String stopReason, Progress progress, BigDecimal costUsd, boolean generatedByAi,
                                   List<TopicView> outline) {}

    public record SourceView(int n, Integer page) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PieceView(UUID id, String kind, String status, String title, boolean generatedByAi, String markdown,
                            List<ExercisesParser.Item> exercises, List<ExamParser.Question> questions, List<SourceView> sources) {}

    private final CourseService courses;
    private final CourseContentRepository content;
    private final GenerationProperties props;
    private final AuditService audit;
    private final ObjectMapper json;

    public CourseContentService(CourseService courses, CourseContentRepository content, GenerationProperties props, AuditService audit,
                                ObjectMapper json) {
        this.courses = courses;
        this.content = content;
        this.props = props;
        this.audit = audit;
        this.json = json;
    }

    // ---------- detalle ----------

    public CourseDetailView detail(AuthUser user, String rawId) {
        UUID id = parse(rawId);
        if (id == null) throw ApiException.notFound();
        return detail(user, courses.get(user, id.toString()));
    }

    private CourseDetailView detail(AuthUser user, CourseService.CourseView base) {
        UUID owner = user.id();
        List<CourseContentRepository.PieceStatus> pieces = content.pieces(base.id(), owner);
        Map<UUID, CourseContentRepository.PieceStatus> lessons = new HashMap<>();
        Map<UUID, CourseContentRepository.PieceStatus> exercises = new HashMap<>();
        Map<UUID, CourseContentRepository.PieceStatus> exams = new HashMap<>();
        int done = 0;
        for (CourseContentRepository.PieceStatus piece : pieces) {
            if ("DONE".equals(piece.status())) done++;
            switch (piece.kind()) {
                case "LESSON" -> lessons.put(piece.chapterId(), piece);
                case "EXERCISES" -> exercises.put(piece.chapterId(), piece);
                default -> exams.put(piece.topicId(), piece);
            }
        }
        Map<UUID, List<ChapterView>> chaptersByTopic = new LinkedHashMap<>();
        for (CourseContentRepository.ChapterRow chapter : content.chapters(base.id(), owner)) {
            chaptersByTopic.computeIfAbsent(chapter.topicId(), k -> new ArrayList<>())
                    .add(new ChapterView(chapter.id(), chapter.title(), chapter.objective(), ref(lessons.get(chapter.id())), ref(exercises.get(chapter.id()))));
        }
        List<TopicView> outline = new ArrayList<>();
        for (CourseContentRepository.TopicRow topic : content.topics(base.id(), owner)) {
            outline.add(new TopicView(topic.id(), topic.title(), topic.summary(), chaptersByTopic.getOrDefault(topic.id(), List.of()), ref(exams.get(topic.id()))));
        }
        return new CourseDetailView(base.id(), base.title(), base.status(), base.sheet(), base.documents(), base.createdAt(),
                content.stopReason(base.id(), owner).orElse(null), new Progress(done, pieces.size()), content.cost(base.id(), owner), true, outline);
    }

    private static PieceRef ref(CourseContentRepository.PieceStatus piece) {
        return piece == null ? null : new PieceRef(piece.id(), piece.status());
    }

    // ---------- pieza ----------

    public PieceView piece(AuthUser user, String rawCourseId, String rawPieceId) {
        UUID courseId = parse(rawCourseId);
        UUID pieceId = parse(rawPieceId);
        if (courseId == null || pieceId == null) throw ApiException.notFound();
        CourseContentRepository.PieceContent piece = content.piece(courseId, pieceId, user.id()).orElseThrow(ApiException::notFound);
        boolean done = "DONE".equals(piece.status());
        return new PieceView(piece.id(), piece.kind(), piece.status(), piece.title(), true,
                done && "LESSON".equals(piece.kind()) ? piece.markdown() : null,
                done && "EXERCISES".equals(piece.kind()) ? exercises(piece) : null,
                done && "EXAM".equals(piece.kind()) ? questions(piece) : null,
                done && "LESSON".equals(piece.kind()) ? sources(piece) : List.of());
    }

    private List<ExercisesParser.Item> exercises(CourseContentRepository.PieceContent piece) {
        return read(piece.contentJson(), "exercises", new TypeReference<List<ExercisesParser.Item>>() {});
    }

    private List<ExamParser.Question> questions(CourseContentRepository.PieceContent piece) {
        return read(piece.contentJson(), "questions", new TypeReference<List<ExamParser.Question>>() {});
    }

    /** Las fuentes que cita la lección, sin el documento: solo el número y la página. */
    private List<SourceView> sources(CourseContentRepository.PieceContent piece) {
        if (piece.sourcesJson() == null) return List.of();
        try {
            List<SourceView> views = new ArrayList<>();
            for (JsonNode node : json.readTree(piece.sourcesJson())) {
                views.add(new SourceView(node.get("n").asInt(), node.hasNonNull("page") ? node.get("page").asInt() : null));
            }
            return views;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dato guardado no válido");
        }
    }

    private <T> T read(String text, String field, TypeReference<T> type) {
        try {
            return json.convertValue(json.readTree(text).get(field), type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Dato guardado no válido");
        }
    }

    // ---------- reanudar ----------

    public CourseDetailView resume(AuthUser user, String rawId, String ip) {
        UUID id = parse(rawId);
        if (id == null) throw ApiException.notFound();
        if (content.resume(id, user.id(), props.resumeCooldown().toSeconds()) == 0) {
            String status = content.status(id, user.id()).orElseThrow(ApiException::notFound);
            if (!"PAUSED".equals(status)) throw GenerationException.notPaused();
            throw GenerationException.resumeTooSoon();
        }
        audit.record("COURSE_GENERATION_RESUMED", AuditService.OK, user.id(), user.id(), ip, Map.of("courseId", id.toString()));
        return detail(user, courses.get(user, id.toString()));
    }

    // ---------- exportación ----------

    /** El curso entero en un JSON versionado: solo lo hecho, con las fuentes como «Fuente n» y sin ids, nombres de documento ni datos del usuario. */
    public Map<String, Object> export(AuthUser user, String rawId) {
        UUID id = parse(rawId);
        if (id == null) throw ApiException.notFound();
        CourseService.CourseView course = courses.get(user, id.toString());
        UUID owner = user.id();

        Map<UUID, CourseContentRepository.PieceContent> lessons = new HashMap<>();
        Map<UUID, CourseContentRepository.PieceContent> exercises = new HashMap<>();
        Map<UUID, CourseContentRepository.PieceContent> exams = new HashMap<>();
        for (CourseContentRepository.PieceContent piece : content.donePieces(id, owner)) {
            switch (piece.kind()) {
                case "LESSON" -> lessons.put(piece.chapterId(), piece);
                case "EXERCISES" -> exercises.put(piece.chapterId(), piece);
                default -> exams.put(piece.topicId(), piece);
            }
        }
        Map<UUID, List<CourseContentRepository.ChapterRow>> chaptersByTopic = new LinkedHashMap<>();
        for (CourseContentRepository.ChapterRow chapter : content.chapters(id, owner)) {
            chaptersByTopic.computeIfAbsent(chapter.topicId(), k -> new ArrayList<>()).add(chapter);
        }

        List<Object> topics = new ArrayList<>();
        for (CourseContentRepository.TopicRow topic : content.topics(id, owner)) {
            List<Object> chapters = new ArrayList<>();
            for (CourseContentRepository.ChapterRow chapter : chaptersByTopic.getOrDefault(topic.id(), List.of())) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("title", chapter.title());
                out.put("objective", chapter.objective());
                CourseContentRepository.PieceContent lesson = lessons.get(chapter.id());
                if (lesson != null) {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("markdown", lesson.markdown());
                    l.put("sources", exportSources(lesson));
                    out.put("lesson", l);
                }
                CourseContentRepository.PieceContent exercise = exercises.get(chapter.id());
                if (exercise != null) out.put("exercises", exercises(exercise));
                chapters.add(out);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("title", topic.title());
            out.put("summary", topic.summary());
            out.put("chapters", chapters);
            CourseContentRepository.PieceContent exam = exams.get(topic.id());
            if (exam != null) out.put("exam", Map.of("questions", questions(exam)));
            topics.add(out);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", EXPORT_VERSION);
        root.put("title", course.title());
        root.put("generatedByAi", true);
        root.put("topics", topics);
        return root;
    }

    private List<Map<String, Object>> exportSources(CourseContentRepository.PieceContent lesson) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SourceView source : sources(lesson)) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("label", "Fuente " + source.n());
            if (source.page() != null) s.put("page", source.page());
            out.add(s);
        }
        return out;
    }

    private static UUID parse(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
