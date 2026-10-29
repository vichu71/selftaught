package es.vicai.studylab.course;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** F18 AC-5 y AC-8: GET /api/courses/material-usage dice en qué cursos del que llama se usa cada documento. */
class MaterialUsageTest extends CourseTestBase {

    private static final String PATH = "/api/courses/material-usage";

    private String doc(String session, String name) throws Exception {
        return uploadedAndProcessed(session, name, text((name + " contenido de apuntes ").repeat(30)));
    }

    private String courseWith(String session, String topic, List<String> docs) throws Exception {
        model(NO_QUESTIONS);
        String body = started(session, topic, docs);
        return JsonPath.read(create(session, intakeId(body), List.of()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void ac5_requiresASession() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void ac5_withNoCoursesTheUsageIsAnEmptyMap() throws Exception {
        String ana = logged("ana@example.com");
        doc(ana, "suelto.txt");

        getAs(ana, PATH).andExpect(status().isOk()).andExpect(jsonPath("$.usage").isMap()).andExpect(jsonPath("$.usage").isEmpty());
    }

    @Test
    void ac5_aDocumentUsedByTwoCoursesListsBothNewestFirstAndEachCourseKeepsItsOwnDocuments() throws Exception {
        String ana = logged("ana@example.com");
        String a = doc(ana, "a.txt");
        String b = doc(ana, "b.txt");
        String first = courseWith(ana, "Primero", List.of(a, b));
        Thread.sleep(15);
        String second = courseWith(ana, "Segundo", List.of(a));

        String body = getAs(ana, PATH).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> ofA = JsonPath.read(body, "$.usage." + a + "[*].id");
        List<String> ofB = JsonPath.read(body, "$.usage." + b + "[*].id");
        assertThat(ofA).containsExactly(second, first);
        assertThat(ofB).containsExactly(first);
        assertThat((List<String>) JsonPath.read(body, "$.usage." + a + "[*].title")).containsExactly("Segundo", "Primero");
    }

    @Test
    void ac8_onlyIdAndTitleAreReturnedNeverContentHashOrOwner() throws Exception {
        String ana = logged("ana@example.com");
        String d = doc(ana, "diario-secreto.txt");
        courseWith(ana, "Curso", List.of(d));

        String body = getAs(ana, PATH).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Map<String, Object> first = JsonPath.read(body, "$.usage." + d + "[0]");
        assertThat(first.keySet()).containsExactlyInAnyOrder("id", "title");
        assertThat(body).doesNotContain("diario-secreto").doesNotContain("content").doesNotContain("hash").doesNotContain("owner")
                .doesNotContain("ana@example.com");
    }

    @Test
    void ac8_anotherUserNeverSeesYourDocumentsOrCourses() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String d = doc(ana, "de-ana.txt");
        courseWith(ana, "Curso de Ana", List.of(d));

        String body = getAs(luis, PATH).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(d).doesNotContain("Curso de Ana");
        getAs(luis, PATH).andExpect(jsonPath("$.usage").isEmpty());
    }

    @Test
    void ac8_aTutorDoesNotReadAMinorsUsageThroughThisEndpoint() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        String d = doc(hugo, "de-hugo.txt");
        courseWith(hugo, "Curso de Hugo", List.of(d));

        getAs(ana, PATH).andExpect(jsonPath("$.usage").isEmpty());
        getAs(hugo, PATH).andExpect(jsonPath("$.usage." + d, hasSize(1)));
    }

    @Test
    void ac4_aDeletedDocumentDisappearsFromTheUsageAndTheCourseKeepsWorking() throws Exception {
        String ana = logged("ana@example.com");
        String keep = doc(ana, "queda.txt");
        String gone = doc(ana, "se-va.txt");
        String course = courseWith(ana, "Curso", List.of(keep, gone));

        deleteAs(ana, gone).andExpect(status().isNoContent());

        String body = getAs(ana, PATH).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains(keep).doesNotContain(gone);
        courseOf(ana, course).andExpect(status().isOk()).andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].name").value("queda.txt"));
    }

    @Test
    void ac5_theEndpointRoutesToItselfAndNotToTheCourseDetail() throws Exception {
        String ana = logged("ana@example.com");

        getAs(ana, PATH).andExpect(status().isOk()).andExpect(jsonPath("$.usage").exists());
    }
}
