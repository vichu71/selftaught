package es.vicai.studylab.coursedelete;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Bloqueo y borrado de cursos propios (F13). SQL siempre parametrizado y siempre con el propietario. */
@Repository
public class CourseDeleteRepository {

    public record Locked(UUID id, String status, UUID intakeId) {}

    private final JdbcClient jdbc;

    public CourseDeleteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Bloquea las filas en orden de id (dos borrados o una edición a la vez no se entrelazan) y devuelve las que son del propietario. */
    public List<Locked> lock(List<UUID> ids, UUID ownerId) {
        return jdbc.sql("SELECT id, status, intake_id FROM course WHERE id = ANY(:ids) AND owner_id = :owner ORDER BY id FOR UPDATE")
                .param("ids", ids.toArray(new UUID[0])).param("owner", ownerId)
                .query((rs, n) -> new Locked(rs.getObject("id", UUID.class), rs.getString("status"), rs.getObject("intake_id", UUID.class))).list();
    }

    /** Vacía lo que el usuario escribió en la consulta del asistente; la fila se queda porque cuenta para el límite por hora (ADR-85). */
    public void blankIntakes(List<UUID> intakeIds, UUID ownerId) {
        jdbc.sql("UPDATE course_intake SET topic = '', questions = NULL, document_ids = '[]'::jsonb WHERE id = ANY(:ids) AND owner_id = :owner")
                .param("ids", intakeIds.toArray(new UUID[0])).param("owner", ownerId).update();
    }

    /** Borra los cursos; el esquema se lleva lo que cuelga de ellos y deja las copias con origin_id nulo. */
    public int delete(List<UUID> ids, UUID ownerId) {
        return jdbc.sql("DELETE FROM course WHERE id = ANY(:ids) AND owner_id = :owner").param("ids", ids.toArray(new UUID[0])).param("owner", ownerId).update();
    }
}
