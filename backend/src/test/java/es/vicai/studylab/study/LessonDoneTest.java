package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-3 (y AC-12, AC-13): «Marcar como hecha» y la navegación entre lecciones. */
class LessonDoneTest extends StudyTestBase {

    @Test
    void ac3_aLessonCanBeMarkedDoneAndUnmarked() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID lesson = piece(course, "LESSON", 0);

        markDone(ana, course, lesson, true).andExpect(status().isNoContent());
        studyOf(ana, course).andExpect(jsonPath("$.topics[0].lessons[0].done").value(true));

        markDone(ana, course, lesson, false).andExpect(status().isNoContent());
        studyOf(ana, course).andExpect(jsonPath("$.topics[0].lessons[0].done").value(false));
    }

    @Test
    void ac3_markingTwiceIsIdempotentAndKeepsASingleRow() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID lesson = piece(course, "LESSON", 0);

        markDone(ana, course, lesson, true).andExpect(status().isNoContent());
        markDone(ana, course, lesson, true).andExpect(status().isNoContent());

        assertThat(rows("lesson_progress")).isEqualTo(1);
        markDone(ana, course, lesson, false);
        markDone(ana, course, lesson, false).andExpect(status().isNoContent());
        assertThat(rows("lesson_progress")).isZero();
    }

    @Test
    void ac3_unmarkingALessonLowersTheProgressButKeepsAPassedExam() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        markDone(ana, course, piece(course, "LESSON", 0), true);
        examTaken(ana, course, piece(course, "EXAM", 0), ALL_RIGHT);

        markDone(ana, course, piece(course, "LESSON", 0), false);

        studyOf(ana, course).andExpect(jsonPath("$.lessonsDone").value(0)).andExpect(jsonPath("$.examsPassed").value(1));
    }

    @Test
    void ac13_onlyALessonThatIsDoneCanBeMarked() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        owner.update("UPDATE course_piece SET status = 'PENDING', content_md = NULL WHERE id = ?", piece(course, "LESSON", 1));

        markDone(ana, course, piece(course, "LESSON", 1), true).andExpect(status().isConflict());
        markDone(ana, course, piece(course, "EXERCISES", 0), true).andExpect(status().isBadRequest());
        markDone(ana, course, piece(course, "EXAM", 0), true).andExpect(status().isBadRequest());
        assertThat(rows("lesson_progress")).isZero();
    }

    @Test
    void ac13_theBodyMustSayExplicitlyWhetherItIsDone() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        String path = piecePath(course, piece(course, "LESSON", 0)) + "/done";

        putAs(ana, path, Map.of()).andExpect(status().isBadRequest());
        putAs(ana, path, Map.of("done", "sí")).andExpect(status().isBadRequest());
        putAs(ana, path, null).andExpect(status().isBadRequest());
    }

    @Test
    void ac3_theLessonViewSaysWhetherItIsMarkedDoneAndOtherPiecesHaveNoSuchField() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID lesson = piece(course, "LESSON", 0);
        pieceOf(ana, course, lesson).andExpect(jsonPath("$.done").value(false));

        markDone(ana, course, lesson, true);

        pieceOf(ana, course, lesson).andExpect(jsonPath("$.done").value(true));
        pieceOf(ana, course, piece(course, "EXERCISES", 0)).andExpect(jsonPath("$.done").doesNotExist());
    }

    // ---------- anterior y siguiente ----------

    @Test
    void ac3_aLessonLinksToThePreviousAndNextLessonOfTheCourseInOrder() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        pieceOf(ana, course, piece(course, "LESSON", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.navigation.previous").value(piece(course, "LESSON", 0).toString()))
                .andExpect(jsonPath("$.navigation.next").value(piece(course, "LESSON", 2).toString()));
    }

    @Test
    void ac3_theFirstLessonHasNoPreviousAndTheLastHasNoNext() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        pieceOf(ana, course, piece(course, "LESSON", 0)).andExpect(jsonPath("$.navigation.previous").doesNotExist())
                .andExpect(jsonPath("$.navigation.next").value(piece(course, "LESSON", 1).toString()));
        pieceOf(ana, course, piece(course, "LESSON", 3)).andExpect(jsonPath("$.navigation.next").doesNotExist());
    }

    @Test
    void ac12_navigationSkipsLessonsThatAreNotGeneratedYet() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        owner.update("UPDATE course_piece SET status = 'PENDING', content_md = NULL WHERE id = ?", piece(course, "LESSON", 2));

        pieceOf(ana, course, piece(course, "LESSON", 1)).andExpect(jsonPath("$.navigation.next").value(piece(course, "LESSON", 3).toString()));
    }

    @Test
    void ac3_exercisesAndExamsHaveNoNavigationBlock() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        pieceOf(ana, course, piece(course, "EXERCISES", 0)).andExpect(jsonPath("$.navigation").doesNotExist());
        pieceOf(ana, course, piece(course, "EXAM", 0)).andExpect(jsonPath("$.navigation").doesNotExist());
    }
}
