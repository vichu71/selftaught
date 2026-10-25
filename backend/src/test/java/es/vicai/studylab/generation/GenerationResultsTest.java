package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Pantalla de resultados: por curso, piezas hechas, falladas y pendientes y su coste; solo lo propio y sin importes para un menor. */
class GenerationResultsTest extends GenerationTestBase {

    private ResultActions results(String session) throws Exception {
        return mvc.perform(get("/api/results").cookie(new Cookie(SESSION_COOKIE, session)));
    }

    private UUID generated(String email, String topic, int topics) throws Exception {
        UUID course = insertCourse(idOf(email), topic, topics, true, List.of());
        modelDoes(happy(topics));
        generator.processPending();
        return course;
    }

    @Test
    void itRequiresASession() throws Exception {
        mvc.perform(get("/api/results")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdultSeesEachCourseWithItsPiecesAndItsCost() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", "Inglés básico", 2);

        results(ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(1)))
                .andExpect(jsonPath("$.courses[0].id").value(course.toString()))
                .andExpect(jsonPath("$.courses[0].title").value("Inglés básico"))
                .andExpect(jsonPath("$.courses[0].status").value("READY"))
                .andExpect(jsonPath("$.courses[0].done").value(10))
                .andExpect(jsonPath("$.courses[0].failed").value(0))
                .andExpect(jsonPath("$.courses[0].pending").value(0))
                .andExpect(jsonPath("$.courses[0].total").value(10))
                .andExpect(jsonPath("$.courses[0].calls").value(11));
    }

    @Test
    void theCostOfACourseIsTheSumOfItsCallsAndTheTotalIsTheSumOfTheCourses() throws Exception {
        String ana = logged("ana@example.com");
        UUID first = generated("ana@example.com", "Primero", 2);
        UUID second = generated("ana@example.com", "Segundo", 2);
        double a = owner.queryForObject("SELECT sum(cost_usd) FROM ai_usage WHERE course_id = ?", Double.class, first);
        double b = owner.queryForObject("SELECT sum(cost_usd) FROM ai_usage WHERE course_id = ?", Double.class, second);

        String body = results(ana).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<java.util.List<Number>>read(body, "$.courses[?(@.title == 'Primero')].costUsd").get(0).doubleValue()).isEqualTo(a);
        assertThat(JsonPath.<java.util.List<Number>>read(body, "$.courses[?(@.title == 'Segundo')].costUsd").get(0).doubleValue()).isEqualTo(b);
        assertThat(JsonPath.<Number>read(body, "$.totalCostUsd").doubleValue()).isEqualTo(a + b, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void failedAndPendingPiecesAreCountedAndThePauseReasonIsShown() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), "Con fallos", 2, true, List.of());
        modelDoes(call -> "examen".equals(piece(call)) ? "no es json" : happy(2).apply(call));
        generator.processPending();

        results(ana).andExpect(jsonPath("$.courses[0].status").value("PAUSED")).andExpect(jsonPath("$.courses[0].stopReason").value("CONTENT"))
                .andExpect(jsonPath("$.courses[0].failed").value(2)).andExpect(jsonPath("$.courses[0].done").value(8)).andExpect(jsonPath("$.courses[0].total").value(10));
        assertThat(course).isNotNull();
    }

    @Test
    void aCourseWithoutOutlineYetHasZeroPieces() throws Exception {
        String ana = logged("ana@example.com");
        insertCourse(idOf("ana@example.com"), 2, true);

        results(ana).andExpect(jsonPath("$.courses[0].status").value("PENDING_GENERATION")).andExpect(jsonPath("$.courses[0].total").value(0))
                .andExpect(jsonPath("$.courses[0].calls").value(0));
    }

    @Test
    void theNewestCourseComesFirst() throws Exception {
        String ana = logged("ana@example.com");
        insertCourse(idOf("ana@example.com"), "Antiguo", 2, true, List.of());
        owner.update("UPDATE course SET created_at = now() - interval '2 days' WHERE title = 'Antiguo'");
        insertCourse(idOf("ana@example.com"), "Reciente", 2, true, List.of());

        results(ana).andExpect(jsonPath("$.courses[0].title").value("Reciente")).andExpect(jsonPath("$.courses[1].title").value("Antiguo"));
    }

    @Test
    void youNeverSeeAnotherUsersCoursesNorAMinorsEvenIfYouAreTheTutor() throws Exception {
        String ana = logged("ana@example.com");
        logged("luis@example.com");
        UUID minor = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        generated("luis@example.com", "Curso de Luis", 2);
        insertCourse(minor, "Curso de Hugo", 2, true, List.of());

        results(ana).andExpect(status().isOk()).andExpect(jsonPath("$.courses", hasSize(0)));
    }

    @Test
    void aMinorSeesTheirCoursesAndPiecesButNoAmounts() throws Exception {
        String ana = logged("ana@example.com");
        UUID minor = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        insertCourse(minor, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();

        String body = results(hugo).andExpect(status().isOk()).andExpect(jsonPath("$.courses", hasSize(1)))
                .andExpect(jsonPath("$.courses[0].done").value(10)).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactly("courses");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.courses[0]").keySet()).doesNotContain("costUsd");
    }

    @Test
    void theResponseExposesExactlyTheAgreedFields() throws Exception {
        String ana = logged("ana@example.com");
        generated("ana@example.com", "Campos", 2);

        String body = results(ana).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder("courses", "totalCostUsd");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.courses[0]").keySet())
                .containsExactlyInAnyOrder("id", "title", "status", "done", "failed", "pending", "total", "calls", "costUsd", "createdAt");
    }
}
