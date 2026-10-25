package es.vicai.studylab.ai;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la capa de IA. Nada de esto está escrito en el código: proveedor, modelo, límites y precios van
 * por entorno o por fichero de configuración. La validación completa se hace al arrancar (AiSettingsValidator).
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        Map<AiTask, TaskConfig> tasks,
        Map<String, ProviderConfig> providers,
        /** Precios por "proveedor/modelo", en USD por millón de tokens. */
        Map<String, Price> pricing,
        @DefaultValue Budget budget,
        @DefaultValue("16000") int hardMaxOutputTokens,
        @DefaultValue("60000") int maxInputChars,
        @DefaultValue Retry retry,
        @DefaultValue("20m") Duration generationLockTtl,
        @DefaultValue Embedding embedding,
        @DefaultValue Benchmark benchmark) {

    public AiProperties {
        tasks = tasks == null ? Map.of() : Map.copyOf(tasks);
        providers = providers == null ? Map.of() : Map.copyOf(providers);
        pricing = pricing == null ? Map.of() : Map.copyOf(pricing);
    }

    public record TaskConfig(String provider, String model, Integer maxOutputTokens, @DefaultValue("120s") Duration timeout) {
        public String providerName() {
            return provider == null ? "" : provider.trim().toLowerCase();
        }
    }

    /** La clave nunca sale en un toString (logs, excepciones). */
    public record ProviderConfig(String apiKey, String baseUrl) {
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "ProviderConfig[apiKey=" + (hasKey() ? "***" : "(vacía)") + ", baseUrl=" + baseUrl + "]";
        }
    }

    public record Price(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {}

    public enum BudgetMode { WARN, BLOCK }

    /**
     * Tope global (avisa; corta solo en BLOCK) y topes personales por usuario (A1), que cortan siempre: así un usuario, también un
     * menor, no puede gastar todo el presupuesto antes de que nadie lo note.
     */
    public record Budget(BigDecimal monthlyLimitUsd, @DefaultValue("80") int warnAtPercent,
                         @DefaultValue("WARN") BudgetMode mode,
                         @DefaultValue("2.00") BigDecimal userLimitMinorUsd,
                         @DefaultValue("10.00") BigDecimal userLimitAdultUsd) {
        public BigDecimal userLimit(boolean minor) {
            return minor ? userLimitMinorUsd : userLimitAdultUsd;
        }

        /** Límite que se usa para calcular porcentajes; solo se puede omitir con el proveedor falso (desarrollo). */
        public BigDecimal effectiveLimit() {
            return monthlyLimitUsd == null ? new BigDecimal("5.00") : monthlyLimitUsd;
        }
    }

    public record Retry(@DefaultValue("2") int maxRetries, @DefaultValue("1s") Duration backoff) {}

    public record Embedding(@DefaultValue("768") int dimensions) {}

    /** Parámetros del run del benchmark (solo con el perfil `benchmark`). */
    public record Benchmark(@DefaultValue("true") boolean autorun, String models,
                            @DefaultValue("5.00") BigDecimal budgetUsd, @DefaultValue("/out") String outDir) {}

    public TaskConfig task(AiTask task) {
        TaskConfig config = tasks.get(task);
        if (config == null) throw AiException.of(AiErrorKind.CONFIG);
        return config;
    }

    public Price price(String provider, String model) {
        Price price = pricing.get(provider + "/" + model);
        if (price == null || price.inputPerMillion() == null || price.outputPerMillion() == null) {
            throw AiException.of(AiErrorKind.CONFIG);
        }
        return price;
    }
}
