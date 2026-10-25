package es.vicai.studylab.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiProperties.ProviderConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

/** Construcción de los adaptadores a partir de la configuración (sin llamar a nadie: solo se crean los objetos). */
class ProviderRegistryTest {

    @SuppressWarnings("unchecked")
    private static ProviderRegistry registry(Map<String, ProviderConfig> providers) {
        AiProperties props = new AiProperties(Map.of(), providers, Map.of(), new AiProperties.Budget(null, 80, AiProperties.BudgetMode.WARN, new java.math.BigDecimal("2.00"), new java.math.BigDecimal("10.00")),
                16000, 60000, new AiProperties.Retry(2, Duration.ofMillis(1)), Duration.ofMinutes(20), new AiProperties.Embedding(8),
                new AiProperties.Benchmark(false, null, BigDecimal.ONE, "/tmp"));
        ObjectProvider<RestClient.Builder> builders = mock(ObjectProvider.class);
        when(builders.getObject()).thenAnswer(invocation -> RestClient.builder());
        return new ProviderRegistry(new FakeProvider(props), builders, props);
    }

    private static final Map<String, ProviderConfig> CONFIGURED = Map.of(
            "anthropic", new ProviderConfig("k-a", "https://api.anthropic.com"),
            "openai", new ProviderConfig("k-o", "https://api.openai.com"),
            "gemini", new ProviderConfig("k-g", "https://generativelanguage.googleapis.com"),
            "ollama", new ProviderConfig(null, "http://ollama:11434"));

    @Test
    void createsTheAdapterOfEachConfiguredProvider() {
        ProviderRegistry registry = registry(CONFIGURED);

        assertThat(registry.get("anthropic")).isInstanceOf(AnthropicProvider.class);
        assertThat(registry.get("openai")).isInstanceOf(OpenAiProvider.class);
        assertThat(registry.get("gemini")).isInstanceOf(GeminiProvider.class);
        assertThat(registry.get("ollama")).isInstanceOf(OllamaProvider.class);
        assertThat(registry.get("fake")).isInstanceOf(FakeProvider.class);
    }

    @Test
    void theSameAdapterIsReusedAcrossCalls() {
        ProviderRegistry registry = registry(CONFIGURED);

        assertThat(registry.get("anthropic")).isSameAs(registry.get("anthropic"));
    }

    @Test
    void aProviderWithoutAKeyIsAConfigurationErrorAndOllamaDoesNotNeedOne() {
        ProviderRegistry registry = registry(Map.of("anthropic", new ProviderConfig("", "https://api.anthropic.com"),
                "ollama", new ProviderConfig(null, "http://ollama:11434")));

        assertThatThrownBy(() -> registry.get("anthropic")).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.kind()).isEqualTo(AiErrorKind.CONFIG));
        assertThat(registry.get("ollama")).isInstanceOf(OllamaProvider.class);
    }

    @Test
    void anUnknownOrUnconfiguredProviderIsAConfigurationError() {
        ProviderRegistry registry = registry(CONFIGURED);

        assertThatThrownBy(() -> registry.get("skynet")).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.kind()).isEqualTo(AiErrorKind.CONFIG));
    }

    @Test
    void theConfigurationNeverPrintsTheKey() {
        assertThat(CONFIGURED.get("anthropic").toString()).doesNotContain("k-a").contains("***");
        assertThat(new ProviderConfig("", "u").toString()).contains("vacía");
    }
}
