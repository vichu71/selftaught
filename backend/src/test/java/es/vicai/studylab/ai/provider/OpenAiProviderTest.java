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
 * Formato PARCIALMENTE verificado (la documentación oficial de OpenAI bloqueó el acceso): campos de uso
 * (prompt_tokens, completion_tokens) y roles confirmados en su OpenAPI; la forma del error y el código
 * insufficient_quota en fuentes secundarias; max_completion_tokens, la cabecera Bearer y finish_reason por
 * conocimiento previo. Queda por confirmar con el run real del benchmark.
 */
class OpenAiProviderTest {

    private static final String KEY = "sk-test-OPENAI-no-es-secreto";
    private static final String URL = "https://api.openai.com/v1/chat/completions";
    private static final ChatCall CALL = new ChatCall("modelo-de-prueba", "INSTRUCCIONES", "<datos>\nDATOS\n</datos>", 200, Duration.ofSeconds(5));

    private MockRestServiceServer server;
    private OpenAiProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiProvider(builder, "https://api.openai.com", KEY);
    }

    private static final String OK = """
            {"id":"c1","choices":[{"index":0,"message":{"role":"assistant","content":"Hola mundo"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":12,"completion_tokens":30,
                      "completion_tokens_details":{"reasoning_tokens":22}}}""";

    @Test
    void sendsBearerAuthAndKeepsInstructionsApartFromData() {
        server.expect(requestTo(URL))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.model").value("modelo-de-prueba"))
                .andExpect(jsonPath("$.max_completion_tokens").value(200))
                .andExpect(jsonPath("$.max_tokens").doesNotExist())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("INSTRUCCIONES"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("<datos>\nDATOS\n</datos>"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        provider.chat(CALL);

        server.verify();
    }

    @Test
    void readsTextAndUsageWhereCompletionTokensAlreadyIncludeReasoning() {
        server.expect(requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(CALL);

        assertThat(result.text()).isEqualTo("Hola mundo");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(30); // el razonamiento ya va incluido, no se suma otra vez
        assertThat(result.finish()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void mapsTheFinishReasons() {
        for (String[] pair : new String[][] {{"stop", "STOP"}, {"length", "LENGTH"}, {"content_filter", "OTHER"}}) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withSuccess(
                    "{\"choices\":[{\"message\":{\"content\":\"x\"},\"finish_reason\":\"" + pair[0]
                            + "\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}", MediaType.APPLICATION_JSON));

            assertThat(provider.chat(CALL).finish()).as(pair[0]).isEqualTo(FinishReason.valueOf(pair[1]));
        }
    }

    @Test
    void anEmptyAnswerIsInvalid() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":null},\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.chat(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(ProviderErrorKind.INVALID_RESPONSE));
    }

    private ProviderException failWith(HttpStatus status, String code) {
        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"eco: DATO-SECRETO\",\"type\":\"" + code + "\",\"param\":null,\"code\":\"" + code + "\"}}"));
        return org.junit.jupiter.api.Assertions.assertThrows(ProviderException.class, () -> provider.chat(CALL));
    }

    @Test
    void insufficientQuotaIsAPermanentErrorAndAPlainRateLimitIsTransient() {
        ProviderException quota = failWith(HttpStatus.TOO_MANY_REQUESTS, "insufficient_quota");
        ProviderException rate = failWith(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded");

        assertThat(quota.kind()).isEqualTo(ProviderErrorKind.QUOTA_EXCEEDED);
        assertThat(quota.retryable()).isFalse();
        assertThat(rate.kind()).isEqualTo(ProviderErrorKind.RATE_LIMITED);
        assertThat(rate.retryable()).isTrue();
    }

    @Test
    void mapsTheOtherHttpErrors() {
        assertThat(failWith(HttpStatus.UNAUTHORIZED, "invalid_api_key").kind()).isEqualTo(ProviderErrorKind.AUTH);
        assertThat(failWith(HttpStatus.BAD_REQUEST, "invalid_request_error").kind()).isEqualTo(ProviderErrorKind.BAD_REQUEST);
        assertThat(failWith(HttpStatus.NOT_FOUND, "model_not_found").kind()).isEqualTo(ProviderErrorKind.MODEL_NOT_FOUND);
        assertThat(failWith(HttpStatus.INTERNAL_SERVER_ERROR, "server_error").kind()).isEqualTo(ProviderErrorKind.SERVER_ERROR);
        assertThat(failWith(HttpStatus.SERVICE_UNAVAILABLE, "server_error").kind()).isEqualTo(ProviderErrorKind.SERVER_ERROR);
    }

    @Test
    void theProviderMessageAndTheKeyNeverReachTheException() {
        ProviderException e = failWith(HttpStatus.BAD_REQUEST, "invalid_request_error");

        assertThat(e.getMessage() + e).doesNotContain("DATO-SECRETO").doesNotContain(KEY);
        assertThat(provider.toString()).doesNotContain(KEY);
    }

    @Test
    void openAiIsNotUsedForEmbeddings() {
        assertThatThrownBy(() -> provider.embed(new EmbedCall("m", java.util.List.of("x"), Duration.ofSeconds(1))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
