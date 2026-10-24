package es.vicai.studylab.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import es.vicai.studylab.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AC-8 (roles) y AC-16 (datos mínimos en las respuestas). */
class MeEndpointTest extends IntegrationTestBase {

    @Test
    void ac8_anAdultIsStudentAndTutorWithTheSameAccount() throws Exception {
        String ana = adultSession("ana@example.com");

        getAs(ana, "/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("ADULT"))
                .andExpect(jsonPath("$.roles").value(containsInAnyOrder("STUDENT", "TUTOR")));
    }

    @Test
    void ac8_aMinorIsOnlyAStudent() throws Exception {
        String ana = adultSession("ana@example.com");
        createStudent(ana, "Hugo", "hugo");

        getAs(login("hugo", PASSWORD), "/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MINOR"))
                .andExpect(jsonPath("$.roles").value(containsInAnyOrder("STUDENT")));
    }

    @Test
    void ac16_adultResponseExposesOnlyTheMinimumFields() throws Exception {
        String body = getAs(adultSession("ana@example.com"), "/api/me")
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> me = JsonPath.read(body, "$");
        assertThat(me.keySet()).containsExactlyInAnyOrder("id", "displayName", "kind", "roles", "email");
    }

    @Test
    void ac16_minorResponseHasNoEmailAndNothingSensitive() throws Exception {
        String ana = adultSession("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String body = getAs(login("hugo", PASSWORD), "/api/me").andReturn().getResponse().getContentAsString();

        Map<String, Object> me = JsonPath.read(body, "$");
        assertThat(me.keySet()).containsExactlyInAnyOrder("id", "displayName", "kind", "roles", "loginId");
        assertThat(body).doesNotContain("password").doesNotContain("hash").doesNotContain("ana@example.com");
    }

    @Test
    void ac16_theUserTableKeepsOnlyMinimalPersonalColumns() {
        var columns = owner.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'app_user' AND table_schema = current_schema()""", String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "kind", "display_name", "email", "login_id",
                "password_hash", "status", "created_at", "updated_at");
    }
}
