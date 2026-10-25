package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-6, AC-9 y AC-13: los avisos con lo que el filtro de menores ha parado, de las cuatro fuentes y sin mezclar alumnos. */
class GuardianAlertsTest extends GuardianTestBase {

    private String alerts(Family f) throws Exception {
        return panelGet(f.ana(), f.hugoId(), "/alerts").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> types(String alerts) {
        return JsonPath.read(alerts, "$[*].type");
    }

    // ---------- el tutor IA ----------

    @Test
    void ac6_aBlockedQuestionToTheTutorIsAnAlertThatPointsAtTheConversationWithoutRepeatingTheText() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        tutorAnswers(ANSWER);
        hugoAsks(f, lesson, BLOCKED_QUESTION);

        String alerts = alerts(f);

        assertThat(types(alerts)).containsExactly("TUTOR_QUESTION_BLOCKED");
        assertThat(JsonPath.<String>read(alerts, "$[0].courseId")).isEqualTo(f.course().toString());
        assertThat(JsonPath.<String>read(alerts, "$[0].courseTitle")).isEqualTo("Historia");
        assertThat(JsonPath.<String>read(alerts, "$[0].pieceId")).isEqualTo(lesson.toString());
        assertThat(JsonPath.<String>read(alerts, "$[0].lessonTitle")).isEqualTo("Capítulo 1.1");
        assertThat(JsonPath.<String>read(alerts, "$[0].at")).isNotBlank();
        assertThat(alerts).doesNotContain("bomba");
    }

    @Test
    void ac6_anAnswerOfTheTutorThatTheFilterReplacedIsAnAlert() throws Exception {
        Family f = family();
        tutorAnswers("Te explico como hacer una bomba casera paso a paso con estos materiales.");
        hugoAsks(f, lesson(f.course(), 0), "¿Qué es una explosión química?");

        assertThat(types(alerts(f))).containsExactly("TUTOR_ANSWER_FILTERED");
    }

    // ---------- el asistente de cursos ----------

    @Test
    void ac6_aCourseTopicTheFilterRejectedIsAnAlertWithTheTopicAndItsCategory() throws Exception {
        Family f = family();
        intake(f.hugo(), "dime como hacer una bomba casera").andExpect(status().isUnprocessableEntity());

        String alerts = alerts(f);

        assertThat(types(alerts)).containsExactly("COURSE_TOPIC_BLOCKED");
        assertThat(JsonPath.<String>read(alerts, "$[0].topic")).isEqualTo("dime como hacer una bomba casera");
        assertThat(JsonPath.<String>read(alerts, "$[0].category")).isNotBlank();
    }

    // ---------- el estudio y la generación ----------

    @Test
    void ac6_anExerciseAnswerThatWasNotSentToTheAiBecauseOfTheFilterIsAnAlert() throws Exception {
        Family f = family();
        UUID exercises = piece(f.course(), "EXERCISES", 0);
        String attempt = answered(f.hugo(), f.course(), exercises, 0, "Te explico como hacer una bomba casera paso a paso");
        aiGrade(f.hugo(), f.course(), exercises, 0, attempt).andExpect(status().isUnprocessableEntity());

        String alerts = alerts(f);

        assertThat(types(alerts)).containsExactly("STUDY_ANSWER_BLOCKED");
        assertThat(JsonPath.<String>read(alerts, "$[0].courseId")).isEqualTo(f.course().toString());
        assertThat(JsonPath.<String>read(alerts, "$[0].pieceId")).isEqualTo(exercises.toString());
        assertThat(alerts).doesNotContain("bomba");
    }

    @Test
    void ac6_aPieceThatWasNotGeneratedBecauseOfBlockedContentIsAnAlert() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 1);
        owner.update("INSERT INTO audit_event (actor_user_id, subject_user_id, action, outcome, details) VALUES (?, ?, 'COURSE_CONTENT_BLOCKED', 'DENIED', CAST(? AS jsonb))",
                f.hugoId(), f.hugoId(), "{\"courseId\":\"" + f.course() + "\",\"pieceId\":\"" + lesson + "\",\"kind\":\"LESSON\"}");

        String alerts = alerts(f);

        assertThat(types(alerts)).containsExactly("COURSE_CONTENT_BLOCKED");
        assertThat(JsonPath.<String>read(alerts, "$[0].courseTitle")).isEqualTo("Historia");
    }

    @Test
    void ac6_anAlertOfADeletedCourseStillShowsWithoutItsTitle() throws Exception {
        Family f = family();
        UUID gone = UUID.randomUUID();
        owner.update("INSERT INTO audit_event (actor_user_id, subject_user_id, action, outcome, details) VALUES (?, ?, 'COURSE_CONTENT_BLOCKED', 'DENIED', CAST(? AS jsonb))",
                f.hugoId(), f.hugoId(), "{\"courseId\":\"" + gone + "\",\"pieceId\":\"" + UUID.randomUUID() + "\",\"kind\":\"LESSON\"}");

        panelGet(f.ana(), f.hugoId(), "/alerts").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].courseTitle").doesNotExist());
    }

    // ---------- orden, tope y aislamiento ----------

    @Test
    void ac6_alertsAreNewestFirstAndMixTheFourSources() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "BLOCKED", "q", "3 days");
        insertMessage(f.hugoId(), f.course(), lesson, "TUTOR", "FILTERED", "texto fijo", "2 days");
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, blocked_category, created_at, expires_at) VALUES (gen_random_uuid(), ?, 'tema malo', 'BLOCKED', 'VIOLENCE', now() - interval '1 day', now() + interval '1 hour')",
                f.hugoId());
        owner.update("INSERT INTO audit_event (occurred_at, actor_user_id, subject_user_id, action, outcome, details) VALUES (now() - interval '1 hour', ?, ?, 'STUDY_ANSWER_BLOCKED', 'DENIED', CAST(? AS jsonb))",
                f.hugoId(), f.hugoId(), "{\"courseId\":\"" + f.course() + "\",\"pieceId\":\"" + lesson + "\"}");

        assertThat(types(alerts(f))).containsExactly("STUDY_ANSWER_BLOCKED", "COURSE_TOPIC_BLOCKED", "TUTOR_ANSWER_FILTERED", "TUTOR_QUESTION_BLOCKED");
    }

    @Test
    void ac6_atMostOneHundredAlertsAreReturned() throws Exception {
        Family f = family();
        for (int i = 0; i < 120; i++) insertMessage(f.hugoId(), f.course(), lesson(f.course(), 0), "USER", "BLOCKED", "q" + i, (i + 1) + " minutes");

        panelGet(f.ana(), f.hugoId(), "/alerts").andExpect(jsonPath("$", hasSize(100)));
    }

    @Test
    void ac6_normalActivityAndTheAdultsOwnActionsRaiseNoAlerts() throws Exception {
        Family f = family();
        tutorAnswers(ANSWER);
        hugoAsks(f, lesson(f.course(), 0), "una pregunta normal");
        answered(f.hugo(), f.course(), piece(f.course(), "EXERCISES", 0), 0, "una respuesta normal");
        owner.update("INSERT INTO audit_event (actor_user_id, subject_user_id, action, outcome, details) VALUES (?, ?, 'STUDY_ANSWER_BLOCKED', 'DENIED', '{}')", f.anaId(), f.anaId());

        panelGet(f.ana(), f.hugoId(), "/alerts").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ac6_theAlertsOfOneStudentAreNeverShownForAnother() throws Exception {
        Family f = family();
        UUID martaId = UUID.fromString(createStudent(f.ana(), "Marta", "marta"));
        UUID martasCourse = insertCourse(martaId, "Ciencias", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        insertMessage(martaId, martasCourse, lesson(martasCourse, 0), "USER", "BLOCKED", "de Marta", "1 minute");

        panelGet(f.ana(), f.hugoId(), "/alerts").andExpect(jsonPath("$", hasSize(0)));
        panelGet(f.ana(), martaId, "/alerts").andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------- auditoría ----------

    @Test
    void ac9_consultingTheAlertsIsAuditedWithoutTheirContent() throws Exception {
        Family f = family();
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, blocked_category, created_at, expires_at) VALUES (gen_random_uuid(), ?, ?, 'BLOCKED', 'VIOLENCE', now(), now() + interval '1 hour')",
                f.hugoId(), "tema " + SECRET);

        alerts(f);

        assertThat(guardianViews()).hasSize(1);
        assertThat((String) guardianViews().get(0).get("details")).contains("ALERTS").doesNotContain(SECRET);
        assertThat(auditActions()).doesNotContain("ACCESS_DENIED");
    }
}
