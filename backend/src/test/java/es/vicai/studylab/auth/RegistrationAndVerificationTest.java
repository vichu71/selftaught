package es.vicai.studylab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RegistrationAndVerificationTest extends IntegrationTestBase {

    // ---------- AC-1: registro de adulto ----------

    @Test
    void ac1_registerCreatesPendingAdultWithArgon2Hash() throws Exception {
        postPublic("/api/auth/register", registrationBody("Ana@Example.com", "Ana")).andExpect(status().isAccepted());

        Map<String, Object> row = owner.queryForMap(
                "SELECT kind, status, email::text AS email, password_hash FROM app_user");
        assertThat(row.get("kind")).isEqualTo("ADULT");
        assertThat(row.get("status")).isEqualTo("PENDING_VERIFICATION");
        assertThat(row.get("email")).isEqualTo("ana@example.com"); // normalizado a minúsculas
        assertThat((String) row.get("password_hash")).startsWith("$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void ac1_registerSendsVerificationEmailWithLink() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana")).andExpect(status().isAccepted());

        assertThat(mailsTo("ana@example.com")).hasSize(1);
        assertThat(bodyOf(mailsTo("ana@example.com").get(0))).contains("http://localhost/verify-email?token=");
    }

    @Test
    void ac1_registerNeedsNoCsrfToken() throws Exception {
        // Endpoint público: el frontend aún no tiene sesión ni token CSRF
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana")).andExpect(status().isAccepted());
    }

    @Test
    void edge_duplicateEmailLooksIdenticalAndCreatesNoSecondAccount() throws Exception {
        String first = postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String second = postPublic("/api/auth/register", registrationBody("ANA@example.com", "Otra"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first); // no revela si el correo existe
        assertThat(owner.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(1);
        assertThat(mailsTo("ana@example.com")).hasSize(2); // el segundo es un aviso de "ya tienes cuenta"
    }

    static Stream<Map<String, Object>> invalidRegistrations() {
        return Stream.of(
                with("email", "no-es-un-correo"),
                with("email", "a".repeat(250) + "@x.es"), // > 254
                with("email", null),
                with("password", "corta-9ch"), // 9 caracteres
                with("password", "x".repeat(129)),
                with("password", null),
                with("displayName", ""),
                with("displayName", "   "),
                with("displayName", "n".repeat(51)),
                with("displayName", null));
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> body = new HashMap<>(
                Map.of("email", "ana@example.com", "password", PASSWORD, "displayName", "Ana"));
        body.put(key, value);
        return body;
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void edge_invalidRegistrationIsRejectedByTheBackend(Map<String, Object> body) throws Exception {
        postPublic("/api/auth/register", body).andExpect(status().isBadRequest());
        assertThat(owner.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isZero();
    }

    @Test
    void edge_registerIgnoresPrivilegedFields() throws Exception {
        Map<String, Object> body = new HashMap<>(registrationBody("ana@example.com", "Ana"));
        body.put("kind", "MINOR");
        body.put("status", "ACTIVE");
        body.put("id", "00000000-0000-0000-0000-000000000001");
        postPublic("/api/auth/register", body).andExpect(status().isAccepted());

        Map<String, Object> row = owner.queryForMap("SELECT id::text AS id, kind, status FROM app_user");
        assertThat(row.get("kind")).isEqualTo("ADULT");
        assertThat(row.get("status")).isEqualTo("PENDING_VERIFICATION");
        assertThat(row.get("id")).isNotEqualTo("00000000-0000-0000-0000-000000000001");
    }

    // ---------- AC-1b: verificación del correo ----------

    @Test
    void ac1b_verifyActivatesTheAccount() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        postPublic("/api/auth/verify-email", Map.of("token", latestToken("ana@example.com")))
                .andExpect(status().isNoContent());

        assertThat(owner.queryForObject("SELECT status FROM app_user", String.class)).isEqualTo("ACTIVE");
        assertThat(owner.queryForObject("SELECT count(*) FROM email_token WHERE used_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ac1b_tokenIsStoredHashedNotInClear() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        String token = latestToken("ana@example.com");

        assertThat(owner.queryForObject("SELECT count(*) FROM email_token WHERE token_hash = ?", Integer.class, token))
                .isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM email_token", Integer.class)).isEqualTo(1);
    }

    @Test
    void ac1b_accountCannotLogInBeforeVerifying() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        tryLogin("ana@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void edge_tokenCannotBeReused() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        String token = latestToken("ana@example.com");
        postPublic("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isNoContent());
        postPublic("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());
    }

    @Test
    void edge_expiredTokenIsRejectedAndAccountStaysPending() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        owner.update("UPDATE email_token SET expires_at = now() - interval '1 minute'");

        postPublic("/api/auth/verify-email", Map.of("token", latestToken("ana@example.com")))
                .andExpect(status().isBadRequest());
        assertThat(owner.queryForObject("SELECT status FROM app_user", String.class))
                .isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void edge_unknownOrMalformedTokensAreRejected() throws Exception {
        postPublic("/api/auth/verify-email", Map.of("token", "inventado-123")).andExpect(status().isBadRequest());
        postPublic("/api/auth/verify-email", Map.of("token", "")).andExpect(status().isBadRequest());
        postPublic("/api/auth/verify-email", Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void edge_passwordResetTokenCannotVerifyAnAccount() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        postPublic("/api/auth/forgot-password", Map.of("email", "ana@example.com"));
        String resetToken = latestToken("ana@example.com");

        postPublic("/api/auth/verify-email", Map.of("token", resetToken)).andExpect(status().isBadRequest());
    }

    @Test
    void ac1b_resendSendsANewEmailForPendingAccounts() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        postPublic("/api/auth/resend-verification", Map.of("email", "ana@example.com"))
                .andExpect(status().isAccepted());

        assertThat(mailsTo("ana@example.com")).hasSize(2);
    }

    @Test
    void ac1b_resendRevealsNothingAboutUnknownOrAlreadyVerifiedEmails() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        greenMail.purgeEmailFromAllMailboxes();

        String active = postPublic("/api/auth/resend-verification", Map.of("email", "ana@example.com"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = postPublic("/api/auth/resend-verification", Map.of("email", "nadie@example.com"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(active).isEqualTo(unknown);
        assertThat(List.of(greenMail.getReceivedMessages())).isEmpty();
    }
}
