package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-11: con el corte global activado (modo BLOCK), alcanzar el tope mensual también se explica con un código claro. Contexto propio: cambia la configuración. */
@TestPropertySource(properties = "app.ai.budget.mode=BLOCK")
class TutorGlobalBudgetTest extends TutorTestBase {

    @Test
    void ac11_theGlobalCapInBlockModeIsRefusedWithoutCallingTheModel() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        spend("10.00"); // el tope global de los tests es 10 USD
        tutorAnswers(ANSWER);

        ask(ana, course, lesson(course, 0), "¿Qué es una variable?").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AI_BUDGET_BLOCKED"));

        assertThat(tutorCalls()).isEmpty();
    }
}
