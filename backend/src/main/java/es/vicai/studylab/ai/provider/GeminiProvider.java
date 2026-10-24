package es.vicai.studylab.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import es.vicai.studylab.ai.FinishReason;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;

/**
 * Gemini generateContent (https://ai.google.dev/api/generate-content): POST /v1beta/models/{modelo}:generateContent con
 * systemInstruction, contents y generationConfig.maxOutputTokens. La clave va en la cabecera x-goog-api-key y NUNCA en la URL
 * (la documentación de ejemplo usa ?key=, pero eso la dejaría en logs y excepciones). Los tokens de razonamiento
 * ("thoughtsTokenCount") se facturan como salida y se suman. Cabecera y formato de error: por confirmar con el benchmark.
 */
public class GeminiProvider extends HttpProvider {

    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private final String apiKey;

    public GeminiProvider(RestClient.Builder builder, String baseUrl, String apiKey) {
        super(builder, baseUrl);
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public ChatResult chat(ChatCall call) {
        // El modelo forma parte de la ruta: se valida para que la configuración no pueda inyectar otra ruta
        if (!MODEL.matcher(call.model()).matches()) throw new ProviderException(ProviderErrorKind.BAD_REQUEST, "Modelo no válido");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", call.instructions()))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", call.dataMessage())))));
        body.put("generationConfig", Map.of("maxOutputTokens", call.maxOutputTokens()));

        Raw raw = post("/v1beta/models/{model}:generateContent", Map.of("x-goog-api-key", apiKey), body, call.model());
        if (!raw.ok()) throw classify(raw);

        JsonNode root = parse(raw);
        JsonNode candidate = root.path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) text.append(part.path("text").asText(""));
        if (text.isEmpty()) throw invalid(); // por ejemplo, una petición bloqueada por seguridad: sin candidatos

        JsonNode usage = root.path("usageMetadata");
        boolean hasUsage = usage.has("promptTokenCount") && usage.has("candidatesTokenCount");
        int output = usage.path("candidatesTokenCount").asInt(0) + usage.path("thoughtsTokenCount").asInt(0);
        return new ChatResult(text.toString(), usage.path("promptTokenCount").asInt(0), output, !hasUsage,
                finish(candidate.path("finishReason").asText("")));
    }

    private static FinishReason finish(String reason) {
        return switch (reason) {
            case "STOP" -> FinishReason.STOP;
            case "MAX_TOKENS" -> FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }

    private ProviderException classify(Raw raw) {
        int status = raw.status();
        String message = errorMessage(raw, "error", "message");
        if (status == 400 || status == 403) {
            if (containsAny(message, "api key not valid", "api_key_invalid")) return failure(ProviderErrorKind.AUTH, status);
            if (containsAny(message, "credit", "depleted", "billing", "prepay", "quota")) return failure(ProviderErrorKind.QUOTA_EXCEEDED, status);
        }
        return byStatus(status);
    }
}
