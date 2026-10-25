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
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Formato verificado en https://platform.claude.com/docs/en/api/messages y /api/errors: POST /v1/messages,
 * cabeceras x-api-key y anthropic-version, system aparte de messages, usage.input_tokens/output_tokens,
 * errores {"type":"error","error":{"type","message"}}. Un tope de gasto llega como 400 o como 429 SIN retry-after.
 * Sin ninguna llamada de red: MockRestServiceServer.
 */
class AnthropicProviderTest {

    private static final String KEY = "k-test-ANTHROPIC-no-es-secreto";
    private static final ChatCall CALL = new ChatCall("modelo-de-prueba", "INSTRUCCIONES", "<datos>\nDATOS\n</datos>", 200, Duration.ofSeconds(5));

    private MockRestServiceServer server;
    private AnthropicProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new AnthropicProvider(builder, "https://api.anthropic.com", KEY);
    }

    private static final String OK = """
            {"id":"msg_1","type":"message","role":"assistant","model":"modelo-de-prueba",
             "content":[{"type":"text","text":"Hola "},{"type":"text","text":"mundo"}],
             "stop_reason":"end_turn","usage":{"input_tokens":12,"output_tokens":8}}""";

    @Test
    void sendsTheKeyInAHeaderNeverInTheUrlAndKeepsInstructionsApartFromData() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(POST))
                .andExpect(header("x-api-key", KEY))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("modelo-de-prueba"))
                .andExpect(jsonPath("$.max_tokens").value(200))
                .andExpect(jsonPath("$.system").value("INSTRUCCIONES"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("<datos>\nDATOS\n</datos>"))
                .andExpect(jsonPath("$.temperature").doesNotExist()) // los modelos nuevos pueden rechazarlo
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        provider.chat(CALL);

        server.verify();
    }

    @Test
    void joinsTheTextBlocksIgnoringThinkingBlocksAndReadsTheUsage() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess("""
                {"content":[{"type":"thinking","thinking":"pienso...","signature":"x"},
                            {"type":"text","text":"Respuesta"}],
                 "stop_reason":"end_turn","usage":{"input_tokens":30,"output_tokens":55}}""", MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(CALL);

        assertThat(result.text()).isEqualTo("Respuesta");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(55);
        assertThat(result.tokensEstimated()).isFalse();
        assertThat(result.finish()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void mapsTheStopReasons() {
        for (String[] pair : new String[][] {{"end_turn", "STOP"}, {"stop_sequence", "STOP"}, {"max_tokens", "LENGTH"}, {"refusal", "OTHER"}}) {
            server.reset();
            server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess(
                    "{\"content\":[{\"type\":\"text\",\"text\":\"x\"}],\"stop_reason\":\"" + pair[0]
                            + "\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}", MediaType.APPLICATION_JSON));

            assertThat(provider.chat(CALL).finish()).as(pair[0]).isEqualTo(FinishReason.valueOf(pair[1]));
        }
    }

    @Test
    void aResponseWithOnlyThinkingIsReturnedEmptyKeepingTheUsageBecauseItIsBilled() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess(
                "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"solo pienso\"}],\"stop_reason\":\"max_tokens\",\"usage\":{\"input_tokens\":30,\"output_tokens\":4000}}",
                MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(CALL);

        assertThat(result.text()).isEmpty();
        assertThat(result.finish()).isEqualTo(FinishReason.LENGTH);
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(4000); // el proveedor ya cobró el razonamiento
    }

    @Test
    void aBodyWithoutAContentArrayIsInvalid() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.chat(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(ProviderErrorKind.INVALID_RESPONSE));
    }

    @Test
    void aBodyThatIsNotJsonIsInvalid() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess("<html>nope</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> provider.chat(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(ProviderErrorKind.INVALID_RESPONSE));
    }

    // ---------- errores (https://platform.claude.com/docs/en/api/errors) ----------

    private ProviderException failWith(HttpStatusCode status, String body, String retryAfter) {
        server.reset();
        var response = withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body);
        if (retryAfter != null) response = response.header("retry-after", retryAfter);
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(response);
        return org.junit.jupiter.api.Assertions.assertThrows(ProviderException.class, () -> provider.chat(CALL));
    }

    private static String error(String type, String message) {
        return "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"},\"request_id\":\"req_1\"}";
    }

    @Test
    void mapsTheHttpErrorsToProviderErrorKinds() {
        assertThat(failWith(HttpStatus.UNAUTHORIZED, error("authentication_error", "x"), null).kind()).isEqualTo(ProviderErrorKind.AUTH);
        assertThat(failWith(HttpStatus.FORBIDDEN, error("permission_error", "x"), null).kind()).isEqualTo(ProviderErrorKind.AUTH);
        assertThat(failWith(HttpStatus.BAD_REQUEST, error("invalid_request_error", "max_tokens: Field required"), null).kind())
                .isEqualTo(ProviderErrorKind.BAD_REQUEST);
        assertThat(failWith(HttpStatus.NOT_FOUND, error("not_found_error", "model: x"), null).kind()).isEqualTo(ProviderErrorKind.MODEL_NOT_FOUND);
        assertThat(failWith(HttpStatus.PAYLOAD_TOO_LARGE, error("request_too_large", "x"), null).kind()).isEqualTo(ProviderErrorKind.BAD_REQUEST);
        assertThat(failWith(HttpStatus.INTERNAL_SERVER_ERROR, error("api_error", "x"), null).kind()).isEqualTo(ProviderErrorKind.SERVER_ERROR);
        assertThat(failWith(HttpStatusCode.valueOf(529), error("overloaded_error", "x"), null).kind()).isEqualTo(ProviderErrorKind.SERVER_ERROR);
        assertThat(failWith(HttpStatus.GATEWAY_TIMEOUT, error("timeout_error", "x"), null).kind()).isEqualTo(ProviderErrorKind.TIMEOUT);
    }

    @Test
    void anOrdinaryRateLimitCarriesRetryAfterAndIsRetryable() {
        ProviderException e = failWith(HttpStatus.TOO_MANY_REQUESTS, error("rate_limit_error", "x"), "7");

        assertThat(e.kind()).isEqualTo(ProviderErrorKind.RATE_LIMITED);
        assertThat(e.retryable()).isTrue();
    }

    @Test
    void aSpendCapIs429WithoutRetryAfterOr400AndIsNeverRetried() {
        ProviderException tier = failWith(HttpStatus.TOO_MANY_REQUESTS, error("rate_limit_error", "x"), null);
        ProviderException payment = failWith(HttpStatus.PAYMENT_REQUIRED, error("billing_error", "x"), null);
        ProviderException workspace = failWith(HttpStatus.BAD_REQUEST,
                error("invalid_request_error", "You have reached your specified workspace API usage limits."), null);

        for (ProviderException e : new ProviderException[] {tier, payment, workspace}) {
            assertThat(e.kind()).isEqualTo(ProviderErrorKind.QUOTA_EXCEEDED);
            assertThat(e.retryable()).isFalse();
        }
    }

    @Test
    void theProviderMessageNeverReachesTheExceptionBecauseItMayEchoThePrompt() {
        ProviderException e = failWith(HttpStatus.BAD_REQUEST, error("invalid_request_error", "eco del prompt: DATO-SECRETO sk-ant-FILTRADA"), null);

        assertThat(e.getMessage() + e).doesNotContain("DATO-SECRETO").doesNotContain("sk-ant-FILTRADA").doesNotContain(KEY);
    }

    @Test
    void networkProblemsAreMappedAndRetryable() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(request -> { throw new SocketTimeoutException("read timed out"); });
        assertThatThrownBy(() -> provider.chat(CALL)).isInstanceOfSatisfying(ProviderException.class, e -> {
            assertThat(e.kind()).isEqualTo(ProviderErrorKind.TIMEOUT);
            assertThat(e.retryable()).isTrue();
        });

        server.reset();
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(request -> { throw new IOException("connection refused"); });
        assertThatThrownBy(() -> provider.chat(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(ProviderErrorKind.UNAVAILABLE));
    }

    @Test
    void theKeyDoesNotAppearWhenTheAdapterIsPrinted() {
        assertThat(provider.toString()).doesNotContain(KEY);
    }

    @Test
    void anthropicDoesNotDoEmbeddings() {
        assertThatThrownBy(() -> provider.embed(new EmbedCall("m", java.util.List.of("x"), Duration.ofSeconds(1))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
