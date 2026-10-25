package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-8: con el corte global activado (modo BLOCK), alcanzar el tope mensual también pausa el curso por gasto. Contexto propio: cambia la configuración. */
@TestPropertySource(properties = "app.ai.budget.mode=BLOCK")
class GenerationGlobalBudgetTest extends GenerationTestBase {

    @Test
    void ac8_theGlobalCapInBlockModePausesTheCourseForBudget() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        spend("10.00"); // el tope global de los tests es 10 USD
        modelDoes(happy(2));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("BUDGET");
        assertThat(chatCalls()).isZero();
    }
}
