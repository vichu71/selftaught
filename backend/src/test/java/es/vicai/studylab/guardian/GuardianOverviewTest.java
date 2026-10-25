package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-1, AC-2, AC-7, AC-12 y AC-13: el resumen de un alumno, su consumo de IA y que no se escape nada que no deba. */
class GuardianOverviewTest extends GuardianTestBase {

    private String body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    // ---------- cursos y avance (AC-2) ----------

    @Test
    void ac2_theOverviewListsTheStudentsCoursesWithTheSameProgressTheStudentSees() throws Exception {
        Family f = family();
        markDone(f.hugo(), f.course(), lesson(f.course(), 0), true).andExpect(status().isNoContent());
        examTaken(f.hugo(), f.course(), piece(f.course(), "EXAM", 0), ALL_RIGHT);

        String mine = body(studyOf(f.hugo(), f.course()).andExpect(status().isOk()));
        String panel = body(panelGet(f.ana(), f.hugoId(), "/overview").andExpect(status().isOk()));

        assertThat(JsonPath.<Integer>read(panel, "$.courses[0].percent")).isEqualTo(JsonPath.<Integer>read(mine, "$.percent"));
        assertThat(JsonPath.<Integer>read(panel, "$.courses[0].lessonsDone")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(panel, "$.courses[0].lessonsTotal")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(panel, "$.courses[0].examsPassed")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(panel, "$.courses[0].examsTotal")).isEqualTo(2);
        assertThat(JsonPath.<String>read(panel, "$.courses[0].title")).isEqualTo("Historia");
        assertThat(JsonPath.<String>read(panel, "$.courses[0].status")).isEqualTo("READY");
        assertThat(JsonPath.<String>read(panel, "$.courses[0].id")).isEqualTo(f.course().toString());
    }

    @Test
    void ac2_coursesAreListedNewestFirstAndADeletedCourseIsGone() throws Exception {
        Family f = family();
        UUID second = insertCourse(f.hugoId(), "Geografía", 1, false, java.util.List.of());
        owner.update("UPDATE course SET created_at = now() + interval '1 minute' WHERE id = ?", second);

        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.courses", hasSize(2))).andExpect(jsonPath("$.courses[0].title").value("Geografía"));

        owner.update("DELETE FROM course WHERE id = ?", second);
        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.courses", hasSize(1)));
    }

    @Test
    void ac2_aCourseStillBeingGeneratedAppearsWithoutBreakingTheOverview() throws Exception {
        Family f = family();
        insertCourse(f.hugoId(), "Pendiente", 2, true, java.util.List.of());

        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(status().isOk()).andExpect(jsonPath("$.courses", hasSize(2)));
    }

    @Test
    void ac2_theLastActivityIsTheMostRecentThingTheStudentDid() throws Exception {
        Family f = family();
        tutorAnswers(ANSWER);
        hugoAsks(f, lesson(f.course(), 0), "una pregunta");
        owner.update("UPDATE tutor_message SET created_at = now() - interval '3 days'");
        String attempt = answered(f.hugo(), f.course(), piece(f.course(), "EXERCISES", 0), 0, "hecho ahora");
        owner.update("UPDATE exercise_attempt SET created_at = now() - interval '1 hour' WHERE id = ?::uuid", attempt);

        String panel = body(panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.lastActivityAt").exists()));

        java.time.Instant last = java.time.Instant.parse(JsonPath.read(panel, "$.lastActivityAt"));
        assertThat(last).isBetween(java.time.Instant.now().minusSeconds(2 * 3600), java.time.Instant.now().minusSeconds(30 * 60));
    }

    // ---------- alumno sin actividad (AC-12) ----------

    @Test
    void ac12_aStudentWithNoCoursesOrActivityGetsAnEmptyOverviewNotAnError() throws Exception {
        String ana = logged("ana@example.com");
        UUID id = UUID.fromString(createStudent(ana, "Nuevo", "nuevo"));

        String panel = body(panelGet(ana, id, "/overview").andExpect(status().isOk()).andExpect(jsonPath("$.student.displayName").value("Nuevo"))
                .andExpect(jsonPath("$.student.active").value(true)).andExpect(jsonPath("$.courses", hasSize(0))).andExpect(jsonPath("$.lastActivityAt").doesNotExist())
                .andExpect(jsonPath("$.alerts.last30Days").value(0)).andExpect(jsonPath("$.usage.byTask", hasSize(0))));
        assertThat(JsonPath.<Number>read(panel, "$.usage.spendUsd").doubleValue()).isZero();
        panelGet(ana, id, "/conversations").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        panelGet(ana, id, "/alerts").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- avisos del último mes ----------

    @Test
    void ac6_theOverviewCountsTheAlertsOfTheLastThirtyDaysOnly() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "BLOCKED", "bloqueada reciente", "2 days");
        insertMessage(f.hugoId(), f.course(), lesson, "TUTOR", "BLOCKED", "Contenido inapropiado. Consulta con tu adulto.", "2 days");
        insertMessage(f.hugoId(), f.course(), lesson, "USER", "BLOCKED", "bloqueada antigua", "40 days");

        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.alerts.last30Days").value(1));
    }

    // ---------- consumo de IA (AC-7) ----------

    @Test
    void ac7_theMonthSpendIsShownAgainstTheStudentsLimitAndSplitByTask() throws Exception {
        Family f = family();
        double before = spendOf(f.hugoId());
        usageRow(f.hugoId(), "OUTLINE", "0.10", "1 minute");
        usageRow(f.hugoId(), "LESSON", "0.30", "1 minute");
        usageRow(f.hugoId(), "TUTOR", "0.05", "1 minute");

        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.usage.month").isNotEmpty())
                .andExpect(jsonPath("$.usage.spendUsd", closeTo(before + 0.45, 0.0001))).andExpect(jsonPath("$.usage.limitUsd").value(1.0))
                .andExpect(jsonPath("$.usage.reached").value(false)).andExpect(jsonPath("$.usage.byTask[?(@.task == 'TUTOR')].spendUsd", org.hamcrest.Matchers.hasItem(closeTo(0.05, 0.0001))))
                .andExpect(jsonPath("$.usage.byTask[?(@.task == 'LESSON')].spendUsd", org.hamcrest.Matchers.hasItem(greaterThanOrEqualTo(0.30))))
                .andExpect(jsonPath("$.usage.byTask[?(@.task == 'TUTOR')].calls", org.hamcrest.Matchers.hasItem(1)));
    }

    @Test
    void ac7_reachingTheStudentsLimitIsMarked() throws Exception {
        Family f = family();
        usageRow(f.hugoId(), "LESSON", "1.50", "1 minute");

        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.usage.reached").value(true));
    }

    @Test
    void ac7_spendOfOtherMonthsAndOfOtherUsersIsNotCounted() throws Exception {
        Family f = family();
        UUID martaId = UUID.fromString(createStudent(f.ana(), "Marta", "marta"));
        double before = spendOf(f.hugoId());
        usageRow(f.hugoId(), "LESSON", "0.90", "70 days");
        usageRow(martaId, "LESSON", "0.90", "1 minute");

        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.usage.spendUsd", closeTo(before, 0.0001)));
    }

    @Test
    void ac7_neitherTokensNorModelsAreShown() throws Exception {
        Family f = family();
        usageRow(f.hugoId(), "LESSON", "0.10", "1 minute");

        String panel = body(panelGet(f.ana(), f.hugoId(), "/overview").andExpect(status().isOk()));

        assertThat(panel).doesNotContain("fake-1").doesNotContain("input_tokens").doesNotContain("inputTokens").doesNotContain("tokens").doesNotContain("model");
    }

    // ---------- sin fugas (AC-13) ----------

    @Test
    void ac13_theOverviewNeverCarriesTheStudentsLoginEmailPasswordOrAnyOtherUserId() throws Exception {
        Family f = family();
        String loginId = loginIdOf(f.hugoId().toString());

        String panel = body(panelGet(f.ana(), f.hugoId(), "/overview").andExpect(status().isOk()));

        assertThat(panel).doesNotContain(loginId).doesNotContain("email").doesNotContain("password").doesNotContain("hash")
                .doesNotContain(f.anaId().toString()).doesNotContain(f.luisId().toString()).doesNotContain("ana@example.com");
    }
}
