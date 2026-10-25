package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-5, AC-11, AC-12 y AC-13: responder un ejercicio, ver la solución solo después y marcar cómo fue. */
class ExerciseAttemptTest extends StudyTestBase {

    // ---------- la solución no viaja antes (ADR-50) ----------

    @Test
    void ac5_thePieceOfExercisesNoLongerCarriesTheSolutionOrTheExplanation() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        String body = pieceOf(ana, course, piece(course, "EXERCISES", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises", hasSize(3)))
                .andExpect(jsonPath("$.exercises[0].statement").value("Primer ejercicio"))
                .andExpect(jsonPath("$.exercises[0].difficulty").value("LOW")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Solución uno").doesNotContain("Explicación uno").doesNotContain("solution").doesNotContain("explanation");
    }

    // ---------- enviar una respuesta ----------

    @Test
    void ac5_sendingAnAnswerRevealsTheSolutionAndTheExplanationAndSavesTheAttempt() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);

        answer(ana, course, exercises, 1, "Mi intento").andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptId").isNotEmpty())
                .andExpect(jsonPath("$.solution").value("Solución dos")).andExpect(jsonPath("$.explanation").value("Explicación dos"));

        assertThat(rows("exercise_attempt")).isEqualTo(1);
        Map<String, Object> row = owner.queryForMap("SELECT * FROM exercise_attempt");
        assertThat(row.get("answer")).isEqualTo("Mi intento");
        assertThat(((Number) row.get("item_index")).intValue()).isEqualTo(1);
        assertThat(row.get("owner_id")).isEqualTo(idOf("ana@example.com"));
        assertThat(row.get("self_result")).isNull();
        assertThat(row.get("ai_result")).isNull();
    }

    @Test
    void ac5_theSameExerciseCanBeAnsweredAsManyTimesAsYouWant() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);

        String first = answered(ana, course, exercises, 0, "uno");
        String second = answered(ana, course, exercises, 0, "dos");

        assertThat(first).isNotEqualTo(second);
        assertThat(rows("exercise_attempt")).isEqualTo(2);
    }

    @Test
    void ac5_aMultilineAnswerAndAnyTextAreKeptAsTheyAreAndNeverInterpreted() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String hostile = "línea 1\nlínea 2 <script>alert(1)</script> \"comillas\" ' ; DROP TABLE exercise_attempt; --";

        answered(ana, course, exercises, 0, hostile);

        assertThat(owner.queryForObject("SELECT answer FROM exercise_attempt", String.class)).isEqualTo(hostile);
        attemptsOf(ana, course, exercises).andExpect(jsonPath("$.attempts[0].answer").value(hostile));
    }

    // ---------- cómo me fue ----------

    @Test
    void ac5_theStudentMarksHowItWentWithOneOfThreeValues() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String attempt = answered(ana, course, exercises, 0, "respuesta");

        for (String result : new String[] {"CORRECT", "PARTIAL", "INCORRECT"}) {
            selfResult(ana, course, exercises, 0, attempt, result).andExpect(status().isNoContent());
            assertThat(owner.queryForObject("SELECT self_result FROM exercise_attempt", String.class)).isEqualTo(result);
        }
    }

    @Test
    void ac13_anyOtherResultIsRejected() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String attempt = answered(ana, course, exercises, 0, "respuesta");

        for (String bad : new String[] {"correct", "OK", "", "CORRECT; DROP", "100"}) {
            selfResult(ana, course, exercises, 0, attempt, bad).andExpect(status().isBadRequest());
        }
        putAs(ana, piecePath(course, exercises) + "/exercises/0/attempts/" + attempt + "/result", Map.of()).andExpect(status().isBadRequest());
        assertThat(owner.queryForObject("SELECT self_result FROM exercise_attempt", String.class)).isNull();
    }

    @Test
    void ac5_theSelfResultIsIndependentFromTheAiOneAndThePlainResultWinsWhenThereIsNoAi() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String attempt = answered(ana, course, exercises, 0, "respuesta");
        selfResult(ana, course, exercises, 0, attempt, "PARTIAL");

        attemptsOf(ana, course, exercises).andExpect(jsonPath("$.attempts[0].selfResult").value("PARTIAL"))
                .andExpect(jsonPath("$.attempts[0].aiResult").doesNotExist());
    }

    // ---------- validación (AC-13) ----------

    @Test
    void ac13_theAnswerMustHaveBetweenOneAndTwoThousandCharactersAndNoControlCharacters() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);

        for (String bad : new String[] {"", "   ", "\n\n", "x".repeat(2001), "con\u0000control", "tab\u0007"}) {
            answer(ana, course, exercises, 0, bad).andExpect(status().isBadRequest());
        }
        answer(ana, course, exercises, 0, "x".repeat(2000)).andExpect(status().isCreated());
        postAs(ana, piecePath(course, exercises) + "/exercises/0/attempts", Map.of()).andExpect(status().isBadRequest());
        assertThat(rows("exercise_attempt")).isEqualTo(1);
    }

    @Test
    void ac13_theExerciseNumberMustExistInThePiece() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);

        for (String item : new String[] {"3", "-1", "99", "uno", "1.5"}) {
            postAs(ana, piecePath(course, exercises) + "/exercises/" + item + "/attempts", Map.of("answer", "x")).andExpect(status().isNotFound());
        }
        assertThat(rows("exercise_attempt")).isZero();
    }

    @Test
    void ac13_aPieceThatIsNotOfExercisesHasNoExercises() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        answer(ana, course, piece(course, "LESSON", 0), 0, "x").andExpect(status().isNotFound());
        answer(ana, course, piece(course, "EXAM", 0), 0, "x").andExpect(status().isNotFound());
    }

    // ---------- mientras se genera (AC-12) ----------

    @Test
    void ac12_exercisesThatAreNotGeneratedYetCannotBeAttempted() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 1);
        owner.update("UPDATE course_piece SET status = 'PENDING', content_json = NULL WHERE id = ?", exercises);

        answer(ana, course, exercises, 0, "x").andExpect(status().isConflict());
        assertThat(rows("exercise_attempt")).isZero();
    }

    // ---------- historial (AC-11) ----------

    @Test
    void ac11_theHistoryOfAnExercisePieceListsTheAttemptsNewestFirstWithTheirResult() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String first = answered(ana, course, exercises, 0, "primero");
        owner.update("UPDATE exercise_attempt SET created_at = now() - interval '1 hour' WHERE id = ?::uuid", first);
        String second = answered(ana, course, exercises, 2, "segundo");
        selfResult(ana, course, exercises, 2, second, "CORRECT");

        attemptsOf(ana, course, exercises).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("EXERCISES")).andExpect(jsonPath("$.attempts", hasSize(2)))
                .andExpect(jsonPath("$.attempts[0].id").value(second)).andExpect(jsonPath("$.attempts[0].itemIndex").value(2))
                .andExpect(jsonPath("$.attempts[0].answer").value("segundo")).andExpect(jsonPath("$.attempts[0].selfResult").value("CORRECT"))
                .andExpect(jsonPath("$.attempts[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.attempts[1].id").value(first)).andExpect(jsonPath("$.attempts[1].itemIndex").value(0));
    }

    @Test
    void ac11_theHistoryOnlyHasMyAttemptsOfThatPiece() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        answered(ana, course, piece(course, "EXERCISES", 0), 0, "de la primera pieza");
        answered(ana, course, piece(course, "EXERCISES", 1), 0, "de la segunda pieza");

        attemptsOf(ana, course, piece(course, "EXERCISES", 0)).andExpect(jsonPath("$.attempts", hasSize(1)))
                .andExpect(jsonPath("$.attempts[0].answer").value("de la primera pieza"));
    }

    @Test
    void ac11_anAttemptNeverChangesEvenIfTheCourseIsQueuedAndResumedAgain() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String attempt = answered(ana, course, exercises, 0, "mi respuesta");
        selfResult(ana, course, exercises, 0, attempt, "CORRECT");
        owner.update("UPDATE course SET status = 'PENDING_GENERATION' WHERE id = ?", course);
        modelDoes(happy(2));
        generator.processPending();

        attemptsOf(ana, course, exercises).andExpect(jsonPath("$.attempts[0].answer").value("mi respuesta"))
                .andExpect(jsonPath("$.attempts[0].selfResult").value("CORRECT"));
    }

    @Test
    void ac5_theSolutionIsStillInTheOwnersExportBecauseItIsTheirOwnContent() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        String body = export(ana, course).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.topics[0].chapters[0].exercises[0].solution")).isEqualTo("Solución uno");
    }
}
