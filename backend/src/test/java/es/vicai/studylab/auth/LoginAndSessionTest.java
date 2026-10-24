package es.vicai.studylab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginAndSessionTest extends IntegrationTestBase {

    // ---------- AC-2: login de adulto y sesión con token ----------

    @Test
    void ac2_adultLogsInWithEmailAndGetsASecureSessionCookie() throws Exception {
        registerAndVerify("ana@example.com", "Ana");

        MockHttpServletResponse res = tryLogin("ana@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andReturn().getResponse();

        Cookie session = res.getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(res.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(h ->
                h.startsWith(SESSION_COOKIE + "=") && h.contains("SameSite=Strict"));
        assertThat(res.getCookie("XSRF-TOKEN")).isNotNull(); // token CSRF para el frontend
    }

    @Test
    void ac2_emailIsCaseInsensitiveAtLogin() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        tryLogin("ANA@Example.COM", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void ac2_sessionTokenIsStoredHashedNotInClear() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String session = login("ana@example.com", PASSWORD);

        assertThat(owner.queryForObject("SELECT count(*) FROM auth_session", Integer.class)).isEqualTo(1);
        assertThat(owner.queryForObject("SELECT count(*) FROM auth_session WHERE token_hash = ?",
                Integer.class, session)).isZero();
    }

    @Test
    void ac2_protectedEndpointsRequireAValidSession() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        getAs("token-inventado", "/api/me").andExpect(status().isUnauthorized());

        registerAndVerify("ana@example.com", "Ana");
        String session = login("ana@example.com", PASSWORD);
        getAs(session, "/api/me").andExpect(status().isOk());
        getAs(session + "x", "/api/me").andExpect(status().isUnauthorized()); // manipulado
    }

    @Test
    void edge_expiredSessionIsRejected() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String session = login("ana@example.com", PASSWORD);
        owner.update("UPDATE auth_session SET expires_at = now() - interval '1 minute'");

        getAs(session, "/api/me").andExpect(status().isUnauthorized());
    }

    @Test
    void ac2_logoutRevokesTheSession() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        String session = login("ana@example.com", PASSWORD);

        postAs(session, "/api/auth/logout", null).andExpect(status().isNoContent());

        getAs(session, "/api/me").andExpect(status().isUnauthorized());
        assertThat(owner.queryForObject("SELECT count(*) FROM auth_session WHERE revoked_at IS NOT NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void ac2_wrongPasswordAndUnknownUserGetTheSameGenericAnswer() throws Exception {
        registerAndVerify("ana@example.com", "Ana");

        String wrongPassword = tryLogin("ana@example.com", "otra-clave-12345")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = tryLogin("nadie@example.com", PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser); // no revela si la cuenta existe
    }

    @Test
    void edge_loginWithMissingFieldsIsABadRequest() throws Exception {
        postPublic("/api/auth/login", Map.of("identifier", "ana@example.com")).andExpect(status().isBadRequest());
        Map<String, Object> nullIdentifier = new HashMap<>();
        nullIdentifier.put("identifier", null);
        nullIdentifier.put("password", PASSWORD);
        postPublic("/api/auth/login", nullIdentifier).andExpect(status().isBadRequest());
    }

    // ---------- AC-6: login del menor con su identificador ----------

    @Test
    void ac6_minorLogsInWithLoginIdAndPassword() throws Exception {
        String adult = adultSession("ana@example.com");
        String studentId = createStudent(adult, "Hugo", "hugo");

        tryLogin(loginIdOf(studentId), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MINOR"));
    }

    @Test
    void ac6_thereIsNoSelfRegistrationForMinors() throws Exception {
        Map<String, Object> body = new HashMap<>(registrationBody("hugo@example.com", "Hugo"));
        body.put("kind", "MINOR");
        postPublic("/api/auth/register", body).andExpect(status().isAccepted());

        assertThat(owner.queryForObject("SELECT count(*) FROM app_user WHERE kind = 'MINOR'", Integer.class)).isZero();
    }
}
