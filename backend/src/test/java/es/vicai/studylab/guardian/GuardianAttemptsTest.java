package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-4, AC-9 y AC-13: los intentos de ejercicio y de examen del alumno, con su respuesta, y el recorte de lo que se devuelve. */
class GuardianAttemptsTest extends GuardianTestBase {

    private String attempts(Family f) throws Exception {
        return panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/attempts").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ---------- ejercicios ----------

    @Test
    void ac4_anExerciseAttemptShowsTheStatementTheStudentsAnswerAndBothVerdicts() throws Exception {
        Family f = family();
        UUID exercises = piece(f.course(), "EXERCISES", 0);
        String attempt = answered(f.hugo(), f.course(), exercises, 1, "mi respuesta " + SECRET);
        selfResult(f.hugo(), f.course(), exercises, 1, attempt, "PARTIAL").andExpect(status().isNoContent());
        owner.update("UPDATE exercise_attempt SET ai_result = 'INCORRECT', ai_comment = 'Revisa el segundo paso.' WHERE id = ?::uuid", attempt);

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/attempts").andExpect(status().isOk()).andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.pieces", hasSize(1))).andExpect(jsonPath("$.pieces[0].pieceId").value(exercises.toString())).andExpect(jsonPath("$.pieces[0].kind").value("EXERCISES"))
                .andExpect(jsonPath("$.pieces[0].title").isNotEmpty()).andExpect(jsonPath("$.pieces[0].exercises", hasSize(1))).andExpect(jsonPath("$.pieces[0].exercises[0].item").value(1))
                .andExpect(jsonPath("$.pieces[0].exercises[0].statement").value("Segundo ejercicio")).andExpect(jsonPath("$.pieces[0].exercises[0].attempts[0].answer").value("mi respuesta " + SECRET))
                .andExpect(jsonPath("$.pieces[0].exercises[0].attempts[0].selfResult").value("PARTIAL")).andExpect(jsonPath("$.pieces[0].exercises[0].attempts[0].aiResult").value("INCORRECT"))
                .andExpect(jsonPath("$.pieces[0].exercises[0].attempts[0].aiComment").value("Revisa el segundo paso.")).andExpect(jsonPath("$.pieces[0].exercises[0].attempts[0].createdAt").isNotEmpty());
    }

    @Test
    void ac4_theAttemptsOfAnExerciseAreNewestFirstAndGroupedByExercise() throws Exception {
        Family f = family();
        UUID exercises = piece(f.course(), "EXERCISES", 0);
        String old = answered(f.hugo(), f.course(), exercises, 0, "primera");
        owner.update("UPDATE exercise_attempt SET created_at = now() - interval '2 hours' WHERE id = ?::uuid", old);
        answered(f.hugo(), f.course(), exercises, 0, "segunda");
        answered(f.hugo(), f.course(), exercises, 2, "del tercero");

        String panel = attempts(f);

        assertThat(JsonPath.<java.util.List<Integer>>read(panel, "$.pieces[0].exercises[*].item")).containsExactly(0, 2);
        assertThat(JsonPath.<java.util.List<String>>read(panel, "$.pieces[0].exercises[0].attempts[*].answer")).containsExactly("segunda", "primera");
    }

    @Test
    void ac4_theSolutionAndExplanationOfAnExerciseAreNotShown() throws Exception {
        Family f = family();
        answered(f.hugo(), f.course(), piece(f.course(), "EXERCISES", 0), 0, "algo");

        assertThat(attempts(f)).doesNotContain("Solución uno").doesNotContain("Explicación uno").doesNotContain("solution").doesNotContain("explanation");
    }

    @Test
    void ac4_aCourseWithoutAttemptsHasAnEmptyListNotAnError() throws Exception {
        Family f = family();

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/attempts").andExpect(status().isOk()).andExpect(jsonPath("$.pieces", hasSize(0))).andExpect(jsonPath("$.truncated").value(false));
    }

    // ---------- exámenes ----------

    @Test
    void ac4_aSubmittedExamShowsItsMarkAndTheQuestionsTheStudentFailed() throws Exception {
        Family f = family();
        UUID exam = piece(f.course(), "EXAM", 0);
        examTaken(f.hugo(), f.course(), exam, 1, 2, 3, 1, 1); // la 4.ª es la única fallada (la correcta es la 0)

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/attempts").andExpect(status().isOk()).andExpect(jsonPath("$.pieces", hasSize(1))).andExpect(jsonPath("$.pieces[0].kind").value("EXAM"))
                .andExpect(jsonPath("$.pieces[0].exams", hasSize(1))).andExpect(jsonPath("$.pieces[0].exams[0].correct").value(4)).andExpect(jsonPath("$.pieces[0].exams[0].total").value(5))
                .andExpect(jsonPath("$.pieces[0].exams[0].percent").value(80)).andExpect(jsonPath("$.pieces[0].exams[0].passed").value(true))
                .andExpect(jsonPath("$.pieces[0].exams[0].submittedAt").isNotEmpty()).andExpect(jsonPath("$.pieces[0].exams[0].failed", hasSize(1)))
                .andExpect(jsonPath("$.pieces[0].exams[0].failed[0]").value("Pregunta 4"));
    }

    @Test
    void ac4_anExamStartedButNeverSubmittedIsNotListedAndTheCorrectOptionsAreNotShown() throws Exception {
        Family f = family();
        UUID exam = piece(f.course(), "EXAM", 0);
        examStarted(f.hugo(), f.course(), exam);
        examTaken(f.hugo(), f.course(), exam, ALL_WRONG);

        String panel = attempts(f);

        assertThat(JsonPath.<java.util.List<Object>>read(panel, "$.pieces[0].exams")).hasSize(1);
        assertThat(panel).doesNotContain("Porque sí").doesNotContain("\"correctOption\"").doesNotContain("explanation");
    }

    @Test
    void ac4_theExamAttemptsAreNewestFirst() throws Exception {
        Family f = family();
        UUID exam = piece(f.course(), "EXAM", 0);
        String first = examTaken(f.hugo(), f.course(), exam, ALL_WRONG);
        owner.update("UPDATE exam_attempt SET submitted_at = now() - interval '1 day' WHERE id = ?::uuid", first);
        examTaken(f.hugo(), f.course(), exam, ALL_RIGHT);

        assertThat(JsonPath.<java.util.List<Integer>>read(attempts(f), "$.pieces[0].exams[*].percent")).containsExactly(100, 0);
    }

    // ---------- recortes ----------

    @Test
    void ac4_onlyTheLastTwentyAttemptsOfEachExerciseAreReturnedAndItIsSaid() throws Exception {
        Family f = family();
        UUID exercises = piece(f.course(), "EXERCISES", 0);
        for (int i = 0; i < 25; i++) {
            owner.update("INSERT INTO exercise_attempt (id, owner_id, course_id, piece_id, item_index, answer, created_at) VALUES (gen_random_uuid(), ?, ?, ?, 0, ?, now() - CAST(? AS interval))",
                    f.hugoId(), f.course(), exercises, "intento " + i, (i + 1) + " minutes");
        }

        String panel = attempts(f);

        assertThat(JsonPath.<Boolean>read(panel, "$.truncated")).isTrue();
        assertThat(JsonPath.<java.util.List<String>>read(panel, "$.pieces[0].exercises[0].attempts[*].answer")).hasSize(20).first().isEqualTo("intento 0");
    }

    @Test
    void ac4_atMostTwoHundredAttemptsInTotalAreReturned() throws Exception {
        Family f = family();
        for (int p = 0; p < 2; p++) {
            UUID exercises = piece(f.course(), "EXERCISES", p);
            for (int item = 0; item < 8; item++) {
                for (int i = 0; i < 20; i++) {
                    owner.update("INSERT INTO exercise_attempt (id, owner_id, course_id, piece_id, item_index, answer, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, 'x', now() - CAST(? AS interval))",
                            f.hugoId(), f.course(), exercises, item, (i + 1) + " minutes");
                }
            }
        }

        String panel = attempts(f);

        int total = JsonPath.<java.util.List<java.util.List<Object>>>read(panel, "$.pieces[*].exercises[*].attempts").stream().mapToInt(java.util.List::size).sum();
        assertThat(total).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(panel, "$.truncated")).isTrue();
    }

    // ---------- texto inerte y auditoría ----------

    @Test
    void ac11_hostileTextOfAnAnswerIsReturnedAsPlainJsonText() throws Exception {
        Family f = family();
        String hostile = "<script>alert(1)</script> [clic](javascript:alert(1)) \" ' ; DROP TABLE x; --";
        answered(f.hugo(), f.course(), piece(f.course(), "EXERCISES", 0), 0, hostile);

        panelGet(f.ana(), f.hugoId(), "/courses/" + f.course() + "/attempts").andExpect(status().isOk()).andExpect(jsonPath("$.pieces[0].exercises[0].attempts[0].answer").value(hostile))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void ac9_everyConsultationOfTheAttemptsIsAuditedWithIdsAndNeverTheAnswers() throws Exception {
        Family f = family();
        answered(f.hugo(), f.course(), piece(f.course(), "EXERCISES", 0), 0, "respuesta " + SECRET);

        attempts(f);

        assertThat(guardianViews()).hasSize(1);
        var view = guardianViews().get(0);
        assertThat(view.get("actor_user_id")).isEqualTo(f.anaId());
        assertThat(view.get("subject_user_id")).isEqualTo(f.hugoId());
        assertThat((String) view.get("details")).contains("ATTEMPTS").contains(f.course().toString()).doesNotContain(SECRET).doesNotContain("respuesta");
    }
}
