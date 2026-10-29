package es.vicai.studylab.material;

import static es.vicai.studylab.material.PhotoFixtures.jpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** F19 AC-5: sin tarea OCR las fotos están desactivadas (contexto propio porque cambia la configuración) y el resto del material sigue funcionando. */
@TestPropertySource(properties = {"app.ai.tasks.ocr.provider=", "app.ai.tasks.ocr.model="})
class PhotosDisabledTest extends PhotoTestBase {

    @Test
    void ac5_theCapabilitiesSayNoPhotosForAnAdult() throws Exception {
        String ana = logged("ana@example.com");

        getAs(ana, "/api/material/capabilities").andExpect(status().isOk()).andExpect(jsonPath("$.images").value(false));
    }

    @Test
    void ac5_anImageIsRejectedAsATypeNotAllowedAndLeavesNoTrace() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "a.jpg", jpeg(40, 40)).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("MATERIAL_TYPE_NOT_ALLOWED"));

        assertThat(documentRows()).isZero();
        assertThat(imageActions(idOf("ana@example.com"))).isZero();
    }

    @Test
    void ac5_otherDocumentsStillWork() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "a.txt", MaterialFixtures.text("hola")).andExpect(status().isAccepted());
    }

    @Test
    void ac5_theTutorCannotTurnOnPhotosWhenTheServerHasThemOff() throws Exception {
        String ana = logged("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");

        putAs(ana, photoPath(hugoId), java.util.Map.of("enabled", true)).andExpect(status().isConflict());

        assertThat(owner.queryForObject("SELECT count(*) FROM child_setting", Integer.class)).isZero();
    }

    @Test
    void ac5_aPhotoUploadedBeforeTheyWereTurnedOffEndsInReadFailedInsteadOfHanging() throws Exception {
        String ana = logged("ana@example.com");
        owner.update("INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status, content) VALUES (gen_random_uuid(), ?, 'a.jpg', 'IMAGE', 10, ?, 'UPLOADED', ?)",
                idOf("ana@example.com"), "a".repeat(64), jpeg(40, 40));

        worker.processPending();

        String id = owner.queryForObject("SELECT id::text FROM source_document", String.class);
        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(errorKindOf(id)).isEqualTo("READ_FAILED");
        assertThat(hasOriginal(id)).isFalse();
    }
}
