package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-16 (ADR-42): un temario que pida demasiadas piezas no dispara las llamadas. Contexto propio: cambia la configuración. */
@TestPropertySource(properties = "app.generation.max-pieces=10")
class GenerationLimitsTest extends GenerationTestBase {

    @Test
    void ac16_anOutlineThatNeedsMorePiecesThanTheMaximumFailsTheCourseWithoutGeneratingAnything() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 3, true); // 3 temas × 2 capítulos con examen = 15 piezas
        modelDoes(happy(3));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("FAILED");
        assertThat(stopReasonOf(course)).isEqualTo("LIMIT");
        assertThat(chatCalls()).isEqualTo(1); // solo el temario
        assertThat(allPieces(course)).isZero();
    }

    @Test
    void ac16_aCourseWithinTheMaximumGeneratesNormally() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true); // 10 piezas
        modelDoes(happy(2));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
    }
}
