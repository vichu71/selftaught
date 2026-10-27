package es.vicai.studylab.ai.benchmark;

import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiImageProperties;
import es.vicai.studylab.ai.AiImageRequest;
import es.vicai.studylab.ai.AiImageResponse;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiSettingsValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.web.util.HtmlUtils;

/**
 * Benchmark reproducible para elegir el modelo de imagen (F12 AC-17, ADR-83). Pide una imagen por tema de {@code benchmark/image-topics.properties} y modelo del plan
 * con el mismo prompt que usa la aplicación, con su propio tope de gasto, y escribe un informe Markdown, las imágenes y una página estática para compararlas una al lado
 * de otra. Gasta dinero real con proveedores reales: solo existe con el perfil {@code benchmark-images} y nunca se ejecuta en el arranque normal. No usa datos de usuarios.
 */
@Component
@Profile("benchmark-images")
public class ImageBenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(ImageBenchmarkRunner.class);

    private record Measure(ModelRef model, String topic, String file, BigDecimal costUsd, long ms) {}

    private final AiGateway gateway;
    private final AiProperties props;
    private final AiImageProperties imageProps;
    private final String template;

    public ImageBenchmarkRunner(AiGateway gateway, AiProperties props, AiImageProperties imageProps) {
        this.gateway = gateway;
        this.props = props;
        this.imageProps = imageProps;
        try (InputStream in = new ClassPathResource("prompts/illustration.txt").getInputStream()) {
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Mismo formato que el de la aplicación (plantilla y después el motivo entre comillas); los temas del benchmark salen de un fichero del proyecto, no de un usuario. */
    private String prompt(String topic) {
        return template + "\n\nMotivo: \"" + topic.replaceAll("[\"<>`{}\\[\\]\\\\|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip() + "\"";
    }

    public ImageBenchmarkResult run(ImageBenchmarkPlan plan, Path outDir) {
        Map<ModelRef, BigDecimal> prices = validate(plan);
        List<String> topics = plan.topicOverride() != null ? List.of(plan.topicOverride()) : loadTopics();
        Path images = outDir.resolve("images");
        try {
            Files.createDirectories(images);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<Measure> measures = new ArrayList<>();
        Map<ModelRef, String> failures = new LinkedHashMap<>();
        BigDecimal spent = BigDecimal.ZERO;
        boolean completed = true;

        run:
        for (ModelRef model : plan.models()) {
            for (String topic : topics) {
                // Nunca se pide una imagen que, al precio del modelo, haría pasar el tope del run
                if (spent.add(prices.get(model)).compareTo(plan.budgetUsd()) > 0) {
                    completed = false;
                    break run;
                }
                try {
                    AiImageResponse response = gateway.generateImageWith(model, AiImageRequest.benchmark(prompt(topic)));
                    spent = spent.add(response.costUsd());
                    String file = slug(model.toString()) + "__" + slug(topic) + "." + extension(response.bytes());
                    Files.write(images.resolve(file), response.bytes());
                    measures.add(new Measure(model, topic, file, response.costUsd(), response.durationMs()));
                    log.info("Benchmark de imágenes {} · {}: {} USD", model, topic, response.costUsd().toPlainString());
                } catch (AiException e) {
                    // Un modelo que falla (clave, nombre mal escrito, moderación, cuota…) no tumba el run: se anota y se pasa al siguiente
                    failures.put(model, e.kind().name());
                    log.warn("Benchmark de imágenes {} falla ({}): se omite el resto de ese modelo", model, e.kind());
                    spent = spent.add(e.costUsd());
                    break;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        Path report = outDir.resolve("report.md");
        Path page = outDir.resolve("index.html");
        write(report, report(plan, topics, measures, spent, completed, failures));
        write(page, page(plan, topics, measures));
        return new ImageBenchmarkResult(report, page, completed, topics, measures.size(), spent, failures);
    }

    // ---------- plan ----------

    /** Falla antes de gastar nada si a un modelo le falta la clave o el precio por imagen, o el proveedor no hace imágenes. Devuelve el precio de cada modelo. */
    private Map<ModelRef, BigDecimal> validate(ImageBenchmarkPlan plan) {
        List<String> problems = new ArrayList<>();
        Map<ModelRef, BigDecimal> prices = new LinkedHashMap<>();
        for (ModelRef model : plan.models()) {
            String provider = model.provider();
            if (!AiSettingsValidator.IMAGE_PROVIDERS.contains(provider)) {
                problems.add(model + ": proveedor sin imágenes");
                continue;
            }
            AiProperties.ProviderConfig config = props.providers().get(provider);
            if (!provider.equals("fake") && (config == null || !config.hasKey())) {
                problems.add(AiSettingsValidator.envVarOf(provider) + " (clave de " + provider + ")");
            }
            try {
                prices.put(model, imageProps.priceOf(provider, model.model()));
            } catch (AiException e) {
                problems.add("app.ai.image-pricing[" + model + "].per-image (precio por imagen)");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("No se puede lanzar el benchmark de imágenes, falta: " + String.join(", ", problems));
        }
        return prices;
    }

    private List<String> loadTopics() {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("benchmark/image-topics.properties").getInputStream()) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
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

    // ---------- ficheros ----------

    private static String report(ImageBenchmarkPlan plan, List<String> topics, List<Measure> measures, BigDecimal spent, boolean completed, Map<ModelRef, String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Benchmark F12 (imágenes) — ").append(LocalDate.now()).append("\n\n");
        sb.append("Tope del run: ").append(plan.budgetUsd().toPlainString()).append(" USD · Gastado: ").append(spent.toPlainString())
                .append(" USD · Imágenes: ").append(measures.size()).append("\n\n");
        if (!completed) sb.append("> **Run interrumpido:** se alcanzó el tope del run antes de terminar. Los resultados son parciales.\n\n");
        if (!failures.isEmpty()) {
            sb.append("## Fallos\n\n| Modelo | Error |\n|---|---|\n");
            failures.forEach((model, kind) -> sb.append("| ").append(model).append(" | ").append(kind).append(" (se omitió el resto de este modelo) |\n"));
            sb.append('\n');
        }
        sb.append("Las imágenes están en `images/` y `index.html` las muestra una al lado de otra (ábrelo en el navegador).\n\n");
        sb.append("## Coste por imagen\n\n| Modelo | Tema | USD | ms | Fichero |\n|---|---|---:|---:|---|\n");
        for (Measure m : measures) {
            sb.append("| ").append(m.model()).append(" | ").append(m.topic().replace("|", "/")).append(" | ").append(m.costUsd().toPlainString()).append(" | ")
                    .append(m.ms()).append(" | images/").append(m.file()).append(" |\n");
        }
        sb.append("\nEs una **estimación** con la tabla de precios de `ai-pricing.properties`: contrástala con el panel de cada proveedor.\n\n");
        sb.append("## Hoja de valoración manual (1–5)\n\n");
        sb.append("Mira `index.html` y puntúa. Criterios: encaja con el tema, estilo coherente, sin texto ni letras dentro, apta para todas las edades.\n\n");
        sb.append("| Modelo | Tema | Encaja | Estilo | Sin texto | Apta | Notas |\n|---|---|:-:|:-:|:-:|:-:|---|\n");
        for (Measure m : measures) {
            sb.append("| ").append(m.model()).append(" | ").append(m.topic().replace("|", "/")).append(" |  |  |  |  |  |\n");
        }
        sb.append("\nTemas: ").append(topics.size()).append(".\n");
        return sb.toString();
    }

    /** Página estática: sin scripts, sin recursos externos y con todo lo que viene del plan o de los temas escapado. */
    private static String page(ImageBenchmarkPlan plan, List<String> topics, List<Measure> measures) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"es\"><head><meta charset=\"utf-8\"><title>Benchmark de ilustraciones</title>\n");
        sb.append("<style>body{font-family:sans-serif;margin:1rem}table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:.5rem;vertical-align:top}");
        sb.append("img{display:block;max-width:240px;height:auto}</style></head><body>\n<h1>Benchmark de ilustraciones</h1>\n<table><tr><th>Tema</th>");
        for (ModelRef model : plan.models()) sb.append("<th>").append(HtmlUtils.htmlEscape(model.toString())).append("</th>");
        sb.append("</tr>\n");
        for (String topic : topics) {
            sb.append("<tr><td>").append(HtmlUtils.htmlEscape(topic)).append("</td>");
            for (ModelRef model : plan.models()) {
                Measure found = measures.stream().filter(m -> m.model().equals(model) && m.topic().equals(topic)).findFirst().orElse(null);
                if (found == null) {
                    sb.append("<td>—</td>");
                } else {
                    sb.append("<td><img src=\"images/").append(HtmlUtils.htmlEscape(found.file())).append("\" alt=\"")
                            .append(HtmlUtils.htmlEscape(model + " · " + topic)).append("\"><small>").append(HtmlUtils.htmlEscape(found.costUsd().toPlainString()))
                            .append(" USD</small></td>");
                }
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n</body></html>\n");
        return sb.toString();
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** La extensión sale del contenido, no de lo que diga el proveedor. */
    private static String extension(byte[] bytes) {
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) return "jpg";
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') return "webp";
        return "png";
    }

    private static String slug(String text) {
        String plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
