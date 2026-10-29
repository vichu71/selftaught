package es.vicai.studylab.material;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F19. Contrato (ART-F19): la tarea OCR usa el proveedor falso ({@code fake-vision}, 1 USD/M de entrada y 2 USD/M de salida) y responde con
 * un sobre JSON {@code {"appropriate": bool, "text": "..."}}; ganchos nuevos de {@code FakeProvider}: {@code visionRespondsWith(raw, in, out)},
 * {@code visionFailsTimes(n, kind)}, {@code visionBefore(Runnable)} y {@code visionCalls()} (cada {@code VisionCall} lleva model, instructions, data, image, mime).
 * Con 1.000 tokens de entrada y 100 de salida una lectura cuesta exactamente 0,001200 USD.
 */
public abstract class PhotoTestBase extends MaterialTestBase {

    protected static final String READ_TEXT = "Las fracciones se suman con común denominador.";

    protected static String envelope(boolean appropriate, String text) {
        return "{\"appropriate\": " + appropriate + ", \"text\": \"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    protected void modelReads(String text) {
        fake.visionRespondsWith(envelope(true, text), 1_000, 100);
    }

    protected void modelReadsRaw(String raw) {
        fake.visionRespondsWith(raw, 1_000, 100);
    }

    protected List<Map<String, Object>> ocrUsage() {
        return owner.queryForList("SELECT * FROM ai_usage WHERE task = 'OCR' ORDER BY id");
    }

    protected int imageActions(UUID user) {
        return owner.queryForObject("SELECT count(*) FROM material_action WHERE owner_id = ? AND kind = 'IMAGE_UPLOAD'", Integer.class, user);
    }

    protected boolean hasOriginal(String documentId) {
        return owner.queryForObject("SELECT content IS NOT NULL FROM source_document WHERE id = ?::uuid", Boolean.class, documentId);
    }

    /** Activa o desactiva las fotos de un menor directamente en la BD (el contrato HTTP del tutor se prueba aparte). */
    protected void photosFor(String minorId, boolean enabled) {
        owner.update("""
                INSERT INTO child_setting (child_id, photo_material, updated_by) VALUES (?::uuid, ?, (SELECT tutor_id FROM guardianship WHERE student_id = ?::uuid LIMIT 1))
                ON CONFLICT (child_id) DO UPDATE SET photo_material = EXCLUDED.photo_material""", minorId, enabled, minorId);
    }

    protected ResultActions putAs(String session, String path, Object body) throws Exception {
        return mvc.perform(put(path).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected static String photoPath(String studentId) {
        return "/api/tutor/students/" + studentId + "/photo-material";
    }

    protected List<String> audit(String action) {
        return owner.queryForList("SELECT details::text FROM audit_event WHERE action = ? ORDER BY id", String.class, action);
    }
}
