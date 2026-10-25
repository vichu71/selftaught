package es.vicai.studylab.courseedit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-2 y AC-5: el tope de piezas del curso y el límite por hora de ediciones con IA. Contexto propio: baja los dos valores. */
@TestPropertySource(properties = {"app.generation.max-pieces=11", "app.course-edit.ai-actions-per-hour=3"})
class CourseEditLimitsTest extends CourseEditTestBase {

    @Test
    void ac2_aNewChapterThatWouldExceedThePieceCapIsRefusedAndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com"); // 10 piezas
        UUID topic = topicIds(course).get(0);

        postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "Roma", "objective", "Entender la república")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COURSE_TOO_BIG"));

        assertThat(pieceCount(course)).isEqualTo(10);
        assertThat(version(course)).isEqualTo(1);
        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(count("SELECT count(*) FROM course_action WHERE kind = 'AI_EDIT'")).isZero();
    }

    @Test
    void ac5_theFourthAiEditOfTheHourIsRefusedWithoutChangingAnythingNorCallingTheModel() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID chapter = chapterIds(topicIds(course).get(0)).get(0);
        for (int i = 0; i < 3; i++) {
            postAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/regenerate"), null).andExpect(status().isAccepted());
            modelDoes(happy(2));
            generator.processPending();
        }
        int calls = fake.calls().size();
        int version = version(course);

        postAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/regenerate"), null).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("EDIT_RATE_LIMITED"));
        generator.processPending();

        assertThat(fake.calls()).hasSize(calls);
        assertThat(version(course)).isEqualTo(version);
        assertThat(pieceRow(lessonOf(chapter)).get("status")).isEqualTo("DONE");
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac5_theLimitIsPerUserAndEditsWithoutAiAreNeverLimited() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID anaCourse = ready("ana@example.com");
        UUID luisCourse = ready("luis@example.com");
        UUID chapter = chapterIds(topicIds(anaCourse).get(0)).get(0);
        for (int i = 0; i < 3; i++) {
            postAs(ana, coursePath(anaCourse, "/pieces/" + lessonOf(chapter) + "/regenerate"), null).andExpect(status().isAccepted());
            modelDoes(happy(2));
            generator.processPending();
        }

        postAs(luis, coursePath(luisCourse, "/pieces/" + lessonOf(chapterIds(topicIds(luisCourse).get(0)).get(0)) + "/regenerate"), null).andExpect(status().isAccepted());
        putAs(ana, coursePath(anaCourse, "/topics/" + topicIds(anaCourse).get(0)), Map.of("title", "Sin IA", "summary", "x")).andExpect(status().isOk());
    }

    @Test
    void ac5_actionsOlderThanAnHourNoLongerCount() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID chapter = chapterIds(topicIds(course).get(0)).get(0);
        for (int i = 0; i < 3; i++) owner.update("INSERT INTO course_action (user_id, kind, created_at) VALUES (?, 'AI_EDIT', now() - interval '2 hours')", idOf("ana@example.com"));

        postAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/regenerate"), null).andExpect(status().isAccepted());
    }
}
