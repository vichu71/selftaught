package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-5, AC-9, AC-11 y AC-12: la lista de conversaciones y cada hilo de un alumno, en solo lectura. */
class GuardianConversationsTest extends GuardianTestBase {

    private String thread(Family f, UUID lesson) throws Exception {
        return panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + lesson + "/tutor").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ---------- la lista ----------

    @Test
    void ac5_theListShowsOneEntryPerLessonWithItsCountsAndTitles() throws Exception {
        Family f = family();
        UUID first = lesson(f.course(), 0);
        UUID second = lesson(f.course(), 1);
        tutorAnswers(ANSWER);
        hugoAsks(f, first, "pregunta 1");
        hugoAsks(f, first, "pregunta 2");
        hugoAsks(f, first, BLOCKED_QUESTION);
        hugoAsks(f, second, "otra lección");

        panelGet(f.ana(), f.hugoId(), "/conversations").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.pieceId == '" + first + "')].messages").value(6)).andExpect(jsonPath("$[?(@.pieceId == '" + first + "')].blocked").value(1))
                .andExpect(jsonPath("$[?(@.pieceId == '" + first + "')].filtered").value(0)).andExpect(jsonPath("$[?(@.pieceId == '" + first + "')].courseTitle").value("Historia"))
                .andExpect(jsonPath("$[?(@.pieceId == '" + first + "')].lessonTitle").value("Capítulo 1.1")).andExpect(jsonPath("$[?(@.pieceId == '" + first + "')].courseId").value(f.course().toString()))
                .andExpect(jsonPath("$[?(@.pieceId == '" + second + "')].messages").value(2)).andExpect(jsonPath("$[0].lastAt").isNotEmpty());
    }

    @Test
    void ac5_theListIsNewestFirst() throws Exception {
        Family f = family();
        tutorAnswers(ANSWER);
        hugoAsks(f, lesson(f.course(), 0), "antes");
        owner.update("UPDATE tutor_message SET created_at = now() - interval '1 day'");
        hugoAsks(f, lesson(f.course(), 1), "después");

        panelGet(f.ana(), f.hugoId(), "/conversations").andExpect(jsonPath("$[0].pieceId").value(lesson(f.course(), 1).toString()));
    }

    @Test
    void ac5_aLessonWithOnlyFailedOrPendingMessagesIsNotListed() throws Exception {
        Family f = family();
        insertMessage(f.hugoId(), f.course(), lesson(f.course(), 0), "USER", "FAILED", "fallida", "10 minutes");
        insertMessage(f.hugoId(), f.course(), lesson(f.course(), 0), "USER", "PENDING", "pendiente", "5 minutes");

        panelGet(f.ana(), f.hugoId(), "/conversations").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ac5_theListOfOneStudentNeverIncludesTheConversationsOfAnother() throws Exception {
        Family f = family();
        UUID martaId = UUID.fromString(createStudent(f.ana(), "Marta", "marta"));
        UUID martasCourse = insertCourse(martaId, "Ciencias", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        insertMessage(martaId, martasCourse, lesson(martasCourse, 0), "USER", "OK", "de Marta", "1 minute");
        insertMessage(f.hugoId(), f.course(), lesson(f.course(), 0), "USER", "OK", "de Hugo", "1 minute");

        panelGet(f.ana(), f.hugoId(), "/conversations").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].courseTitle").value("Historia"));
    }

    // ---------- el hilo ----------

    @Test
    void ac5_theThreadShowsEveryVisibleMessageOldestFirstWithItsMarks() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        tutorAnswers("Te explico como hacer una bomba casera paso a paso con estos materiales.");
        hugoAsks(f, lesson, "¿Qué es una explosión química?"); // respuesta filtrada
        hugoAsks(f, lesson, BLOCKED_QUESTION); // pregunta bloqueada

        String thread = thread(f, lesson);

        assertThat(JsonPath.<List<String>>read(thread, "$.messages[*].status")).containsExactly("OK", "FILTERED", "BLOCKED", "BLOCKED");
        assertThat(JsonPath.<List<String>>read(thread, "$.messages[*].role")).containsExactly("USER", "TUTOR", "USER", "TUTOR");
        assertThat(JsonPath.<String>read(thread, "$.messages[0].content")).isEqualTo("¿Qué es una explosión química?");
        assertThat(JsonPath.<String>read(thread, "$.messages[2].content")).isEqualTo(BLOCKED_QUESTION); // el adulto ve lo que preguntó
        assertThat(thread).doesNotContain("canClear").doesNotContain("estos materiales");
    }

    @Test
    void ac5_theThreadNeverShowsPendingOrFailedMessages() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "PENDING", "PENDIENTE", "1 hour");
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "FAILED", "FALLIDA", "50 minutes");
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "OK", "VISIBLE", "40 minutes");

        String thread = thread(f, lesson);

        assertThat(thread).contains("VISIBLE").doesNotContain("PENDIENTE").doesNotContain("FALLIDA");
    }

    @Test
    void ac5_theSourcesOfAnAnswerAreShownLikeTheStudentSeesThem() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        owner.update("INSERT INTO tutor_message (id, owner_id, course_id, piece_id, role, status, content, sources) VALUES (gen_random_uuid(), ?, ?, ?, 'TUTOR', 'OK', 'según [1]', CAST('[{\"n\":1,\"page\":3}]' AS jsonb))",
                f.hugoId(), f.course(), lesson);

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + lesson + "/tutor").andExpect(jsonPath("$.messages[0].sources[0].n").value(1))
                .andExpect(jsonPath("$.messages[0].sources[0].page").value(3));
    }

    @Test
    void ac5_aLongThreadIsReturnedWholeUpToItsRetentionLimit() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        for (int i = 0; i < 400; i++) insertMessage(f.hugoId(), f.course(), lesson, i % 2 == 0 ? "USER" : "TUTOR", "OK", "mensaje " + i, (500 - i) + " minutes");

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + lesson + "/tutor").andExpect(jsonPath("$.messages", hasSize(400))).andExpect(jsonPath("$.messages[0].content").value("mensaje 0"));
    }

    @Test
    void ac5_theThreadOfALessonWithoutMessagesIsEmptyNotAnError() throws Exception {
        Family f = family();

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + lesson(f.course(), 0) + "/tutor").andExpect(status().isOk()).andExpect(jsonPath("$.messages", hasSize(0)));
    }

    @Test
    void ac5_onlyLessonsHaveAThread() throws Exception {
        Family f = family();

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + piece(f.course(), "EXERCISES", 0) + "/tutor").andExpect(status().isNotFound());
        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + piece(f.course(), "EXAM", 0) + "/tutor").andExpect(status().isNotFound());
        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + UUID.randomUUID() + "/tutor").andExpect(status().isNotFound());
    }

    @Test
    void ac5_theThreadSurvivesALessonThatLaterFails() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        tutorAnswers(ANSWER);
        hugoAsks(f, lesson, "una pregunta");
        owner.update("UPDATE course_piece SET status = 'FAILED' WHERE id = ?", lesson);

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + lesson + "/tutor").andExpect(jsonPath("$.messages", hasSize(2)));
    }

    // ---------- texto inerte y auditoría ----------

    @Test
    void ac11_hostileTextOfAConversationIsReturnedAsPlainJsonText() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        String hostile = "<img src=x onerror=alert(1)> [clic](javascript:alert(1))";
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "OK", hostile, "1 minute");

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + lesson + "/tutor").andExpect(jsonPath("$.messages[0].content").value(hostile))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void ac9_theListAndEachThreadAreAuditedWithIdsAndNeverTheirContent() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "OK", SECRET, "1 minute");

        panelGet(f.ana(), f.hugoId(), "/conversations").andExpect(status().isOk());
        thread(f, lesson);

        var views = guardianViews();
        assertThat(views).hasSize(2);
        assertThat((String) views.get(0).get("details")).contains("CONVERSATIONS").doesNotContain(SECRET);
        assertThat((String) views.get(1).get("details")).contains("THREAD").contains(f.course().toString()).contains(lesson.toString()).doesNotContain(SECRET);
    }
}
