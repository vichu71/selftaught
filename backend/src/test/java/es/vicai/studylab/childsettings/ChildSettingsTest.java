package es.vicai.studylab.childsettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.material.PhotoTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * F19 AC-11b (ADR-104): el permiso de fotos de un menor es un ajuste del servidor que solo cambia su tutor vinculado. Un menor ajeno, uno inexistente, un id mal formado
 * y el propio menor reciben el mismo 404. Por defecto está desactivado, es idempotente y audita solo cuando cambia, con ids.
 */
class ChildSettingsTest extends PhotoTestBase {

    private record Family(String ana, String hugoId, String hugo, String luis) {}

    private Family family() throws Exception {
        String ana = logged("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        return new Family(ana, hugoId, login("hugo", PASSWORD), logged("luis@example.com"));
    }

    @Test
    void ac11b_itIsOffByDefaultForTheTutor() throws Exception {
        Family f = family();

        getAs(f.ana(), photoPath(f.hugoId())).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(owner.queryForObject("SELECT count(*) FROM child_setting", Integer.class)).isZero(); // sin fila = desactivado
    }

    @Test
    void ac11b_theTutorTurnsItOnAndOffAndTheChangeIsStoredWithWhoDidIt() throws Exception {
        Family f = family();

        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        getAs(f.ana(), photoPath(f.hugoId())).andExpect(jsonPath("$.enabled").value(true));
        assertThat(owner.queryForObject("SELECT updated_by FROM child_setting WHERE child_id = ?::uuid", UUID.class, f.hugoId())).isEqualTo(idOf("ana@example.com"));

        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", false)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        getAs(f.ana(), photoPath(f.hugoId())).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void ac11b_itIsIdempotentAndAuditsOnlyWhenSomethingChanges() throws Exception {
        Family f = family();

        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", false)).andExpect(status().isOk()); // ya estaba desactivado
        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isOk());
        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isOk());
        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", false)).andExpect(status().isOk());

        assertThat(audit("CHILD_PHOTOS_ENABLED")).hasSize(1);
        assertThat(audit("CHILD_PHOTOS_DISABLED")).hasSize(1);
    }

    @Test
    void ac11b_theAuditCarriesIdsAndNeverNamesNorEmails() throws Exception {
        Family f = family();

        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isOk());

        String details = audit("CHILD_PHOTOS_ENABLED").get(0);
        assertThat(details).contains(f.hugoId()).doesNotContain("Hugo").doesNotContain("ana@example.com");
    }

    @Test
    void ac11b_anotherAdultWithoutLinkGetsTheSame404AsForANonexistentStudent() throws Exception {
        Family f = family();
        String strangerBody = getAs(f.luis(), photoPath(f.hugoId())).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missingBody = getAs(f.luis(), photoPath(UUID.randomUUID().toString())).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(strangerBody).isEqualTo(missingBody);
        putAs(f.luis(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isNotFound());
        assertThat(owner.queryForObject("SELECT count(*) FROM child_setting", Integer.class)).isZero();
    }

    @Test
    void ac11b_aMalformedIdIsTheSame404() throws Exception {
        Family f = family();

        getAs(f.ana(), "/api/tutor/students/no-es-un-uuid/photo-material").andExpect(status().isNotFound());
        putAs(f.ana(), "/api/tutor/students/no-es-un-uuid/photo-material", Map.of("enabled", true)).andExpect(status().isNotFound());
    }

    @Test
    void ac11b_theMinorCannotReadNorChangeItsOwnSetting() throws Exception {
        Family f = family();

        getAs(f.hugo(), photoPath(f.hugoId())).andExpect(status().isNotFound());
        putAs(f.hugo(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isNotFound());
        assertThat(owner.queryForObject("SELECT count(*) FROM child_setting", Integer.class)).isZero();
    }

    @Test
    void ac11b_itRequiresASessionAndTheCsrfTokenToChange() throws Exception {
        Family f = family();

        mvc.perform(get(photoPath(f.hugoId()))).andExpect(status().isUnauthorized());
        mvc.perform(put(photoPath(f.hugoId())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(put(photoPath(f.hugoId())).cookie(new Cookie(SESSION_COOKIE, f.ana())).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        assertThat(owner.queryForObject("SELECT count(*) FROM child_setting", Integer.class)).isZero();
    }

    @Test
    void ac11b_aBodyWithoutABooleanIsRejectedByTheBackend() throws Exception {
        Family f = family();

        for (String body : new String[] {"{}", "{\"enabled\":\"si\"}", "{\"enabled\":null}", "[]", ""}) {
            mvc.perform(put(photoPath(f.hugoId())).with(csrf()).cookie(new Cookie(SESSION_COOKIE, f.ana())).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void ac11b_aDeactivatedStudentCanBeReadButNotChanged() throws Exception {
        Family f = family();
        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isOk());
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?::uuid", f.hugoId());

        getAs(f.ana(), photoPath(f.hugoId())).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", false)).andExpect(status().isConflict());
    }

    @Test
    void ac11b_turningItOffDoesNotDeleteWhatTheMinorAlreadyUploaded() throws Exception {
        Family f = family();
        photosFor(f.hugoId(), true);
        String id = uploaded(f.hugo(), "a.jpg", es.vicai.studylab.material.PhotoFixtures.jpeg(40, 40));

        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", false)).andExpect(status().isOk());

        assertThat(statusOf(id)).isEqualTo("UPLOADED");
    }

    @Test
    void ac11b_theSettingOfOneMinorDoesNotTouchAnother() throws Exception {
        Family f = family();
        String pabloId = createStudent(f.ana(), "Pablo", "pablo");

        putAs(f.ana(), photoPath(f.hugoId()), Map.of("enabled", true)).andExpect(status().isOk());

        getAs(f.ana(), photoPath(pabloId)).andExpect(jsonPath("$.enabled").value(false));
    }
}
