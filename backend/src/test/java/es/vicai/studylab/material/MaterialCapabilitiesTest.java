package es.vicai.studylab.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** F19 AC-14 (ART-003): {@code GET /api/material/capabilities} dice a quien llama si puede subir fotos. Solo eso: ni modelo, ni coste, ni límites. */
class MaterialCapabilitiesTest extends PhotoTestBase {

    @Test
    void itRequiresASession() throws Exception {
        mvc.perform(get("/api/material/capabilities")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdultCanUploadPhotosWhenTheServerOffersThem() throws Exception {
        String ana = logged("ana@example.com");

        getAs(ana, "/api/material/capabilities").andExpect(status().isOk()).andExpect(jsonPath("$.images").value(true));
    }

    @Test
    void aMinorCannotUntilTheTutorTurnsItOn() throws Exception {
        String ana = logged("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);

        getAs(hugo, "/api/material/capabilities").andExpect(jsonPath("$.images").value(false));
        photosFor(hugoId, true);
        getAs(hugo, "/api/material/capabilities").andExpect(jsonPath("$.images").value(true));
    }

    @Test
    void theTutorOfAMinorIsNotAffectedByTheSettingOfTheMinor() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");

        getAs(ana, "/api/material/capabilities").andExpect(jsonPath("$.images").value(true));
    }

    @Test
    void theAnswerHasOnlyTheImagesField() throws Exception {
        String ana = logged("ana@example.com");

        getAs(ana, "/api/material/capabilities").andExpect(jsonPath("$.length()").value(1));
    }
}
