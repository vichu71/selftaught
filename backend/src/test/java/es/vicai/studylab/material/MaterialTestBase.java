package es.vicai.studylab.material;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import es.vicai.studylab.ai.AiTestBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F03. Contrato (ART-003): POST /api/material (multipart "file"), GET /api/material, GET y DELETE
 * /api/material/{id}; {@code MaterialWorker.processPending()} procesa de forma síncrona los documentos pendientes (en los tests el
 * hilo de fondo está apagado: app.material.worker.enabled=false) y {@code MaterialSearch} es el servicio interno de recuperación.
 * Los embeddings son los del proveedor falso (dimensión 8): ningún test llama a Ollama ni a una API de pago.
 */
public abstract class MaterialTestBase extends AiTestBase {

    @Autowired protected MaterialWorker worker;
    @Autowired protected MaterialSearch search;

    // ---------- peticiones ----------

    protected ResultActions upload(String session, String filename, byte[] content) throws Exception {
        return upload(session, filename, content, "application/octet-stream");
    }

    protected ResultActions upload(String session, String filename, byte[] content, String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        return mvc.perform(multipart("/api/material").file(file).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions deleteAs(String session, String id) throws Exception {
        return mvc.perform(delete("/api/material/" + id).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    /** Sube esperando 202 y devuelve el id del documento. */
    protected String uploaded(String session, String filename, byte[] content) throws Exception {
        String body = upload(session, filename, content).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Sube y procesa hasta dejar el documento terminado (READY o ERROR). */
    protected String uploadedAndProcessed(String session, String filename, byte[] content) throws Exception {
        String id = uploaded(session, filename, content);
        worker.processPending();
        return id;
    }

    // ---------- usuarios y estado ----------

    protected String logged(String email) throws Exception {
        return adultSession(email);
    }

    protected UUID idOf(String email) {
        return userIdByEmail(email);
    }

    protected String statusOf(String documentId) {
        return owner.queryForObject("SELECT status FROM source_document WHERE id = ?::uuid", String.class, documentId);
    }

    protected String errorKindOf(String documentId) {
        return owner.queryForObject("SELECT error_kind FROM source_document WHERE id = ?::uuid", String.class, documentId);
    }

    protected int chunkCount(String documentId) {
        return owner.queryForObject("SELECT count(*) FROM source_chunk WHERE document_id = ?::uuid", Integer.class, documentId);
    }

    protected List<Map<String, Object>> chunks(String documentId) {
        return owner.queryForList("SELECT * FROM source_chunk WHERE document_id = ?::uuid ORDER BY ordinal", documentId);
    }

    protected Map<String, Object> documentRow(String documentId) {
        return owner.queryForMap("SELECT * FROM source_document WHERE id = ?::uuid", documentId);
    }

    protected int documentRows() {
        return owner.queryForObject("SELECT count(*) FROM source_document", Integer.class);
    }

    protected List<String> embeddingUsageKinds() {
        return owner.queryForList("SELECT status FROM ai_usage WHERE task = 'EMBEDDING' ORDER BY id", String.class);
    }

    /** Un texto con muchas frases distintas, para generar varios fragmentos. */
    protected static String longText(int sentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences; i++) {
            sb.append("La frase número ").append(i).append(" explica un concepto distinto del tema número ").append(i * 7).append(". ");
            if (i % 8 == 7) sb.append("\n\n");
        }
        return sb.toString().trim();
    }
}
