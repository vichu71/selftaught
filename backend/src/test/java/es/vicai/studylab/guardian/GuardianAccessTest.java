package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** AC-1 y AC-8: solo el adulto vinculado accede, ajeno e inexistente responden igual, y el panel no modifica nada. */
class GuardianAccessTest extends GuardianTestBase {

    // ---------- sesión y rol ----------

    @Test
    void ac8_everyEndpointRequiresASession() throws Exception {
        Family f = family();

        for (String path : panelPaths(f.hugoId(), f.course(), lesson(f.course(), 0))) mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void ac8_aMinorCannotUseThePanelNotEvenForThemselves() throws Exception {
        Family f = family();

        for (String path : panelPaths(f.hugoId(), f.course(), lesson(f.course(), 0))) getAs(f.hugo(), path).andExpect(status().isForbidden());
    }

    // ---------- el vínculo ----------

    @Test
    void ac8_anotherAdultGetsA404OnEveryEndpointAndLeavesTrace() throws Exception {
        Family f = family();

        for (String path : panelPaths(f.hugoId(), f.course(), lesson(f.course(), 0))) getAs(f.luis(), path).andExpect(status().isNotFound());

        assertThat(auditActions()).contains("ACCESS_DENIED");
        assertThat(guardianViews()).isEmpty();
    }

    @Test
    void ac8_aStudentOfAnotherAdultAnswersExactlyLikeANonexistentOne() throws Exception {
        Family f = family();

        String foreign = panelGet(f.luis(), f.hugoId(), "/overview").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = panelGet(f.luis(), UUID.randomUUID(), "/overview").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
    }

    @Test
    void ac8_anInvalidIdentifierIsNotFoundNeverAnError() throws Exception {
        Family f = family();

        getAs(f.ana(), "/api/tutor/students/no-es-un-uuid/overview").andExpect(status().isNotFound());
        getAs(f.ana(), panel(f.hugoId(), "/courses/no-es-un-uuid")).andExpect(status().isNotFound());
        getAs(f.ana(), panel(f.hugoId(), "/courses/" + f.course() + "/pieces/otra/tutor")).andExpect(status().isNotFound());
    }

    @Test
    void ac8_theAdultCannotUseThePanelOnThemselvesOrOnAnotherAdult() throws Exception {
        Family f = family();

        panelGet(f.ana(), f.anaId(), "/overview").andExpect(status().isNotFound());
        panelGet(f.ana(), f.luisId(), "/overview").andExpect(status().isNotFound());
    }

    // ---------- lo que es de otro alumno ----------

    @Test
    void ac8_aCourseOrLessonOfAnotherStudentOfTheSameAdultIsNotFound() throws Exception {
        Family f = family();
        UUID martaId = UUID.fromString(createStudent(f.ana(), "Marta", "marta"));
        UUID martasCourse = insertCourse(martaId, "Ciencias", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        UUID martasLesson = lesson(martasCourse, 0);

        panelGet(f.ana(), f.hugoId(), "/courses/" + martasCourse).andExpect(status().isNotFound());
        panelGet(f.ana(), f.hugoId(), "/courses/" + martasCourse + "/attempts").andExpect(status().isNotFound());
        panelGet(f.ana(), f.hugoId(), "/courses/" + martasCourse + "/pieces/" + martasLesson + "/tutor").andExpect(status().isNotFound());
        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/pieces/" + martasLesson + "/tutor").andExpect(status().isNotFound());
        panelGet(f.ana(), martaId, "/courses/" + f.course()).andExpect(status().isNotFound());
    }

    @Test
    void ac8_theOverviewOfOneStudentNeverIncludesTheCoursesOfAnother() throws Exception {
        Family f = family();
        UUID martaId = UUID.fromString(createStudent(f.ana(), "Marta", "marta"));
        insertCourse(martaId, "Ciencias", 2, true, List.of());

        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.courses", hasSize(1))).andExpect(jsonPath("$.courses[0].title").value("Historia"));
        panelGet(f.ana(), martaId, "/overview").andExpect(jsonPath("$.courses", hasSize(1))).andExpect(jsonPath("$.courses[0].title").value("Ciencias"));
    }

    // ---------- alumnos desactivados ----------

    @Test
    void ac1_aDeactivatedStudentCanStillBeConsultedInReadOnlyMode() throws Exception {
        Family f = family();
        postAs(f.ana(), "/api/tutor/students/" + f.hugoId() + "/deactivate", null).andExpect(status().isNoContent());

        for (String path : panelPaths(f.hugoId(), f.course(), lesson(f.course(), 0))) getAs(f.ana(), path).andExpect(status().isOk());
        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.student.active").value(false));
    }

    // ---------- solo lectura ----------

    @Test
    void ac8_noMethodThatWritesIsAcceptedOnAnyPanelPathAndNothingChanges() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        tutorAnswers(ANSWER);
        hugoAsks(f, lesson, "una pregunta del alumno");
        int before = writableRows();

        for (String path : panelPaths(f.hugoId(), f.course(), lesson)) {
            for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, path).with(csrf()).cookie(new Cookie(SESSION_COOKIE, f.ana())).contentType("application/json").content("{}"))
                        .andExpect(status().is4xxClientError());
            }
        }

        assertThat(writableRows()).isEqualTo(before);
        assertThat(messageCount("OK")).isEqualTo(2); // el hilo del alumno sigue intacto
    }

    @Test
    void ac8_theMinorStillCannotEmptyTheirThreadAndTheAdultStillCannotThroughTheirOwnEndpoint() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);
        tutorAnswers(ANSWER);
        hugoAsks(f, lesson, "una pregunta");

        clear(f.hugo(), f.course(), lesson).andExpect(status().isForbidden());
        clear(f.ana(), f.course(), lesson).andExpect(status().isNotFound());

        assertThat(messages()).hasSize(2);
    }
}
