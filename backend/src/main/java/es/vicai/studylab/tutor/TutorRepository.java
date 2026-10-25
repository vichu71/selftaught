package es.vicai.studylab.tutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a las conversaciones del tutor y a lo que el tutor lee del curso. SQL siempre parametrizado y siempre con el propietario: nadie lee ni escribe un hilo ajeno. */
@Repository
public class TutorRepository {

    public record LessonRow(UUID id, String status, String contentMd, UUID chapterId, String chapterTitle, String chapterObjective) {}

    public record MessageRow(UUID id, String role, String status, String content, String sources, Instant createdAt) {}

    public record Counts(int lastHour, int lastDay) {}

    /** El último intento de un ejercicio del capítulo, con su resultado y el contenido de la pieza para sacar el enunciado. */
    public record ExerciseOutcome(int item, String result, String contentJson) {}

    /** Un tema del temario con el título de uno de sus capítulos (nulo si todavía no tiene). */
    public record OutlineRow(int position, String topicTitle, String topicSummary, String chapterTitle) {}

    private final JdbcClient jdbc;

    public TutorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- lo que el tutor lee del curso ----------

    public Optional<LessonRow> lesson(UUID courseId, UUID pieceId, UUID ownerId) {
        return jdbc.sql("""
                SELECT p.id, p.status, p.content_md, p.chapter_id, ch.title, ch.objective
                FROM course_piece p JOIN course_chapter ch ON ch.id = p.chapter_id AND ch.owner_id = p.owner_id
                WHERE p.id = :piece AND p.course_id = :course AND p.owner_id = :owner AND p.kind = 'LESSON'""")
                .param("piece", pieceId).param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new LessonRow(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("content_md"),
                        rs.getObject("chapter_id", UUID.class), rs.getString("title"), rs.getString("objective"))).optional();
    }

    public Optional<String> sheet(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT sheet::text FROM course WHERE id = :course AND owner_id = :owner")
                .param("course", courseId).param("owner", ownerId).query(String.class).optional();
    }

    public List<OutlineRow> outline(UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT t.position, t.title AS topic_title, t.summary, ch.title AS chapter_title
                FROM course_topic t LEFT JOIN course_chapter ch ON ch.topic_id = t.id AND ch.owner_id = t.owner_id
                WHERE t.course_id = :course AND t.owner_id = :owner ORDER BY t.position, ch.position""")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new OutlineRow(rs.getInt("position"), rs.getString("topic_title"), rs.getString("summary"), rs.getString("chapter_title"))).list();
    }

    /** Los documentos de material que el usuario vinculó a este curso. */
    public List<UUID> documents(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT document_id FROM course_document WHERE course_id = :course AND owner_id = :owner")
                .param("course", courseId).param("owner", ownerId).query(UUID.class).list();
    }

    /** El último intento de cada ejercicio de las piezas de ejercicios del capítulo: gana lo que marcó el alumno y, si no marcó nada, el veredicto de la IA. */
    public List<ExerciseOutcome> exerciseOutcomes(UUID courseId, UUID chapterId, UUID ownerId) {
        return jdbc.sql("""
                SELECT DISTINCT ON (a.item_index) a.item_index, coalesce(a.self_result, a.ai_result) AS result, p.content_json::text AS content
                FROM exercise_attempt a JOIN course_piece p ON p.id = a.piece_id AND p.owner_id = a.owner_id
                WHERE a.owner_id = :owner AND a.course_id = :course AND p.chapter_id = :chapter AND p.kind = 'EXERCISES' AND p.status = 'DONE'
                ORDER BY a.item_index, a.created_at DESC, a.id DESC""")
                .param("owner", ownerId).param("course", courseId).param("chapter", chapterId)
                .query((rs, row) -> new ExerciseOutcome(rs.getInt("item_index"), rs.getString("result"), rs.getString("content"))).list();
    }

    // ---------- el hilo ----------

    /** El hilo visible de una lección, del más antiguo al más reciente: ni los pendientes ni los fallidos. */
    public List<MessageRow> thread(UUID pieceId, UUID ownerId) {
        return jdbc.sql("""
                SELECT id, role, status, content, sources::text AS sources, created_at FROM tutor_message
                WHERE owner_id = :owner AND piece_id = :piece AND status IN ('OK', 'BLOCKED', 'FILTERED') ORDER BY created_at, id""")
                .param("owner", ownerId).param("piece", pieceId).query(TutorRepository::messageRow).list();
    }

    /** Los últimos mensajes correctos de la lección (los bloqueados, filtrados y fallidos nunca van al modelo), del más antiguo al más reciente. */
    public List<MessageRow> recentOk(UUID pieceId, UUID ownerId, int limit) {
        List<MessageRow> rows = new ArrayList<>(jdbc.sql("""
                SELECT id, role, status, content, sources::text AS sources, created_at FROM tutor_message
                WHERE owner_id = :owner AND piece_id = :piece AND status = 'OK' ORDER BY created_at DESC, id DESC LIMIT :limit""")
                .param("owner", ownerId).param("piece", pieceId).param("limit", limit).query(TutorRepository::messageRow).list());
        Collections.reverse(rows);
        return rows;
    }

    public Optional<MessageRow> message(UUID messageId, UUID ownerId) {
        return jdbc.sql("SELECT id, role, status, content, sources::text AS sources, created_at FROM tutor_message WHERE id = :id AND owner_id = :owner")
                .param("id", messageId).param("owner", ownerId).query(TutorRepository::messageRow).optional();
    }

    private static MessageRow messageRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new MessageRow(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("status"), rs.getString("content"), rs.getString("sources"),
                rs.getTimestamp("created_at").toInstant());
    }

    public int clear(UUID pieceId, UUID ownerId) {
        return jdbc.sql("DELETE FROM tutor_message WHERE owner_id = :owner AND piece_id = :piece").param("owner", ownerId).param("piece", pieceId).update();
    }

    // ---------- límites por usuario (ADR-55) ----------

    /** Serializa las preguntas de un usuario hasta el final de la transacción: sin esto, dos peticiones a la vez podrían pasar los límites a la vez. */
    public void lockUser(UUID ownerId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "tutor:" + ownerId).query(rs -> {});
    }

    /** Las preguntas del alumno de la última hora y del último día, sea cual sea su estado. */
    public Counts counts(UUID ownerId) {
        return jdbc.sql("""
                SELECT count(*) FILTER (WHERE created_at > now() - interval '1 hour') AS hour, count(*) AS day FROM tutor_message
                WHERE owner_id = :owner AND role = 'USER' AND created_at > now() - interval '1 day'""")
                .param("owner", ownerId).query((rs, row) -> new Counts(rs.getInt("hour"), rs.getInt("day"))).single();
    }

    /** Si hay una pregunta reciente sin contestar. Una más antigua que la ventana se da por abandonada (la instancia que la atendía se cayó). */
    public boolean hasRecentPending(UUID ownerId, long windowSeconds) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM tutor_message WHERE owner_id = :owner AND status = 'PENDING'
                               AND created_at > now() - make_interval(secs => CAST(:secs AS double precision)))""")
                .param("owner", ownerId).param("secs", windowSeconds).query(Boolean.class).single();
    }

    // ---------- escritura ----------

    public UUID insert(UUID courseId, UUID pieceId, UUID ownerId, String role, String status, String content, String sourcesJson) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tutor_message (id, owner_id, course_id, piece_id, role, status, content, sources)
                VALUES (:id, :owner, :course, :piece, :role, :status, :content, CAST(:sources AS jsonb))""")
                .param("id", id).param("owner", ownerId).param("course", courseId).param("piece", pieceId).param("role", role).param("status", status)
                .param("content", content).param("sources", sourcesJson).update();
        return id;
    }

    /** Cambia el estado de una pregunta que sigue pendiente; devuelve 0 si ya no existe (el hilo se vació mientras tanto). */
    public int resolve(UUID messageId, UUID ownerId, String status) {
        return jdbc.sql("UPDATE tutor_message SET status = :status WHERE id = :id AND owner_id = :owner AND status = 'PENDING'")
                .param("status", status).param("id", messageId).param("owner", ownerId).update();
    }

    /** Conserva solo los últimos mensajes de la lección (los más antiguos se descartan, también los fallidos). */
    public void prune(UUID pieceId, UUID ownerId, int keep) {
        jdbc.sql("""
                DELETE FROM tutor_message WHERE owner_id = :owner AND piece_id = :piece AND id NOT IN (
                    SELECT id FROM tutor_message WHERE owner_id = :owner AND piece_id = :piece ORDER BY created_at DESC, id DESC LIMIT :keep)""")
                .param("owner", ownerId).param("piece", pieceId).param("keep", keep).update();
    }
}
