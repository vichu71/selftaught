package es.vicai.studylab.study;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a las lecciones hechas y a los intentos. SQL siempre parametrizado y siempre con el propietario: nadie lee ni escribe estudio ajeno. */
@Repository
public class StudyRepository {

    public record PieceRow(UUID id, String kind, String status, String contentJson, UUID topicId, int seq) {}

    /** Una pieza con lo que hace falta para calcular el progreso: tema, orden y si la lección está marcada. */
    public record StudyPiece(UUID id, String kind, String status, UUID topicId, boolean done) {}

    public record ExamStat(UUID pieceId, int correct, int total, Instant submittedAt) {}

    public record ExerciseAttemptRow(UUID id, int itemIndex, String answer, String selfResult, String aiResult, String aiComment, Instant aiRequestedAt,
                                     Instant createdAt) {}

    public record ExamAttemptRow(UUID id, String status, String answersJson, Integer correct, Integer total, Instant submittedAt) {}

    public record CourseTotals(UUID courseId, int lessons, int exams) {}

    private final JdbcClient jdbc;

    public StudyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- curso y piezas ----------

    public boolean courseExists(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM course WHERE id = :id AND owner_id = :owner)")
                .param("id", courseId).param("owner", ownerId).query(Boolean.class).single();
    }

    public Optional<PieceRow> piece(UUID courseId, UUID pieceId, UUID ownerId) {
        return jdbc.sql("SELECT id, kind, status, content_json::text AS content, topic_id, seq FROM course_piece WHERE id = :id AND course_id = :course AND owner_id = :owner")
                .param("id", pieceId).param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new PieceRow(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"), rs.getString("content"),
                        rs.getObject("topic_id", UUID.class), rs.getInt("seq"))).optional();
    }

    // ---------- lecciones hechas ----------

    public void markDone(UUID courseId, UUID pieceId, UUID ownerId) {
        jdbc.sql("INSERT INTO lesson_progress (owner_id, piece_id, course_id) VALUES (:owner, :piece, :course) ON CONFLICT (piece_id, owner_id) DO NOTHING")
                .param("owner", ownerId).param("piece", pieceId).param("course", courseId).update();
    }

    public void unmarkDone(UUID pieceId, UUID ownerId) {
        jdbc.sql("DELETE FROM lesson_progress WHERE piece_id = :piece AND owner_id = :owner").param("piece", pieceId).param("owner", ownerId).update();
    }

    public boolean isDone(UUID pieceId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM lesson_progress WHERE piece_id = :piece AND owner_id = :owner)")
                .param("piece", pieceId).param("owner", ownerId).query(Boolean.class).single();
    }

    // ---------- progreso ----------

    /** Las piezas del temario en orden de generación, con el tema (por posición) y si la lección está marcada. */
    public List<StudyPiece> studyPieces(UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT p.id, p.kind, p.status, p.topic_id, (lp.piece_id IS NOT NULL) AS done
                FROM course_piece p
                JOIN course_topic t ON t.id = p.topic_id AND t.owner_id = p.owner_id
                LEFT JOIN lesson_progress lp ON lp.piece_id = p.id AND lp.owner_id = p.owner_id
                WHERE p.course_id = :course AND p.owner_id = :owner ORDER BY p.seq""")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new StudyPiece(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"),
                        rs.getObject("topic_id", UUID.class), rs.getBoolean("done"))).list();
    }

    /** Los intentos entregados de los exámenes de un curso, del más antiguo al más reciente. */
    public List<ExamStat> examStats(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT piece_id, correct, total, submitted_at FROM exam_attempt WHERE course_id = :course AND owner_id = :owner AND status = 'SUBMITTED' ORDER BY submitted_at, id")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new ExamStat(rs.getObject("piece_id", UUID.class), rs.getInt("correct"), rs.getInt("total"), rs.getTimestamp("submitted_at").toInstant())).list();
    }

    /** Para la lista de cursos: cuántas lecciones y exámenes tiene cada uno (también los que aún no se generaron). */
    public List<CourseTotals> courseTotals(UUID ownerId) {
        return jdbc.sql("""
                SELECT c.id, count(p.id) FILTER (WHERE p.kind = 'LESSON') AS lessons, count(p.id) FILTER (WHERE p.kind = 'EXAM') AS exams
                FROM course c LEFT JOIN course_piece p ON p.course_id = c.id AND p.owner_id = c.owner_id
                WHERE c.owner_id = :owner GROUP BY c.id ORDER BY c.created_at DESC, c.id""")
                .param("owner", ownerId)
                .query((rs, row) -> new CourseTotals(rs.getObject("id", UUID.class), rs.getInt("lessons"), rs.getInt("exams"))).list();
    }

    public Map<UUID, Integer> lessonsDoneByCourse(UUID ownerId) {
        Map<UUID, Integer> done = new HashMap<>();
        jdbc.sql("""
                SELECT lp.course_id, count(*) AS n FROM lesson_progress lp
                JOIN course_piece p ON p.id = lp.piece_id AND p.owner_id = lp.owner_id
                WHERE lp.owner_id = :owner AND p.status = 'DONE' GROUP BY lp.course_id""")
                .param("owner", ownerId).query((rs, row) -> Map.entry(rs.getObject("course_id", UUID.class), rs.getInt("n"))).list()
                .forEach(e -> done.put(e.getKey(), e.getValue()));
        return done;
    }

    public Map<UUID, Integer> examsPassedByCourse(UUID ownerId, int passPercent) {
        Map<UUID, Integer> passed = new HashMap<>();
        jdbc.sql("""
                SELECT course_id, count(DISTINCT piece_id) AS n FROM exam_attempt
                WHERE owner_id = :owner AND status = 'SUBMITTED' AND correct * 100 >= total * :pass GROUP BY course_id""")
                .param("owner", ownerId).param("pass", passPercent)
                .query((rs, row) -> Map.entry(rs.getObject("course_id", UUID.class), rs.getInt("n"))).list()
                .forEach(e -> passed.put(e.getKey(), e.getValue()));
        return passed;
    }

    // ---------- límites por usuario (ADR-53) ----------

    /** Los ejercicios enviados y los exámenes entregados en la última hora. */
    public int submissionsLastHour(UUID ownerId) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM exercise_attempt WHERE owner_id = :owner AND created_at > now() - interval '1 hour')
                     + (SELECT count(*) FROM exam_attempt WHERE owner_id = :owner AND status = 'SUBMITTED' AND submitted_at > now() - interval '1 hour')""")
                .param("owner", ownerId).query(Integer.class).single();
    }

    /** Las correcciones de IA pedidas en el último día, también las que fallaron. */
    public int aiGradesLastDay(UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM exercise_attempt WHERE owner_id = :owner AND ai_requested_at > now() - interval '1 day'")
                .param("owner", ownerId).query(Integer.class).single();
    }

    // ---------- intentos de ejercicio ----------

    public UUID insertExerciseAttempt(UUID courseId, UUID pieceId, UUID ownerId, int item, String answer) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO exercise_attempt (id, owner_id, course_id, piece_id, item_index, answer) VALUES (:id, :owner, :course, :piece, :item, :answer)")
                .param("id", id).param("owner", ownerId).param("course", courseId).param("piece", pieceId).param("item", item).param("answer", answer).update();
        return id;
    }

    /** Conserva solo los últimos intentos de la pieza (los más antiguos se descartan). */
    public void pruneExerciseAttempts(UUID pieceId, UUID ownerId, int keep) {
        jdbc.sql("""
                DELETE FROM exercise_attempt WHERE owner_id = :owner AND piece_id = :piece AND id NOT IN (
                    SELECT id FROM exercise_attempt WHERE owner_id = :owner AND piece_id = :piece ORDER BY created_at DESC, id DESC LIMIT :keep)""")
                .param("owner", ownerId).param("piece", pieceId).param("keep", keep).update();
    }

    public Optional<ExerciseAttemptRow> exerciseAttempt(UUID attemptId, UUID courseId, UUID pieceId, int item, UUID ownerId) {
        return jdbc.sql("""
                SELECT id, item_index, answer, self_result, ai_result, ai_comment, ai_requested_at, created_at FROM exercise_attempt
                WHERE id = :id AND course_id = :course AND piece_id = :piece AND item_index = :item AND owner_id = :owner""")
                .param("id", attemptId).param("course", courseId).param("piece", pieceId).param("item", item).param("owner", ownerId)
                .query(StudyRepository::exerciseRow).optional();
    }

    public List<ExerciseAttemptRow> exerciseAttempts(UUID pieceId, UUID ownerId) {
        return jdbc.sql("""
                SELECT id, item_index, answer, self_result, ai_result, ai_comment, ai_requested_at, created_at FROM exercise_attempt
                WHERE piece_id = :piece AND owner_id = :owner ORDER BY created_at DESC, id DESC""")
                .param("piece", pieceId).param("owner", ownerId).query(StudyRepository::exerciseRow).list();
    }

    private static ExerciseAttemptRow exerciseRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        java.sql.Timestamp requested = rs.getTimestamp("ai_requested_at");
        return new ExerciseAttemptRow(rs.getObject("id", UUID.class), rs.getInt("item_index"), rs.getString("answer"), rs.getString("self_result"),
                rs.getString("ai_result"), rs.getString("ai_comment"), requested == null ? null : requested.toInstant(), rs.getTimestamp("created_at").toInstant());
    }

    public int setSelfResult(UUID attemptId, UUID ownerId, String result) {
        return jdbc.sql("UPDATE exercise_attempt SET self_result = :result WHERE id = :id AND owner_id = :owner")
                .param("result", result).param("id", attemptId).param("owner", ownerId).update();
    }

    /** Reserva la corrección de IA de un intento: solo una vez (cuenta para el límite diario aunque la IA falle). */
    public int claimAiGrade(UUID attemptId, UUID ownerId) {
        return jdbc.sql("UPDATE exercise_attempt SET ai_requested_at = now() WHERE id = :id AND owner_id = :owner AND ai_requested_at IS NULL")
                .param("id", attemptId).param("owner", ownerId).update();
    }

    public void saveAiGrade(UUID attemptId, UUID ownerId, String verdict, String comment) {
        jdbc.sql("UPDATE exercise_attempt SET ai_result = :verdict, ai_comment = :comment WHERE id = :id AND owner_id = :owner")
                .param("verdict", verdict).param("comment", comment).param("id", attemptId).param("owner", ownerId).update();
    }

    // ---------- intentos de examen ----------

    /** Los exámenes empezados y nunca entregados con más de un día se descartan. */
    public void purgeOldOpenExams(UUID ownerId) {
        jdbc.sql("DELETE FROM exam_attempt WHERE owner_id = :owner AND status = 'STARTED' AND started_at < now() - interval '1 day'").param("owner", ownerId).update();
    }

    public Optional<UUID> openExam(UUID pieceId, UUID ownerId) {
        return jdbc.sql("SELECT id FROM exam_attempt WHERE piece_id = :piece AND owner_id = :owner AND status = 'STARTED' ORDER BY started_at DESC LIMIT 1")
                .param("piece", pieceId).param("owner", ownerId).query(UUID.class).optional();
    }

    public UUID insertExam(UUID courseId, UUID pieceId, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO exam_attempt (id, owner_id, course_id, piece_id, status) VALUES (:id, :owner, :course, :piece, 'STARTED')")
                .param("id", id).param("owner", ownerId).param("course", courseId).param("piece", pieceId).update();
        return id;
    }

    public Optional<ExamAttemptRow> examAttempt(UUID attemptId, UUID pieceId, UUID ownerId) {
        return jdbc.sql("SELECT id, status, answers::text AS answers, correct, total, submitted_at FROM exam_attempt WHERE id = :id AND piece_id = :piece AND owner_id = :owner")
                .param("id", attemptId).param("piece", pieceId).param("owner", ownerId).query(StudyRepository::examRow).optional();
    }

    /** Cierra el intento; devuelve 0 si ya estaba entregado (otra pestaña o un doble clic se adelantó). */
    public int submitExam(UUID attemptId, UUID ownerId, String answersJson, int correct, int total) {
        return jdbc.sql("""
                UPDATE exam_attempt SET status = 'SUBMITTED', answers = CAST(:answers AS jsonb), correct = :correct, total = :total, submitted_at = now()
                WHERE id = :id AND owner_id = :owner AND status = 'STARTED'""")
                .param("answers", answersJson).param("correct", correct).param("total", total).param("id", attemptId).param("owner", ownerId).update();
    }

    public void pruneExamAttempts(UUID pieceId, UUID ownerId, int keep) {
        jdbc.sql("""
                DELETE FROM exam_attempt WHERE owner_id = :owner AND piece_id = :piece AND status = 'SUBMITTED' AND id NOT IN (
                    SELECT id FROM exam_attempt WHERE owner_id = :owner AND piece_id = :piece AND status = 'SUBMITTED' ORDER BY submitted_at DESC, id DESC LIMIT :keep)""")
                .param("owner", ownerId).param("piece", pieceId).param("keep", keep).update();
    }

    public List<ExamAttemptRow> submittedExams(UUID pieceId, UUID ownerId) {
        return jdbc.sql("SELECT id, status, answers::text AS answers, correct, total, submitted_at FROM exam_attempt WHERE piece_id = :piece AND owner_id = :owner AND status = 'SUBMITTED' ORDER BY submitted_at DESC, id DESC")
                .param("piece", pieceId).param("owner", ownerId).query(StudyRepository::examRow).list();
    }

    private static ExamAttemptRow examRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        java.sql.Timestamp submitted = rs.getTimestamp("submitted_at");
        return new ExamAttemptRow(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("answers"), rs.getObject("correct", Integer.class),
                rs.getObject("total", Integer.class), submitted == null ? null : submitted.toInstant());
    }
}
