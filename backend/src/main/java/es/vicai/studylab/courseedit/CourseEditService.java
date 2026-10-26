package es.vicai.studylab.courseedit;

import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.course.CourseException;
import es.vicai.studylab.generation.CourseContentService;
import es.vicai.studylab.generation.GenerationProperties;
import es.vicai.studylab.safety.ContentGuard;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import es.vicai.studylab.web.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Editar un curso propio (F09, ADR-63): renombrar, reordenar, quitar y añadir temas y capítulos, escribir el texto de una lección y regenerar una pieza. Cada cambio
 * es una transacción que bloquea la fila del curso, comprueba que no esté en cola ni generándose y sube su versión. Lo nuevo no lo genera este servicio: crea piezas
 * pendientes y devuelve el curso a la cola de F05. Para un menor, lo que escribe pasa por {@link ContentGuard} antes de guardarse. Ni logs ni auditoría llevan los textos.
 */
@Service
public class CourseEditService {

    static final int MAX_TITLE = 120;
    static final int MAX_SUMMARY = 300;
    static final int MAX_OBJECTIVE = 200;
    static final int MAX_LESSON = 20_000;
    private static final Set<String> BUSY = Set.of("PENDING_GENERATION", "GENERATING");

    /** Lo que se hace dentro de la transacción con el curso ya bloqueado. */
    private interface Change {
        void apply(UUID courseId);
    }

    private final CourseEditRepository repo;
    private final CourseActionRepository actions;
    private final CourseEditProperties props;
    private final GenerationProperties generation;
    private final CourseContentService content;
    private final ContentGuard guard;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public CourseEditService(CourseEditRepository repo, CourseActionRepository actions, CourseEditProperties props, GenerationProperties generation, CourseContentService content,
                             ContentGuard guard, AuditService audit, TransactionTemplate tx) {
        this.repo = repo;
        this.actions = actions;
        this.props = props;
        this.generation = generation;
        this.content = content;
        this.guard = guard;
        this.audit = audit;
        this.tx = tx;
    }

    // ---------- temas ----------

    public CourseContentService.CourseDetailView renameTopic(AuthUser user, String rawCourse, String rawTopic, Map<String, Object> body, String ip) {
        UUID topicId = id(rawTopic);
        String title = text(body, "title", MAX_TITLE);
        String summary = text(body, "summary", MAX_SUMMARY);
        return edit(user, rawCourse, ip, List.of(title, summary), course -> {
            if (repo.renameTopic(topicId, course, user.id(), title, summary) == 0) throw ApiException.notFound();
        });
    }

    public CourseContentService.CourseDetailView reorderTopics(AuthUser user, String rawCourse, Map<String, Object> body, String ip) {
        List<UUID> ids = ids(body);
        return edit(user, rawCourse, ip, List.of(), course -> {
            requireSameSet(ids, repo.topicIds(course, user.id()));
            repo.orderTopics(course, user.id(), ids);
        });
    }

    public CourseContentService.CourseDetailView removeTopic(AuthUser user, String rawCourse, String rawTopic, String ip) {
        UUID topicId = id(rawTopic);
        return edit(user, rawCourse, ip, List.of(), course -> {
            List<UUID> topics = repo.topicIds(course, user.id());
            if (!topics.contains(topicId)) throw ApiException.notFound();
            if (topics.size() == 1) throw ApiException.validation("Un curso necesita al menos un tema");
            repo.deleteTopic(topicId, course, user.id());
        });
    }

    public CourseContentService.CourseDetailView addTopic(AuthUser user, String rawCourse, Map<String, Object> body, String ip) {
        String title = text(body, "title", MAX_TITLE);
        String summary = text(body, "summary", MAX_SUMMARY);
        Map<String, Object> chapter = object(body == null ? null : body.get("chapter"));
        String chapterTitle = text(chapter, "title", MAX_TITLE);
        String objective = text(chapter, "objective", MAX_OBJECTIVE);
        return edit(user, rawCourse, ip, List.of(title, summary, chapterTitle, objective), course -> {
            int pieces = 2 + (repo.examPerTopic(course, user.id()) ? 1 : 0);
            checkRoom(course, user.id(), pieces);
            aiAction(user.id());
            UUID topic = UUID.randomUUID();
            UUID chapterId = UUID.randomUUID();
            repo.insertTopic(topic, course, user.id(), repo.nextTopicPosition(course, user.id()), title, summary);
            repo.insertChapter(chapterId, topic, course, user.id(), 1, chapterTitle, objective);
            int seq = repo.nextSeq(course, user.id());
            repo.insertPiece(course, user.id(), topic, chapterId, "LESSON", seq++);
            repo.insertPiece(course, user.id(), topic, chapterId, "EXERCISES", seq++);
            if (repo.examPerTopic(course, user.id())) repo.insertPiece(course, user.id(), topic, null, "EXAM", seq);
            repo.queue(course, user.id());
        });
    }

    // ---------- capítulos ----------

    public CourseContentService.CourseDetailView renameChapter(AuthUser user, String rawCourse, String rawChapter, Map<String, Object> body, String ip) {
        UUID chapterId = id(rawChapter);
        String title = text(body, "title", MAX_TITLE);
        String objective = text(body, "objective", MAX_OBJECTIVE);
        return edit(user, rawCourse, ip, List.of(title, objective), course -> {
            if (repo.renameChapter(chapterId, course, user.id(), title, objective) == 0) throw ApiException.notFound();
        });
    }

    public CourseContentService.CourseDetailView reorderChapters(AuthUser user, String rawCourse, String rawTopic, Map<String, Object> body, String ip) {
        UUID topicId = id(rawTopic);
        List<UUID> ids = ids(body);
        return edit(user, rawCourse, ip, List.of(), course -> {
            if (!repo.topicExists(topicId, course, user.id())) throw ApiException.notFound();
            requireSameSet(ids, repo.chapterIds(topicId, course, user.id()));
            repo.orderChapters(topicId, course, user.id(), ids);
        });
    }

    public CourseContentService.CourseDetailView removeChapter(AuthUser user, String rawCourse, String rawChapter, String ip) {
        UUID chapterId = id(rawChapter);
        return edit(user, rawCourse, ip, List.of(), course -> {
            UUID topic = repo.topicOfChapter(chapterId, course, user.id()).orElseThrow(ApiException::notFound);
            if (repo.chapterIds(topic, course, user.id()).size() == 1) throw ApiException.validation("Un tema necesita al menos un capítulo");
            repo.deleteChapter(chapterId, course, user.id());
            repo.markExamOutdated(topic, course, user.id());
        });
    }

    public CourseContentService.CourseDetailView addChapter(AuthUser user, String rawCourse, String rawTopic, Map<String, Object> body, String ip) {
        UUID topicId = id(rawTopic);
        String title = text(body, "title", MAX_TITLE);
        String objective = text(body, "objective", MAX_OBJECTIVE);
        return edit(user, rawCourse, ip, List.of(title, objective), course -> {
            if (!repo.topicExists(topicId, course, user.id())) throw ApiException.notFound();
            checkRoom(course, user.id(), 2);
            aiAction(user.id());
            UUID chapterId = UUID.randomUUID();
            repo.insertChapter(chapterId, topicId, course, user.id(), repo.nextChapterPosition(topicId, user.id()), title, objective);
            int seq = repo.nextSeq(course, user.id());
            repo.insertPiece(course, user.id(), topicId, chapterId, "LESSON", seq++);
            repo.insertPiece(course, user.id(), topicId, chapterId, "EXERCISES", seq);
            repo.markExamOutdated(topicId, course, user.id());
            repo.queue(course, user.id());
        });
    }

    // ---------- piezas ----------

    public CourseContentService.CourseDetailView editLesson(AuthUser user, String rawCourse, String rawPiece, Map<String, Object> body, String ip) {
        UUID pieceId = id(rawPiece);
        String markdown = markdown(body);
        return edit(user, rawCourse, ip, List.of(markdown), course -> {
            CourseEditRepository.PieceRow piece = repo.piece(pieceId, course, user.id()).orElseThrow(ApiException::notFound);
            if (!"LESSON".equals(piece.kind())) throw ApiException.validation("Solo se puede editar el texto de una lección");
            if (!"DONE".equals(piece.status())) throw new ApiException(HttpStatus.CONFLICT, "PIECE_NOT_READY", "Esta lección todavía no está lista");
            repo.setLessonText(pieceId, course, user.id(), markdown);
            repo.markLessonDependentsOutdated(piece.chapterId(), piece.topicId(), course, user.id());
        });
    }

    /** Regenerar una pieza ya hecha, solo por petición explícita del propietario (amplía ADR-42). */
    public CourseContentService.CourseDetailView regenerate(AuthUser user, String rawCourse, String rawPiece, String ip) {
        UUID pieceId = id(rawPiece);
        return edit(user, rawCourse, ip, List.of(), course -> {
            CourseEditRepository.PieceRow piece = repo.piece(pieceId, course, user.id()).orElseThrow(ApiException::notFound);
            if (!"DONE".equals(piece.status())) throw new ApiException(HttpStatus.CONFLICT, "PIECE_NOT_READY", "Esta parte del curso todavía no está lista");
            aiAction(user.id());
            repo.deleteDependents(pieceId, user.id());
            repo.resetPiece(pieceId, course, user.id());
            if ("LESSON".equals(piece.kind())) repo.markLessonDependentsOutdated(piece.chapterId(), piece.topicId(), course, user.id());
            repo.queue(course, user.id());
        });
    }

    // ---------- el cuerpo común de toda edición ----------

    private CourseContentService.CourseDetailView edit(AuthUser user, String rawCourse, String ip, List<String> texts, Change change) {
        UUID courseId = id(rawCourse);
        if (!repo.courseExists(courseId, user.id())) throw ApiException.notFound();
        if (user.kind() == UserKind.MINOR) filter(user, courseId, texts, ip);
        tx.executeWithoutResult(status -> {
            String state = repo.lockCourse(courseId, user.id()).orElseThrow(ApiException::notFound);
            if (BUSY.contains(state)) throw CourseEditException.busy();
            change.apply(courseId);
            repo.bumpVersion(courseId, user.id());
        });
        return content.detail(user, rawCourse);
    }

    /** Lo que escribe un menor pasa por el filtro antes de guardarse o de enviarse a la IA; si salta, no se guarda nada y su adulto tutor lo ve. */
    private void filter(AuthUser user, UUID courseId, List<String> texts, String ip) {
        for (String text : texts) {
            if (guard.localCheck(text).isPresent()) {
                audit.record("COURSE_EDIT_BLOCKED", AuditService.DENIED, user.id(), user.id(), ip, Map.of("courseId", courseId.toString()));
                throw CourseEditException.notAllowed();
            }
        }
    }

    private void checkRoom(UUID courseId, UUID ownerId, int adding) {
        if (repo.pieceCount(courseId, ownerId) + adding > generation.maxPieces()) throw CourseEditException.tooBig();
    }

    /** Añadir con IA y regenerar cuentan para un límite por hora y usuario, contado en la BD (ADR-63). */
    private void aiAction(UUID userId) {
        actions.lockUser(userId);
        actions.purgeOld(userId);
        if (actions.inTheLastHour(userId, "AI_EDIT") >= props.aiActionsPerHour()) throw CourseEditException.rateLimited();
        actions.record(userId, "AI_EDIT");
    }

    // ---------- validación ----------

    private static void requireSameSet(List<UUID> sent, List<UUID> current) {
        Set<UUID> distinct = new HashSet<>(sent);
        if (distinct.size() != sent.size() || sent.size() != current.size() || !distinct.containsAll(current)) throw ApiException.validation("El orden no coincide con lo que hay");
    }

    private static List<UUID> ids(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        if (!(raw instanceof List<?> list)) throw ApiException.validation("Falta la lista de identificadores");
        List<UUID> out = new ArrayList<>();
        for (Object item : list) {
            try {
                out.add(UUID.fromString(String.valueOf(item)));
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("Hay un identificador que no es válido");
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object raw) {
        if (raw instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw ApiException.validation("Falta el primer capítulo del tema");
    }

    /** Un texto corto: entre 1 y {@code max} caracteres y sin caracteres de control. */
    static String text(Map<String, Object> body, String field, int max) {
        Object raw = body == null ? null : body.get(field);
        if (!(raw instanceof String text)) throw ApiException.validation("Falta " + field);
        String value = text.strip();
        if (value.isEmpty() || value.length() > max) throw ApiException.validation("El texto debe tener entre 1 y " + max + " caracteres");
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) throw ApiException.validation("El texto lleva caracteres no permitidos");
        }
        return value;
    }

    /** El texto de una lección: entre 1 y 20 000 caracteres; solo se admiten como control los saltos de línea y los tabuladores. */
    static String markdown(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("markdown");
        if (!(raw instanceof String text) || text.strip().isEmpty() || text.length() > MAX_LESSON) throw ApiException.validation("El texto debe tener entre 1 y " + MAX_LESSON + " caracteres");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') throw ApiException.validation("El texto lleva caracteres no permitidos");
        }
        return text;
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
