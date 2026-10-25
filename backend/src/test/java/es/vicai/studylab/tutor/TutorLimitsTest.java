package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-10 (ADR-55): límites por usuario en la BD y una sola respuesta a la vez. Contexto propio: cambia la configuración. */
@TestPropertySource(properties = {"app.tutor.max-per-hour=3", "app.tutor.max-per-day=5", "app.tutor.max-stored=4"})
class TutorLimitsTest extends TutorTestBase {

    @Test
    void ac10_theFourthMessageOfTheHourIsRefusedWithoutCallingTheModelAndLeavesNoTrace() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        for (int i = 0; i < 3; i++) ask(ana, course, lesson, "pregunta " + i).andExpect(status().isCreated());
        int rows = messages().size();
        int calls = tutorCalls().size();

        ask(ana, course, lesson, "una de más").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TUTOR_RATE_LIMITED"));

        assertThat(tutorCalls()).hasSize(calls);
        assertThat(messages()).hasSize(rows);
    }

    @Test
    void ac10_blockedAndFailedAttemptsCountToo() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        insertMessage(anaId, course, lesson, "USER", "FAILED", "fallida", "10 minutes");
        insertMessage(anaId, course, lesson, "USER", "BLOCKED", "bloqueada", "9 minutes");
        tutorAnswers(ANSWER);
        ask(ana, course, lesson, "buena").andExpect(status().isCreated());

        ask(ana, course, lesson, "otra").andExpect(status().isTooManyRequests());
    }

    @Test
    void ac10_theTutorAnswersThemselvesDoNotCountAsMessages() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);

        for (int i = 0; i < 3; i++) ask(ana, course, lesson, "pregunta " + i).andExpect(status().isCreated()); // 3 preguntas y 3 respuestas: solo cuentan las preguntas
    }

    @Test
    void ac10_messagesOlderThanAnHourNoLongerCountForTheHourButStillForTheDay() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        for (int i = 0; i < 3; i++) insertMessage(anaId, course, lesson, "USER", "OK", "vieja " + i, "2 hours");
        tutorAnswers(ANSWER);

        ask(ana, course, lesson, "ya puedo").andExpect(status().isCreated());
        ask(ana, course, lesson, "otra").andExpect(status().isCreated());
        // 3 antiguas + 2 de ahora = 5: el límite del día

        ask(ana, course, lesson, "la sexta del día").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TUTOR_RATE_LIMITED"));
    }

    @Test
    void ac10_messagesOlderThanADayNoLongerCount() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        for (int i = 0; i < 5; i++) insertMessage(anaId, course, lesson, "USER", "OK", "de hace dos días " + i, "2 days");
        tutorAnswers(ANSWER);

        ask(ana, course, lesson, "hoy").andExpect(status().isCreated());
    }

    @Test
    void ac10_otherUsersAreNotAffected() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID anaCourse = readyCourse("ana@example.com");
        UUID luisCourse = readyCourse("luis@example.com");
        tutorAnswers(ANSWER);
        for (int i = 0; i < 3; i++) ask(ana, anaCourse, lesson(anaCourse, 0), "pregunta " + i);

        ask(ana, anaCourse, lesson(anaCourse, 0), "una más").andExpect(status().isTooManyRequests());
        ask(luis, luisCourse, lesson(luisCourse, 0), "mi primera").andExpect(status().isCreated());
    }

    // ---------- una a la vez ----------

    @Test
    void ac10_aSecondQuestionWhileAnAnswerIsInProgressIsRefusedAndTheFirstStillFinishes() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        AtomicInteger nested = new AtomicInteger();
        fake.reset();
        fake.respondUsing(call -> {
            try {
                nested.set(ask(ana, course, lesson, "mientras tanto").andReturn().getResponse().getStatus());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return ANSWER;
        });

        ask(ana, course, lesson, "la primera").andExpect(status().isCreated());

        assertThat(nested.get()).isEqualTo(429);
        assertThat(messages()).hasSize(2); // solo el intercambio de la primera
        ask(ana, course, lesson, "ahora sí").andExpect(status().isCreated()); // y la reserva se liberó
    }

    @Test
    void ac10_theBusyReasonIsDistinguishedFromTheRateLimit() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        insertMessage(anaId, course, lesson, "USER", "PENDING", "en marcha desde otra instancia", "10 seconds");
        tutorAnswers(ANSWER);

        ask(ana, course, lesson, "otra").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TUTOR_BUSY"));

        assertThat(tutorCalls()).isEmpty();
    }

    @Test
    void ac10_aPendingMessageAbandonedForMoreThanTwoMinutesNoLongerBlocks() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        insertMessage(anaId, course, lesson, "USER", "PENDING", "la instancia se cayó", "3 minutes");
        tutorAnswers(ANSWER);

        ask(ana, course, lesson, "ahora").andExpect(status().isCreated());
        thread(ana, course, lesson).andExpect(jsonPath("$.messages[0].content").value("ahora")); // el pendiente abandonado nunca se ve
    }

    @Test
    void ac10_aPendingMessageOfAnotherUserNeverBlocksMe() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID luisId = idOf("luis@example.com");
        UUID anaCourse = readyCourse("ana@example.com");
        UUID luisCourse = readyCourse("luis@example.com");
        insertMessage(luisId, luisCourse, lesson(luisCourse, 0), "USER", "PENDING", "de Luis", "5 seconds");
        tutorAnswers(ANSWER);

        ask(ana, anaCourse, lesson(anaCourse, 0), "mía").andExpect(status().isCreated());
        assertThat(luis).isNotNull();
    }

    // ---------- retención ----------

    @Test
    void ac9_onlyTheLatestMessagesOfALessonAreKeptAndOtherLessonsAreUntouched() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        UUID other = lesson(course, 1);
        insertMessage(anaId, course, other, "USER", "OK", "de otra lección 1", "5 hours");
        insertMessage(anaId, course, other, "TUTOR", "OK", "de otra lección 2", "5 hours");
        insertMessage(anaId, course, other, "USER", "OK", "de otra lección 3", "5 hours");
        insertMessage(anaId, course, other, "TUTOR", "OK", "de otra lección 4", "5 hours");
        insertMessage(anaId, course, other, "USER", "OK", "de otra lección 5", "5 hours");
        tutorAnswers(ANSWER);
        for (int i = 1; i <= 3; i++) ask(ana, course, lesson, "pregunta " + i).andExpect(status().isCreated());

        assertThat(owner.queryForObject("SELECT count(*) FROM tutor_message WHERE piece_id = ?", Integer.class, lesson)).isEqualTo(4); // 3 intercambios = 6 → quedan 4
        assertThat(owner.queryForObject("SELECT count(*) FROM tutor_message WHERE piece_id = ?", Integer.class, other)).isEqualTo(5); // las otras lecciones no se tocan
        thread(ana, course, lesson).andExpect(jsonPath("$.messages[3].role").value("TUTOR"));
    }
}
