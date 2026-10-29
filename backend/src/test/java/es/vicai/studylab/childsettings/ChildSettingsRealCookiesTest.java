package es.vicai.studylab.childsettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import es.vicai.studylab.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * F19 AC-11b: el cambio del permiso se prueba con las cookies y la cabecera exactamente como las usa el navegador (sin {@code csrf()} de MockMvc), en una clase con
 * su propio contexto: {@code csrf()} sustituye el repositorio CSRF del contexto compartido (lección de F01). Se prueba con el rol real de la aplicación.
 */
@TestPropertySource(properties = "app.test.isolated-context=child-settings-real-cookies")
class ChildSettingsRealCookiesTest extends IntegrationTestBase {

    @Test
    void ac11b_theTutorChangesItWithRealCookiesAndAWrongHeaderIsRejected() throws Exception {
        registerAndVerify("ana@example.com", "Ana");
        var login = tryLogin("ana@example.com", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        var session = login.getCookie(SESSION_COOKIE);
        var xsrf = login.getCookie("XSRF-TOKEN");
        String created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/tutor/students").cookie(session, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(studentBody("Hugo", "hugo"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String hugoId = JsonPath.read(created, "$.id");
        String path = "/api/tutor/students/" + hugoId + "/photo-material";

        mvc.perform(put(path).cookie(session, xsrf).header("X-XSRF-TOKEN", "otro-token").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get(path).cookie(session, xsrf)).andExpect(status().isOk());

        assertThat(owner.queryForObject("SELECT photo_material FROM child_setting WHERE child_id = ?::uuid", Boolean.class, hugoId)).isTrue();
    }
}
