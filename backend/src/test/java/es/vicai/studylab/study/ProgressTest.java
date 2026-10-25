package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-1, AC-2, AC-4 y AC-12 (ADR-49): el progreso se calcula en el servidor a partir de las lecciones hechas y los exámenes superados. */
class ProgressTest extends StudyTestBase {

    @Test
    void ac4_aFreshCourseStartsAtZeroWithTheWholeOutlineAsTheDenominator() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true); // 4 lecciones + 2 exámenes

        studyOf(ana, course).andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(0)).andExpect(jsonPath("$.lessonsDone").value(0)).andExpect(jsonPath("$.lessonsTotal").value(4))
                .andExpect(jsonPath("$.examsPassed").value(0)).andExpect(jsonPath("$.examsTotal").value(2))
                .andExpect(jsonPath("$.topics", hasSize(2)));
    }

    @Test
    void ac2_eachTopicListsItsLessonsInOrderWithTheirStateAndItsExam() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        studyOf(ana, course).andExpect(jsonPath("$.topics[0].lessons", hasSize(2)))
                .andExpect(jsonPath("$.topics[0].lessons[0].pieceId").value(piece(course, "LESSON", 0).toString()))
                .andExpect(jsonPath("$.topics[0].lessons[1].pieceId").value(piece(course, "LESSON", 1).toString()))
                .andExpect(jsonPath("$.topics[0].lessons[0].status").value("DONE"))
                .andExpect(jsonPath("$.topics[0].lessons[0].done").value(false))
                .andExpect(jsonPath("$.topics[0].exam.pieceId").value(piece(course, "EXAM", 0).toString()))
                .andExpect(jsonPath("$.topics[0].exam.attempts").value(0))
                .andExpect(jsonPath("$.topics[0].exam.passed").value(false))
                .andExpect(jsonPath("$.topics[0].exam.best").doesNotExist())
                .andExpect(jsonPath("$.topics[1].lessons[0].pieceId").value(piece(course, "LESSON", 2).toString()));
    }

    @Test
    void ac4_markingLessonsAndPassingExamsRaisesTheCourseAndTopicPercentages() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true); // 6 elementos en total; 3 por tema
        markDone(ana, course, piece(course, "LESSON", 0), true).andExpect(status().isNoContent());
        markDone(ana, course, piece(course, "LESSON", 1), true).andExpect(status().isNoContent());
        examTaken(ana, course, piece(course, "EXAM", 0), ALL_RIGHT);

        studyOf(ana, course)
                .andExpect(jsonPath("$.lessonsDone").value(2)).andExpect(jsonPath("$.examsPassed").value(1))
                .andExpect(jsonPath("$.percent").value(50)) // 3 de 6
                .andExpect(jsonPath("$.topics[0].percent").value(100))
                .andExpect(jsonPath("$.topics[1].percent").value(0));
    }

    @Test
    void ac4_aCourseWithoutExamsCountsOnlyLessons() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, false);
        markDone(ana, course, piece(course, "LESSON", 0), true);
        markDone(ana, course, piece(course, "LESSON", 1), true);

        studyOf(ana, course).andExpect(jsonPath("$.examsTotal").value(0)).andExpect(jsonPath("$.percent").value(50))
                .andExpect(jsonPath("$.topics[0].exam").doesNotExist());
    }

    @Test
    void ac4_practiceExercisesNeverChangeTheProgress() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        answered(ana, course, piece(course, "EXERCISES", 0), 0, "mi respuesta");

        studyOf(ana, course).andExpect(jsonPath("$.percent").value(0));
    }

    @Test
    void ac4_aFailedExamDoesNotCountAsPassed() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        examTaken(ana, course, piece(course, "EXAM", 0), ALL_WRONG);

        studyOf(ana, course).andExpect(jsonPath("$.examsPassed").value(0)).andExpect(jsonPath("$.topics[0].exam.passed").value(false))
                .andExpect(jsonPath("$.topics[0].exam.attempts").value(1)).andExpect(jsonPath("$.topics[0].exam.best").value(0));
    }

    @Test
    void ac4_aCourseWithoutOutlineYetHasNoPercentage() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);

        studyOf(ana, course).andExpect(jsonPath("$.percent").doesNotExist()).andExpect(jsonPath("$.lessonsTotal").value(0))
                .andExpect(jsonPath("$.topics", hasSize(0)));
    }

    // ---------- mientras se genera (AC-12) ----------

    @Test
    void ac12_aPieceThatIsNotGeneratedYetIsShownAsPendingAndKeepsTheDenominator() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        owner.update("UPDATE course_piece SET status = 'PENDING', content_md = NULL WHERE id = ?", piece(course, "LESSON", 1));
        markDone(ana, course, piece(course, "LESSON", 0), true);

        studyOf(ana, course).andExpect(jsonPath("$.topics[0].lessons[1].status").value("PENDING")).andExpect(jsonPath("$.topics[0].lessons[1].done").value(false))
                .andExpect(jsonPath("$.lessonsTotal").value(4)).andExpect(jsonPath("$.lessonsDone").value(1));
    }

    @Test
    void ac12_aLessonMarkedDoneButNotGeneratedAnymoreNeverCounts() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, false);
        markDone(ana, course, piece(course, "LESSON", 0), true);
        owner.update("UPDATE course_piece SET status = 'FAILED' WHERE id = ?", piece(course, "LESSON", 0));

        studyOf(ana, course).andExpect(jsonPath("$.lessonsDone").value(0));
    }

    // ---------- la lista de cursos (AC-1) ----------

    @Test
    void ac1_theListOfCoursesCarriesThePercentageOfEachOne() throws Exception {
        String ana = logged("ana@example.com");
        UUID started = generated("ana@example.com", 2, false);
        UUID waiting = insertCourse(idOf("ana@example.com"), "Sin temario", 2, true, java.util.List.of());
        markDone(ana, started, piece(started, "LESSON", 0), true);
        markDone(ana, started, piece(started, "LESSON", 1), true);

        getAs(ana, "/api/study/progress").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.courseId == '" + started + "')].percent[0]").value(50))
                .andExpect(jsonPath("$[?(@.courseId == '" + waiting + "')].percent").isEmpty());
    }

    @Test
    void ac1_theListOnlyHasMyCourses() throws Exception {
        String ana = logged("ana@example.com");
        logged("luis@example.com");
        generated("luis@example.com", 2, true);

        getAs(ana, "/api/study/progress").andExpect(jsonPath("$", hasSize(0)));
        assertThat(rows("lesson_progress")).isZero();
    }
}
