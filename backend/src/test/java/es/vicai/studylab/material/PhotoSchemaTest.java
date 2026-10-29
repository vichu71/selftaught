package es.vicai.studylab.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.AiTask;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** F19 ART-002 (migración V13): el esquema admite el tipo IMAGE y la tarea OCR, y los permisos del rol de la aplicación son los mínimos. */
class PhotoSchemaTest extends PhotoTestBase {

    private boolean app(String table, String privilege) {
        return owner.queryForObject("SELECT has_table_privilege('studylab_app', ?, ?)", Boolean.class, table, privilege);
    }

    @Test
    void theDocumentTypeAcceptsImageAndStillRejectsAnythingElse() {
        UUID user = UUID.randomUUID();
        owner.update("INSERT INTO app_user (id, kind, display_name, email, password_hash, status) VALUES (?, 'ADULT', 'Ana', CAST(? AS citext), 'h', 'ACTIVE')", user, user + "@e.com");

        owner.update("INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status) VALUES (gen_random_uuid(), ?, 'a', 'IMAGE', 1, ?, 'READY')", user, "b".repeat(64));
        assertThatThrownBy(() -> owner.update("INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status) VALUES (gen_random_uuid(), ?, 'a', 'GIF', 1, ?, 'READY')", user, "c".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void everyAiTaskOfTheCodeIsAcceptedByTheUsageTableIncludingOcr() {
        for (AiTask task : AiTask.values()) {
            owner.update("INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status) VALUES (gen_random_uuid(), 1, NULL, 'APP', ?, 'fake', 'm', 0, 'OK')", task.name());
        }
        assertThat(owner.queryForObject("SELECT count(*) FROM ai_usage WHERE task = 'OCR'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theSettingOfAChildIsOffByDefaultAndOnePerChild() {
        UUID child = UUID.randomUUID();
        owner.update("INSERT INTO app_user (id, kind, display_name, login_id, password_hash, status) VALUES (?, 'MINOR', 'Hugo', ?, 'h', 'ACTIVE')", child, "hugo-" + child);
        owner.update("INSERT INTO child_setting (child_id, updated_by) VALUES (?, ?)", child, child);

        assertThat(owner.queryForObject("SELECT photo_material FROM child_setting WHERE child_id = ?", Boolean.class, child)).isFalse();
        assertThatThrownBy(() -> owner.update("INSERT INTO child_setting (child_id, updated_by) VALUES (?, ?)", child, child)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theApplicationRoleHasTheMinimumPrivileges() {
        assertThat(app("child_setting", "SELECT")).isTrue();
        assertThat(app("child_setting", "INSERT")).isTrue();
        assertThat(app("child_setting", "UPDATE")).isTrue();
        assertThat(app("child_setting", "DELETE")).isFalse();
        assertThat(app("material_action", "SELECT")).isTrue();
        assertThat(app("material_action", "INSERT")).isTrue();
        assertThat(app("material_action", "UPDATE")).isFalse(); // el contador solo se añade
        assertThat(app("material_action", "DELETE")).isFalse();
    }

    @Test
    void theDailyCounterOnlyAcceptsTheKnownKind() {
        UUID user = newUser();

        owner.update("INSERT INTO material_action (owner_id, kind) VALUES (?, 'IMAGE_UPLOAD')", user);
        assertThatThrownBy(() -> owner.update("INSERT INTO material_action (owner_id, kind) VALUES (?, 'OTRA')", user)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
