package es.vicai.studylab.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Ollama local (documentación: docs/api.md): POST /api/embed con {model, input} y GET /api/tags para listar modelos. Sin
 * autenticación: solo se alcanza por la red interna de Docker (ADR-25). En esta versión solo hace embeddings.
 */
public class OllamaProvider extends HttpProvider {

    public OllamaProvider(RestClient.Builder builder, String baseUrl) {
        super(builder, baseUrl);
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public EmbedResult embed(EmbedCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", call.model());
        body.put("input", call.inputs());

        Raw raw = post("/api/embed", Map.of(), body);
        if (!raw.ok()) throw byStatus(raw.status()); // 404 = el modelo no está descargado (MODEL_NOT_FOUND)

        JsonNode root = parse(raw);
        JsonNode embeddings = root.path("embeddings");
        if (!embeddings.isArray() || embeddings.size() != call.inputs().size()) throw invalid();

        List<float[]> vectors = new ArrayList<>();
        for (JsonNode vector : embeddings) {
            if (!vector.isArray() || vector.isEmpty()) throw invalid();
            float[] values = new float[vector.size()];
            for (int i = 0; i < values.length; i++) values[i] = (float) vector.get(i).asDouble();
            vectors.add(values);
        }
        return new EmbedResult(vectors, root.path("prompt_eval_count").asInt(0));
    }

    /** ¿Está descargado el modelo? Para comprobar la salud (false ante cualquier fallo). */
    public boolean hasModel(String model) {
        try {
            Raw raw = get("/api/tags");
            if (!raw.ok()) return false;
            for (JsonNode entry : parse(raw).path("models")) {
                String name = entry.path("name").asText("");
                if (name.equals(model) || name.startsWith(model + ":")) return true;
            }
            return false;
        } catch (ProviderException e) {
            return false;
        }
    }
}
