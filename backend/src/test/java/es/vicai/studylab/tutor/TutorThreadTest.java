package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** AC-9 (ADR-55): el hilo de cada lección, su orden, lo que se ve y lo que no, y vaciarlo. */
class TutorThreadTest extends TutorTestBase {

    @Test
    void ac9_theThreadIsOrderedOldestFirstAndSeparatePerLesson() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID first = lesson(course, 0);
        UUID second = lesson(course, 1);
        tutorAnswers(ANSWER);
        ask(ana, course, first, "UNO");
        ask(ana, course, first, "DOS");
        ask(ana, course, second, "OTRA-LECCION");

        thread(ana, course, first).andExpect(jsonPath("$.messages", hasSize(4))).andExpect(jsonPath("$.messages[0].content").value("UNO"))
                .andExpect(jsonPath("$.messages[2].content").value("DOS"));
        thread(ana, course, second).andExpect(jsonPath("$.messages", hasSize(2))).andExpect(jsonPath("$.messages[0].content").value("OTRA-LECCION"));
    }

    @Test
    void ac9_pendingAndFailedMessagesNeverShowInTheThread() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        insertMessage(anaId, course, lesson, "USER", "PENDING", "PENDIENTE", "1 hour");
        insertMessage(anaId, course, lesson, "USER", "FAILED", "FALLIDA", "50 minutes");
        insertMessage(anaId, course, lesson, "USER", "OK", "VISIBLE", "40 minutes");
        insertMessage(anaId, course, lesson, "USER", "BLOCKED", "BLOQUEADA", "30 minutes");
        insertMessage(anaId, course, lesson, "TUTOR", "FILTERED", "FILTRADA", "20 minutes");

        String body = thread(ana, course, lesson).andExpect(jsonPath("$.messages", hasSize(3))).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("VISIBLE").contains("BLOQUEADA").contains("FILTRADA").doesNotContain("PENDIENTE").doesNotContain("FALLIDA");
    }

    @Test
    void ac9_aTutorMessageWithoutSourcesOmitsTheField() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        ask(ana, course, lesson, "¿Qué es?");

        thread(ana, course, lesson).andExpect(jsonPath("$.messages[0].sources").doesNotExist()).andExpect(jsonPath("$.messages[1].sources").doesNotExist());
    }

    @Test
    void ac9_emptyingTheThreadDeletesEverythingOfThatLessonIncludingFailedAndPendingOnesAndNothingElse() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        UUID other = lesson(course, 1);
        tutorAnswers(ANSWER);
        ask(ana, course, lesson, "borrar");
        ask(ana, course, other, "conservar");
        insertMessage(anaId, course, lesson, "USER", "FAILED", "fallida", "1 hour");
        insertMessage(anaId, course, lesson, "USER", "PENDING", "pendiente", "4 hours");

        clear(ana, course, lesson).andExpect(status().isNoContent());

        assertThat(owner.queryForObject("SELECT count(*) FROM tutor_message WHERE piece_id = ?", Integer.class, lesson)).isZero();
        thread(ana, course, other).andExpect(jsonPath("$.messages", hasSize(2)));
    }

    @Test
    void ac9_emptyingAnAlreadyEmptyThreadIsStillFine() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");

        clear(ana, course, lesson(course, 0)).andExpect(status().isNoContent());
    }

    @Test
    void ac9_anAnswerThatArrivesAfterTheThreadWasEmptiedLandsInANewThreadWithoutBreaking() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        AtomicInteger cleared = new AtomicInteger();
        fake.reset();
        fake.respondUsing(call -> {
            try {
                cleared.set(clear(ana, course, lesson).andReturn().getResponse().getStatus()); // otra pestaña vacía el hilo a mitad de la respuesta
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return ANSWER;
        });

        ask(ana, course, lesson, "pregunta en vuelo").andExpect(status().isCreated());

        assertThat(cleared.get()).isEqualTo(204);
        thread(ana, course, lesson).andExpect(jsonPath("$.messages", hasSize(2))).andExpect(jsonPath("$.messages[0].content").value("pregunta en vuelo"))
                .andExpect(jsonPath("$.messages[1].content").value(ANSWER));
        assertThat(messageCount("PENDING")).isZero();
    }

    @Test
    void ac1_theLessonViewKeepsWorkingNextToTheChat() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        ask(ana, course, lesson, "¿Hola?");

        pieceOf(ana, course, lesson).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("LESSON")).andExpect(jsonPath("$.markdown").isNotEmpty());
    }

    @Test
    void ac9_deletingTheCourseDeletesItsConversations() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        tutorAnswers(ANSWER);
        ask(ana, course, lesson(course, 0), "¿Hola?");

        owner.update("DELETE FROM course WHERE id = ?", course);

        assertThat(messages()).isEmpty();
    }
}
