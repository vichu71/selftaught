package es.vicai.studylab.guardian;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas de SOLO LECTURA del panel del adulto tutor (F08, ADR-59). Todas llevan el id del alumno como propietario ({@code owner_id}; {@code user_id} en el
 * consumo y {@code actor_user_id} en la auditoría) y todo va parametrizado: aquí nadie escribe y nadie lee a un alumno que no se haya comprobado antes.
 */
@Repository
public class GuardianRepository {

    public record StudentRow(String displayName, boolean active) {}

    public record CourseRow(UUID id, String title, String status, Instant createdAt) {}

    public record AlertRow(String type, Instant at, UUID courseId, UUID pieceId, String topic, String category) {}

    public record ConversationRow(UUID courseId, UUID pieceId, int messages, int blocked, int filtered, Instant lastAt) {}

    /** Una pieza de ejercicios o examen con lo que hace falta para enseñar sus intentos. */
    public record PieceRow(UUID id, String kind, String title, String contentJson) {}

    public record ExerciseAttemptRow(UUID pieceId, int item, String answer, String selfResult, String aiResult, String aiComment, Instant createdAt) {}

    public record ExamAttemptRow(UUID pieceId, int correct, int total, String answersJson, Instant submittedAt) {}

    private final JdbcClient jdbc;

    public GuardianRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- el alumno y sus cursos ----------

    public Optional<StudentRow> student(UUID studentId) {
        return jdbc.sql("SELECT display_name, status FROM app_user WHERE id = :id").param("id", studentId)
                .query((rs, i) -> new StudentRow(rs.getString("display_name"), "ACTIVE".equals(rs.getString("status")))).optional();
    }

    public List<CourseRow> courses(UUID studentId) {
        return jdbc.sql("SELECT id, title, status, created_at FROM course WHERE owner_id = :owner ORDER BY created_at DESC, id")
                .param("owner", studentId).query(GuardianRepository::courseRow).list();
    }

    public Optional<CourseRow> course(UUID courseId, UUID studentId) {
        return jdbc.sql("SELECT id, title, status, created_at FROM course WHERE id = :id AND owner_id = :owner")
                .param("id", courseId).param("owner", studentId).query(GuardianRepository::courseRow).optional();
    }

    private static CourseRow courseRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new CourseRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("status"), rs.getTimestamp("created_at").toInstant());
    }

    /** Lo último que hizo el alumno: un intento de ejercicio, un examen entregado, una pregunta al tutor o una lección hecha. */
    public Optional<Instant> lastActivity(UUID studentId) {
        return jdbc.sql("""
                SELECT max(happened_at) FROM (
                    SELECT max(created_at) AS happened_at FROM exercise_attempt WHERE owner_id = :owner
                    UNION ALL SELECT max(submitted_at) FROM exam_attempt WHERE owner_id = :owner AND status = 'SUBMITTED'
                    UNION ALL SELECT max(created_at) FROM tutor_message WHERE owner_id = :owner AND role = 'USER'
                    UNION ALL SELECT max(completed_at) FROM lesson_progress WHERE owner_id = :owner) activity""")
                .param("owner", studentId).query((rs, i) -> Optional.ofNullable(rs.getTimestamp(1)).map(java.sql.Timestamp::toInstant)).single();
    }

    // ---------- avisos ----------

    /** Lo que el filtro de menores ha parado, de las cuatro fuentes, lo más reciente primero (ADR-60). Los títulos se añaden aparte. */
    public List<AlertRow> alerts(UUID studentId, Instant since, int limit) {
        return jdbc.sql("""
                SELECT type, happened_at, course_id, piece_id, topic, category FROM (
                    SELECT 'TUTOR_QUESTION_BLOCKED' AS type, created_at AS happened_at, course_id, piece_id, NULL::text AS topic, NULL::text AS category
                    FROM tutor_message WHERE owner_id = :owner AND role = 'USER' AND status = 'BLOCKED' AND created_at >= :since
                    UNION ALL
                    SELECT 'TUTOR_ANSWER_FILTERED', created_at, course_id, piece_id, NULL, NULL
                    FROM tutor_message WHERE owner_id = :owner AND role = 'TUTOR' AND status = 'FILTERED' AND created_at >= :since
                    UNION ALL
                    SELECT 'COURSE_TOPIC_BLOCKED', created_at, NULL::uuid, NULL::uuid, topic, blocked_category
                    FROM course_intake WHERE owner_id = :owner AND status = 'BLOCKED' AND created_at >= :since
                    UNION ALL
                    SELECT action, occurred_at, CAST(NULLIF(details->>'courseId', '') AS uuid), CAST(NULLIF(details->>'pieceId', '') AS uuid), NULL, NULL
                    FROM audit_event WHERE actor_user_id = :owner AND action IN ('STUDY_ANSWER_BLOCKED', 'COURSE_CONTENT_BLOCKED', 'COURSE_COPY_BLOCKED', 'COURSE_EDIT_BLOCKED', 'DOCUMENT_BLOCKED') AND occurred_at >= :since
                ) alerts ORDER BY happened_at DESC, type LIMIT :limit""")
                .param("owner", studentId).param("since", java.sql.Timestamp.from(since)).param("limit", limit)
                .query((rs, i) -> new AlertRow(rs.getString("type"), rs.getTimestamp("happened_at").toInstant(), rs.getObject("course_id", UUID.class),
                        rs.getObject("piece_id", UUID.class), rs.getString("topic"), rs.getString("category"))).list();
    }

    // ---------- títulos ----------

    public Map<UUID, String> courseTitles(UUID studentId, Collection<UUID> courseIds) {
        Map<UUID, String> titles = new HashMap<>();
        if (courseIds.isEmpty()) return titles;
        jdbc.sql("SELECT id, title FROM course WHERE owner_id = :owner AND id IN (:ids)").param("owner", studentId).param("ids", courseIds)
                .query((rs, i) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("title"))).list().forEach(e -> titles.put(e.getKey(), e.getValue()));
        return titles;
    }

    /** El título de la lección o ejercicios es el de su capítulo; un examen no tiene capítulo y queda sin título. */
    public Map<UUID, String> chapterTitles(UUID studentId, Collection<UUID> pieceIds) {
        Map<UUID, String> titles = new HashMap<>();
        if (pieceIds.isEmpty()) return titles;
        jdbc.sql("""
                SELECT p.id, ch.title FROM course_piece p JOIN course_chapter ch ON ch.id = p.chapter_id AND ch.owner_id = p.owner_id
                WHERE p.owner_id = :owner AND p.id IN (:ids)""")
                .param("owner", studentId).param("ids", pieceIds)
                .query((rs, i) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("title"))).list().forEach(e -> titles.put(e.getKey(), e.getValue()));
        return titles;
    }

    // ---------- conversaciones ----------

    public List<ConversationRow> conversations(UUID studentId) {
        return jdbc.sql("""
                SELECT course_id, piece_id, count(*) AS messages,
                       count(*) FILTER (WHERE role = 'USER' AND status = 'BLOCKED') AS blocked,
                       count(*) FILTER (WHERE status = 'FILTERED') AS filtered, max(created_at) AS last_at
                FROM tutor_message WHERE owner_id = :owner AND status IN ('OK', 'BLOCKED', 'FILTERED')
                GROUP BY course_id, piece_id ORDER BY max(created_at) DESC, piece_id""")
                .param("owner", studentId)
                .query((rs, i) -> new ConversationRow(rs.getObject("course_id", UUID.class), rs.getObject("piece_id", UUID.class), rs.getInt("messages"),
                        rs.getInt("blocked"), rs.getInt("filtered"), rs.getTimestamp("last_at").toInstant())).list();
    }

    // ---------- intentos ----------

    /** Las piezas de ejercicios y exámenes del curso, en orden de generación, con el título de su capítulo (o de su tema, si es un examen). */
    public List<PieceRow> practicePieces(UUID courseId, UUID studentId) {
        return jdbc.sql("""
                SELECT p.id, p.kind, COALESCE(ch.title, t.title) AS title, p.content_json::text AS content
                FROM course_piece p
                JOIN course_topic t ON t.id = p.topic_id AND t.owner_id = p.owner_id
                LEFT JOIN course_chapter ch ON ch.id = p.chapter_id AND ch.owner_id = p.owner_id
                WHERE p.owner_id = :owner AND p.course_id = :course AND p.kind IN ('EXERCISES', 'EXAM') ORDER BY p.seq""")
                .param("owner", studentId).param("course", courseId)
                .query((rs, i) -> new PieceRow(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("title"), rs.getString("content"))).list();
    }

    public List<ExerciseAttemptRow> exerciseAttempts(UUID courseId, UUID studentId) {
        return jdbc.sql("""
                SELECT piece_id, item_index, answer, self_result, ai_result, ai_comment, created_at FROM exercise_attempt
                WHERE owner_id = :owner AND course_id = :course ORDER BY piece_id, item_index, created_at DESC, id DESC""")
                .param("owner", studentId).param("course", courseId)
                .query((rs, i) -> new ExerciseAttemptRow(rs.getObject("piece_id", UUID.class), rs.getInt("item_index"), rs.getString("answer"), rs.getString("self_result"),
                        rs.getString("ai_result"), rs.getString("ai_comment"), rs.getTimestamp("created_at").toInstant())).list();
    }

    public List<ExamAttemptRow> examAttempts(UUID courseId, UUID studentId) {
        return jdbc.sql("""
                SELECT piece_id, correct, total, answers::text AS answers, submitted_at FROM exam_attempt
                WHERE owner_id = :owner AND course_id = :course AND status = 'SUBMITTED' ORDER BY piece_id, submitted_at DESC, id DESC""")
                .param("owner", studentId).param("course", courseId)
                .query((rs, i) -> new ExamAttemptRow(rs.getObject("piece_id", UUID.class), rs.getInt("correct"), rs.getInt("total"), rs.getString("answers"),
                        rs.getTimestamp("submitted_at").toInstant())).list();
    }
}
