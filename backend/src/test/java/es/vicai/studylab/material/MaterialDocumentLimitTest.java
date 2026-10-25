package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-3: máximo de documentos por usuario (contexto propio porque cambia la configuración). */
@TestPropertySource(properties = "app.material.max-documents=3")
class MaterialDocumentLimitTest extends MaterialTestBase {

    @Test
    void ac3_theFourthDocumentIsRejected() throws Exception {
        String ana = logged("ana@example.com");
        for (int i = 0; i < 3; i++) upload(ana, "d" + i + ".txt", text("contenido " + i)).andExpect(status().isAccepted());

        upload(ana, "d4.txt", text("contenido 4")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_LIMIT_REACHED"));
    }

    @Test
    void ac3_deletedDocumentsDoNotCountSoYouCanUploadAgain() throws Exception {
        String ana = logged("ana@example.com");
        String first = null;
        for (int i = 0; i < 3; i++) {
            String body = upload(ana, "d" + i + ".txt", text("contenido " + i)).andReturn().getResponse().getContentAsString();
            if (i == 0) first = JsonPath.read(body, "$.id");
        }
        deleteAs(ana, first).andExpect(status().isNoContent());

        upload(ana, "nuevo.txt", text("contenido nuevo")).andExpect(status().isAccepted());
    }

    @Test
    void ac3_theLimitIsPerUserNotGlobal() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        for (int i = 0; i < 3; i++) upload(ana, "d" + i + ".txt", text("contenido " + i)).andExpect(status().isAccepted());

        upload(luis, "suyo.txt", text("de luis")).andExpect(status().isAccepted());
    }
}
