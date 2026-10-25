package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** AC-11 y AC-14: el borrado es real, completo y no deja nada a medias. */
class MaterialDeleteTest extends MaterialTestBase {

    @Test
    void ac11_deletingRemovesTheDocumentItsChunksAndItsEmbeddings() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploadedAndProcessed(ana, "a.txt", text(longText(30)));
        assertThat(chunkCount(id)).isGreaterThan(0);

        deleteAs(ana, id).andExpect(status().isNoContent());

        assertThat(documentRows()).isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM source_chunk", Integer.class)).isZero();
    }

    @Test
    void ac11_deletingOneDocumentLeavesTheOthersIntactEvenOtherUsers() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String mine = uploadedAndProcessed(ana, "a.txt", text(longText(20)));
        String keepAna = uploadedAndProcessed(ana, "b.txt", text("otro texto distinto de ana"));
        String keepLuis = uploadedAndProcessed(luis, "c.txt", text("texto de luis"));
        int keptChunks = chunkCount(keepAna) + chunkCount(keepLuis);

        deleteAs(ana, mine).andExpect(status().isNoContent());

        assertThat(owner.queryForObject("SELECT count(*) FROM source_chunk", Integer.class)).isEqualTo(keptChunks);
        assertThat(documentRows()).isEqualTo(2);
    }

    @Test
    void ac11_theSameContentCanBeUploadedAgainAfterDeleting() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "a.txt", text("mismo contenido"));
        deleteAs(ana, id).andExpect(status().isNoContent());

        upload(ana, "a.txt", text("mismo contenido")).andExpect(status().isAccepted());
    }

    @Test
    void ac11_deletingTwiceTheSecondTimeIs404() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "a.txt", text("hola"));

        deleteAs(ana, id).andExpect(status().isNoContent());
        deleteAs(ana, id).andExpect(status().isNotFound());
    }

    @Test
    void ac14_theDeletionIsAuditedWithTheIdAndNeverTheNameOrContent() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "confidencial.txt", text("texto muy privado"));

        deleteAs(ana, id).andExpect(status().isNoContent());

        assertThat(auditActions()).contains("DOCUMENT_DELETED");
        String details = owner.queryForObject("SELECT details::text FROM audit_event WHERE action = 'DOCUMENT_DELETED'", String.class);
        assertThat(details).contains(id).doesNotContain("confidencial").doesNotContain("muy privado");
    }

    @Test
    void ac11_deletingWhileTheDocumentIsBeingProcessedLeavesNoChunksBehind() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "largo.txt", text(longText(200)));
        fake.delay(Duration.ofMillis(400)); // el indexado queda esperando a Ollama el tiempo suficiente
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread background = new Thread(() -> {
            try {
                worker.processPending();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        background.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (fake.embedCalls().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertThat(fake.embedCalls()).isNotEmpty(); // está a mitad del procesado

        deleteAs(ana, id).andExpect(status().isNoContent());
        background.join(15000);

        assertThat(failure.get()).isNull();
        assertThat(documentRows()).isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM source_chunk", Integer.class)).isZero();
    }
}
