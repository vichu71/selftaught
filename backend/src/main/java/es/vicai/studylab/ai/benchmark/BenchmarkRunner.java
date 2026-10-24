package es.vicai.studylab.ai.benchmark;

import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiRequest;
import es.vicai.studylab.ai.AiResponse;
import es.vicai.studylab.ai.AiSettingsValidator;
import es.vicai.studylab.ai.AiTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Benchmark reproducible para elegir modelos (AC-17, ADR-24). Ejecuta los mismos prompts sobre los temas de
 * {@code benchmark/topics.properties} con cada modelo del plan, con su propio tope de gasto, y escribe un informe Markdown, las
 * salidas completas y una hoja de valoración manual. Gasta dinero real con proveedores reales: solo existe con el perfil
 * {@code benchmark} y nunca se ejecuta en el arranque normal. No usa datos de usuarios.
 */
@Component
@Profile("benchmark")
public class BenchmarkRunner {

    /** Curso tipo para estimar el coste: 1 ficha + 8 lecciones + 9 evaluaciones (8 de ejercicios y 1 examen). */
    static final Map<AiTask, Integer> COURSE_SHAPE = new EnumMap<>(Map.of(AiTask.OUTLINE, 1, AiTask.LESSON, 8, AiTask.ASSESSMENT, 9));
    private static final Map<AiTask, String> TEMPLATES = new EnumMap<>(Map.of(
            AiTask.OUTLINE, "benchmark-outline", AiTask.LESSON, "benchmark-lesson", AiTask.ASSESSMENT, "benchmark-assessment"));

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private record Measure(ModelRef model, String topic, AiTask task, AiResponse response) {}

    private final AiGateway gateway;
    private final AiProperties props;

    public BenchmarkRunner(AiGateway gateway, AiProperties props) {
        this.gateway = gateway;
        this.props = props;
    }

    public BenchmarkResult run(BenchmarkPlan plan, Path outDir) {
        validate(plan);
        List<String> topics = loadTopics();
        Path raw = outDir.resolve("raw");
        try {
            Files.createDirectories(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<Measure> measures = new ArrayList<>();
        BigDecimal spent = BigDecimal.ZERO;
        boolean completed = true;

        // Modelo a modelo: si el tope se alcanza se para sin haber tocado el resto de modelos más de lo necesario
        run:
        for (ModelRef model : plan.models()) {
            for (String topic : topics) {
                for (AiTask task : TEMPLATES.keySet()) {
                    if (spent.compareTo(plan.budgetUsd()) >= 0) {
                        completed = false;
                        break run;
                    }
                    AiResponse response = gateway.completeWith(model, AiRequest.benchmark(task, TEMPLATES.get(task)).data("tema", topic));
                    spent = spent.add(response.costUsd());
                    measures.add(new Measure(model, topic, task, response));
                    writeRaw(raw, model, topic, task, response);
                    log.info("Benchmark {} · {} · {}: {} USD", model, task, topic, response.costUsd().toPlainString());
                }
            }
        }

        Map<ModelRef, BigDecimal> perCourse = costPerCourse(plan.models(), measures);
        Path report = outDir.resolve("report.md");
        write(report, report(plan, topics, measures, spent, completed, perCourse));
        return new BenchmarkResult(report, completed, topics, measures.size(), spent, perCourse);
    }

    // ---------- plan ----------

    /** Falla antes de gastar nada si a un modelo le falta la clave, el precio o el proveedor no es de chat. */
    private void validate(BenchmarkPlan plan) {
        List<String> problems = new ArrayList<>();
        for (ModelRef model : plan.models()) {
            String provider = model.provider();
            if (!AiSettingsValidator.KNOWN_PROVIDERS.contains(provider) || provider.equals("ollama")) {
                problems.add(model + ": proveedor no válido para chat");
                continue;
            }
            AiProperties.ProviderConfig config = props.providers().get(provider);
            if (!provider.equals("fake") && (config == null || !config.hasKey())) {
                problems.add(AiSettingsValidator.envVarOf(provider) + " (clave de " + provider + ")");
            }
            AiProperties.Price price = props.pricing().get(model.toString());
            if (price == null || price.inputPerMillion() == null || price.outputPerMillion() == null) {
                problems.add("app.ai.pricing[" + model + "] (precio del modelo)");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("No se puede lanzar el benchmark, falta: " + String.join(", ", problems));
        }
    }

    private List<String> loadTopics() {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("benchmark/topics.properties").getInputStream()) {
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<Integer, String> ordered = new TreeMap<>();
        properties.forEach((key, value) -> {
            String k = String.valueOf(key);
            if (k.startsWith("topic.")) ordered.put(Integer.parseInt(k.substring("topic.".length())), String.valueOf(value).trim());
        });
        return List.copyOf(ordered.values());
    }

    // ---------- coste por curso ----------

    private static Map<ModelRef, BigDecimal> costPerCourse(List<ModelRef> models, List<Measure> measures) {
        Map<ModelRef, BigDecimal> result = new LinkedHashMap<>();
        for (ModelRef model : models) {
            BigDecimal total = BigDecimal.ZERO;
            boolean complete = true;
            for (Map.Entry<AiTask, Integer> part : COURSE_SHAPE.entrySet()) {
                List<BigDecimal> costs = measures.stream().filter(m -> m.model().equals(model) && m.task() == part.getKey())
                        .map(m -> m.response().costUsd()).toList();
                if (costs.isEmpty()) {
                    complete = false;
                    break;
                }
                BigDecimal average = costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(costs.size()), 6, RoundingMode.HALF_UP);
                total = total.add(average.multiply(BigDecimal.valueOf(part.getValue())));
            }
            if (complete) result.put(model, total.setScale(6, RoundingMode.HALF_UP));
        }
        return result;
    }

    // ---------- ficheros ----------

    private static void writeRaw(Path rawDir, ModelRef model, String topic, AiTask task, AiResponse r) {
        String name = slug(model.toString()) + "__" + slug(topic) + "__" + task.name().toLowerCase() + ".md";
        // Sin marcas de tiempo ni duraciones: dos runs con las mismas entradas dan ficheros idénticos
        String content = "# " + model + " · " + task + "\n\n**Tema:** " + topic + "\n\n"
                + "**Tokens:** entrada " + r.inputTokens() + " · salida " + r.outputTokens() + (r.tokensEstimated() ? " (estimados)" : "")
                + " · **Coste:** " + r.costUsd().toPlainString() + " USD · **Fin:** " + r.finishReason() + "\n\n---\n\n" + r.text() + "\n";
        write(rawDir.resolve(name), content);
    }

    private static String report(BenchmarkPlan plan, List<String> topics, List<Measure> measures, BigDecimal spent, boolean completed,
                                 Map<ModelRef, BigDecimal> perCourse) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Benchmark F02 — ").append(LocalDate.now()).append("\n\n");
        sb.append("Tope del run: ").append(plan.budgetUsd().toPlainString()).append(" USD · Gastado: ").append(spent.toPlainString())
                .append(" USD · Llamadas: ").append(measures.size()).append("\n\n");
        if (!completed) {
            sb.append("> **Run interrumpido:** se alcanzó el tope del run antes de terminar. Los resultados son parciales.\n\n");
        }
        sb.append("Curso tipo = 1 ficha + 8 lecciones + 9 evaluaciones (8 de ejercicios y 1 examen). Las salidas completas están en `raw/`.\n\n");

        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            sb.append("## Tema ").append(i + 1).append(": ").append(topic).append("\n\n");
            sb.append("| Modelo | Tarea | Tok. entrada | Tok. salida | Coste (USD) | ms | Fin |\n|---|---|---:|---:|---:|---:|---|\n");
            for (Measure m : measures) {
                if (!m.topic().equals(topic)) continue;
                AiResponse r = m.response();
                sb.append("| ").append(m.model()).append(" | ").append(m.task()).append(" | ").append(r.inputTokens())
                        .append(r.tokensEstimated() ? "*" : "").append(" | ").append(r.outputTokens()).append(" | ")
                        .append(r.costUsd().toPlainString()).append(" | ").append(r.durationMs()).append(" | ").append(r.finishReason()).append(" |\n");
            }
            sb.append('\n');
        }

        sb.append("## Coste estimado por curso\n\n| Modelo | USD por curso tipo |\n|---|---:|\n");
        perCourse.forEach((model, cost) -> sb.append("| ").append(model).append(" | ").append(cost.toPlainString()).append(" |\n"));
        if (perCourse.isEmpty()) sb.append("| (ningún modelo completó las tres tareas) | — |\n");
        sb.append("\nEs una **estimación** con la tabla de precios de `ai-pricing.properties`: contrástala con el panel de cada proveedor.\n\n");

        sb.append("## Hoja de valoración manual (1–5)\n\n");
        sb.append("Lee las salidas de `raw/` y puntúa. Criterios: precisión del contenido, claridad, adecuación al nivel y formato.\n\n");
        sb.append("| Modelo | Tema | Tarea | Precisión | Claridad | Nivel | Formato | Notas |\n|---|---|---|:-:|:-:|:-:|:-:|---|\n");
        for (Measure m : measures) {
            sb.append("| ").append(m.model()).append(" | ").append(m.topic()).append(" | ").append(m.task()).append(" |  |  |  |  |  |\n");
        }
        return sb.toString();
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "Inglés de 2º" → "ingles-de-2": sin tildes ni símbolos, apto para un nombre de fichero. */
    private static String slug(String text) {
        String plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
