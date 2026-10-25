package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A1 (BACKLOG): tope mensual de gasto POR USUARIO, con importes distintos para menores y adultos (application-test.properties:
 * menor 1,00 USD, adulto 5,00 USD). A diferencia del tope global, este corta siempre, también en modo WARN (el predeterminado).
 */
class AiUserLimitTest extends AiTestBase {

    private UUID newMinor() {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO app_user (id, kind, display_name, login_id, password_hash, status)
                VALUES (?, 'MINOR', 'Hugo', CAST(? AS citext), 'hash', 'ACTIVE')""", id, "m" + id.toString().substring(0, 8));
        return id;
    }

    /** Gasto previo de un usuario; {@code monthsAgo} > 0 lo sitúa en un mes anterior. */
    private void spendFor(UUID user, String usd, int monthsAgo) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens,
                                      output_tokens, tokens_estimated, cost_usd, status, duration_ms, occurred_at)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, ?, 'OK', 1,
                        now() - make_interval(months => ?))""", user, new BigDecimal(usd), monthsAgo);
    }

    private AiException rejected(UUID user) {
        return org.junit.jupiter.api.Assertions.assertThrows(AiException.class, () -> gateway.complete(request(user)));
    }

    @Test
    void a1_anAdultAtTheirLimitIsRejectedWithoutReachingTheProvider() {
        UUID user = newUser();
        spendFor(user, "5.00", 0);

        AiException e = rejected(user);

        assertThat(e.kind()).isEqualTo(AiErrorKind.USER_LIMIT);
        assertThat(fake.calls()).isEmpty();
        var row = usage().get(usage().size() - 1);
        assertThat(row.get("status")).isEqualTo("BLOCKED");
        assertThat(row.get("error_kind")).isEqualTo("USER_LIMIT");
        assertThat(auditActions()).contains("AI_USER_LIMIT_REACHED");
    }

    @Test
    void a1_itCutsEvenInWarnModeWhichOnlyAppliesToTheGlobalCap() {
        UUID user = newUser();
        spendFor(user, "5.00", 0); // muy por debajo del tope global (10 USD)

        assertThat(rejected(user).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
    }

    @Test
    void a1_aCallWhoseWorstCaseWouldCrossTheLimitIsRejectedBeforehand() {
        UUID user = newUser();
        spendFor(user, "4.9997", 0); // el peor caso de una llamada OUTLINE son ≈ 0,0004 USD

        assertThat(rejected(user).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void a1_belowTheLimitWithRoomForTheWorstCaseTheCallGoesThrough() {
        UUID user = newUser();
        spendFor(user, "4.00", 0);

        assertThat(gateway.complete(request(user)).text()).isNotBlank();
    }

    @Test
    void a1_aMinorHasALowerLimitThanAnAdult() {
        UUID adult = newUser();
        UUID minor = newMinor();
        spendFor(adult, "2.00", 0);
        spendFor(minor, "1.00", 0);

        assertThat(gateway.complete(request(adult)).text()).isNotBlank();
        assertThat(rejected(minor).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
    }

    @Test
    void a1_oneUsersSpendNeverCountsAgainstAnother() {
        UUID big = newUser();
        UUID other = newUser();
        spendFor(big, "5.00", 0);

        assertThat(rejected(big).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
        assertThat(gateway.complete(request(other)).text()).isNotBlank();
    }

    @Test
    void a1_theLimitResetsWithTheMonth() {
        UUID user = newUser();
        spendFor(user, "5.00", 1);

        assertThat(gateway.complete(request(user)).text()).isNotBlank();
    }

    @Test
    void a1_failedAttemptsBilledByTheProviderCountTowardsTheLimit() {
        UUID user = newUser();
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens,
                                      output_tokens, tokens_estimated, cost_usd, status, error_kind, duration_ms)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, 5.00, 'ERROR',
                        'INVALID_RESPONSE', 1)""", user);

        assertThat(rejected(user).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
    }

    @Test
    void a1_embeddingsAreFreeAndNeverBlocked() {
        UUID user = newUser();
        spendFor(user, "5.00", 0);

        assertThat(gateway.embed(user, java.util.List.of("uno")).vectors()).hasSize(1);
    }

    @Test
    void a1_theMessageIsClearAndGeneric() {
        UUID user = newUser();
        spendFor(user, "5.00", 0);

        assertThat(rejected(user)).hasMessageContaining("tope").hasMessageNotContaining("5");
    }

    @Test
    void a1_theBlockIsRecordedInAuditWithoutAmounts() {
        UUID user = newUser();
        spendFor(user, "5.00", 0);
        rejected(user);

        String detail = owner.queryForObject(
                "SELECT details::text FROM audit_event WHERE action = 'AI_USER_LIMIT_REACHED'", String.class);
        assertThat(detail).doesNotContain("5.00").doesNotContain("hash");
    }
}
