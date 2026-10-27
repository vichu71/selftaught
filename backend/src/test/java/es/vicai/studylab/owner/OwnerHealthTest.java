package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-3, AC-15 (D11): {@code GET /api/owner/health}, con lo que ya existe y sin instrumentar nada nuevo: colas, piezas y documentos fallidos, ilustraciones, errores de IA de las últimas
 * 24 horas por tipo y proveedor, bloqueos de contenido, tamaño de la BD y filas de las tablas grandes.
 */
class OwnerHealthTest extends OwnerTestBase {

    @Test
    void d11_theQueuesAndTheFailuresAreCounted() throws Exception {
        Family f = quietFamily();
        UUID queued = insertCourse(f.anaId(), "En cola", 1, false, List.of());
        UUID generating = insertCourse(f.anaId(), "Generando", 1, false, List.of());
        setStatus(queued, "PENDING_GENERATION");
        setStatus(generating, "GENERATING");
        owner.update("UPDATE course_piece SET status = 'FAILED' WHERE id = ?", firstPiece(f.course(), "LESSON"));
        owner.update("UPDATE course_piece SET status = 'PENDING' WHERE id = ?", firstPiece(f.course(), "EXERCISES"));
        uploaded(f.ana(), "a.txt", longText(3).getBytes());
        uploaded(f.ana(), "b.txt", longText(3).getBytes());
        List<UUID> docs = owner.queryForList("SELECT id FROM source_document ORDER BY id", UUID.class);
        owner.update("UPDATE source_document SET status = 'ERROR' WHERE id = ?", docs.get(0));
        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status) VALUES (gen_random_uuid(), ?, ?, ?, 'PENDING')", f.course(), f.hugoId(), topicIds(f.course()).get(0));
        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status) VALUES (gen_random_uuid(), ?, ?, ?, 'FAILED')", f.course(), f.hugoId(), topicIds(f.course()).get(1));

        ownerGet(f.ana(), "/api/owner/health").andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.pendingGeneration").value(1))
                .andExpect(jsonPath("$.courses.generating").value(1))
                .andExpect(jsonPath("$.pieces.failed").value(1))
                .andExpect(jsonPath("$.pieces.pending").value(1))
                .andExpect(jsonPath("$.documents.failed").value(1))
                .andExpect(jsonPath("$.documents.pending").value(1))
                .andExpect(jsonPath("$.illustrations.pending").value(1))
                .andExpect(jsonPath("$.illustrations.failed").value(1));
    }

    @Test
    void d11_aiErrorsOfTheLastDayAreGroupedByProviderAndKind() throws Exception {
        Family f = quietFamily();
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "ERROR", "0.00", 0, 0, 10, "1 hour");
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "ERROR", "0.00", 0, 0, 10, "2 hours");
        aiCall(f.anaId(), "gemini", "g1", "LESSON", "ERROR", "0.00", 0, 0, 10, "3 hours");
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "ERROR", "0.00", 0, 0, 10, "3 days"); // fuera de las 24 h
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "0.10", 1, 1, 10, "1 hour");

        ownerGet(f.ana(), "/api/owner/health").andExpect(jsonPath("$.aiErrors", hasSize(2)))
                .andExpect(jsonPath("$.aiErrors[0].provider").value("anthropic")).andExpect(jsonPath("$.aiErrors[0].errorKind").value("PROVIDER_ERROR"))
                .andExpect(jsonPath("$.aiErrors[0].count").value(2)).andExpect(jsonPath("$.aiErrors[1].provider").value("gemini")).andExpect(jsonPath("$.aiErrors[1].count").value(1));
    }

    @Test
    void d11_contentBlocksOfTheLastDayAreCountedFromTheAudit() throws Exception {
        Family f = quietFamily();
        auditAt("COURSE_TOPIC_BLOCKED", f.hugoId(), "1 hour");
        auditAt("TUTOR_QUESTION_BLOCKED", f.hugoId(), "2 hours");
        auditAt("TUTOR_ANSWER_FILTERED", f.hugoId(), "3 hours");
        auditAt("COURSE_TOPIC_BLOCKED", f.hugoId(), "3 days"); // fuera

        ownerGet(f.ana(), "/api/owner/health").andExpect(jsonPath("$.contentBlocks24h").value(3));
    }

    @Test
    void d11_itReportsTheDatabaseSizeAndTheRowsOfTheBigTables() throws Exception {
        Family f = quietFamily();
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "0.10", 1, 1, 10, "1 hour");

        ownerGet(f.ana(), "/api/owner/health").andExpect(jsonPath("$.databaseBytes").value(greaterThan(0)))
                .andExpect(jsonPath("$.tables[*].name").value(hasItem("ai_usage"))).andExpect(jsonPath("$.tables[*].name").value(hasItem("audit_event")))
                .andExpect(jsonPath("$.tables[*].name").value(hasItem("source_chunk")));
        String body = ownerGet(f.ana(), "/api/owner/health").andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("tables")).allSatisfy(t -> assertThat(t.get("rows").asLong()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void d11_anEmptyInstallationHasNothingPendingOrFailed() throws Exception {
        String ana = logged("ana@example.com");

        ownerGet(ana, "/api/owner/health").andExpect(status().isOk()).andExpect(jsonPath("$.courses.pendingGeneration").value(0)).andExpect(jsonPath("$.pieces.failed").value(0))
                .andExpect(jsonPath("$.documents.failed").value(0)).andExpect(jsonPath("$.aiErrors", hasSize(0))).andExpect(jsonPath("$.contentBlocks24h").value(0));
    }

    @Test
    void d11_theHealthReportListsNoTextOfAnyErrorOrProvider() throws Exception {
        Family f = quietFamily();
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status, error_kind)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'LESSON', 'anthropic', 'm1', 0, 'ERROR', 'PROVIDER_ERROR')""", f.anaId());

        String body = ownerGet(f.ana(), "/api/owner/health").andReturn().getResponse().getContentAsString();

        // solo tipos de error de la enumeración: ningún mensaje del proveedor ni texto libre
        assertThat(body).doesNotContainIgnoringCase("message").doesNotContainIgnoringCase("exception").doesNotContainIgnoringCase("stack");
    }
}
