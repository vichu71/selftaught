package es.vicai.studylab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** AC-3: límite de intentos de login (5 fallos por cuenta / 20 por IP en 15 minutos). */
class LoginLockoutTest extends IntegrationTestBase {

    private void failTimes(String identifier, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            tryLogin(identifier, "clave-equivocada-1").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void ac3_fiveFailuresLockTheAccountEvenWithTheRightPassword() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        failTimes("ana@example.com", 5);

        tryLogin("ana@example.com", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void ac3_lockoutIsIdenticalForExistingAndUnknownAccounts() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        failTimes("ana@example.com", 5);
        failTimes("nadie@example.com", 5);

        String known = tryLogin("ana@example.com", PASSWORD).andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        String unknown = tryLogin("nadie@example.com", PASSWORD).andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
    }

    @Test
    void ac3_lockoutExpiresAfterTheWindow() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        failTimes("ana@example.com", 5);
        owner.update("UPDATE login_attempt SET at = now() - interval '16 minutes'");

        tryLogin("ana@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void ac3_aSuccessfulLoginResetsTheFailureCount() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        failTimes("ana@example.com", 4);
        tryLogin("ana@example.com", PASSWORD).andExpect(status().isOk());
        failTimes("ana@example.com", 4);

        tryLogin("ana@example.com", PASSWORD).andExpect(status().isOk()); // 4+4 no consecutivos: sin bloqueo
    }

    @Test
    void ac3_lockingOneAccountDoesNotLockOthers() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        registerAndVerify("luis@example.com", "Luis");
        failTimes("ana@example.com", 5);

        tryLogin("luis@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void ac3_manyFailuresFromOneIpBlockThatIpOnly() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String attacker = "203.0.113.7";
        for (int i = 0; i < 20; i++) {
            postPublicFrom(attacker, "/api/auth/login",
                    java.util.Map.of("identifier", "victima" + i + "@example.com", "password", "clave-equivocada-1"))
                    .andExpect(status().isUnauthorized());
        }

        postPublicFrom(attacker, "/api/auth/login",
                java.util.Map.of("identifier", "ana@example.com", "password", PASSWORD))
                .andExpect(status().isTooManyRequests());
        postPublicFrom("198.51.100.9", "/api/auth/login",
                java.util.Map.of("identifier", "ana@example.com", "password", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void ac3_loginAttemptsDoNotStoreTheIdentifierInClear() throws Exception {
        failTimes("ana@example.com", 1);

        Integer clear = owner.queryForObject(
                "SELECT count(*) FROM login_attempt WHERE identifier_hash = 'ana@example.com'", Integer.class);
        assertThat(clear).isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM login_attempt", Integer.class)).isEqualTo(1);
    }
}
