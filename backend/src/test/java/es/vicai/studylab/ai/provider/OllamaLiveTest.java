package es.vicai.studylab.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Prueba OPCIONAL contra un Ollama real (el del contenedor del proyecto). No gasta dinero ni sale a Internet, pero necesita el servicio
 * levantado y el modelo descargado (`make pull-embedding-model`), así que solo corre si se define OLLAMA_LIVE_URL:
 *
 *   docker compose --profile test run --rm -e OLLAMA_LIVE_URL=http://ollama:11434 backend-test mvn -B test -Dtest=OllamaLiveTest
 *
 * Confirma el formato real que la documentación no cubría: forma de la respuesta, dimensión del vector y error de modelo inexistente.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_URL", matches = "https?://.+")
class OllamaLiveTest {

    private final OllamaProvider provider = new OllamaProvider(RestClient.builder(), System.getenv("OLLAMA_LIVE_URL"));

    @Test
    void embedsRealTextsWithTheConfiguredDimension() {
        EmbedResult result = provider.embed(new EmbedCall("nomic-embed-text", List.of("hola mundo", "adiós"), Duration.ofSeconds(30)));

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors()).allSatisfy(v -> assertThat(v).hasSize(768)); // app.ai.embedding.dimensions por defecto
        assertThat(result.inputTokens()).isGreaterThan(0);
    }

    @Test
    void theModelIsListedAsDownloaded() {
        assertThat(provider.hasModel("nomic-embed-text")).isTrue();
        assertThat(provider.hasModel("modelo-que-no-esta")).isFalse();
    }

    @Test
    void aMissingModelIsReportedAsModelNotFoundAndNotRetryable() {
        assertThatThrownBy(() -> provider.embed(new EmbedCall("no-existe", List.of("x"), Duration.ofSeconds(30))))
                .isInstanceOfSatisfying(ProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ProviderErrorKind.MODEL_NOT_FOUND);
                    assertThat(e.retryable()).isFalse();
                });
    }

    @Test
    void anUnreachableServerIsUnavailableAndRetryable() {
        OllamaProvider nobody = new OllamaProvider(RestClient.builder(), "http://127.0.0.1:1");

        assertThatThrownBy(() -> nobody.embed(new EmbedCall("nomic-embed-text", List.of("x"), Duration.ofSeconds(5))))
                .isInstanceOfSatisfying(ProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ProviderErrorKind.UNAVAILABLE);
                    assertThat(e.retryable()).isTrue();
                });
    }
}
