package es.vicai.studylab.ai.provider;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Entrega el adaptador de cada proveedor, creado la primera vez que se pide (así solo se exigen las claves de los
 * proveedores que alguien usa). El proveedor falso es siempre el mismo bean.
 */
@Component
public class ProviderRegistry {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final FakeProvider fake;
    private final ObjectProvider<RestClient.Builder> builders;
    private final AiProperties props;
    private final Map<String, AiProvider> providers = new ConcurrentHashMap<>();

    public ProviderRegistry(FakeProvider fake, ObjectProvider<RestClient.Builder> builders, AiProperties props) {
        this.fake = fake;
        this.builders = builders;
        this.props = props;
    }

    public AiProvider get(String name) {
        return providers.computeIfAbsent(name, this::create);
    }

    private AiProvider create(String name) {
        if (name.equals("fake")) return fake;
        AiProperties.ProviderConfig config = props.providers().get(name);
        if (config == null) throw AiException.of(AiErrorKind.CONFIG);
        if (!name.equals("ollama") && !config.hasKey()) throw AiException.of(AiErrorKind.CONFIG);

        RestClient.Builder builder = builders.getObject().requestFactory(requestFactory(name));
        return switch (name) {
            case "anthropic" -> new AnthropicProvider(builder, config.baseUrl(), config.apiKey());
            case "openai" -> new OpenAiProvider(builder, config.baseUrl(), config.apiKey());
            case "gemini" -> new GeminiProvider(builder, config.baseUrl(), config.apiKey());
            case "ollama" -> new OllamaProvider(builder, config.baseUrl());
            default -> throw AiException.of(AiErrorKind.CONFIG);
        };
    }

    /** Tiempo de lectura = el mayor timeout de las tareas que usan este proveedor (por defecto, 3 minutos). */
    private JdkClientHttpRequestFactory requestFactory(String provider) {
        Duration read = props.tasks().values().stream()
                .filter(t -> t.providerName().equals(provider) && t.timeout() != null)
                .map(AiProperties.TaskConfig::timeout)
                .max(Duration::compareTo).orElse(Duration.ofMinutes(3));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(read);
        return factory;
    }
}
