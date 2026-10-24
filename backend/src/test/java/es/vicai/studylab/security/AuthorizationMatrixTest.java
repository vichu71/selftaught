package es.vicai.studylab.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/** AC-10: comprobación de permisos común; lo no declarado está denegado por defecto. */
class AuthorizationMatrixTest extends IntegrationTestBase {

    // Constante de compilación: las anotaciones @CsvSource no admiten valores calculados
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    @ParameterizedTest
    @CsvSource({
            "GET,/api/me",
            "POST,/api/auth/logout",
            "GET,/api/tutor/students",
            "POST,/api/tutor/students",
            "POST,/api/tutor/students/" + ID + "/reset-password",
            "POST,/api/tutor/students/" + ID + "/deactivate",
            "POST,/api/tutor/students/" + ID + "/reactivate",
            "GET,/api/endpoint-que-no-existe"})
    void ac10_everythingExceptThePublicAuthEndpointsNeedsASession(String method, String path) throws Exception {
        var request = method.equals("GET") ? get(path) : post(path).with(csrf());
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({
            "/api/tutor/students",
            "/api/tutor/students/" + ID + "/deactivate"})
    void ac10_tutorEndpointsRejectMinorsBeforeLookingAtTheData(String path) throws Exception {
        String ana = adultSession("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String minor = login("hugo", PASSWORD);

        postAs(minor, path, java.util.Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void ac10_mutatingRequestsWithASessionButWithoutCsrfAreRejected() throws Exception {
        String ana = adultSession("ana@example.com");
        postAsWithoutCsrf(ana, "/api/auth/logout", null).andExpect(status().isForbidden());
        getAs(ana, "/api/me").andExpect(status().isOk()); // la sesión sigue viva
    }

    @Test
    void ac10_apiResponsesAreNotCachedAndHaveBasicSecurityHeaders() throws Exception {
        String ana = adultSession("ana@example.com");
        mvc.perform(get("/api/me").cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, ana)))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("X-Powered-By"));
    }

    @Test
    void ac10_methodsNotDeclaredAreRefused() throws Exception {
        String ana = adultSession("ana@example.com");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(HttpMethod.DELETE, "/api/tutor/students/" + ID)
                        .with(csrf()).cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, ana)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    @Test
    void ac12_healthIsUpAndExposesNoDetailsOrOtherActuatorEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("UP"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.components").doesNotExist());
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/mappings"}) {
            mvc.perform(get(path)).andExpect(result ->
                    assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        }
    }
}
