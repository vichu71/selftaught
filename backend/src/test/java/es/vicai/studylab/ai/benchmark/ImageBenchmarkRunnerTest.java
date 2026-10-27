package es.vicai.studylab.ai.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.AiTestBase;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * F12 AC-17 (ADR-83): el arnés del benchmark de imágenes se prueba con el proveedor falso (0,05 USD por imagen). El run real, con las claves del usuario, se hace a
 * mano con {@code make benchmark-images}: nunca desde un test ni en el arranque normal.
 */
@ActiveProfiles("benchmark-images")
@TestPropertySource(properties = "app.ai.benchmark.autorun=false")
class ImageBenchmarkRunnerTest extends AiTestBase {

    @Autowired ImageBenchmarkRunner runner;

    private static final ModelRef FAKE = new ModelRef("fake", "fake-image");

    private ImageBenchmarkPlan plan(String budget) {
        return ImageBenchmarkPlan.of(List.of(FAKE), new BigDecimal(budget));
    }

    @Test
    void ac17_generatesOneImagePerTopicAndModelAndWritesTheReportTheImagesAndAStaticPage(@TempDir Path out) throws IOException {
        ImageBenchmarkResult result = runner.run(plan("5.00"), out);

        assertThat(result.completed()).isTrue();
        assertThat(result.topics()).hasSize(3);
        assertThat(result.calls()).isEqualTo(3);
        assertThat(result.spentUsd()).isEqualByComparingTo(new BigDecimal("0.15"));
        assertThat(out.resolve("report.md")).exists();
        assertThat(out.resolve("index.html")).exists();
        try (Stream<Path> images = Files.list(out.resolve("images"))) {
            assertThat(images.filter(p -> p.toString().endsWith(".jpg") || p.toString().endsWith(".png")).count()).isEqualTo(3);
        }
    }

    @Test
    void ac17_theReportNamesEveryModelItsRealCostAndNeverAKey(@TempDir Path out) throws IOException {
        runner.run(plan("5.00"), out);

        String report = Files.readString(out.resolve("report.md"));
        assertThat(report).contains("fake/fake-image").contains("0.05").contains("0.15");
        assertThat(report).doesNotContainIgnoringCase("api-key").doesNotContain("sk-");
    }

    @Test
    void ac17_theStaticPageShowsTheImagesSideBySideWithoutScriptsOrExternalResources(@TempDir Path out) throws IOException {
        runner.run(plan("5.00"), out);

        String html = Files.readString(out.resolve("index.html"));
        assertThat(html).contains("<img").contains("fake-image");
        assertThat(html).doesNotContain("<script").doesNotContain("http://").doesNotContain("https://").doesNotContain("onerror");
    }

    @Test
    void ac17_aTopicWithMarkupIsEscapedInThePage(@TempDir Path out) throws IOException {
        runner.run(ImageBenchmarkPlan.of(List.of(FAKE), new BigDecimal("5.00")).withTopicOverride("<b>negrita</b>"), out);

        assertThat(Files.readString(out.resolve("index.html"))).doesNotContain("<b>negrita</b>");
    }

    @Test
    void ac17_stopsWhenTheBudgetOfTheRunIsReachedAndSaysSo(@TempDir Path out) throws IOException {
        ImageBenchmarkResult result = runner.run(plan("0.05"), out);

        assertThat(result.completed()).isFalse();
        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.spentUsd()).isLessThanOrEqualTo(new BigDecimal("0.05"));
        assertThat(Files.readString(out.resolve("report.md"))).containsIgnoringCase("interrumpido");
    }

    @Test
    void ac17_everyCallIsRecordedAsBenchmarkSourceWithoutUser(@TempDir Path out) throws IOException {
        runner.run(plan("5.00"), out);

        assertThat(count("SELECT count(*) FROM ai_usage WHERE task = 'IMAGE' AND source = 'BENCHMARK' AND user_id IS NULL")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM ai_usage WHERE task = 'IMAGE' AND source = 'APP'")).isZero();
    }

    @Test
    void ac17_theBenchmarkTopicsAreFixedInAFileOfTheProject() throws IOException {
        Path topics = Path.of("src/main/resources/benchmark/image-topics.properties");

        assertThat(topics).exists();
        assertThat(Files.readAllLines(topics).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).count()).isGreaterThanOrEqualTo(3);
    }

    private int count(String sql) {
        return owner.queryForObject(sql, Integer.class);
    }
}
