package es.vicai.studylab.generation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Escritura de la generación (ADR-42): reclamar un curso de la cola, guardar el temario y las piezas, y dejar cada pieza y el curso en su estado.
 * SQL siempre parametrizado y con el propietario en cada consulta sobre el contenido del curso.
 */
@Repository
public class GenerationRepository {

    /** Un curso reclamado. {@code minor} dice si el propietario es un menor (filtro de contenido y audiencia escolar). */
    public record Claim(UUID id, UUID ownerId, String sheetJson, boolean minor) {}

    /** Una pieza por generar, con el contexto del temario que necesita su petición. */
    public record PieceRow(UUID id, UUID topicId, UUID chapterId, String kind, int seq, String topicTitle, String topicSummary,
                           String chapterTitle, String chapterObjective) {}

    public record ChapterRef(String title, String objective) {}

    /** Cuántas piezas hay en cada estado. */
    public record Counts(int done, int failed, int pending) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public GenerationRepository(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    // ---------- la cola de cursos ----------

    /**
     * Reclama el curso pendiente más antiguo de un propietario activo que no tenga otro curso generándose ni un cerrojo vigente, y lo marca
     * {@code GENERATING}. {@code SKIP LOCKED}: varios hilos pueden pedir a la vez sin pisarse. {@code skip} son los cursos ya vistos en esta pasada.
     */
    public Optional<Claim> claimNext(Set<UUID> skip) {
        String skipped = "{" + String.join(",", skip.stream().map(UUID::toString).toList()) + "}";
        return jdbc.sql("""
                UPDATE course c SET status = 'GENERATING', updated_at = now()
                FROM app_user u
                WHERE u.id = c.owner_id AND c.id = (
                    SELECT k.id FROM course k JOIN app_user a ON a.id = k.owner_id
                    WHERE k.status = 'PENDING_GENERATION' AND a.status = 'ACTIVE'
                      AND k.id <> ALL (CAST(:skip AS uuid[]))
                      AND NOT EXISTS (SELECT 1 FROM course g WHERE g.owner_id = k.owner_id AND g.status = 'GENERATING')
                      AND NOT EXISTS (SELECT 1 FROM ai_generation_lock l WHERE l.user_id = k.owner_id AND l.expires_at > now())
                    ORDER BY k.created_at, k.id LIMIT 1 FOR UPDATE OF k SKIP LOCKED)
                RETURNING c.id, c.owner_id, c.sheet::text AS sheet, u.kind""")
                .param("skip", skipped)
                .query((rs, row) -> new Claim(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("sheet"),
                        "MINOR".equals(rs.getString("kind"))))
                .optional();
    }

    /** Devuelve a la cola un curso reclamado, sin cambiar nada más. */
    public void requeue(UUID courseId, UUID ownerId) {
        jdbc.sql("UPDATE course SET status = 'PENDING_GENERATION', updated_at = now() WHERE id = :id AND owner_id = :owner AND status = 'GENERATING'")
                .param("id", courseId).param("owner", ownerId).update();
    }

    /** Al arrancar nada se está generando de verdad: lo que quedó a medias vuelve a la cola y sus cerrojos se liberan. */
    public void recoverInterrupted() {
        jdbc.sql("DELETE FROM ai_generation_lock WHERE user_id IN (SELECT owner_id FROM course WHERE status = 'GENERATING')").update();
        jdbc.sql("UPDATE course SET status = 'PENDING_GENERATION', updated_at = now() WHERE status = 'GENERATING'").update();
    }

    /** Termina la pasada del curso: {@code status} y, si procede, el motivo de la parada. */
    public void finish(UUID courseId, UUID ownerId, String status, String stopReason) {
        jdbc.sql("""
                UPDATE course SET status = :status, stop_reason = :reason, updated_at = now(),
                       completed_at = CASE WHEN :status = 'READY' THEN now() ELSE completed_at END
                WHERE id = :id AND owner_id = :owner AND status = 'GENERATING'""")
                .param("status", status).param("reason", stopReason).param("id", courseId).param("owner", ownerId).update();
    }

    // ---------- temario y piezas ----------

    public boolean hasOutline(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM course_topic WHERE course_id = :course AND owner_id = :owner)")
                .param("course", courseId).param("owner", ownerId).query(Boolean.class).single();
    }

    /** Guarda de una vez el temario y todas las piezas (lección y ejercicios por capítulo, examen al final de cada tema). */
    public void saveOutline(UUID courseId, UUID ownerId, OutlineParser.Outline outline, boolean examPerTopic) {
        transaction.executeWithoutResult(status -> {
            int seq = 1;
            int topicPosition = 1;
            for (OutlineParser.Topic topic : outline.topics()) {
                UUID topicId = UUID.randomUUID();
                jdbc.sql("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (:id, :course, :owner, :position, :title, :summary)")
                        .param("id", topicId).param("course", courseId).param("owner", ownerId).param("position", topicPosition++)
                        .param("title", topic.title()).param("summary", topic.summary()).update();
                int chapterPosition = 1;
                for (OutlineParser.Chapter chapter : topic.chapters()) {
                    UUID chapterId = UUID.randomUUID();
                    jdbc.sql("""
                            INSERT INTO course_chapter (id, topic_id, course_id, owner_id, position, title, objective)
                            VALUES (:id, :topic, :course, :owner, :position, :title, :objective)""")
                            .param("id", chapterId).param("topic", topicId).param("course", courseId).param("owner", ownerId)
                            .param("position", chapterPosition++).param("title", chapter.title()).param("objective", chapter.objective()).update();
                    insertPiece(courseId, ownerId, topicId, chapterId, "LESSON", seq++);
                    insertPiece(courseId, ownerId, topicId, chapterId, "EXERCISES", seq++);
                }
                if (examPerTopic) insertPiece(courseId, ownerId, topicId, null, "EXAM", seq++);
            }
        });
    }

    private void insertPiece(UUID courseId, UUID ownerId, UUID topicId, UUID chapterId, String kind, int seq) {
        jdbc.sql("""
                INSERT INTO course_piece (id, course_id, owner_id, topic_id, chapter_id, kind, seq)
                VALUES (:id, :course, :owner, :topic, :chapter, :kind, :seq)""")
                .param("id", UUID.randomUUID()).param("course", courseId).param("owner", ownerId).param("topic", topicId)
                .param("chapter", chapterId).param("kind", kind).param("seq", seq).update();
    }

    /** Las piezas que faltan (pendientes o falladas), en orden de generación. Las ya hechas no salen nunca. */
    public List<PieceRow> piecesToGenerate(UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT p.id, p.topic_id, p.chapter_id, p.kind, p.seq, t.title AS topic_title, t.summary AS topic_summary,
                       ch.title AS chapter_title, ch.objective AS chapter_objective
                FROM course_piece p
                JOIN course_topic t ON t.id = p.topic_id AND t.owner_id = p.owner_id
                LEFT JOIN course_chapter ch ON ch.id = p.chapter_id AND ch.owner_id = p.owner_id
                WHERE p.course_id = :course AND p.owner_id = :owner AND p.status IN ('PENDING', 'FAILED')
                ORDER BY p.seq""")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new PieceRow(rs.getObject("id", UUID.class), rs.getObject("topic_id", UUID.class),
                        rs.getObject("chapter_id", UUID.class), rs.getString("kind"), rs.getInt("seq"), rs.getString("topic_title"),
                        rs.getString("topic_summary"), rs.getString("chapter_title"), rs.getString("chapter_objective")))
                .list();
    }

    /** El Markdown de la lección ya hecha de un capítulo: los ejercicios se escriben a partir de él. */
    public Optional<String> doneLesson(UUID chapterId, UUID ownerId) {
        return jdbc.sql("SELECT content_md FROM course_piece WHERE owner_id = :owner AND chapter_id = :chapter AND kind = 'LESSON' AND status = 'DONE'")
                .param("owner", ownerId).param("chapter", chapterId).query(String.class).optional();
    }

    public List<ChapterRef> chaptersOf(UUID topicId, UUID ownerId) {
        return jdbc.sql("SELECT title, objective FROM course_chapter WHERE owner_id = :owner AND topic_id = :topic ORDER BY position")
                .param("owner", ownerId).param("topic", topicId)
                .query((rs, row) -> new ChapterRef(rs.getString("title"), rs.getString("objective"))).list();
    }

    /** Los documentos que siguen enlazados al curso (si el usuario borró alguno, su enlace ya no está). */
    public List<UUID> documentsOf(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT document_id FROM course_document WHERE course_id = :course AND owner_id = :owner ORDER BY document_id")
                .param("course", courseId).param("owner", ownerId).query(UUID.class).list();
    }

    public void saveDone(UUID pieceId, UUID courseId, UUID ownerId, String markdown, String contentJson, String sourcesJson, int calls) {
        jdbc.sql("""
                UPDATE course_piece SET status = 'DONE', error_kind = NULL, content_md = :md, content_json = CAST(:json AS jsonb),
                       sources = CAST(:sources AS jsonb), attempts = attempts + :calls, generated_at = now()
                WHERE id = :id AND course_id = :course AND owner_id = :owner""")
                .param("md", markdown).param("json", contentJson).param("sources", sourcesJson).param("calls", calls)
                .param("id", pieceId).param("course", courseId).param("owner", ownerId).update();
    }

    public void saveFailed(UUID pieceId, UUID courseId, UUID ownerId, String errorKind, int calls) {
        jdbc.sql("""
                UPDATE course_piece SET status = 'FAILED', error_kind = :kind, attempts = attempts + :calls
                WHERE id = :id AND course_id = :course AND owner_id = :owner""")
                .param("kind", errorKind).param("calls", calls).param("id", pieceId).param("course", courseId).param("owner", ownerId).update();
    }

    public Counts counts(UUID courseId, UUID ownerId) {
        Map<String, Integer> byStatus = new HashMap<>();
        jdbc.sql("SELECT status, count(*) AS n FROM course_piece WHERE course_id = :course AND owner_id = :owner GROUP BY status")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> Map.entry(rs.getString("status"), rs.getInt("n"))).list()
                .forEach(e -> byStatus.put(e.getKey(), e.getValue()));
        return new Counts(byStatus.getOrDefault("DONE", 0), byStatus.getOrDefault("FAILED", 0), byStatus.getOrDefault("PENDING", 0));
    }
}
