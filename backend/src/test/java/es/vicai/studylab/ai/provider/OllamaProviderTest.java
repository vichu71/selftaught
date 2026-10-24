package es.vicai.studylab.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Formato verificado en la documentación de Ollama (docs/api.md): POST /api/embed con {model, input (texto o lista)}
 * → {model, embeddings: [[...]], prompt_eval_count}; GET /api/tags lista los modelos locales. El modelo inexistente
 * responde 404 (el cuerpo no está documentado: se trata solo por el estado). Sin autenticación.
 */
class OllamaProviderTest {

    private static final String BASE = "http://ollama:11434";
    private static final EmbedCall CALL = new EmbedCall("nomic-embed-text", List.of("uno", "dos"), Duration.ofSeconds(5));

    private MockRestServiceServer server;
    private OllamaProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OllamaProvider(builder, BASE);
    }

    @Test
    void sendsAllTheTextsInOneBatchAndReadsTheVectorsInOrder() {
        server.expect(requestTo(BASE + "/api/embed"))
                .andExpect(method(POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(jsonPath("$.model").value("nomic-embed-text"))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andExpect(jsonPath("$.input[0]").value("uno"))
                .andExpect(jsonPath("$.input[1]").value("dos"))
                .andRespond(withSuccess("""
                        {"model":"nomic-embed-text","embeddings":[[0.1,0.2,0.3],[0.4,0.5,0.6]],
                         "total_duration":1000,"load_duration":10,"prompt_eval_count":7}""", MediaType.APPLICATION_JSON));

        EmbedResult result = provider.embed(CALL);

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.vectors().get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        assertThat(result.inputTokens()).isEqualTo(7);
    }

    @Test
    void aDifferentNumberOfVectorsThanTextsIsInvalid() {
        server.expect(requestTo(BASE + "/api/embed")).andRespond(withSuccess(
                "{\"model\":\"m\",\"embeddings\":[[0.1]],\"prompt_eval_count\":1}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.embed(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(ProviderErrorKind.INVALID_RESPONSE));
    }

    @Test
    void aMissingModelIsReportedAsModelNotFoundAndIsNotRetried() {
        server.expect(requestTo(BASE + "/api/embed")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"model 'nomic-embed-text' not found\"}"));

        assertThatThrownBy(() -> provider.embed(CALL)).isInstanceOfSatisfying(ProviderException.class, e -> {
            assertThat(e.kind()).isEqualTo(ProviderErrorKind.MODEL_NOT_FOUND);
            assertThat(e.retryable()).isFalse();
        });
    }

    @Test
    void serverErrorsAreRetryableAndAnUnreachableServerIsUnavailable() {
        server.expect(requestTo(BASE + "/api/embed")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> provider.embed(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.retryable()).isTrue());

        server.reset();
        server.expect(requestTo(BASE + "/api/embed")).andRespond(request -> { throw new IOException("connection refused"); });
        assertThatThrownBy(() -> provider.embed(CALL)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(ProviderErrorKind.UNAVAILABLE));
    }

    @Test
    void healthCheckSaysWhetherTheModelIsDownloaded() {
        server.expect(requestTo(BASE + "/api/tags")).andRespond(withSuccess(
                "{\"models\":[{\"name\":\"nomic-embed-text:latest\",\"model\":\"nomic-embed-text:latest\"}]}", MediaType.APPLICATION_JSON));
        assertThat(provider.hasModel("nomic-embed-text")).isTrue();

        server.reset();
        server.expect(requestTo(BASE + "/api/tags")).andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));
        assertThat(provider.hasModel("nomic-embed-text")).isFalse();
    }

    @Test
    void ollamaIsNotUsedForChatInThisVersion() {
        assertThatThrownBy(() -> provider.chat(new ChatCall("m", "i", "d", 10, Duration.ofSeconds(1))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
