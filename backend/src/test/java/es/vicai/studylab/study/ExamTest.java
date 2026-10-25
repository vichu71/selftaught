package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-8 a AC-13 (ADR-50, 51): el examen se corrige en el servidor, la opción correcta no sale hasta entregar y cada intento queda guardado. */
class ExamTest extends StudyTestBase {

    // ---------- lo que se ve antes de entregar (AC-8) ----------

    @Test
    void ac8_thePieceOfTheExamNoLongerCarriesTheCorrectOptionOrTheExplanation() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        String body = pieceOf(ana, course, piece(course, "EXAM", 0)).andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(5)))
                .andExpect(jsonPath("$.questions[0].options", hasSize(4))).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("correct").doesNotContain("explanation").doesNotContain("Porque sí");
    }

    @Test
    void ac8_startingAnExamReturnsTheQuestionsWithoutTheAnswers() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        String body = startExam(ana, course, piece(course, "EXAM", 0)).andExpect(status().isCreated()).andExpect(jsonPath("$.attemptId").isNotEmpty())
                .andExpect(jsonPath("$.questions", hasSize(5))).andExpect(jsonPath("$.questions[0].text").value("Pregunta 1"))
                .andExpect(jsonPath("$.questions[0].options", hasSize(4))).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$.questions[0]").keySet()).containsExactlyInAnyOrder("text", "options");
        assertThat(body).doesNotContain("correct").doesNotContain("explanation");
    }

    @Test
    void ac8_startingAgainWhileOneIsOpenReusesIt() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String first = examStarted(ana, course, exam);

        startExam(ana, course, exam).andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").value(first));

        assertThat(rows("exam_attempt")).isEqualTo(1);
    }

    @Test
    void ac12_anExamThatIsNotGeneratedYetCannotBeStarted() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 1);
        owner.update("UPDATE course_piece SET status = 'PENDING', content_json = NULL WHERE id = ?", exam);

        startExam(ana, course, exam).andExpect(status().isConflict());
        assertThat(rows("exam_attempt")).isZero();
    }

    @Test
    void ac13_onlyAnExamPieceCanBeStarted() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        startExam(ana, course, piece(course, "LESSON", 0)).andExpect(status().isNotFound());
        startExam(ana, course, piece(course, "EXERCISES", 0)).andExpect(status().isNotFound());
    }

    // ---------- entregar y corregir (AC-8, AC-9) ----------

    @Test
    void ac9_aPerfectExamGetsFullMarksAndPassesTheTopic() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examStarted(ana, course, exam);

        submitExam(ana, course, exam, attempt, ALL_RIGHT).andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(5)).andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.percent").value(100))
                .andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    void ac9_theResultShowsEveryQuestionWithTheChosenAndTheCorrectOptionAndTheExplanation() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examStarted(ana, course, exam);

        submitExam(ana, course, exam, attempt, 1, 0, 3, null, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(3)).andExpect(jsonPath("$.percent").value(60)).andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.questions", hasSize(5)))
                .andExpect(jsonPath("$.questions[0].text").value("Pregunta 1")).andExpect(jsonPath("$.questions[0].chosen").value(1))
                .andExpect(jsonPath("$.questions[0].correct").value(1)).andExpect(jsonPath("$.questions[0].ok").value(true))
                .andExpect(jsonPath("$.questions[0].explanation").value("Porque sí 1"))
                .andExpect(jsonPath("$.questions[1].chosen").value(0)).andExpect(jsonPath("$.questions[1].correct").value(2)).andExpect(jsonPath("$.questions[1].ok").value(false))
                .andExpect(jsonPath("$.questions[3].chosen").doesNotExist()).andExpect(jsonPath("$.questions[3].ok").value(false));
    }

    @Test
    void ac9_anExamHandedInBlankScoresZeroAndIsSaved() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examStarted(ana, course, exam);

        submitExam(ana, course, exam, attempt, null, null, null, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(0)).andExpect(jsonPath("$.percent").value(0)).andExpect(jsonPath("$.passed").value(false));

        assertThat(owner.queryForObject("SELECT status FROM exam_attempt", String.class)).isEqualTo("SUBMITTED");
    }

    @Test
    void ac9_theTopicIsPassedWithExactlyHalfOfTheQuestionsAndNotWithLess() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        fourQuestionExam(exam);
        String half = examStarted(ana, course, exam);
        submitExam(ana, course, exam, half, 0, 0, 1, 1).andExpect(jsonPath("$.percent").value(50)).andExpect(jsonPath("$.passed").value(true));

        String less = examStarted(ana, course, exam);
        submitExam(ana, course, exam, less, 0, 1, 1, 1).andExpect(jsonPath("$.percent").value(25)).andExpect(jsonPath("$.passed").value(false));
    }

    // ---------- entregar dos veces e intentos ajenos (ADR-51) ----------

    @Test
    void ac10_handingInTwiceReturnsTheSameResultAndNeverDuplicatesTheAttempt() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examStarted(ana, course, exam);
        submitExam(ana, course, exam, attempt, ALL_RIGHT).andExpect(status().isOk());

        submitExam(ana, course, exam, attempt, ALL_WRONG).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(5)).andExpect(jsonPath("$.passed").value(true));

        assertThat(rows("exam_attempt")).isEqualTo(1);
    }

    @Test
    void ac13_theNumberOfAnswersMustMatchTheQuestionsAndEveryIndexMustExist() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examStarted(ana, course, exam);

        submitExam(ana, course, exam, attempt, 1, 2).andExpect(status().isBadRequest());
        submitExam(ana, course, exam, attempt, 1, 2, 3, 0, 1, 1).andExpect(status().isBadRequest());
        submitExam(ana, course, exam, attempt, 1, 2, 3, 0, 4).andExpect(status().isBadRequest());
        submitExam(ana, course, exam, attempt, 1, 2, 3, 0, -1).andExpect(status().isBadRequest());
        postAs(ana, piecePath(course, exam) + "/exam/attempts/" + attempt + "/submit", Map.of()).andExpect(status().isBadRequest());
        postAs(ana, piecePath(course, exam) + "/exam/attempts/" + attempt + "/submit", Map.of("answers", "1,2,3,0,1")).andExpect(status().isBadRequest());

        assertThat(owner.queryForObject("SELECT status FROM exam_attempt", String.class)).isEqualTo("STARTED"); // un 400 no gasta el intento
        submitExam(ana, course, exam, attempt, ALL_RIGHT).andExpect(status().isOk());
    }

    @Test
    void ac13_anAttemptOfAnotherExamOrAnotherUserOrThatDoesNotExistIsNotFound() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examStarted(ana, course, exam);

        submitExam(luis, course, exam, attempt, ALL_RIGHT).andExpect(status().isNotFound());
        submitExam(ana, course, piece(course, "EXAM", 1), attempt, ALL_RIGHT).andExpect(status().isNotFound());
        submitExam(ana, course, exam, UUID.randomUUID().toString(), ALL_RIGHT).andExpect(status().isNotFound());
        submitExam(ana, course, exam, "no-es-un-uuid", ALL_RIGHT).andExpect(status().isNotFound());
    }

    @Test
    void ac10_theQuestionsOfAnOpenAttemptStayTheSameEvenIfTheyAreSeenAgain() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);

        String first = startExam(ana, course, exam).andReturn().getResponse().getContentAsString();
        String second = startExam(ana, course, exam).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Object>read(second, "$.questions")).isEqualTo(JsonPath.<Object>read(first, "$.questions"));
    }

    // ---------- repetir sin límite y la mejor nota (AC-10) ----------

    @Test
    void ac10_theExamCanBeRepeatedAndTheBestMarkIsTheOneThatCountsForPassingTheTopic() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        examTaken(ana, course, exam, ALL_RIGHT);
        examTaken(ana, course, exam, ALL_WRONG);

        studyOf(ana, course).andExpect(jsonPath("$.topics[0].exam.attempts").value(2)).andExpect(jsonPath("$.topics[0].exam.best").value(100))
                .andExpect(jsonPath("$.topics[0].exam.last").value(0)).andExpect(jsonPath("$.topics[0].exam.passed").value(true))
                .andExpect(jsonPath("$.examsPassed").value(1));
    }

    @Test
    void ac10_openAttemptsThatWereNeverHandedInDoNotCount() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        examStarted(ana, course, exam);

        studyOf(ana, course).andExpect(jsonPath("$.topics[0].exam.attempts").value(0)).andExpect(jsonPath("$.topics[0].exam.best").doesNotExist());
        attemptsOf(ana, course, exam).andExpect(jsonPath("$.attempts", hasSize(0)));
    }

    @Test
    void ac10_startingAnotherExamPurgesTheOpenAttemptsOlderThanADay() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        examStarted(ana, course, piece(course, "EXAM", 0));
        owner.update("UPDATE exam_attempt SET started_at = now() - interval '2 days'");

        examStarted(ana, course, piece(course, "EXAM", 1));

        assertThat(rows("exam_attempt")).isEqualTo(1);
    }

    // ---------- historial y «qué fallé» (AC-11) ----------

    @Test
    void ac11_theHistoryListsTheHandedInAttemptsNewestFirstWithTheirMark() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String old = examTaken(ana, course, exam, ALL_WRONG);
        owner.update("UPDATE exam_attempt SET submitted_at = now() - interval '2 hours' WHERE id = ?::uuid", old);
        String recent = examTaken(ana, course, exam, ALL_RIGHT);

        attemptsOf(ana, course, exam).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("EXAM")).andExpect(jsonPath("$.attempts", hasSize(2)))
                .andExpect(jsonPath("$.attempts[0].id").value(recent)).andExpect(jsonPath("$.attempts[0].percent").value(100)).andExpect(jsonPath("$.attempts[0].passed").value(true))
                .andExpect(jsonPath("$.attempts[0].correct").value(5)).andExpect(jsonPath("$.attempts[0].total").value(5)).andExpect(jsonPath("$.attempts[0].submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.attempts[1].id").value(old)).andExpect(jsonPath("$.attempts[1].percent").value(0));
    }

    @Test
    void ac11_anOldAttemptCanBeOpenedToSeeWhatWentWrong() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examTaken(ana, course, exam, 0, 2, 3, 0, 0);

        getAs(ana, piecePath(course, exam) + "/exam/attempts/" + attempt).andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(3)).andExpect(jsonPath("$.questions[0].ok").value(false)).andExpect(jsonPath("$.questions[0].correct").value(1))
                .andExpect(jsonPath("$.questions[0].explanation").value("Porque sí 1")).andExpect(jsonPath("$.questions[1].ok").value(true));
    }

    @Test
    void ac11_anAttemptThatWasNotHandedInHasNoResultYet() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examStarted(ana, course, exam);

        getAs(ana, piecePath(course, exam) + "/exam/attempts/" + attempt).andExpect(status().isConflict());
    }

    @Test
    void ac11_aSavedAttemptDoesNotChangeEvenIfTheCourseIsGeneratedAgain() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        String attempt = examTaken(ana, course, exam, ALL_RIGHT);
        owner.update("UPDATE course SET status = 'PENDING_GENERATION' WHERE id = ?", course);
        modelDoes(happy(2));
        generator.processPending();

        getAs(ana, piecePath(course, exam) + "/exam/attempts/" + attempt).andExpect(jsonPath("$.correct").value(5)).andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    void ac11_theResultOfTheExamNeverLeaksThroughTheOtherEndpointsBeforeHandingIn() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        examStarted(ana, course, exam);

        for (String body : new String[] {pieceOf(ana, course, exam).andReturn().getResponse().getContentAsString(),
                studyOf(ana, course).andReturn().getResponse().getContentAsString(), attemptsOf(ana, course, exam).andReturn().getResponse().getContentAsString()}) {
            assertThat(body).doesNotContain("Porque sí").doesNotContain("\"correct\"");
        }
    }
}
