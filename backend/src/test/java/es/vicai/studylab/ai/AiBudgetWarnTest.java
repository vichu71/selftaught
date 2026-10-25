package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-8 (modo aviso, el predeterminado), AC-9 y AC-18: el gasto se vigila y se avisa, pero no se corta. */
class AiBudgetWarnTest extends AiTestBase {

    private long warnings(int threshold) {
        return owner.queryForObject(
                "SELECT count(*) FROM audit_event WHERE action = 'AI_BUDGET_WARNING' AND details ->> 'threshold' = ?",
                Long.class, String.valueOf(threshold));
    }

    @Test
    void ac9_crossingTheWarningThresholdRaisesOneAuditEventAndOneAlertRow() {
        respondWithCost20Cents();
        spend("7.90"); // 7,90 + 0,20 = 8,10 USD → 81 % del tope de 10 USD
        UUID user = newUser();

        gateway.complete(request(user));

        assertThat(warnings(80)).isEqualTo(1);
        assertThat(owner.queryForObject("SELECT count(*) FROM ai_budget_alert WHERE threshold = 80", Integer.class)).isEqualTo(1);
        String details = owner.queryForObject("SELECT details::text FROM audit_event WHERE action = 'AI_BUDGET_WARNING'", String.class);
        assertThat(details).contains("\"percent\"").contains("\"threshold\"");
    }

    @Test
    void ac9_theSameThresholdIsNotRaisedTwiceInTheSameMonth() {
        respondWithCost20Cents();
        spend("7.90");
        UUID user = newUser();

        gateway.complete(request(user));
        gateway.complete(request(user));
        gateway.complete(request(user));

        assertThat(warnings(80)).isEqualTo(1);
    }

    @Test
    void ac9_noWarningWhileTheSpendStaysBelowTheThreshold() {
        respondWithCost20Cents();
        spend("1.00");

        gateway.complete(request(newUser()));

        assertThat(auditActions()).doesNotContain("AI_BUDGET_WARNING");
    }

    @Test
    void ac8_inWarnModeCallsKeepWorkingEvenAboveTheLimitAndTheHundredPercentWarningIsRaised() {
        respondWithCost20Cents();
        spend("9.95"); // 9,95 + 0,20 = 10,15 USD → 101 %
        UUID user = newUser();

        AiResponse response = gateway.complete(request(user));

        assertThat(response.text()).isNotBlank();
        assertThat(fake.calls()).hasSize(1);
        assertThat(warnings(100)).isEqualTo(1);
        assertThat(auditActions()).doesNotContain("AI_BUDGET_BLOCKED");
        gateway.complete(request(user)); // sigue funcionando
        assertThat(fake.calls()).hasSize(2);
    }

    @Test
    void edge_spendFromThePreviousMadridMonthDoesNotCount() {
        respondWithCost20Cents();
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms, occurred_at)
                VALUES (gen_random_uuid(), 1, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, 9.50, 'OK', 1,
                        (date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid') - interval '1 minute')""");

        gateway.complete(request(newUser()));

        assertThat(auditActions()).doesNotContain("AI_BUDGET_WARNING");
    }

    @Test
    void edge_spendFromTheFirstMinuteOfTheMadridMonthDoesCount() {
        respondWithCost20Cents();
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms, occurred_at)
                VALUES (gen_random_uuid(), 1, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, 8.50, 'OK', 1,
                        (date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid') + interval '1 minute')""");

        gateway.complete(request(newUser()));

        assertThat(warnings(80)).isEqualTo(1);
    }

    @Test
    void edge_failedAndBlockedAttemptsCostNothing() {
        fake.failNext(es.vicai.studylab.ai.provider.ProviderErrorKind.AUTH, 1);
        try {
            gateway.complete(request(newUser()));
        } catch (AiException expected) {
            // el intento fallido queda registrado con coste 0
        }

        assertThat(usage().get(0).get("cost_usd")).isEqualTo(new java.math.BigDecimal("0.000000"));
    }
}
