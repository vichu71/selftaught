package es.vicai.studylab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AC-3b: recuperación de contraseña por correo. */
class PasswordRecoveryTest extends IntegrationTestBase {

    private static final String NEW_PASSWORD = "Nueva-Clave-77x";

    private String requestResetToken(String email) throws Exception {
        postPublic("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isAccepted());
        return latestToken(email);
    }

    @Test
    void ac3b_forgotPasswordSendsAResetLink() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        greenMail.purgeEmailFromAllMailboxes();

        postPublic("/api/auth/forgot-password", Map.of("email", "ana@example.com")).andExpect(status().isAccepted());

        assertThat(mailsTo("ana@example.com")).hasSize(1);
        assertThat(bodyOf(mailsTo("ana@example.com").get(0))).contains("http://localhost/reset-password?token=");
    }

    @Test
    void ac3b_answerIsTheSameForUnknownEmailsAndNoMailIsSent() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        greenMail.purgeEmailFromAllMailboxes();

        String known = postPublic("/api/auth/forgot-password", Map.of("email", "ana@example.com"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        greenMail.purgeEmailFromAllMailboxes();
        String unknown = postPublic("/api/auth/forgot-password", Map.of("email", "nadie@example.com"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void edge_forgotPasswordRejectsMalformedEmail() throws Exception {
        postPublic("/api/auth/forgot-password", Map.of("email", "no-es-correo")).andExpect(status().isBadRequest());
        postPublic("/api/auth/forgot-password", Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void ac3b_resetChangesThePasswordAndRevokesEveryOpenSession() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String oldSession = login("ana@example.com", PASSWORD);
        String token = requestResetToken("ana@example.com");

        postPublic("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        getAs(oldSession, "/api/me").andExpect(status().isUnauthorized());
        tryLogin("ana@example.com", PASSWORD).andExpect(status().isUnauthorized());
        tryLogin("ana@example.com", NEW_PASSWORD).andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT password_hash FROM app_user", String.class))
                .startsWith("$argon2id$");
    }

    @Test
    void edge_resetTokenCannotBeReused() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String token = requestResetToken("ana@example.com");
        postPublic("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        postPublic("/api/auth/reset-password", Map.of("token", token, "newPassword", "Otra-Clave-1234"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void edge_expiredResetTokenIsRejected() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String token = requestResetToken("ana@example.com");
        owner.update("UPDATE email_token SET expires_at = now() - interval '1 minute' WHERE purpose = 'RESET_PASSWORD'");

        postPublic("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        tryLogin("ana@example.com", PASSWORD).andExpect(status().isOk()); // la clave antigua sigue valiendo
    }

    @Test
    void edge_resetRejectsWeakPasswordsAndGarbageTokens() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String token = requestResetToken("ana@example.com");

        postPublic("/api/auth/reset-password", Map.of("token", token, "newPassword", "corta"))
                .andExpect(status().isBadRequest());
        postPublic("/api/auth/reset-password", Map.of("token", token, "newPassword", "x".repeat(129)))
                .andExpect(status().isBadRequest());
        postPublic("/api/auth/reset-password", Map.of("token", "inventado", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        // una petición inválida no gasta el token
        postPublic("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void edge_verificationTokenCannotResetAPassword() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        String verificationToken = latestToken("ana@example.com");

        postPublic("/api/auth/reset-password", Map.of("token", verificationToken, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void edge_pendingAccountsDoNotReceiveResetLinks() throws Exception {
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana"));
        greenMail.purgeEmailFromAllMailboxes();

        postPublic("/api/auth/forgot-password", Map.of("email", "ana@example.com")).andExpect(status().isAccepted());

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
