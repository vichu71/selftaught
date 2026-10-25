package es.vicai.studylab.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-10 (A2, ADR-41): límite de frecuencia por usuario y de cursos pendientes, contados en la base de datos. Contexto propio: cambia la configuración. */
@TestPropertySource(properties = {"app.course.intakes-per-hour=3", "app.course.max-pending=2"})
class CourseLimitsTest extends CourseTestBase {

    @Test
    void ac10_theFourthIntakeInAnHourIsRejectedWithAClearCode() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        for (int i = 0; i < 3; i++) intake(ana, "Tema número " + i).andExpect(status().isCreated());

        intake(ana, "Tema cuarto").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        assertThat(chatCalls()).isEqualTo(3); // el cuarto no llegó al modelo
    }

    @Test
    void ac10_theLimitIsPerUserNotGlobal() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        model(NO_QUESTIONS);
        for (int i = 0; i < 3; i++) intake(ana, "Tema número " + i).andExpect(status().isCreated());

        intake(luis, "Tema de Luis").andExpect(status().isCreated());
    }

    @Test
    void ac10_intakesOlderThanAnHourNoLongerCount() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        for (int i = 0; i < 3; i++) intake(ana, "Tema número " + i).andExpect(status().isCreated());
        owner.update("UPDATE course_intake SET created_at = now() - interval '61 minutes', expires_at = now() - interval '1 minute'");

        intake(ana, "Tema nuevo").andExpect(status().isCreated());
    }

    @Test
    void ac10_theLimitDoesNotDependOnTheIpAddress() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        for (int i = 0; i < 3; i++) intake(ana, "Tema número " + i).andExpect(status().isCreated());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/courses/intake")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .with(req -> { req.setRemoteAddr("203.0.113.77"); return req; })
                .cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, ana)).contentType("application/json")
                .content("{\"topic\":\"Otra IP distinta\"}")).andExpect(status().isTooManyRequests());
    }

    @Test
    void ac10_aRejectedIntakeLeavesNoRowAndSpendsNothing() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        for (int i = 0; i < 3; i++) intake(ana, "Tema número " + i).andExpect(status().isCreated());
        int rows = intakes().size();

        intake(ana, "Tema cuarto").andExpect(status().isTooManyRequests());

        assertThat(intakes()).hasSize(rows);
    }

    @Test
    void ac10_theThirdPendingCourseIsRejectedAtTheIntakeToSaveTheModelCall() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        courseCreated(ana, "Curso uno");
        courseCreated(ana, "Curso dos");
        int calls = chatCalls();

        // límite de frecuencia 3: la tercera consulta aún cabe, pero ya hay 2 cursos pendientes
        intake(ana, "Curso tres").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_LIMIT_REACHED"));

        assertThat(chatCalls()).isEqualTo(calls);
    }

    @Test
    void ac10_theLimitOfPendingCoursesIsAlsoCheckedWhenAccepting() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        String a = started(ana, "Curso uno", List.of());
        courseCreated(ana, "Curso dos");
        courseCreated(ana, "Curso tres");

        create(ana, intakeId(a), List.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_LIMIT_REACHED"));

        assertThat(courses()).hasSize(2);
    }

    @Test
    void ac10_onlyPendingCoursesCountTowardsTheLimit() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        courseCreated(ana, "Curso uno");
        courseCreated(ana, "Curso dos");
        owner.update("UPDATE course SET status = 'READY' WHERE title = 'Curso uno'");

        intake(ana, "Curso tres").andExpect(status().isCreated());
    }
}
