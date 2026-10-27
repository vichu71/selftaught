package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-1, AC-2, AC-12 (ADR-87): quién es el propietario y quién puede llamar al panel. Ana es la propietaria (variable escrita con mayúsculas y espacios). */
class OwnerAccessTest extends OwnerTestBase {

    private static String withoutPath(String body, String path) {
        return body.replace(path, "RUTA").replace(path.substring(1), "RUTA");
    }

    private List<String> allGets(UUID someUser) {
        List<String> paths = new ArrayList<>(REPORTS);
        paths.add("/api/owner/users");
        paths.add(userPath(someUser, "/limit")); // un GET sobre una ruta de acción: para un anónimo, 401 igual
        return paths;
    }

    @Test
    void ac1_theAccountWhoseEmailMatchesTheVariableIsTheOwnerIgnoringCaseAndSpaces() throws Exception {
        Family f = family();

        for (String path : REPORTS) ownerGet(f.ana(), path).andExpect(status().isOk());
        ownerGet(f.ana(), "/api/owner/users").andExpect(status().isOk());
    }

    @Test
    void ac2_anAdultWhoIsNotTheOwnerGetsTheSame404AsAnUnknownRouteOnEveryEndpoint() throws Exception {
        Family f = family();
        // la respuesta de Spring a una ruta inexistente repite la ruta pedida: se compara sin ella
        String unknown = withoutPath(getAs(f.luis(), NOT_FOUND_PROBE).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(), NOT_FOUND_PROBE);

        for (String path : REPORTS) {
            String body = ownerGet(f.luis(), path).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertThat(withoutPath(body, path)).as(path).isEqualTo(unknown);
        }
        ownerGet(f.luis(), "/api/owner/users").andExpect(status().isNotFound());
        for (Action action : actions()) perform(f.luis(), f.anaId(), action).andExpect(status().isNotFound());
    }

    @Test
    void ac2_aTutorWithAMinorAndTheMinorThemselvesGetThe404Too() throws Exception {
        Family f = family();

        // Ana es la tutora de Hugo y la propietaria: sus propios cursos de adulta no cambian eso; probamos con un tutor que no es el propietario
        String marta = logged("marta@example.com");
        String nicoId = createStudent(marta, "Nico", "nico");
        String nico = login(loginIdOf(nicoId), PASSWORD);

        for (String session : List.of(marta, f.hugo(), nico)) {
            for (String path : REPORTS) ownerGet(session, path).andExpect(status().isNotFound());
            ownerGet(session, "/api/owner/users").andExpect(status().isNotFound());
            for (Action action : actions()) perform(session, f.hugoId(), action).andExpect(status().isNotFound());
        }
    }

    @Test
    void ac2_anAnonymousCallerGets401LikeAnyOtherRouteAndAWriteWithoutCsrfGets403() throws Exception {
        Family f = family();

        for (String path : allGets(f.hugoId())) mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(NOT_FOUND_PROBE)).andExpect(status().isUnauthorized());
        for (Action action : actions()) {
            String path = userPath(f.luisId(), action.suffix());
            switch (action.method()) {
                case "POST" -> postAsWithoutCsrf(f.ana(), path, action.body()).andExpect(status().isForbidden());
                case "PUT" -> putWithoutCsrf(f.ana(), path, action.body()).andExpect(status().isForbidden());
                default -> deleteWithoutCsrf(f.ana(), path).andExpect(status().isForbidden());
            }
        }
        assertThat(statusOfUser(f.luisId())).isEqualTo("ACTIVE");
        assertThat(overrideOf(f.luisId())).isNull();
    }

    @Test
    void ac2_anAdultWhoTriesLeavesAnAccessDeniedEventWithTheirIdAndNothingElse() throws Exception {
        Family f = family();

        ownerGet(f.luis(), "/api/owner/ai").andExpect(status().isNotFound());

        List<Map<String, Object>> denied = owner.queryForList("SELECT actor_user_id, details::text AS details FROM audit_event WHERE action = 'ACCESS_DENIED'");
        assertThat(denied).hasSize(1);
        assertThat(denied.get(0).get("actor_user_id")).isEqualTo(f.luisId());
        assertThat(String.valueOf(denied.get(0).get("details"))).doesNotContain("luis@example.com");
    }

    @Test
    void ac1_anUnverifiedAccountWithTheOwnerEmailCannotEvenLogInSoItHasNoPanel() throws Exception {
        // Nadie ha registrado todavía «ana»: el correo de la variable no da poder a quien no lo ha verificado
        postPublic("/api/auth/register", registrationBody("ana@example.com", "Ana")).andExpect(status().isAccepted());

        tryLogin("ana@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void ac1_aDeactivatedOwnerLosesTheSessionAndThePanel() throws Exception {
        Family f = family();
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", f.anaId());

        ownerGet(f.ana(), "/api/owner/ai").andExpect(status().isUnauthorized());
    }

    @Test
    void ac1_theRoleComesFromTheEmailOnEveryRequestSoChangingTheEmailRemovesIt() throws Exception {
        Family f = family();
        ownerGet(f.ana(), "/api/owner/prices").andExpect(status().isOk());

        owner.update("UPDATE app_user SET email = CAST('otra@example.com' AS citext) WHERE id = ?", f.anaId());

        ownerGet(f.ana(), "/api/owner/prices").andExpect(status().isNotFound());
    }

    @Test
    void ac12_meTellsOnlyTheOwnerThatTheyAreTheOwner() throws Exception {
        Family f = family();

        getAs(f.ana(), "/api/me").andExpect(status().isOk()).andExpect(jsonPath("$.owner").value(true));
        getAs(f.luis(), "/api/me").andExpect(status().isOk()).andExpect(jsonPath("$.owner").doesNotExist());
        getAs(f.hugo(), "/api/me").andExpect(status().isOk()).andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void ac1_theOwnerGetsTheirNormalRolesToo() throws Exception {
        Family f = family();

        getAs(f.ana(), "/api/me").andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItems("STUDENT", "TUTOR")));
    }
}
