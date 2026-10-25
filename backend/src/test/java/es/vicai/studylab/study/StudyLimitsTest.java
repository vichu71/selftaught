package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** AC-15 (ADR-53): límites por usuario contados en la BD. Contexto propio: cambia la configuración. */
@TestPropertySource(properties = {"app.study.max-submissions-per-hour=5", "app.study.ai-grades-per-day=2", "app.study.max-attempts-per-piece=3"})
class StudyLimitsTest extends StudyTestBase {

    private static final String PARTIAL = "{\"verdict\":\"PARTIAL\",\"comment\":\"Casi.\"}";

    @Test
    void ac15_theSixthSubmissionInAnHourIsRefusedAndExercisesAndExamsCountTogether() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        for (int i = 0; i < 3; i++) answer(ana, course, exercises, 0, "intento " + i).andExpect(status().isCreated());
        examTaken(ana, course, piece(course, "EXAM", 0), ALL_RIGHT);
        examTaken(ana, course, piece(course, "EXAM", 0), ALL_WRONG);

        answer(ana, course, exercises, 0, "uno de más").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("STUDY_RATE_LIMITED"));
        String open = examStarted(ana, course, piece(course, "EXAM", 0));
        submitExam(ana, course, piece(course, "EXAM", 0), open, ALL_RIGHT).andExpect(status().isTooManyRequests());
    }

    @Test
    void ac15_aRefusedSubmissionLeavesNoTraceAndOtherUsersAreNotAffected() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID anaCourse = generated("ana@example.com", 2, true);
        UUID luisCourse = generated("luis@example.com", 2, true);
        for (int i = 0; i < 5; i++) answer(ana, anaCourse, piece(anaCourse, "EXERCISES", 0), 0, "x").andExpect(status().isCreated());
        int rows = rows("exercise_attempt");

        answer(ana, anaCourse, piece(anaCourse, "EXERCISES", 0), 0, "x").andExpect(status().isTooManyRequests());
        answer(luis, luisCourse, piece(luisCourse, "EXERCISES", 0), 0, "x").andExpect(status().isCreated());

        assertThat(rows("exercise_attempt")).isEqualTo(rows + 1); // solo la de Luis
    }

    @Test
    void ac15_submissionsOlderThanAnHourNoLongerCount() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        for (int i = 0; i < 5; i++) answer(ana, course, exercises, 0, "x").andExpect(status().isCreated());
        owner.update("UPDATE exercise_attempt SET created_at = now() - interval '2 hours'");

        answer(ana, course, exercises, 0, "x").andExpect(status().isCreated());
    }

    @Test
    void ac15_onlyTheLatestAttemptsOfEachPieceAreKept() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        for (int i = 1; i <= 3; i++) {
            answer(ana, course, exercises, 0, "intento " + i).andExpect(status().isCreated());
            owner.update("UPDATE exercise_attempt SET created_at = now() - interval '1 day' * (4 - ?) WHERE answer = ?", i, "intento " + i); // más antiguo = intento 1
        }
        owner.update("UPDATE exercise_attempt SET created_at = created_at - interval '3 hours'"); // fuera de la ventana de la hora

        answer(ana, course, exercises, 0, "intento 4").andExpect(status().isCreated());

        assertThat(owner.queryForList("SELECT answer FROM exercise_attempt ORDER BY created_at", String.class)).containsExactly("intento 2", "intento 3", "intento 4");
    }

    @Test
    void ac15_theHistoryOfAnExamKeepsOnlyTheLatestHandedInAttempts() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exam = piece(course, "EXAM", 0);
        examTaken(ana, course, exam, ALL_WRONG);
        owner.update("UPDATE exam_attempt SET submitted_at = now() - interval '5 hours'");
        String second = examTaken(ana, course, exam, ALL_RIGHT);
        owner.update("UPDATE exam_attempt SET submitted_at = now() - interval '4 hours' WHERE id = ?::uuid", second);
        examTaken(ana, course, exam, ALL_RIGHT);

        assertThat(owner.queryForObject("SELECT count(*) FROM exam_attempt WHERE status = 'SUBMITTED'", Integer.class)).isEqualTo(3);
        owner.update("UPDATE exam_attempt SET submitted_at = submitted_at - interval '3 hours'"); // todos fuera de la ventana de la hora
        examTaken(ana, course, exam, ALL_RIGHT);

        assertThat(owner.queryForObject("SELECT count(*) FROM exam_attempt WHERE status = 'SUBMITTED'", Integer.class)).isEqualTo(3);
    }

    @Test
    void ac15_theThirdAiCorrectionOfTheDayIsRefusedAndFailedOnesCountToo() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String a = answered(ana, course, exercises, 0, "a");
        String b = answered(ana, course, exercises, 1, "b");
        String c = answered(ana, course, exercises, 2, "c");
        fake.reset();
        fake.respondWith(PARTIAL, 100, 50);
        aiGrade(ana, course, exercises, 0, a).andExpect(status().isOk());
        fake.reset();
        fake.respondWith("no es json", 100, 50);
        aiGrade(ana, course, exercises, 1, b).andExpect(status().isBadGateway()); // fallida, pero cuenta
        int calls = fake.calls().size();

        aiGrade(ana, course, exercises, 2, c).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AI_GRADING_LIMIT"));

        assertThat(fake.calls().size()).isEqualTo(calls);
    }

    @Test
    void ac15_correctionsOlderThanADayNoLongerCountAndOtherUsersAreNotAffected() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID luisCourse = generated("luis@example.com", 2, true);
        UUID exercises = piece(course, "EXERCISES", 0);
        String a = answered(ana, course, exercises, 0, "a");
        String b = answered(ana, course, exercises, 1, "b");
        String c = answered(ana, course, exercises, 2, "c");
        fake.reset();
        fake.respondWith(PARTIAL, 100, 50);
        aiGrade(ana, course, exercises, 0, a).andExpect(status().isOk());
        aiGrade(ana, course, exercises, 1, b).andExpect(status().isOk());
        aiGrade(ana, course, exercises, 2, c).andExpect(status().isTooManyRequests());

        String other = answered(luis, luisCourse, piece(luisCourse, "EXERCISES", 0), 0, "d");
        aiGrade(luis, luisCourse, piece(luisCourse, "EXERCISES", 0), 0, other).andExpect(status().isOk());
        owner.update("UPDATE exercise_attempt SET ai_requested_at = now() - interval '2 days' WHERE ai_requested_at IS NOT NULL AND id = ?::uuid", a);
        aiGrade(ana, course, exercises, 2, c).andExpect(status().isOk());
    }

    @Test
    void ac15_theDefaultsAreTheAgreedOnes() {
        StudyProperties defaults = new StudyProperties(50, 120, 20, 200);

        assertThat(defaults.passPercent()).isEqualTo(50);
        assertThat(defaults.maxSubmissionsPerHour()).isEqualTo(120);
        assertThat(defaults.aiGradesPerDay()).isEqualTo(20);
        assertThat(defaults.maxAttemptsPerPiece()).isEqualTo(200);
    }
}
