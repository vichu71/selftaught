package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-11: el tope de gasto y los fallos del proveedor llegan al alumno como un mensaje claro, sin perder la pregunta y sin dejar mensajes huérfanos. */
class TutorBudgetTest extends TutorTestBase {

    @Test
    void ac11_thePersonalLimitIsRefusedWithTheAiLayerCodeAndNoModelCall() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        spendPersonalLimit(idOf("ana@example.com"));
        tutorAnswers(ANSWER);

        ask(ana, course, lesson, "¿Qué es una variable?").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AI_USER_LIMIT"));

        assertThat(tutorCalls()).isEmpty();
        thread(ana, course, lesson).andExpect(jsonPath("$.messages", hasSize(0)));
        assertThat(messageCount("PENDING")).isZero();
    }

    @Test
    void ac11_afterTheLimitTheEarlierThreadIsStillReadable() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        ask(ana, course, lesson, "antes del tope").andExpect(status().isCreated());
        spendPersonalLimit(idOf("ana@example.com"));

        ask(ana, course, lesson, "después del tope").andExpect(status().isTooManyRequests());

        thread(ana, course, lesson).andExpect(jsonPath("$.messages", hasSize(2))).andExpect(jsonPath("$.messages[0].content").value("antes del tope"));
    }

    @Test
    void ac11_aProviderThatIsDownIsAGenericErrorAndLeavesNoOrphanMessage() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        fake.failNext(ProviderErrorKind.UNAVAILABLE, 99);

        String body = ask(ana, course, lesson, "¿Qué es una variable?").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("variable");
        thread(ana, course, lesson).andExpect(jsonPath("$.messages", hasSize(0)));
        assertThat(messageCount("FAILED")).isEqualTo(1);
        assertThat(messageCount("PENDING")).isZero();
    }

    @Test
    void ac11_afterAFailureTheSameQuestionCanBeAskedAgain() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        fake.failNext(ProviderErrorKind.UNAVAILABLE, 99);
        ask(ana, course, lesson, "¿Qué es una variable?").andExpect(status().isServiceUnavailable());
        fake.failNext(ProviderErrorKind.UNAVAILABLE, 0);

        ask(ana, course, lesson, "¿Qué es una variable?").andExpect(status().isCreated());

        thread(ana, course, lesson).andExpect(jsonPath("$.messages", hasSize(2)));
    }

    @Test
    void ac11_aRejectedKeyIsAlsoAGenericProviderErrorNeverItsMessage() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        tutorAnswers(ANSWER);
        fake.failNextWithMessage(ProviderErrorKind.AUTH, 99, "MENSAJE-SECRETO-DEL-PROVEEDOR");

        String body = ask(ana, course, lesson(course, 0), "¿Hola?").andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SECRETO");
    }
}
