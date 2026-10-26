package es.vicai.studylab.courseedit;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** El contador por usuario de las acciones limitadas de F09 (ediciones con IA y canjes). Todo por {@code user_id}: nadie cuenta ni escribe las de otro. */
@Repository
public class CourseActionRepository {

    private final JdbcClient jdbc;

    public CourseActionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Serializa las acciones de un usuario hasta el final de la transacción: sin esto, dos peticiones a la vez podrían pasar el límite a la vez. */
    public void lockUser(UUID userId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "course-action:" + userId).query(rs -> {});
    }

    public int inTheLastHour(UUID userId, String kind) {
        return jdbc.sql("SELECT count(*) FROM course_action WHERE user_id = :user AND kind = :kind AND created_at > now() - interval '1 hour'")
                .param("user", userId).param("kind", kind).query(Integer.class).single();
    }

    public void record(UUID userId, String kind) {
        jdbc.sql("INSERT INTO course_action (user_id, kind) VALUES (:user, :kind)").param("user", userId).param("kind", kind).update();
    }

    /** Lo de más de un día ya no cuenta para ningún límite. */
    public void purgeOld(UUID userId) {
        jdbc.sql("DELETE FROM course_action WHERE user_id = :user AND created_at < now() - interval '1 day'").param("user", userId).update();
    }
}
