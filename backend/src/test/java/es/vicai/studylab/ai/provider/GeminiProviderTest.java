package es.vicai.studylab.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import es.vicai.studylab.ai.FinishReason;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Formato verificado en https://ai.google.dev/api/generate-content: POST /v1beta/models/{model}:generateContent,
 * contents[].parts[].text, systemInstruction, generationConfig.maxOutputTokens, candidates[].finishReason y
 * usageMetadata (promptTokenCount, candidatesTokenCount, thoughtsTokenCount). La doc de ejemplo pasa la clave como
 * parámetro de URL; aquí se envía por cabecera x-goog-api-key para que NUNCA aparezca en una URL (logs, excepciones):
 * esa cabecera y el formato exacto del cuerpo de error quedan por confirmar con el run real.
 */
class GeminiProviderTest {

    private static final String KEY = "g-test-GEMINI-no-es-secreto";
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/modelo-de-prueba:generateContent";
    private static final ChatCall CALL = new ChatCall("modelo-de-prueba", "INSTRUCCIONES", "<datos>\nDATOS\n</datos>", 200, Duration.ofSeconds(5));

    private MockRestServiceServer server;
    private GeminiProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GeminiProvider(builder, "https://generativelanguage.googleapis.com", KEY);
    }

    private static final String OK = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Hola "},{"text":"mundo"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":10,"thoughtsTokenCount":25,"totalTokenCount":47}}""";

    @Test
    void sendsTheKeyInAHeaderAndNeverInTheUrl() {
        server.expect(requestTo(URL))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", KEY))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("INSTRUCCIONES"))
                .andExpect(jsonPath("$.contents.length()").value(1))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("<datos>\nDATOS\n</datos>"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(200))
                .andExpect(jsonPath("$.generationConfig.temperature").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        provider.chat(CALL);

        server.verify(); // requestTo(URL) ya comprueba que no hay ?key= en la URL
    }

    @Test
    void thinkingTokensAreBilledAsOutputSoTheyAreAddedToTheCandidatesTokens() {
        server.expect(requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(CALL);

        assertThat(result.text()).isEqualTo("Hola mundo");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(35); // 10 de respuesta + 25 de razonamiento
        assertThat(result.finish()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void usageWithoutThinkingTokensStillWorks() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":3,\"candidatesTokenCount\":4}}",
                MediaType.APPLICATION_JSON));

        assertThat(provider.chat(CALL).outputTokens()).isEqualTo(4);
    }

    @Test
    void mapsTheFinishReasons() {
        for (String[] pair : new String[][] {{"STOP", "STOP"}, {"MAX_TOKENS", "LENGTH"}, {"SAFETY", "OTHER"}}) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withSuccess(
                    "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]},\"finishReason\":\"" + pair[0]
                            + "\"}],\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":1}}", MediaType.APPLICATION_JSON));

            assertThat(provider.chat(CALL).finish()).as(pair[0]).isEqualTo(FinishReason.valueOf(pair[1]));
        }
    }

    @Test
    void aBlockedPromptWithNoCandidatesIsInvalid() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},\"usageMetadata\":{\"promptTokenCount\":5}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.chat(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(ProviderErrorKind.INVALID_RESPONSE));
    }

    private ProviderException failWith(HttpStatus status, String statusText, String message) {
        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":" + status.value() + ",\"message\":\"" + message + "\",\"status\":\"" + statusText + "\"}}"));
        return org.junit.jupiter.api.Assertions.assertThrows(ProviderException.class, () -> provider.chat(CALL));
    }

    @Test
    void mapsTheHttpErrors() {
        assertThat(failWith(HttpStatus.TOO_MANY_REQUESTS, "RESOURCE_EXHAUSTED", "x").kind()).isEqualTo(ProviderErrorKind.RATE_LIMITED);
        assertThat(failWith(HttpStatus.TOO_MANY_REQUESTS, "RESOURCE_EXHAUSTED", "x").retryable()).isTrue();
        assertThat(failWith(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "x").kind()).isEqualTo(ProviderErrorKind.SERVER_ERROR);
        assertThat(failWith(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "x").kind()).isEqualTo(ProviderErrorKind.SERVER_ERROR);
        assertThat(failWith(HttpStatus.GATEWAY_TIMEOUT, "DEADLINE_EXCEEDED", "x").kind()).isEqualTo(ProviderErrorKind.TIMEOUT);
        assertThat(failWith(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "x").kind()).isEqualTo(ProviderErrorKind.AUTH);
        assertThat(failWith(HttpStatus.NOT_FOUND, "NOT_FOUND", "models/x is not found").kind()).isEqualTo(ProviderErrorKind.MODEL_NOT_FOUND);
        assertThat(failWith(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "bad syntax").kind()).isEqualTo(ProviderErrorKind.BAD_REQUEST);
    }

    @Test
    void aBadRequestSayingTheKeyIsNotValidIsAnAuthErrorAndDepletedCreditsAreQuota() {
        assertThat(failWith(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.").kind())
                .isEqualTo(ProviderErrorKind.AUTH);
        ProviderException credits = failWith(HttpStatus.BAD_REQUEST, "FAILED_PRECONDITION", "Your prepayment credits are depleted.");
        assertThat(credits.kind()).isEqualTo(ProviderErrorKind.QUOTA_EXCEEDED);
        assertThat(credits.retryable()).isFalse();
    }

    @Test
    void theProviderMessageAndTheKeyNeverReachTheException() {
        ProviderException e = failWith(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "eco del prompt: DATO-SECRETO");

        assertThat(e.getMessage() + e).doesNotContain("DATO-SECRETO").doesNotContain(KEY);
        assertThat(provider.toString()).doesNotContain(KEY);
    }

    @Test
    void geminiIsNotUsedForEmbeddings() {
        assertThatThrownBy(() -> provider.embed(new EmbedCall("m", java.util.List.of("x"), Duration.ofSeconds(1))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
