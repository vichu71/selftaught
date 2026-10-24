package es.vicai.studylab.ai.benchmark;

import es.vicai.studylab.ai.AiProperties;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Lanza el benchmark al arrancar con el perfil `benchmark` (servicio de Compose de un solo uso: `make benchmark`).
 * Los modelos salen de APP_AI_BENCHMARK_MODELS ("proveedor/modelo,proveedor/modelo") y el tope, de APP_AI_BENCHMARK_BUDGET_USD.
 */
@Component
@Profile("benchmark")
@ConditionalOnProperty(prefix = "app.ai.benchmark", name = "autorun", havingValue = "true", matchIfMissing = true)
public class BenchmarkLauncher implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkLauncher.class);

    private final BenchmarkRunner runner;
    private final AiProperties props;

    public BenchmarkLauncher(BenchmarkRunner runner, AiProperties props) {
        this.runner = runner;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        String models = props.benchmark().models();
        if (models == null || models.isBlank()) {
            throw new IllegalStateException("Indica los modelos en APP_AI_BENCHMARK_MODELS (por ejemplo: proveedor/modelo,proveedor/modelo)");
        }
        List<ModelRef> plan = Arrays.stream(models.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(ModelRef::parse).toList();
        BenchmarkResult result = runner.run(BenchmarkPlan.of(plan, props.benchmark().budgetUsd()), Path.of(props.benchmark().outDir()));
        log.info("Benchmark {}: {} llamadas, {} USD. Informe: {}", result.completed() ? "completo" : "INTERRUMPIDO por su tope",
                result.calls(), result.spentUsd().toPlainString(), result.report());
    }
}
