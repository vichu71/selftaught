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

        return parseChat(post("/v1/chat/completions", Map.of("Authorization", "Bearer " + apiKey), body));
    }

    /**
     * Lectura de imágenes (F19): el mismo POST /v1/chat/completions, con el mensaje del usuario como lista de partes: el texto de los datos y la imagen como
     * data URI (image_url). La imagen cuenta como tokens de entrada y "usage" los informa. Formato según la documentación: lo confirma el benchmark.
     */
    @Override
    public ChatResult vision(VisionCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", call.model());
        body.put("max_completion_tokens", call.maxOutputTokens());
        String dataUri = "data:" + call.mime() + ";base64," + java.util.Base64.getEncoder().encodeToString(call.image());
        body.put("messages", List.of(
                Map.of("role", "system", "content", call.instructions()),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", call.data()),
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUri))))));
        return parseChat(post("/v1/chat/completions", Map.of("Authorization", "Bearer " + apiKey), body));
    }

    private ChatResult parseChat(Raw raw) {
        if (!raw.ok()) throw classify(raw);

        JsonNode root = parse(raw);
        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode()) throw invalid();
        String text = choice.path("message").path("content").asText(""); // vacío con length: ya cobrado, lo trata el gateway

        JsonNode usage = root.path("usage");
        boolean hasUsage = usage.has("prompt_tokens") && usage.has("completion_tokens");
        return new ChatResult(text, usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0), !hasUsage,
                finish(choice.path("finish_reason").asText("")));
    }

    /**
     * Imágenes (F12): POST /v1/images/generations con n=1, size, quality y output_format=png; los modelos gpt-image devuelven la imagen en base64 (data[0].b64_json).
     * Una negativa por moderación llega como 400 con código moderation_blocked o content_policy_violation. Formato según la documentación: lo confirma el benchmark.
     */
    @Override
    public ImageResult image(ImageCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", call.model());
        body.put("prompt", call.prompt());
        body.put("n", 1);
        body.put("size", call.size() + "x" + call.size());
        body.put("quality", call.quality());
        body.put("output_format", "png");

        Raw raw = post("/v1/images/generations", Map.of("Authorization", "Bearer " + apiKey), body);
        if (!raw.ok()) {
            String code = errorMessage(raw, "error", "code") + " " + errorMessage(raw, "error", "type");
            if (raw.status() == 400 && containsAny(code, "moderation_blocked", "content_policy_violation", "safety")) {
                throw failure(ProviderErrorKind.MODERATION, raw.status());
            }
            throw classify(raw);
        }
        String b64 = parse(raw).path("data").path(0).path("b64_json").asText("");
        if (b64.isBlank()) throw invalid();
        try {
            return new ImageResult(java.util.Base64.getMimeDecoder().decode(b64), "image/png");
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
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
