package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-3 y AC-12: el avance por tema de un curso del alumno, sin el texto de lo generado. */
class GuardianCourseTest extends GuardianTestBase {

    @Test
    void ac3_theCourseShowsTheSameProgressPerTopicThatTheStudentSees() throws Exception {
        Family f = family();
        markDone(f.hugo(), f.course(), lesson(f.course(), 0), true).andExpect(status().isNoContent());
        examTaken(f.hugo(), f.course(), piece(f.course(), "EXAM", 0), ALL_WRONG);
        examTaken(f.hugo(), f.course(), piece(f.course(), "EXAM", 0), ALL_RIGHT);

        String mine = studyOf(f.hugo(), f.course()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String panel = panelGet(f.ana(), f.hugoId(), "/courses/" + f.course()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Historia"))
                .andExpect(jsonPath("$.status").value("READY")).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Object>read(panel, "$.overview.topics")).isEqualTo(JsonPath.<Object>read(mine, "$.topics"));
        assertThat(JsonPath.<Integer>read(panel, "$.overview.percent")).isEqualTo(JsonPath.<Integer>read(mine, "$.percent"));
        assertThat(JsonPath.<Integer>read(panel, "$.overview.topics[0].exam.attempts")).isEqualTo(2);
        assertThat(JsonPath.<Boolean>read(panel, "$.overview.topics[0].lessons[0].done")).isTrue();
    }

    @Test
    void ac3_theTextOfLessonsExercisesAndExamsNeverLeavesTheServerThroughThePanel() throws Exception {
        Family f = family();

        String panel = panelGet(f.ana(), f.hugoId(), "/courses/" + f.course()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(panel).doesNotContain("Esta lección explica").doesNotContain("Primer ejercicio").doesNotContain("Solución uno").doesNotContain("Pregunta 1")
                .doesNotContain("Porque sí").doesNotContain("markdown").doesNotContain("content");
    }

    @Test
    void ac3_aCourseThatIsNotGeneratedYetCanBeOpened() throws Exception {
        Family f = family();
        UUID pending = insertCourse(f.hugoId(), "Pendiente", 2, true, List.of());

        panelGet(f.ana(), f.hugoId(), "/courses/" + pending).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Pendiente"));
    }

    @Test
    void ac3_aNonexistentOrForeignCourseIsNotFound() throws Exception {
        Family f = family();
        UUID lucasCourse = insertCourse(f.luisId(), "De Luis", 1, false, List.of());

        panelGet(f.ana(), f.hugoId(), "/courses/" + UUID.randomUUID()).andExpect(status().isNotFound());
        panelGet(f.ana(), f.hugoId(), "/courses/" + lucasCourse).andExpect(status().isNotFound());
    }

    @Test
    void ac3_readingACourseDoesNotWriteAnythingNorAuditAView() throws Exception {
        Family f = family();
        int before = writableRows();

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course()).andExpect(status().isOk());

        assertThat(writableRows()).isEqualTo(before);
        assertThat(guardianViews()).isEmpty();
    }
}
