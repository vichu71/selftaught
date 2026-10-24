package es.vicai.studylab.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** AC-17: registro de auditoría de solo añadir, sin secretos. */
class AuditTrailTest extends IntegrationTestBase {

    private List<String> actions() {
        return owner.queryForList("SELECT action FROM audit_event ORDER BY id", String.class);
    }

    private Map<String, Object> lastEvent(String action) {
        return owner.queryForMap("""
                SELECT action, outcome, ip, occurred_at, actor_user_id::text AS actor, subject_user_id::text AS subject
                FROM audit_event WHERE action = ? ORDER BY id DESC LIMIT 1""", action);
    }

    private String userId(String where, String value) {
        return owner.queryForObject("SELECT id::text FROM app_user WHERE " + where + " = ?", String.class, value);
    }

    @Test
    void ac17_accountLifecycleEventsAreRecorded() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String session = login("ana@example.com", PASSWORD);
        postAs(session, "/api/auth/logout", null).andExpect(status().isNoContent());
        postPublic("/api/auth/forgot-password", Map.of("email", "ana@example.com"));
        postPublic("/api/auth/reset-password", Map.of("token", latestToken("ana@example.com"),
                "newPassword", "Nueva-Clave-77x")).andExpect(status().isNoContent());

        assertThat(actions()).containsSubsequence("REGISTER", "EMAIL_VERIFIED", "LOGIN_OK", "LOGOUT",
                "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_DONE");
        Map<String, Object> ok = lastEvent("LOGIN_OK");
        assertThat(ok.get("outcome")).isEqualTo("OK");
        assertThat(ok.get("actor")).isEqualTo(userId("email", "ana@example.com"));
        assertThat(ok.get("ip")).isNotNull();
        assertThat(ok.get("occurred_at")).isNotNull();
    }

    @Test
    void ac17_failedAndLockedLoginsAreRecorded() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        for (int i = 0; i < 5; i++) tryLogin("ana@example.com", "clave-equivocada-1");
        tryLogin("ana@example.com", PASSWORD).andExpect(status().isTooManyRequests());

        assertThat(actions()).contains("LOGIN_FAILED", "LOGIN_LOCKED");
        assertThat(lastEvent("LOGIN_FAILED").get("outcome")).isEqualTo("FAILED");
        assertThat(lastEvent("LOGIN_LOCKED").get("outcome")).isEqualTo("DENIED");
    }

    @Test
    void ac17_studentManagementEventsRecordWhoDidWhatToWhom() throws Exception {
        String ana = adultSession("ana@example.com");
        String id = createStudent(ana, "Hugo", "hugo");
        postAs(ana, "/api/tutor/students/" + id + "/reset-password", Map.of("newPassword", "Otra-Clave-1234"));
        postAs(ana, "/api/tutor/students/" + id + "/deactivate", null);
        postAs(ana, "/api/tutor/students/" + id + "/reactivate", null);

        assertThat(actions()).containsSubsequence("STUDENT_CREATED", "STUDENT_PASSWORD_RESET",
                "STUDENT_DEACTIVATED", "STUDENT_REACTIVATED");
        Map<String, Object> created = lastEvent("STUDENT_CREATED");
        assertThat(created.get("actor")).isEqualTo(userId("email", "ana@example.com"));
        assertThat(created.get("subject")).isEqualTo(id);
    }

    @Test
    void ac17_deniedAccessToAnotherFamilyIsRecorded() throws Exception {
        String ana = adultSession("ana@example.com");
        String id = createStudent(ana, "Hugo", "hugo");
        String luis = adultSession("luis@example.com");

        postAs(luis, "/api/tutor/students/" + id + "/deactivate", null).andExpect(status().isNotFound());
        postAs(luis, "/api/tutor/students/" + UUID.randomUUID() + "/deactivate", null);

        Map<String, Object> denied = lastEvent("ACCESS_DENIED");
        assertThat(denied.get("outcome")).isEqualTo("DENIED");
        assertThat(denied.get("actor")).isEqualTo(userId("email", "luis@example.com"));
    }

    @Test
    void ac17_noSecretsEndUpInTheAuditLog() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String session = login("ana@example.com", PASSWORD);
        String token = "";
        postPublic("/api/auth/forgot-password", Map.of("email", "ana@example.com"));
        token = latestToken("ana@example.com");
        createStudent(session, "Hugo", "hugo");
        String hash = owner.queryForObject("SELECT password_hash FROM app_user WHERE email = 'ana@example.com'",
                String.class);

        String everything = String.join("|", owner.queryForList(
                "SELECT concat_ws('|', action, outcome, ip, details::text) FROM audit_event", String.class));

        assertThat(everything)
                .doesNotContain(PASSWORD)
                .doesNotContain(session)
                .doesNotContain(token)
                .doesNotContain(hash)
                .doesNotContain("argon2");
    }

    @Test
    void ac17_unknownEmailsAtLoginAreNotStoredInClear() throws Exception {
        tryLogin("fantasma@example.com", PASSWORD).andExpect(status().isUnauthorized());

        String everything = String.join("|", owner.queryForList(
                "SELECT concat_ws('|', action, details::text) FROM audit_event", String.class));
        assertThat(everything).doesNotContain("fantasma@example.com");
        assertThat(actions()).contains("LOGIN_FAILED");
    }

    @Test
    void ac17_theApplicationRoleCannotModifyOrDeleteAuditRows() throws Exception {
        registerAndVerify("ana@example.com", "Ana");

        assertThatThrownBy(() -> app.update("UPDATE audit_event SET action = 'MANIPULADO'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.update("DELETE FROM audit_event"))
                .isInstanceOf(DataAccessException.class);
        assertThat(actions()).contains("REGISTER");
    }

    @Test
    void ac17_evenTheOwnerCannotUpdateOrDeleteRowsBecauseOfTheTrigger() {
        owner.update("INSERT INTO audit_event (action, outcome, ip) VALUES ('PRUEBA', 'OK', '127.0.0.1')");

        assertThatThrownBy(() -> owner.update("UPDATE audit_event SET action = 'MANIPULADO'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> owner.update("DELETE FROM audit_event WHERE action = 'PRUEBA'"))
                .isInstanceOf(DataAccessException.class);
    }
}
