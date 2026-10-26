package es.vicai.studylab.courseedit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Escritura del temario y de las piezas de un curso propio (F09). SQL siempre parametrizado y siempre con el propietario: nadie edita un curso ajeno. */
@Repository
public class CourseEditRepository {

    public record PieceRow(UUID id, String kind, String status, UUID topicId, UUID chapterId) {}

    private final JdbcClient jdbc;

    public CourseEditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- el curso ----------

    public boolean courseExists(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM course WHERE id = :id AND owner_id = :owner)").param("id", courseId).param("owner", ownerId).query(Boolean.class).single();
    }

    public String title(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT title FROM course WHERE id = :id AND owner_id = :owner").param("id", courseId).param("owner", ownerId).query(String.class).single();
    }

    /** Bloquea la fila del curso hasta el final de la transacción y devuelve su estado: dos ediciones a la vez se aplican una detrás de otra. */
    public Optional<String> lockCourse(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT status FROM course WHERE id = :id AND owner_id = :owner FOR UPDATE").param("id", courseId).param("owner", ownerId).query(String.class).optional();
    }

    public void bumpVersion(UUID courseId, UUID ownerId) {
        jdbc.sql("UPDATE course SET version = version + 1, updated_at = now() WHERE id = :id AND owner_id = :owner").param("id", courseId).param("owner", ownerId).update();
    }

    /** El curso vuelve a la cola de la generación (la de F05 hace el resto). */
    public void queue(UUID courseId, UUID ownerId) {
        jdbc.sql("UPDATE course SET status = 'PENDING_GENERATION', stop_reason = NULL, completed_at = NULL, updated_at = now() WHERE id = :id AND owner_id = :owner")
                .param("id", courseId).param("owner", ownerId).update();
    }

    public boolean examPerTopic(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT COALESCE((sheet->>'examPerTopic')::boolean, false) FROM course WHERE id = :id AND owner_id = :owner")
                .param("id", courseId).param("owner", ownerId).query(Boolean.class).single();
    }

    public int pieceCount(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM course_piece WHERE course_id = :course AND owner_id = :owner").param("course", courseId).param("owner", ownerId).query(Integer.class).single();
    }

    // ---------- temas ----------

    public List<UUID> topicIds(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT id FROM course_topic WHERE course_id = :course AND owner_id = :owner ORDER BY position").param("course", courseId).param("owner", ownerId).query(UUID.class).list();
    }

    public int renameTopic(UUID topicId, UUID courseId, UUID ownerId, String title, String summary) {
        return jdbc.sql("UPDATE course_topic SET title = :title, summary = :summary WHERE id = :id AND course_id = :course AND owner_id = :owner")
                .param("title", title).param("summary", summary).param("id", topicId).param("course", courseId).param("owner", ownerId).update();
    }

    public int deleteTopic(UUID topicId, UUID courseId, UUID ownerId) {
        return jdbc.sql("DELETE FROM course_topic WHERE id = :id AND course_id = :course AND owner_id = :owner").param("id", topicId).param("course", courseId).param("owner", ownerId).update();
    }

    public int nextTopicPosition(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT COALESCE(max(position), 0) + 1 FROM course_topic WHERE course_id = :course AND owner_id = :owner").param("course", courseId).param("owner", ownerId).query(Integer.class).single();
    }

    public void insertTopic(UUID id, UUID courseId, UUID ownerId, int position, String title, String summary) {
        jdbc.sql("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (:id, :course, :owner, :position, :title, :summary)")
                .param("id", id).param("course", courseId).param("owner", ownerId).param("position", position).param("title", title).param("summary", summary).update();
    }

    /** Pone los temas en el orden dado. Primero se apartan todas las posiciones (son únicas por curso) y luego se asignan de 1 en adelante. */
    public void orderTopics(UUID courseId, UUID ownerId, List<UUID> ids) {
        jdbc.sql("UPDATE course_topic SET position = position + 1000 WHERE course_id = :course AND owner_id = :owner").param("course", courseId).param("owner", ownerId).update();
        for (int i = 0; i < ids.size(); i++) {
            jdbc.sql("UPDATE course_topic SET position = :position WHERE id = :id AND course_id = :course AND owner_id = :owner")
                    .param("position", i + 1).param("id", ids.get(i)).param("course", courseId).param("owner", ownerId).update();
        }
    }

    // ---------- capítulos ----------

    public List<UUID> chapterIds(UUID topicId, UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT id FROM course_chapter WHERE topic_id = :topic AND course_id = :course AND owner_id = :owner ORDER BY position")
                .param("topic", topicId).param("course", courseId).param("owner", ownerId).query(UUID.class).list();
    }

    public Optional<UUID> topicOfChapter(UUID chapterId, UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT topic_id FROM course_chapter WHERE id = :id AND course_id = :course AND owner_id = :owner").param("id", chapterId).param("course", courseId).param("owner", ownerId)
                .query(UUID.class).optional();
    }

    public boolean topicExists(UUID topicId, UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM course_topic WHERE id = :id AND course_id = :course AND owner_id = :owner)").param("id", topicId).param("course", courseId).param("owner", ownerId)
                .query(Boolean.class).single();
    }

    public int renameChapter(UUID chapterId, UUID courseId, UUID ownerId, String title, String objective) {
        return jdbc.sql("UPDATE course_chapter SET title = :title, objective = :objective WHERE id = :id AND course_id = :course AND owner_id = :owner")
                .param("title", title).param("objective", objective).param("id", chapterId).param("course", courseId).param("owner", ownerId).update();
    }

    public int deleteChapter(UUID chapterId, UUID courseId, UUID ownerId) {
        return jdbc.sql("DELETE FROM course_chapter WHERE id = :id AND course_id = :course AND owner_id = :owner").param("id", chapterId).param("course", courseId).param("owner", ownerId).update();
    }

    public int nextChapterPosition(UUID topicId, UUID ownerId) {
        return jdbc.sql("SELECT COALESCE(max(position), 0) + 1 FROM course_chapter WHERE topic_id = :topic AND owner_id = :owner").param("topic", topicId).param("owner", ownerId).query(Integer.class).single();
    }

    public void insertChapter(UUID id, UUID topicId, UUID courseId, UUID ownerId, int position, String title, String objective) {
        jdbc.sql("INSERT INTO course_chapter (id, topic_id, course_id, owner_id, position, title, objective) VALUES (:id, :topic, :course, :owner, :position, :title, :objective)")
                .param("id", id).param("topic", topicId).param("course", courseId).param("owner", ownerId).param("position", position).param("title", title).param("objective", objective).update();
    }

    public void orderChapters(UUID topicId, UUID courseId, UUID ownerId, List<UUID> ids) {
        jdbc.sql("UPDATE course_chapter SET position = position + 1000 WHERE topic_id = :topic AND course_id = :course AND owner_id = :owner")
                .param("topic", topicId).param("course", courseId).param("owner", ownerId).update();
        for (int i = 0; i < ids.size(); i++) {
            jdbc.sql("UPDATE course_chapter SET position = :position WHERE id = :id AND topic_id = :topic AND course_id = :course AND owner_id = :owner")
                    .param("position", i + 1).param("id", ids.get(i)).param("topic", topicId).param("course", courseId).param("owner", ownerId).update();
        }
    }

    // ---------- piezas ----------

    public Optional<PieceRow> piece(UUID pieceId, UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT id, kind, status, topic_id, chapter_id FROM course_piece WHERE id = :id AND course_id = :course AND owner_id = :owner")
                .param("id", pieceId).param("course", courseId).param("owner", ownerId)
                .query((rs, i) -> new PieceRow(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"), rs.getObject("topic_id", UUID.class),
                        rs.getObject("chapter_id", UUID.class))).optional();
    }

    public int nextSeq(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT COALESCE(max(seq), 0) + 1 FROM course_piece WHERE course_id = :course AND owner_id = :owner").param("course", courseId).param("owner", ownerId).query(Integer.class).single();
    }

    public void insertPiece(UUID courseId, UUID ownerId, UUID topicId, UUID chapterId, String kind, int seq) {
        jdbc.sql("INSERT INTO course_piece (id, course_id, owner_id, topic_id, chapter_id, kind, seq) VALUES (:id, :course, :owner, :topic, :chapter, :kind, :seq)")
                .param("id", UUID.randomUUID()).param("course", courseId).param("owner", ownerId).param("topic", topicId).param("chapter", chapterId).param("kind", kind).param("seq", seq).update();
    }

    /** Lo que depende de la lección de un capítulo queda «desactualizado»: sus ejercicios y el examen del tema. No se regenera nada solo (ADR-66). */
    public void markLessonDependentsOutdated(UUID chapterId, UUID topicId, UUID courseId, UUID ownerId) {
        jdbc.sql("""
                UPDATE course_piece SET outdated = true WHERE course_id = :course AND owner_id = :owner
                AND ((kind = 'EXERCISES' AND chapter_id = :chapter) OR (kind = 'EXAM' AND topic_id = :topic))""")
                .param("course", courseId).param("owner", ownerId).param("chapter", chapterId).param("topic", topicId).update();
    }

    public void markExamOutdated(UUID topicId, UUID courseId, UUID ownerId) {
        jdbc.sql("UPDATE course_piece SET outdated = true WHERE course_id = :course AND owner_id = :owner AND kind = 'EXAM' AND topic_id = :topic")
                .param("course", courseId).param("owner", ownerId).param("topic", topicId).update();
    }

    /** El texto de una lección hecha por su propietario: ya no cita material, así que se quitan sus fuentes. */
    public void setLessonText(UUID pieceId, UUID courseId, UUID ownerId, String markdown) {
        jdbc.sql("UPDATE course_piece SET content_md = :md, sources = NULL, generated_at = now() WHERE id = :id AND course_id = :course AND owner_id = :owner")
                .param("md", markdown).param("id", pieceId).param("course", courseId).param("owner", ownerId).update();
    }

    /** Una pieza hecha vuelve a pendiente y vacía: la cola de F05 la volverá a generar. */
    public void resetPiece(UUID pieceId, UUID courseId, UUID ownerId) {
        jdbc.sql("""
                UPDATE course_piece SET status = 'PENDING', content_md = NULL, content_json = NULL, sources = NULL, error_kind = NULL, attempts = 0, generated_at = NULL, outdated = false
                WHERE id = :id AND course_id = :course AND owner_id = :owner""")
                .param("id", pieceId).param("course", courseId).param("owner", ownerId).update();
    }

    /** Lo que el alumno hizo con una pieza que ya no existe tal cual: progreso, intentos y su conversación con el tutor. */
    public void deleteDependents(UUID pieceId, UUID ownerId) {
        jdbc.sql("DELETE FROM lesson_progress WHERE piece_id = :piece AND owner_id = :owner").param("piece", pieceId).param("owner", ownerId).update();
        jdbc.sql("DELETE FROM exercise_attempt WHERE piece_id = :piece AND owner_id = :owner").param("piece", pieceId).param("owner", ownerId).update();
        jdbc.sql("DELETE FROM exam_attempt WHERE piece_id = :piece AND owner_id = :owner").param("piece", pieceId).param("owner", ownerId).update();
        jdbc.sql("DELETE FROM tutor_message WHERE piece_id = :piece AND owner_id = :owner").param("piece", pieceId).param("owner", ownerId).update();
    }
}
