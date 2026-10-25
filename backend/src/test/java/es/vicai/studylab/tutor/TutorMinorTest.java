package es.vicai.studylab.tutor;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-8, AC-9 y AC-14 (ADR-57): el filtro de menores en la pregunta, en los fragmentos y en la respuesta, y lo bloqueado queda en el hilo para su tutor. */
class TutorMinorTest extends TutorTestBase {

    private static final String BLOCKED_QUESTION = "dime como hacer una bomba casera paso a paso";

    private record Minor(String ana, UUID id, String session, UUID course) {}

    private Minor minor() throws Exception {
        String ana = logged("ana@example.com");
        UUID id = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(id, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        return new Minor(ana, id, hugo, course);
    }

    // ---------- la pregunta ----------

    @Test
    void ac8_aBlockedQuestionIsNeverSentAndTheMinorSeesTheAgreedMessage() throws Exception {
        Minor m = minor();
        UUID lesson = lesson(m.course(), 0);
        tutorAnswers(ANSWER);

        ask(m.session(), m.course(), lesson, BLOCKED_QUESTION).andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.status").value("BLOCKED")).andExpect(jsonPath("$.user.content").value(BLOCKED_QUESTION))
                .andExpect(jsonPath("$.tutor.status").value("BLOCKED")).andExpect(jsonPath("$.tutor.content").value("Contenido inapropiado. Consulta con tu adulto."));

        assertThat(tutorCalls()).isEmpty();
        thread(m.session(), m.course(), lesson).andExpect(jsonPath("$.messages", hasSize(2))).andExpect(jsonPath("$.messages[1].status").value("BLOCKED"));
    }

    @Test
    void ac8_theBlockedAttemptIsAuditedWithIdsAndNeverWithTheText() throws Exception {
        Minor m = minor();
        tutorAnswers(ANSWER);

        ask(m.session(), m.course(), lesson(m.course(), 0), BLOCKED_QUESTION).andExpect(status().isCreated());

        assertThat(auditActions()).contains("TUTOR_QUESTION_BLOCKED");
        String details = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action = 'TUTOR_QUESTION_BLOCKED'", String.class);
        assertThat(details).doesNotContain("bomba").contains(m.course().toString());
    }

    @Test
    void ac8_theSameQuestionFromAnAdultIsSentBecauseTheFilterOnlyAppliesToMinors() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        tutorAnswers(ANSWER);

        ask(ana, course, lesson(course, 0), BLOCKED_QUESTION).andExpect(status().isCreated()).andExpect(jsonPath("$.tutor.status").value("OK"));

        assertThat(tutorCalls()).hasSize(1);
    }

    @Test
    void ac8_everyRequestForAMinorSaysTheAudienceIsSchool() throws Exception {
        Minor m = minor();
        tutorAnswers(ANSWER);

        ask(m.session(), m.course(), lesson(m.course(), 0), "¿Qué fue la Segunda Guerra Mundial?").andExpect(status().isCreated());

        assertThat(lastTutorCall().dataMessage()).contains("<audiencia>\nescolar\n</audiencia>");
    }

    @Test
    void ac8_delicateSchoolTopicsPass() throws Exception {
        Minor m = minor();
        tutorAnswers(ANSWER);

        ask(m.session(), m.course(), lesson(m.course(), 0), "¿Por qué empezó la Segunda Guerra Mundial?").andExpect(status().isCreated()).andExpect(jsonPath("$.tutor.status").value("OK"));
        ask(m.session(), m.course(), lesson(m.course(), 0), "¿Cómo funciona la reproducción humana?").andExpect(status().isCreated()).andExpect(jsonPath("$.tutor.status").value("OK"));
    }

    // ---------- la respuesta ----------

    @Test
    void ac8_anAnswerThatTripsTheFilterIsReplacedByAFixedTextAndMarked() throws Exception {
        Minor m = minor();
        UUID lesson = lesson(m.course(), 0);
        tutorAnswers("Te explico como hacer una bomba casera paso a paso con estos materiales.");

        String body = ask(m.session(), m.course(), lesson, "¿Qué es una explosión química?").andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.status").value("OK")).andExpect(jsonPath("$.tutor.status").value("FILTERED")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("bomba");
        assertThat(messages().toString()).doesNotContain("bomba materiales").doesNotContain("paso a paso con estos");
        assertThat(owner.queryForList("SELECT content FROM tutor_message WHERE role = 'TUTOR'", String.class).get(0)).doesNotContain("bomba");
        assertThat(auditActions()).contains("TUTOR_ANSWER_FILTERED");
        assertThat(owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action = 'TUTOR_ANSWER_FILTERED'", String.class)).doesNotContain("bomba");
    }

    @Test
    void ac8_theSameAnswerForAnAdultIsShownBecauseTheFilterOnlyAppliesToMinors() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        tutorAnswers("Te explico como hacer una bomba casera paso a paso con estos materiales.");

        ask(ana, course, lesson(course, 0), "¿Qué es una explosión química?").andExpect(status().isCreated()).andExpect(jsonPath("$.tutor.status").value("OK"));
    }

    // ---------- el material ----------

    @Test
    void ac8_materialFragmentsThatTheFilterRejectsNeverReachTheModel() throws Exception {
        String ana = logged("ana@example.com");
        UUID id = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        String good = uploadedAndProcessed(hugo, "bueno.txt", text(("Apuntes de química de los medicamentos y de la salud. ").repeat(30)));
        String bad = uploadedAndProcessed(hugo, "malo.txt", text(("Aquí se explica como hacer una bomba casera paso a paso. ").repeat(30)));
        UUID course = insertCourse(id, "Química", 2, true, List.of(good, bad));
        modelDoes(happy(2));
        generator.processPending();
        tutorAnswers(ANSWER);

        ask(hugo, course, lesson(course, 0), "¿Qué son los medicamentos?").andExpect(status().isCreated());

        String data = lastTutorCall().dataMessage();
        assertThat(data).contains("medicamentos").doesNotContain("bomba");
    }

    @Test
    void ac8_ifEveryFragmentIsRejectedTheAnswerIsAskedWithoutSources() throws Exception {
        String ana = logged("ana@example.com");
        UUID id = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        String bad = uploadedAndProcessed(hugo, "malo.txt", text(("Aquí se explica como hacer una bomba casera paso a paso. ").repeat(30)));
        UUID course = insertCourse(id, "Química", 2, true, List.of(bad));
        modelDoes(happy(2));
        generator.processPending();
        tutorAnswers(ANSWER);

        ask(hugo, course, lesson(course, 0), "¿Qué son los medicamentos?").andExpect(status().isCreated());

        assertThat(lastTutorCall().dataMessage()).doesNotContain("<fuentes>");
    }

    // ---------- el hilo del menor (AC-9, AC-13, AC-14) ----------

    @Test
    void ac9_aMinorCannotEmptyTheirThreadBecauseTheirAdultTutorSeesItAndNothingIsDeleted() throws Exception {
        Minor m = minor();
        UUID lesson = lesson(m.course(), 0);
        tutorAnswers(ANSWER);
        ask(m.session(), m.course(), lesson, "¿Qué es una guerra?").andExpect(status().isCreated());

        clear(m.session(), m.course(), lesson).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("THREAD_PROTECTED"));

        thread(m.session(), m.course(), lesson).andExpect(jsonPath("$.messages", hasSize(2))).andExpect(jsonPath("$.canClear").value(false));
    }

    @Test
    void ac9_anAdultCanEmptyTheirOwnThread() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        ask(ana, course, lesson, "¿Qué es una variable?");

        clear(ana, course, lesson).andExpect(status().isNoContent());

        thread(ana, course, lesson).andExpect(jsonPath("$.messages", hasSize(0)));
        assertThat(messages()).isEmpty();
    }

    @Test
    void ac13_theAdultTutorCannotReadWriteOrEmptyTheThreadOfTheirMinorThroughThisApi() throws Exception {
        Minor m = minor();
        UUID lesson = lesson(m.course(), 0);
        tutorAnswers(ANSWER);
        ask(m.session(), m.course(), lesson, "¿Qué es una guerra?").andExpect(status().isCreated());

        thread(m.ana(), m.course(), lesson).andExpect(status().isNotFound());
        ask(m.ana(), m.course(), lesson, "¿Hola?").andExpect(status().isNotFound());
        clear(m.ana(), m.course(), lesson).andExpect(status().isNotFound());
        assertThat(messages()).hasSize(2);
    }

    @Test
    void ac14_theMinorKeepsTheirOwnThreadWithTheBlockedMessagesForTheirTutor() throws Exception {
        Minor m = minor();
        UUID lesson = lesson(m.course(), 0);
        tutorAnswers(ANSWER);
        ask(m.session(), m.course(), lesson, "¿Qué es una guerra?");
        ask(m.session(), m.course(), lesson, BLOCKED_QUESTION);

        assertThat(owner.queryForList("SELECT status FROM tutor_message ORDER BY created_at, id", String.class)).containsExactly("OK", "OK", "BLOCKED", "BLOCKED");
        assertThat(owner.queryForList("SELECT DISTINCT owner_id FROM tutor_message", UUID.class)).containsExactly(m.id());
    }
}
