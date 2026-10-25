package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** AC-14: sesión, CSRF y propietario en todos los endpoints de estudio; ajeno e inexistente responden igual, y el tutor no lee los intentos de su menor. */
class StudyAccessTest extends StudyTestBase {

    /** Todos los endpoints de estudio de una pieza, con un cuerpo válido, para recorrerlos de una vez. */
    private record Endpoint(String name, String method, String path, Object body) {}

    private List<Endpoint> endpoints(UUID course, UUID lesson, UUID exercises, UUID exam, String exerciseAttempt, String examAttempt) {
        String c = "/api/courses/" + course;
        return List.of(
                new Endpoint("estudio del curso", "GET", c + "/study", null),
                new Endpoint("marcar lección", "PUT", piecePath(course, lesson) + "/done", Map.of("done", true)),
                new Endpoint("responder ejercicio", "POST", piecePath(course, exercises) + "/exercises/0/attempts", Map.of("answer", "x")),
                new Endpoint("autocorregir", "PUT", piecePath(course, exercises) + "/exercises/0/attempts/" + exerciseAttempt + "/result", Map.of("result", "CORRECT")),
                new Endpoint("corregir con IA", "POST", piecePath(course, exercises) + "/exercises/0/attempts/" + exerciseAttempt + "/ai-grade", null),
                new Endpoint("historial de ejercicios", "GET", piecePath(course, exercises) + "/attempts", null),
                new Endpoint("empezar examen", "POST", piecePath(course, exam) + "/exam/attempts", null),
                new Endpoint("entregar examen", "POST", piecePath(course, exam) + "/exam/attempts/" + examAttempt + "/submit", Map.of("answers", Arrays(ALL_RIGHT))),
                new Endpoint("resultado de un intento", "GET", piecePath(course, exam) + "/exam/attempts/" + examAttempt, null),
                new Endpoint("historial de exámenes", "GET", piecePath(course, exam) + "/attempts", null));
    }

    private static List<Integer> Arrays(Integer[] values) {
        return java.util.Arrays.asList(values);
    }

    private MockHttpServletRequestBuilder build(Endpoint e, String session, boolean csrf) throws Exception {
        MockHttpServletRequestBuilder req = switch (e.method()) {
            case "GET" -> get(e.path());
            case "PUT" -> put(e.path());
            default -> post(e.path());
        };
        if (session != null) req.cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, session));
        if (csrf) req.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());
        if (e.body() != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(e.body()));
        return req;
    }

    @Test
    void ac14_everyEndpointRequiresASession() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        String exerciseAttempt = answered(ana, course, piece(course, "EXERCISES", 0), 0, "x");
        String examAttempt = examStarted(ana, course, piece(course, "EXAM", 0));

        for (Endpoint e : endpoints(course, piece(course, "LESSON", 0), piece(course, "EXERCISES", 0), piece(course, "EXAM", 0), exerciseAttempt, examAttempt)) {
            mvc.perform(build(e, null, true)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/study/progress")).andExpect(status().isUnauthorized());
    }

    @Test
    void ac14_everyEndpointThatChangesSomethingRequiresTheCsrfToken() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        String exerciseAttempt = answered(ana, course, piece(course, "EXERCISES", 0), 0, "x");
        String examAttempt = examStarted(ana, course, piece(course, "EXAM", 0));

        for (Endpoint e : endpoints(course, piece(course, "LESSON", 0), piece(course, "EXERCISES", 0), piece(course, "EXAM", 0), exerciseAttempt, examAttempt)) {
            if (e.method().equals("GET")) continue;
            mvc.perform(build(e, ana, false)).andExpect(status().isForbidden());
        }
        assertThat(rows("lesson_progress")).isZero();
        assertThat(owner.queryForObject("SELECT status FROM exam_attempt", String.class)).isEqualTo("STARTED");
    }

    @Test
    void ac14_anotherUserGetsA404OnEveryEndpointAndNothingIsChanged() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = generated("ana@example.com", 2, true);
        String exerciseAttempt = answered(ana, course, piece(course, "EXERCISES", 0), 0, "x");
        String examAttempt = examStarted(ana, course, piece(course, "EXAM", 0));

        for (Endpoint e : endpoints(course, piece(course, "LESSON", 0), piece(course, "EXERCISES", 0), piece(course, "EXAM", 0), exerciseAttempt, examAttempt)) {
            mvc.perform(build(e, luis, true)).andExpect(status().isNotFound());
        }
        assertThat(rows("lesson_progress")).isZero();
        assertThat(rows("exercise_attempt")).isEqualTo(1);
        assertThat(owner.queryForObject("SELECT status FROM exam_attempt", String.class)).isEqualTo("STARTED");
    }

    @Test
    void ac14_aForeignCourseAnswersExactlyLikeANonexistentOne() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID lesson = piece(course, "LESSON", 0);

        String foreign = markDone(luis, course, lesson, true).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = markDone(luis, UUID.randomUUID(), lesson, true).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String foreignStudy = studyOf(luis, course).andReturn().getResponse().getContentAsString();
        String missingStudy = studyOf(luis, UUID.randomUUID()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
        assertThat(foreignStudy).isEqualTo(missingStudy);
        assertThat(ana).isNotNull();
    }

    @Test
    void ac14_aPieceAskedThroughAnotherCourseOfTheSameUserIsNotFound() throws Exception {
        String ana = logged("ana@example.com");
        UUID first = generated("ana@example.com", 2, true);
        UUID second = insertCourse(idOf("ana@example.com"), "Otro curso", 2, true, List.of());

        markDone(ana, second, piece(first, "LESSON", 0), true).andExpect(status().isNotFound());
        startExam(ana, second, piece(first, "EXAM", 0)).andExpect(status().isNotFound());
        answer(ana, second, piece(first, "EXERCISES", 0), 0, "x").andExpect(status().isNotFound());
    }

    @Test
    void ac14_invalidIdentifiersAreNotFoundNeverAnError() throws Exception {
        String ana = logged("ana@example.com");

        getAs(ana, "/api/courses/no-es-un-uuid/study").andExpect(status().isNotFound());
        putAs(ana, "/api/courses/no-es-un-uuid/pieces/otra/done", Map.of("done", true)).andExpect(status().isNotFound());
        postAs(ana, "/api/courses/" + UUID.randomUUID() + "/pieces/no-es-un-uuid/exam/attempts", null).andExpect(status().isNotFound());
    }

    @Test
    void ac14_aTutorCannotReadOrTouchTheStudyDataOfTheirMinorThroughThisApi() throws Exception {
        String ana = logged("ana@example.com");
        UUID minor = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(minor, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        String attempt = answered(hugo, course, piece(course, "EXERCISES", 0), 0, "respuesta de Hugo");
        examTaken(hugo, course, piece(course, "EXAM", 0), ALL_RIGHT);

        studyOf(ana, course).andExpect(status().isNotFound());
        attemptsOf(ana, course, piece(course, "EXERCISES", 0)).andExpect(status().isNotFound());
        attemptsOf(ana, course, piece(course, "EXAM", 0)).andExpect(status().isNotFound());
        markDone(ana, course, piece(course, "LESSON", 0), true).andExpect(status().isNotFound());
        selfResult(ana, course, piece(course, "EXERCISES", 0), 0, attempt, "CORRECT").andExpect(status().isNotFound());
        getAs(ana, "/api/study/progress").andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT self_result FROM exercise_attempt", String.class)).isNull();
    }
}
