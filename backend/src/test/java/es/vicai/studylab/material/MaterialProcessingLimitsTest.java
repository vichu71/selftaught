package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-5 y AC-7: los límites de extracción vienen de la configuración (contexto propio). */
@TestPropertySource(properties = {"app.material.max-text-chars=2000", "app.material.max-pages=3"})
class MaterialProcessingLimitsTest extends MaterialTestBase {

    @Test
    void ac7_textAboveTheConfiguredMaximumEndsInError() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "enorme.txt", text(longText(100)));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(errorKindOf(id)).isEqualTo("TOO_MUCH_TEXT");
        assertThat(chunkCount(id)).isZero();
    }

    @Test
    void ac5_aPdfWithMorePagesThanTheMaximumEndsInError() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "largo.pdf", pdf("1", "2", "3", "4"));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(errorKindOf(id)).isEqualTo("TOO_MANY_PAGES");
    }

    @Test
    void ac7_documentsWithinTheLimitsAreIndexedNormally() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "ok.pdf", pdf("uno", "dos", "tres"));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("READY");
    }
}
