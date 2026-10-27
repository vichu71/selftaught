package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** AC-7, AC-10, AC-15 (ART-002, V12): tablas, restricciones, índices y permisos mínimos del rol de la aplicación. Se prueba con el rol real ({@code app}), no con el dueño. */
class OwnerSchemaTest extends OwnerTestBase {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V12__owner_panel.sql");

    private UUID user() {
        return newUser();
    }

    @Test
    void theLimitTableRejectsOutOfRangeAndDanglingValues() {
        UUID a = user();
        UUID b = user();
        for (String bad : List.of("-0.01", "500.01", "1000")) {
            assertThatThrownBy(() -> owner.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, CAST(? AS numeric), ?)", a, bad, b))
                    .as(bad).isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> owner.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, 1, ?)", UUID.randomUUID(), b)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> owner.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, 1, ?)", a, UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        owner.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, 0, ?)", a, b);
        assertThatThrownBy(() -> owner.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, 500, ?)", a, b)).as("una fila por usuario").isInstanceOf(DataAccessException.class);
    }

    @Test
    void theActionTableOnlyAcceptsTheTwoKindsAndAnExistingOwner() {
        UUID a = user();
        owner.update("INSERT INTO owner_action (owner_id, kind) VALUES (?, 'QUERY')", a);
        owner.update("INSERT INTO owner_action (owner_id, kind) VALUES (?, 'ACTION')", a);

        assertThatThrownBy(() -> owner.update("INSERT INTO owner_action (owner_id, kind) VALUES (?, 'OTHER')", a)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> owner.update("INSERT INTO owner_action (owner_id, kind) VALUES (?, 'QUERY')", UUID.randomUUID())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void theApplicationRoleCanDoExactlyWhatTheLimitsNeed() {
        UUID a = user();
        UUID b = user();

        app.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, 3, ?)", a, b);
        app.update("UPDATE user_limit_override SET limit_usd = 4 WHERE user_id = ?", a);
        assertThat(app.queryForObject("SELECT limit_usd FROM user_limit_override WHERE user_id = ?", java.math.BigDecimal.class, a)).isEqualByComparingTo("4");
        app.update("DELETE FROM user_limit_override WHERE user_id = ?", a);

        app.update("INSERT INTO owner_action (owner_id, kind) VALUES (?, 'QUERY')", a);
        assertThat(app.queryForObject("SELECT count(*) FROM owner_action", Integer.class)).isEqualTo(1);
        app.update("DELETE FROM owner_action WHERE owner_id = ?", a);

        app.update("INSERT INTO owner_action (owner_id, kind) VALUES (?, 'QUERY')", a);
        assertThatThrownBy(() -> app.update("UPDATE owner_action SET kind = 'ACTION'")).as("las filas de límite no se reescriben").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.execute("TRUNCATE owner_action")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.execute("TRUNCATE user_limit_override")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void theMigrationGivesNoNewPrivilegeOnUsageOrAuditTables() {
        UUID a = user();

        assertThatThrownBy(() -> app.update("DELETE FROM ai_usage")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.update("UPDATE ai_usage SET cost_usd = 0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.update("UPDATE audit_event SET action = 'X'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.update("DELETE FROM audit_event")).isInstanceOf(DataAccessException.class);
        // pero lo que el panel necesita leer se puede leer
        for (String table : List.of("ai_usage", "audit_event", "auth_session", "app_user", "course", "course_piece", "lesson_progress", "exercise_attempt", "exam_attempt", "tutor_message",
                "source_document", "course_image", "share_code", "guardianship")) {
            assertThat(app.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isNotNull();
        }
        assertThat(a).isNotNull();
    }

    @Test
    void thePanelCanAskForTheDatabaseSizeWithTheApplicationRole() {
        assertThat(app.queryForObject("SELECT pg_database_size(current_database())", Long.class)).isPositive();
    }

    @Test
    void theIndexesForTheWindowsExist() {
        List<String> indexes = owner.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename IN ('audit_event', 'owner_action')", String.class);

        assertThat(indexes).anyMatch(d -> d.contains("audit_event_action_idx") && d.contains("(action, occurred_at)"));
        assertThat(indexes).anyMatch(d -> d.contains("owner_action_idx") && d.contains("(owner_id, kind, created_at)"));
    }

    @Test
    void theMigrationOnlyAddsAndNeverDestroysNorTouchesUsage() throws IOException {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION);

        assertThat(sql).containsIgnoringCase("CREATE TABLE user_limit_override").containsIgnoringCase("CREATE TABLE owner_action").containsIgnoringCase("audit_event_action_idx");
        assertThat(sql).doesNotContainIgnoringCase("DROP ").doesNotContainIgnoringCase("TRUNCATE").doesNotContainIgnoringCase("ALTER TABLE ai_usage")
                .doesNotContainPattern("(?i)GRANT[^;]*\\bai_usage\\b").doesNotContainPattern("(?i)GRANT[^;]*\\baudit_event\\b").doesNotContainPattern("(?i)GRANT[^;]*\\bapp_user\\b");
    }

    @Test
    void theTestCleanupAlsoClearsTheNewTablesThroughTheUserCascade() {
        UUID a = user();
        UUID b = user();
        owner.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, 1, ?)", a, b);
        owner.update("INSERT INTO owner_action (owner_id, kind) VALUES (?, 'QUERY')", a);

        owner.execute("TRUNCATE app_user CASCADE");

        assertThat(count("SELECT count(*) FROM user_limit_override")).isZero();
        assertThat(count("SELECT count(*) FROM owner_action")).isZero();
    }
}
