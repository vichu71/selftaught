package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** AC-13: sesión, CSRF, propietario y validación de la pregunta; ajeno e inexistente responden igual. */
class TutorAccessTest extends TutorTestBase {

    @Test
    void ac13_everyEndpointRequiresASession() throws Exception {
        UUID id = UUID.randomUUID();
        String path = "/api/courses/" + id + "/pieces/" + id + "/tutor";

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void ac13_postAndDeleteRequireTheCsrfToken() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);

        postAsWithoutCsrf(ana, tutorPath(course, lesson), Map.of("question", "¿Hola?")).andExpect(status().isForbidden());
        clearWithoutCsrf(ana, course, lesson).andExpect(status().isForbidden());

        assertThat(tutorCalls()).isEmpty();
        assertThat(messages()).isEmpty();
    }

    @Test
    void ac13_anotherUserGetsA404OnEveryEndpointAndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        ask(ana, course, lesson, "mía");

        thread(luis, course, lesson).andExpect(status().isNotFound());
        ask(luis, course, lesson, "intrusa").andExpect(status().isNotFound());
        clear(luis, course, lesson).andExpect(status().isNotFound());

        assertThat(messages()).hasSize(2);
        assertThat(tutorCalls()).hasSize(1);
    }

    @Test
    void ac13_aForeignLessonAnswersExactlyLikeANonexistentOne() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);

        String foreign = thread(luis, course, lesson).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = thread(luis, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
        assertThat(ana).isNotNull();
    }

    @Test
    void ac13_aLessonAskedThroughAnotherCourseOfTheSameUserIsNotFound() throws Exception {
        String ana = logged("ana@example.com");
        UUID first = readyCourse("ana@example.com");
        UUID second = insertCourse(idOf("ana@example.com"), "Otro curso", 2, true, java.util.List.of());

        ask(ana, second, lesson(first, 0), "¿Hola?").andExpect(status().isNotFound());
        thread(ana, second, lesson(first, 0)).andExpect(status().isNotFound());
    }

    @Test
    void ac13_invalidIdentifiersAreNotFoundNeverAnError() throws Exception {
        String ana = logged("ana@example.com");

        getAs(ana, "/api/courses/no-es-un-uuid/pieces/otra/tutor").andExpect(status().isNotFound());
        postAs(ana, "/api/courses/" + UUID.randomUUID() + "/pieces/no-es-un-uuid/tutor", Map.of("question", "x")).andExpect(status().isNotFound());
    }

    // ---------- la pregunta (AC-13) ----------

    @Test
    void ac13_theQuestionMustHaveBetweenOneAndAThousandCharactersAndNoControlCharacters() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);

        for (String bad : new String[] {"", "   ", "\n\n", "x".repeat(1001), "con\u0000control", "tab\u0007"}) {
            ask(ana, course, lesson, bad).andExpect(status().isBadRequest());
        }
        postAs(ana, tutorPath(course, lesson), Map.of()).andExpect(status().isBadRequest());
        postAs(ana, tutorPath(course, lesson), Map.of("question", 5)).andExpect(status().isBadRequest());
        ask(ana, course, lesson, "x".repeat(1000)).andExpect(status().isCreated());

        assertThat(tutorCalls()).hasSize(1);
        assertThat(messages()).hasSize(2); // las no válidas no dejan nada ni cuentan para el límite
    }

    @Test
    void ac13_aQuestionWithMarkupIsStoredAsPlainTextAndReturnedAsItIs() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers(ANSWER);
        String hostile = "línea 1\n<script>alert(1)</script> \"comillas\" '; DROP TABLE tutor_message; --";

        ask(ana, course, lesson, hostile).andExpect(status().isCreated()).andExpect(jsonPath("$.user.content").value(hostile));

        assertThat(owner.queryForObject("SELECT content FROM tutor_message WHERE role = 'USER'", String.class)).isEqualTo(hostile);
        thread(ana, course, lesson).andExpect(jsonPath("$.messages[0].content").value(hostile));
    }

    @Test
    void ac13_aStoredAnswerNeverInterpretsMarkupItsViewerPaintsAsText() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID lesson = lesson(course, 0);
        tutorAnswers("Usa `<div>` para agrupar y **negrita** para destacar.");

        ask(ana, course, lesson, "¿Cómo?").andExpect(status().isCreated()).andExpect(jsonPath("$.tutor.content").value("Usa `<div>` para agrupar y **negrita** para destacar."));
    }
}
