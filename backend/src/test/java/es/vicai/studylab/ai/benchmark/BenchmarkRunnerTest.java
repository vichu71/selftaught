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
 * AC-17: el arnés del benchmark se prueba con el proveedor falso (precios 1/2 y 3/6 USD por millón; cada llamada
 * del falso gasta 100 tokens de entrada y 50 de salida: 0,000200 USD con fake-1 y 0,000600 con fake-2).
 * El run real, con las claves del usuario, se hace a mano con `make benchmark`.
 */
@ActiveProfiles("benchmark")
@TestPropertySource(properties = "app.ai.benchmark.autorun=false")
class BenchmarkRunnerTest extends AiTestBase {

    @Autowired BenchmarkRunner runner;

    private static final ModelRef FAKE_1 = new ModelRef("fake", "fake-1");
    private static final ModelRef FAKE_2 = new ModelRef("fake", "fake-2");

    private BenchmarkPlan plan(String budget) {
        return BenchmarkPlan.of(List.of(FAKE_1, FAKE_2), new BigDecimal(budget));
    }

    @Test
    void ac17_runsEveryTopicTaskAndModelAndWritesTheReportAndTheRawOutputs(@TempDir Path out) throws IOException {
        BenchmarkResult result = runner.run(plan("5.00"), out);

        assertThat(result.completed()).isTrue();
        assertThat(result.topics()).hasSize(3);
        assertThat(result.calls()).isEqualTo(3 * 3 * 2); // 3 temas · (ficha, lección, evaluación) · 2 modelos
        assertThat(result.spentUsd()).isEqualByComparingTo(new BigDecimal("0.007200")); // 9·0,0002 + 9·0,0006
        try (Stream<Path> raw = Files.list(out.resolve("raw"))) {
            assertThat(raw.count()).isEqualTo(18);
        }
        assertThat(result.report()).exists().isEqualTo(out.resolve("report.md"));
    }

    @Test
    void ac17_theReportHasTheAgreedSections(@TempDir Path out) throws IOException {
        String report = Files.readString(runner.run(plan("5.00"), out).report());

        assertThat(report).contains("Benchmark F02").contains("Tope del run");
        assertThat(report.split("(?m)^## Tema ", -1)).hasSize(4); // 3 temas
        assertThat(report).contains("fake/fake-1").contains("fake/fake-2");
        assertThat(report).contains("Coste estimado por curso");
        assertThat(report).contains("Hoja de valoración").containsIgnoringCase("precisión").containsIgnoringCase("claridad")
                .containsIgnoringCase("nivel").containsIgnoringCase("formato");
    }

    @Test
    void ac17_theCostPerCourseUsesTheAgreedCourseShape(@TempDir Path out) {
        BenchmarkResult result = runner.run(plan("5.00"), out);

        // Curso tipo = 1 ficha + 8 lecciones + 9 evaluaciones (8 de ejercicios y 1 examen) = 18 llamadas
        assertThat(result.costPerCourse().get(FAKE_1)).isEqualByComparingTo(new BigDecimal("0.003600")); // 18 · 0,0002
        assertThat(result.costPerCourse().get(FAKE_2)).isEqualByComparingTo(new BigDecimal("0.010800")); // 18 · 0,0006
    }

    @Test
    void ac17_theRunStopsAtItsOwnBudgetAndSaysSoInTheReport(@TempDir Path out) throws IOException {
        BenchmarkResult result = runner.run(plan("0.0005"), out); // fake-1 gasta 0,0002 por llamada

        assertThat(result.completed()).isFalse();
        assertThat(result.calls()).isEqualTo(3); // tras 3 llamadas (0,0006) se alcanza el tope y se para
        assertThat(result.spentUsd()).isLessThan(new BigDecimal("0.0011"));
        assertThat(Files.readString(result.report())).containsIgnoringCase("interrumpido");
    }

    @Test
    void ac17_theCallsAreRecordedAsBenchmarkWithoutAnyUser(@TempDir Path out) {
        BenchmarkResult result = runner.run(plan("5.00"), out);

        assertThat(owner.queryForObject("SELECT count(*) FROM ai_usage WHERE source = 'BENCHMARK' AND user_id IS NULL", Integer.class))
                .isEqualTo(result.calls());
        assertThat(owner.queryForObject("SELECT count(*) FROM ai_usage WHERE source <> 'BENCHMARK'", Integer.class)).isZero();
        assertThat(owner.queryForObject("SELECT sum(cost_usd) FROM ai_usage", BigDecimal.class))
                .isEqualByComparingTo(result.spentUsd());
    }

    @Test
    void ac17_theRunIsReproducibleWithTheSameInputs(@TempDir Path first, @TempDir Path second) throws IOException {
        runner.run(plan("5.00"), first);
        runner.run(plan("5.00"), second);

        try (Stream<Path> files = Files.list(first.resolve("raw"))) {
            for (Path file : files.toList()) {
                assertThat(Files.readString(second.resolve("raw").resolve(file.getFileName()))).isEqualTo(Files.readString(file));
            }
        }
    }

    @Test
    void ac17_theBenchmarkUsesTheGatewayTemplatesNotUserData(@TempDir Path out) {
        runner.run(plan("5.00"), out);

        assertThat(fake.calls()).isNotEmpty();
        assertThat(fake.calls()).allSatisfy(call -> assertThat(call.instructions()).isNotBlank());
    }
}
