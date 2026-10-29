package es.vicai.studylab.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * F19 AC-4 y AC-5 (ADR-100): la operación de visión de los adaptadores. El formato es el de la documentación de cada proveedor TAL COMO SE CONOCE hoy y queda
 * <b>pendiente de confirmar con el run real del benchmark</b> (regla de F02): estos tests fijan la forma que se implementará y se corrigen si el run la contradice.
 * La clave va en una cabecera, nunca en la URL; la imagen va en el mensaje del usuario y las instrucciones aparte de los datos.
 */
class VisionProvidersTest {

    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4};
    private static final String IMAGE_B64 = Base64.getEncoder().encodeToString(IMAGE);
    private static final VisionCall CALL = new VisionCall("modelo-de-prueba", "INSTRUCCIONES", "<datos>\naudiencia: adulto\n</datos>", IMAGE, "image/jpeg", 2000,
            Duration.ofSeconds(5));

    @Test
    void theVisionCallNeverPrintsTheBytesTheInstructionsNorTheData() {
        assertThat(CALL.toString()).doesNotContain("INSTRUCCIONES").doesNotContain("audiencia").doesNotContain("[B@").contains("modelo-de-prueba");
    }

    // ---------- OpenAI ----------

    @Test
    void openAiSendsTheImageAsADataUriInTheUserMessageWithTheKeyInTheHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProvider provider = new OpenAiProvider(builder, "https://api.openai.com", "sk-test-no-es-secreto");
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer sk-test-no-es-secreto"))
                .andExpect(jsonPath("$.model").value("modelo-de-prueba"))
                .andExpect(jsonPath("$.max_completion_tokens").value(2000))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("INSTRUCCIONES"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[1].content[0].text").value("<datos>\naudiencia: adulto\n</datos>"))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").value("data:image/jpeg;base64," + IMAGE_B64))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"appropriate\\":true,\\"text\\":\\"hola\\"}"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":850,"completion_tokens":40}}""", MediaType.APPLICATION_JSON));

        ChatResult result = provider.vision(CALL);

        server.verify();
        assertThat(result.text()).isEqualTo("{\"appropriate\":true,\"text\":\"hola\"}");
        assertThat(result.inputTokens()).isEqualTo(850); // la imagen cuenta como tokens de entrada: es lo que el proveedor informa
        assertThat(result.outputTokens()).isEqualTo(40);
    }

    @Test
    void openAiKeepsTheUsageOfAnEmptyAnswerCutByTheLimitBecauseItIsBilled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProvider provider = new OpenAiProvider(builder, "https://api.openai.com", "sk-test");
        server.expect(requestTo("https://api.openai.com/v1/chat/completions")).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":null},\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":900,\"completion_tokens\":2000}}",
                MediaType.APPLICATION_JSON));

        ChatResult result = provider.vision(CALL);

        assertThat(result.text()).isEmpty();
        assertThat(result.outputTokens()).isEqualTo(2000);
    }

    // ---------- Google ----------

    @Test
    void geminiSendsTheImageAsInlineDataWithTheKeyInAHeaderAndNeverInTheUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiProvider provider = new GeminiProvider(builder, "https://generativelanguage.googleapis.com", "AIza-test-no-es-secreto");
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/modelo-de-prueba:generateContent"))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", "AIza-test-no-es-secreto"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("INSTRUCCIONES"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("<datos>\naudiencia: adulto\n</datos>"))
                .andExpect(jsonPath("$.contents[0].parts[1].inline_data.mime_type").value("image/jpeg"))
                .andExpect(jsonPath("$.contents[0].parts[1].inline_data.data").value(IMAGE_B64))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(2000))
                .andExpect(jsonPath("$.generationConfig.temperature").doesNotExist())
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"appropriate\\":true,\\"text\\":\\"hola\\"}"}]},"finishReason":"STOP"}],
                         "usageMetadata":{"promptTokenCount":700,"candidatesTokenCount":30,"thoughtsTokenCount":0}}""", MediaType.APPLICATION_JSON));

        ChatResult result = provider.vision(CALL);

        server.verify();
        assertThat(result.text()).isEqualTo("{\"appropriate\":true,\"text\":\"hola\"}");
        assertThat(result.inputTokens()).isEqualTo(700);
        assertThat(result.outputTokens()).isEqualTo(30);
    }

    // ---------- los que no leen imágenes ----------

    @Test
    void anthropicAndOllamaDoNotImplementVisionBecauseTheConfigurationPreventsAskingThem() {
        RestClient.Builder builder = RestClient.builder();
        assertThatThrownBy(() -> new AnthropicProvider(builder, "https://api.anthropic.com", "k").vision(CALL)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new OllamaProvider(builder, "http://localhost:11434").vision(CALL)).isInstanceOf(UnsupportedOperationException.class);
    }
}
