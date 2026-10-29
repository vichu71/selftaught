package es.vicai.studylab.childsettings;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a {@code child_setting}: un ajuste por menor, sin fila = desactivado. Todo el SQL va parametrizado. */
@Repository
public class ChildSettingsRepository {

    private final JdbcClient jdbc;

    public ChildSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean photoMaterial(UUID childId) {
        return jdbc.sql("SELECT photo_material FROM child_setting WHERE child_id = :child").param("child", childId)
                .query(Boolean.class).optional().orElse(false);
    }

    /** Fija el ajuste y devuelve true si cambió algo (una fila nueva con false no cambia nada). Bloquea la fila para que dos cambios a la vez no se pisen. */
    public boolean setPhotoMaterial(UUID childId, boolean enabled, UUID tutorId) {
        jdbc.sql("INSERT INTO child_setting (child_id, photo_material, updated_by) VALUES (:child, false, :tutor) ON CONFLICT (child_id) DO NOTHING")
                .param("child", childId).param("tutor", tutorId).update();
        boolean current = jdbc.sql("SELECT photo_material FROM child_setting WHERE child_id = :child FOR UPDATE").param("child", childId)
                .query(Boolean.class).single();
        if (current == enabled) return false;
        jdbc.sql("UPDATE child_setting SET photo_material = :enabled, updated_by = :tutor, updated_at = now() WHERE child_id = :child")
                .param("enabled", enabled).param("tutor", tutorId).param("child", childId).update();
        return true;
    }
}
