package es.vicai.studylab.ai.benchmark;

import es.vicai.studylab.ai.AiImageProperties;
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
 * Lanza el benchmark de imágenes al arrancar con el perfil `benchmark-images` (servicio de Compose de un solo uso: `make benchmark-images`). Los modelos salen de
 * APP_AI_IMAGE_BENCHMARK_MODELS ("proveedor/modelo,proveedor/modelo") y el tope, de APP_AI_IMAGE_BENCHMARK_BUDGET_USD.
 */
@Component
@Profile("benchmark-images")
@ConditionalOnProperty(prefix = "app.ai.benchmark", name = "autorun", havingValue = "true", matchIfMissing = true)
public class ImageBenchmarkLauncher implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ImageBenchmarkLauncher.class);

    private final ImageBenchmarkRunner runner;
    private final AiImageProperties props;

    public ImageBenchmarkLauncher(ImageBenchmarkRunner runner, AiImageProperties props) {
        this.runner = runner;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        String models = props.imageBenchmark().models();
        if (models == null || models.isBlank()) {
            throw new IllegalStateException("Indica los modelos en APP_AI_IMAGE_BENCHMARK_MODELS (por ejemplo: openai/modelo,gemini/modelo)");
        }
        List<ModelRef> plan = Arrays.stream(models.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(ModelRef::parse).toList();
        ImageBenchmarkResult result = runner.run(ImageBenchmarkPlan.of(plan, props.imageBenchmark().budgetUsd()), Path.of(props.imageBenchmark().outDir()));
        log.info("Benchmark de imágenes {}: {} imágenes, {} USD. Informe: {}", result.completed() ? "completo" : "INTERRUMPIDO por su tope", result.calls(),
                result.spentUsd().toPlainString(), result.report());
    }
}
