package es.vicai.studylab.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import es.vicai.studylab.ai.FinishReason;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Messages API (https://platform.claude.com/docs/en/api/messages): POST /v1/messages con x-api-key y anthropic-version.
 * Las instrucciones van en "system" y los datos en el único mensaje de usuario. No se envía "temperature" (los modelos nuevos
 * pueden rechazarla). Un tope de gasto llega como 400 o como 429 SIN retry-after: error permanente (ADR-23).
 */
public class AnthropicProvider extends HttpProvider {

    private final String apiKey;

    public AnthropicProvider(RestClient.Builder builder, String baseUrl, String apiKey) {
        super(builder, baseUrl);
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public ChatResult chat(ChatCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", call.model());
        body.put("max_tokens", call.maxOutputTokens());
        body.put("system", call.instructions());
        body.put("messages", List.of(Map.of("role", "user", "content", call.dataMessage())));

        Raw raw = post("/v1/messages", Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01"), body);
        if (!raw.ok()) throw classify(raw);

        JsonNode root = parse(raw);
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            // Los bloques "thinking" no son respuesta: solo se concatena el texto
            if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
        }
        if (text.isEmpty()) throw invalid();

        JsonNode usage = root.path("usage");
        boolean hasUsage = usage.has("input_tokens") && usage.has("output_tokens");
        return new ChatResult(text.toString(), usage.path("input_tokens").asInt(0), usage.path("output_tokens").asInt(0),
                !hasUsage, finish(root.path("stop_reason").asText("")));
    }

    private static FinishReason finish(String stopReason) {
        return switch (stopReason) {
            case "end_turn", "stop_sequence" -> FinishReason.STOP;
            case "max_tokens" -> FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }

    private ProviderException classify(Raw raw) {
        int status = raw.status();
        if (status == 402) return failure(ProviderErrorKind.QUOTA_EXCEEDED, status);
        if (status == 429) {
            // Un límite de ritmo normal trae retry-after; un tope de gasto de la cuenta, no (y no se arregla esperando)
            return raw.headers().containsKey("retry-after") ? failure(ProviderErrorKind.RATE_LIMITED, status)
                    : failure(ProviderErrorKind.QUOTA_EXCEEDED, status);
        }
        if (status == 400 && containsAny(errorMessage(raw, "error", "message"), "usage limit", "spend limit", "spending limit",
                "credit balance", "billing")) {
            return failure(ProviderErrorKind.QUOTA_EXCEEDED, status);
        }
        return byStatus(status);
    }
}
