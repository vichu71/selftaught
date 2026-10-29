package es.vicai.studylab.material;

import static es.vicai.studylab.material.PhotoFixtures.jpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * F19 AC-11 (ADR-104): un menor solo sube fotos si su tutor lo ha activado, y entonces la lectura devuelve {@code appropriate} y {@code ContentGuard} revisa el
 * texto leído antes de indexarlo. El mensaje al menor no lleva categoría ni motivo. Un adulto ignora {@code appropriate}.
 */
class PhotoMinorTest extends PhotoTestBase {

    private static final String BLOCKED_TEXT = "Aquí se explica como hacer una bomba casera paso a paso.";

    private record Pair(String ana, String hugoId, String hugo) {}

    private Pair family(boolean photos) throws Exception {
        String ana = logged("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        if (photos) photosFor(hugoId, true);
        return new Pair(ana, hugoId, hugo);
    }

    @Test
    void ac11_aMinorWithoutThePermissionGets403WithNoRowNoActionAndNoCall() throws Exception {
        Pair f = family(false);

        upload(f.hugo(), "a.jpg", jpeg(40, 40)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHILD_IMAGES_NOT_ENABLED"));

        assertThat(documentRows()).isZero();
        assertThat(imageActions(java.util.UUID.fromString(f.hugoId()))).isZero();
        assertThat(fake.visionCalls()).isEmpty();
    }

    @Test
    void ac11_theMinorStillUploadsOtherDocumentsWithoutThePermission() throws Exception {
        Pair f = family(false);

        upload(f.hugo(), "a.txt", MaterialFixtures.text("apuntes de historia")).andExpect(status().isAccepted());
    }

    @Test
    void ac11_afterTheTutorTurnsItOffTheMinorCannotUploadMoreButWhatWasUploadedStays() throws Exception {
        Pair f = family(true);
        String id = uploaded(f.hugo(), "a.jpg", jpeg(40, 40));

        photosFor(f.hugoId(), false);

        upload(f.hugo(), "b.jpg", jpeg(41, 41)).andExpect(status().isForbidden());
        assertThat(statusOf(id)).isEqualTo("UPLOADED");
    }

    @Test
    void ac11_withThePermissionAReadablePhotoIsReadWithTheAudienceOfAMinorAsData() throws Exception {
        Pair f = family(true);
        modelReads(READ_TEXT);
        String id = uploaded(f.hugo(), "a.jpg", jpeg(40, 40));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("READY");
        assertThat(fake.visionCalls().get(0).data()).contains("menor");
        assertThat(chunkCount(id)).isGreaterThan(0);
    }

    @Test
    void ac11_anInappropriateJudgementBlocksTheDocumentAndNothingIsIndexed() throws Exception {
        Pair f = family(true);
        fake.visionRespondsWith(envelope(false, ""), 1_000, 100);
        String id = uploaded(f.hugo(), "a.jpg", jpeg(40, 40));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(errorKindOf(id)).isEqualTo("BLOCKED");
        assertThat(chunkCount(id)).isZero();
        assertThat(hasOriginal(id)).isFalse();
        assertThat(fake.embedCalls()).isEmpty();
    }

    @Test
    void ac11_theLocalFilterOnTheReadTextBlocksEvenWhenTheModelSaysAppropriate() throws Exception {
        Pair f = family(true);
        modelReads(BLOCKED_TEXT);
        String id = uploaded(f.hugo(), "a.jpg", jpeg(40, 40));

        worker.processPending();

        assertThat(errorKindOf(id)).isEqualTo("BLOCKED");
        assertThat(chunkCount(id)).isZero();
        assertThat(fake.embedCalls()).isEmpty();
    }

    @Test
    void ac11_theBlockIsAuditedWithIdsAndNeverTheTextNorTheName() throws Exception {
        Pair f = family(true);
        modelReads(BLOCKED_TEXT);
        String id = uploaded(f.hugo(), "nombre-del-menor.jpg", jpeg(40, 40));

        worker.processPending();

        assertThat(audit("DOCUMENT_BLOCKED")).hasSize(1);
        assertThat(audit("DOCUMENT_BLOCKED").get(0)).contains(id).doesNotContain("bomba").doesNotContain("nombre-del-menor");
    }

    @Test
    void ac11_theMessageOfTheDocumentHasNoCategoryNorReason() throws Exception {
        Pair f = family(true);
        modelReads(BLOCKED_TEXT);
        uploaded(f.hugo(), "a.jpg", jpeg(40, 40));
        worker.processPending();

        String body = getAs(f.hugo(), "/api/material").andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"errorKind\":\"BLOCKED\"").doesNotContain("bomba").doesNotContain("VIOLENCE").doesNotContain("category");
    }

    @Test
    void ac11_theTutorSeesTheBlockedDocumentInTheListOfTheMinorButNotTheTextNorTheImage() throws Exception {
        Pair f = family(true);
        modelReads(BLOCKED_TEXT);
        uploaded(f.hugo(), "a.jpg", jpeg(40, 40));
        worker.processPending();

        String body = getAs(f.ana(), "/api/material").andExpect(jsonPath("$.minors[0].documents[0].mediaType").value("IMAGE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("bomba").doesNotContain("content");
    }

    @Test
    void ac11_theTutorGetsAnAlertInThePanelWithoutTheText() throws Exception {
        Pair f = family(true);
        modelReads(BLOCKED_TEXT);
        uploaded(f.hugo(), "a.jpg", jpeg(40, 40));
        worker.processPending();

        String body = getAs(f.ana(), "/api/tutor/students/" + f.hugoId() + "/alerts").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("DOCUMENT_BLOCKED").doesNotContain("bomba");
    }

    @Test
    void ac11_anAdultIgnoresTheJudgementOfTheModel() throws Exception {
        String ana = logged("ana@example.com");
        fake.visionRespondsWith(envelope(false, READ_TEXT), 1_000, 100);
        String id = uploaded(ana, "a.jpg", jpeg(40, 40));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("READY");
        assertThat(fake.visionCalls().get(0).data()).contains("adulto");
    }
}
