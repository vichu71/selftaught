package es.vicai.studylab.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Regresión hallada en la prueba de humo con el stack real: Spring Security borraba la cookie XSRF-TOKEN tras la
 * primera petición autenticada y las siguientes escrituras daban 403 en el navegador.
 *
 * Aquí NO se usa csrf(): se reenvían las cookies y la cabecera exactamente como lo hace el navegador. Va en una clase
 * aparte con su propio contexto de Spring (la propiedad de abajo cambia la clave de caché) porque csrf() sustituye de
 * forma permanente el repositorio CSRF del contexto compartido y falsearía este test.
 */
@TestPropertySource(properties = "app.test.isolated-context=csrf-cookie-flow")
class CsrfCookieFlowTest extends IntegrationTestBase {

    @Test
    void ac10_theCsrfCookieSurvivesAuthenticatedRequestsAndWorksLikeInABrowser() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        var login = tryLogin("ana@example.com", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        var session = login.getCookie(SESSION_COOKIE);
        var xsrf = login.getCookie("XSRF-TOKEN");

        for (int i = 0; i < 3; i++) {
            var response = mvc.perform(get("/api/me").cookie(session, xsrf)).andExpect(status().isOk())
                    .andReturn().getResponse();
            var cookie = response.getCookie("XSRF-TOKEN");
            assertThat(cookie == null || cookie.getMaxAge() != 0)
                    .as("el servidor no debe borrar la cookie XSRF-TOKEN").isTrue();
        }

        mvc.perform(post("/api/tutor/students").cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(studentBody("Hugo", "hugo"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/logout").cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void ac10_aWrongCsrfHeaderIsStillRejectedWithRealCookies() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        var login = tryLogin("ana@example.com", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        var session = login.getCookie(SESSION_COOKIE);
        var xsrf = login.getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/auth/logout").cookie(session, xsrf).header("X-XSRF-TOKEN", "otro-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(session, xsrf)).andExpect(status().isForbidden());
    }
}
