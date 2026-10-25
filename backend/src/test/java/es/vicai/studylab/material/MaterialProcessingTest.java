package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.ai.provider.EmbedCall;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AC-6 a AC-9, AC-14: del documento subido a los fragmentos con su embedding, y los fallos posibles. */
class MaterialProcessingTest extends MaterialTestBase {

    @Test
    void ac8_aTextDocumentEndsReadyWithItsChunksAndEmbeddings() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "tema.txt", text(longText(40)));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("READY");
        assertThat(chunkCount(id)).isGreaterThan(1);
        Map<String, Object> doc = documentRow(id);
        assertThat(((Number) doc.get("chunk_count")).intValue()).isEqualTo(chunkCount(id));
        assertThat(chunks(id)).allSatisfy(c -> {
            assertThat(c.get("owner_id")).isEqualTo(doc.get("owner_id"));
            assertThat(c.get("embedding_model")).isEqualTo("fake-embed");
            assertThat(c.get("embedding")).isNotNull();
            assertThat((String) c.get("text")).isNotBlank();
        });
    }

    @Test
    void ac9_theOriginalIsDroppedOnceProcessed() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "tema.txt", text("contenido pequeño"));
        assertThat(documentRow(id).get("content")).isNotNull(); // mientras espera, sí está

        worker.processPending();

        assertThat(documentRow(id).get("content")).isNull();
    }

    @Test
    void ac6_aPdfIsExtractedAndEachChunkKeepsItsPage() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "libro.pdf", pdf("Contenido de la página uno", "Contenido de la página dos"));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("READY");
        assertThat(documentRow(id).get("page_count")).isEqualTo(2);
        assertThat(chunks(id)).allSatisfy(c -> assertThat(c.get("page")).isNotNull());
    }

    @Test
    void ac6_aDocxIsExtracted() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "resumen.docx", docx("Primer párrafo del resumen", "Segundo párrafo del resumen"));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("READY");
        assertThat((String) chunks(id).get(0).get("text")).contains("Primer párrafo");
    }

    @Test
    void ac8_embeddingsAreRequestedInBatchesAndEveryChunkGetsOne() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "largo.txt", text(longText(400)));

        worker.processPending();

        List<EmbedCall> calls = fake.embedCalls();
        assertThat(calls.size()).isGreaterThan(1);
        assertThat(calls).allSatisfy(c -> assertThat(c.inputs().size()).isLessThanOrEqualTo(16));
        assertThat(calls.stream().mapToInt(c -> c.inputs().size()).sum()).isEqualTo(chunkCount(id));
    }

    @Test
    void ac8_everyEmbeddingCallIsRecordedForTheOwnerWithZeroCostAndNoContent() throws Exception {
        String ana = logged("ana@example.com");
        uploaded(ana, "tema.txt", text(longText(30)));

        worker.processPending();

        List<Map<String, Object>> rows = owner.queryForList("SELECT * FROM ai_usage WHERE task = 'EMBEDDING'");
        assertThat(rows).isNotEmpty().allSatisfy(r -> {
            assertThat(r.get("user_id")).isEqualTo(idOf("ana@example.com"));
            assertThat(((java.math.BigDecimal) r.get("cost_usd")).signum()).isZero();
            assertThat(r.get("status")).isEqualTo("OK");
        });
    }

    @Test
    void ac14_readyAndFailedAreAuditedWithoutContent() throws Exception {
        String ana = logged("ana@example.com");
        uploaded(ana, "bien.txt", text("contenido útil"));
        uploaded(ana, "mal.pdf", blankPdf());

        worker.processPending();

        assertThat(auditActions()).contains("DOCUMENT_READY", "DOCUMENT_FAILED");
        String details = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action LIKE 'DOCUMENT_%'", String.class);
        assertThat(details).doesNotContain("bien.txt").doesNotContain("mal.pdf").doesNotContain("contenido útil");
    }

    // ---------- errores del documento ----------

    @Test
    void ac6_aScannedPdfEndsInErrorWithAClearReasonAndNoChunks() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "escaneado.pdf", blankPdf());

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(errorKindOf(id)).isEqualTo("NO_TEXT");
        assertThat(chunkCount(id)).isZero();
        assertThat(documentRow(id).get("content")).isNull();
    }

    @Test
    void ac6_anEncryptedPdfEndsInError() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "protegido.pdf", encryptedPdf("secreto"));

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(errorKindOf(id)).isEqualTo("ENCRYPTED");
    }

    @Test
    void ac6_aCorruptPdfAndAZipBombEndInErrorWithoutBreakingTheWorker() throws Exception {
        String ana = logged("ana@example.com");
        String corrupt = uploaded(ana, "roto.pdf", text("%PDF-1.7 no es un pdf"));
        String bomb = uploaded(ana, "bomba.docx", zipBombDocx());
        String fine = uploaded(ana, "bien.txt", text("este sí funciona"));

        worker.processPending();

        assertThat(errorKindOf(corrupt)).isEqualTo("CORRUPT");
        assertThat(errorKindOf(bomb)).isEqualTo("TOO_LARGE");
        assertThat(statusOf(fine)).isEqualTo("READY"); // un documento malo no frena a los demás
    }

    @Test
    void ac9_ifTheEmbeddingServiceIsDownTheDocumentEndsInErrorWithNoPartialChunks() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "tema.txt", text(longText(400)));
        fake.failNext(ProviderErrorKind.UNAVAILABLE, 99);

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(errorKindOf(id)).isEqualTo("INDEXING_UNAVAILABLE");
        assertThat(chunkCount(id)).isZero();
    }

    @Test
    void ac8_vectorsOfTheWrongDimensionEndInErrorWithNoChunks() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "largo.txt", text(longText(400))); // varios lotes
        fake.embeddingDimensions(3); // el servicio devuelve vectores de otra dimensión: respuesta inválida

        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(chunkCount(id)).isZero();
    }

    @Test
    void ac9_theErrorMessageNeverReachesTheUserFromTheLibraryOrTheProvider() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "tema.txt", text("contenido"));
        fake.failNextWithMessage(ProviderErrorKind.UNAVAILABLE, 99, "SECRETO-DEL-PROVEEDOR");

        worker.processPending();

        getAs(ana, "/api/material/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errorKind").value("INDEXING_UNAVAILABLE"));
        assertThat(documentRow(id).toString()).doesNotContain("SECRETO-DEL-PROVEEDOR");
    }

    // ---------- reinicios y colas ----------

    @Test
    void ac9_processingTwiceNeverDuplicatesChunks() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploadedAndProcessed(ana, "tema.txt", text(longText(30)));
        int before = chunkCount(id);

        assertThat(worker.processPending()).isZero();

        assertThat(chunkCount(id)).isEqualTo(before);
    }

    @Test
    void ac9_aDocumentLeftInProcessingByACrashIsPickedUpAgain() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "tema.txt", text("contenido que sobrevive"));
        owner.update("UPDATE source_document SET status = 'PROCESSING', processing_started_at = now() WHERE id = ?::uuid", id);

        worker.recoverInterrupted();
        worker.processPending();

        assertThat(statusOf(id)).isEqualTo("READY");
    }

    @Test
    void ac9_aDocumentThatKeepsKillingTheWorkerEndsInErrorAfterThreeAttempts() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "tema.txt", text("contenido venenoso"));
        owner.update("UPDATE source_document SET status = 'PROCESSING', attempts = 3 WHERE id = ?::uuid", id);

        worker.recoverInterrupted();

        assertThat(statusOf(id)).isEqualTo("ERROR");
        assertThat(documentRow(id).get("content")).isNull();
    }

    @Test
    void edge_aDeactivatedOwnersDocumentsAreNotProcessedUntilTheyAreBack() throws Exception {
        String ana = logged("ana@example.com");
        String studentId = createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        String id = uploaded(hugo, "suyo.txt", text("material del menor"));
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?::uuid", studentId);

        assertThat(worker.processPending()).isZero();
        assertThat(statusOf(id)).isEqualTo("UPLOADED");

        owner.update("UPDATE app_user SET status = 'ACTIVE' WHERE id = ?::uuid", studentId);
        worker.processPending();
        assertThat(statusOf(id)).isEqualTo("READY");
    }

    @Test
    void ac9_documentsAreProcessedInUploadOrder() throws Exception {
        String ana = logged("ana@example.com");
        String first = uploaded(ana, "uno.txt", text("primero"));
        String second = uploaded(ana, "dos.txt", text("segundo"));

        worker.processPending();

        assertThat(fake.embedCalls().get(0).inputs().get(0)).contains("primero");
        assertThat(statusOf(first)).isEqualTo("READY");
        assertThat(statusOf(second)).isEqualTo("READY");
    }

    @Test
    void ac9_aReadyDocumentShowsItsStateAndChunkCountThroughTheApi() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploadedAndProcessed(ana, "tema.txt", text(longText(30)));

        getAs(ana, "/api/material/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(chunkCount(id)));
    }
}
