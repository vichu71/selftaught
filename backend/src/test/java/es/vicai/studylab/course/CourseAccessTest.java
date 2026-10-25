package es.vicai.studylab.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-14: cada usuario solo ve y toca lo suyo; ajeno e inexistente responden igual. */
class CourseAccessTest extends CourseTestBase {

    @Test
    void ac14_listingAndReadingRequireASession() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void ac14_youSeeYourOwnCoursesNewestFirst() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        courseCreated(ana, "Primero");
        Thread.sleep(15);
        courseCreated(ana, "Segundo");

        getAs(ana, "/api/courses").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Segundo")).andExpect(jsonPath("$[1].title").value("Primero"))
                .andExpect(jsonPath("$[0].status").value("PENDING_GENERATION"));
    }

    @Test
    void ac14_anotherUsersCoursesNeverAppearInYourList() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        model(NO_QUESTIONS);
        courseCreated(ana, "Curso de Ana");

        getAs(luis, "/api/courses").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ac14_aForeignCourseAndANonexistentOneAnswerTheSame404() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        model(NO_QUESTIONS);
        String id = JsonPath.read(courseCreated(ana, "Curso de Ana"), "$.id");

        String foreign = courseOf(luis, id).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = courseOf(luis, UUID.randomUUID().toString()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String malformed = courseOf(luis, "no-es-un-uuid").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing).isEqualTo(malformed);
    }

    @Test
    void ac14_aTutorDoesNotReadAMinorsCourseThroughThisApiThatPanelIsF08() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        model(NO_QUESTIONS);
        String id = JsonPath.read(courseCreated(hugo, "Curso de Hugo"), "$.id");

        courseOf(ana, id).andExpect(status().isNotFound());
        getAs(ana, "/api/courses").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ac14_aMinorCanCreateAndSeeTheirOwnCourses() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        model(NO_QUESTIONS);

        String id = JsonPath.read(courseCreated(hugo, "Cocina básica"), "$.id");

        courseOf(hugo, id).andExpect(status().isOk());
        getAs(hugo, "/api/courses").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void ac14_theResponseExposesOnlyTheAgreedFields() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);

        String course = courseCreated(ana, "Cocina básica");

        assertThat(JsonPath.<Map<String, Object>>read(course, "$").keySet())
                .containsExactlyInAnyOrder("id", "title", "status", "sheet", "documents", "createdAt");
        assertThat(course).doesNotContain("owner").doesNotContain("intake");
    }

    @Test
    void ac14_theIntakeOfAnotherUserCannotBeUsedToReadItsQuestions() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", java.util.List.of());

        // no existe ningún endpoint para releer una consulta, y crear con la de otro es un 404 (CourseCreationTest)
        mvc.perform(get("/api/courses/intake/" + intakeId(body)).cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, luis)))
                .andExpect(status().isNotFound());
    }
}
