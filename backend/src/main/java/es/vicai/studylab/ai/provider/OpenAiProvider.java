package es.vicai.studylab.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import es.vicai.studylab.ai.FinishReason;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Chat Completions: POST /v1/chat/completions con "Authorization: Bearer". Instrucciones en el mensaje de sistema y datos
 * en el de usuario; límite con max_completion_tokens (válido también para los modelos de razonamiento). No se envía
 * "temperature". "completion_tokens" ya incluye los tokens de razonamiento. Una cuota agotada llega como 429 con código
 * insufficient_quota: error permanente. Formato verificado solo en parte (ver OpenAiProviderTest); lo confirma el benchmark.
 */
public class OpenAiProvider extends HttpProvider {

    private final String apiKey;

    public OpenAiProvider(RestClient.Builder builder, String baseUrl, String apiKey) {
        super(builder, baseUrl);
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public ChatResult chat(ChatCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", call.model());
        body.put("max_completion_tokens", call.maxOutputTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", call.instructions()),
                Map.of("role", "user", "content", call.dataMessage())));

        Raw raw = post("/v1/chat/completions", Map.of("Authorization", "Bearer " + apiKey), body);
        if (!raw.ok()) throw classify(raw);

        JsonNode root = parse(raw);
        JsonNode choice = root.path("choices").path(0);
        String text = choice.path("message").path("content").asText("");
        if (text.isBlank()) throw invalid();

        JsonNode usage = root.path("usage");
        boolean hasUsage = usage.has("prompt_tokens") && usage.has("completion_tokens");
        return new ChatResult(text, usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0), !hasUsage,
                finish(choice.path("finish_reason").asText("")));
    }

    private static FinishReason finish(String reason) {
        return switch (reason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }

    private ProviderException classify(Raw raw) {
        int status = raw.status();
        String code = errorMessage(raw, "error", "code") + " " + errorMessage(raw, "error", "type");
        boolean quota = containsAny(code, "insufficient_quota", "billing", "credit_balance", "usage_limit");
        if ((status == 429 || status == 400 || status == 402) && quota) return failure(ProviderErrorKind.QUOTA_EXCEEDED, status);
        if (status == 400 && code.contains("model_not_found")) return failure(ProviderErrorKind.MODEL_NOT_FOUND, status);
        return byStatus(status);
    }
}
