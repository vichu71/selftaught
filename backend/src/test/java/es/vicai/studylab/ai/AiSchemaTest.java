package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** AC-6 y AC-17 a nivel de BD: el registro de uso no puede guardar contenido ni alterarse. */
class AiSchemaTest extends AiTestBase {

    @Test
    void ac6_theUsageTableHasNoColumnThatCouldHoldPromptsOrAnswers() {
        var columns = owner.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'ai_usage' AND table_schema = current_schema()""", String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "request_id", "attempt", "occurred_at", "user_id", "course_id",
                "source", "task", "provider", "model", "input_tokens", "output_tokens", "tokens_estimated", "cost_usd",
                "status", "error_kind", "finish_reason", "duration_ms");
    }

    @Test
    void ac6_theUsageLogIsAppendOnlyEvenForTheOwner() {
        spend("0.10");

        assertThatThrownBy(() -> owner.update("UPDATE ai_usage SET cost_usd = 0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> owner.update("DELETE FROM ai_usage")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.update("UPDATE ai_usage SET cost_usd = 0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.update("DELETE FROM ai_usage")).isInstanceOf(DataAccessException.class);
        assertThat(usage()).hasSize(1);
    }

    @Test
    void ac6_theApplicationRoleCanInsertAndReadUsageButNotChangeTheSchema() {
        app.update("""
                INSERT INTO ai_usage (request_id, attempt, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, 'APP', 'LESSON', 'fake', 'fake-1', 1, 1, false, 0, 'OK', 1)""");

        assertThat(app.queryForObject("SELECT count(*) FROM ai_usage", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> app.execute("DROP TABLE ai_usage")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void ac6_onlyKnownValuesAreAcceptedForSourceStatusAndTask() {
        for (String bad : new String[] {
                "source = 'OTRA'", "status = 'MAL'", "task = 'INVENTADA'"}) {
            String sql = "INSERT INTO ai_usage (request_id, attempt, source, task, provider, model, input_tokens, output_tokens,"
                    + " tokens_estimated, cost_usd, status, duration_ms) SELECT gen_random_uuid(), 1, "
                    + (bad.startsWith("source") ? "'OTRA'" : "'APP'") + ", "
                    + (bad.startsWith("task") ? "'INVENTADA'" : "'LESSON'") + ", 'fake', 'fake-1', 1, 1, false, 0, "
                    + (bad.startsWith("status") ? "'MAL'" : "'OK'") + ", 1";
            assertThatThrownBy(() -> owner.update(sql)).as(bad).isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void ac9_onlyOneAlertPerMonthAndThreshold() {
        owner.update("INSERT INTO ai_budget_alert (month, threshold) VALUES ('2026-11', 80)");

        assertThatThrownBy(() -> owner.update("INSERT INTO ai_budget_alert (month, threshold) VALUES ('2026-11', 80)"))
                .isInstanceOf(DataAccessException.class);
        owner.update("INSERT INTO ai_budget_alert (month, threshold) VALUES ('2026-11', 100)");
        owner.update("INSERT INTO ai_budget_alert (month, threshold) VALUES ('2026-12', 80)");
    }

    @Test
    void ac11_thereIsAtMostOneLockPerUserAndItBelongsToARealUser() {
        UUID user = newUser();
        owner.update("INSERT INTO ai_generation_lock (user_id, owner, expires_at) VALUES (?, gen_random_uuid(), now() + interval '1 minute')", user);

        assertThatThrownBy(() -> owner.update(
                "INSERT INTO ai_generation_lock (user_id, owner, expires_at) VALUES (?, gen_random_uuid(), now())", user))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> owner.update(
                "INSERT INTO ai_generation_lock (user_id, owner, expires_at) VALUES (gen_random_uuid(), gen_random_uuid(), now())"))
                .isInstanceOf(DataAccessException.class);
    }
}
