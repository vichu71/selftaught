package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-4 (ADR-56): el tutor refuerza con un resumen corto de lo fallado en los ejercicios del capítulo, nunca con lo que escribió el alumno. */
class TutorContextTest extends TutorTestBase {

    private String data(String session, UUID course, UUID lesson) throws Exception {
        tutorAnswers(ANSWER);
        ask(session, course, lesson, "¿Me ayudas?").andExpect(status().isCreated());
        return lastTutorCall().dataMessage();
    }

    @Test
    void ac4_failedAndPartialExercisesOfTheChapterAreSummarisedByStatementAndResult() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID exercises = piece(course, "EXERCISES", 0); // capítulo 1.1, el de la lección 0
        selfResult(ana, course, exercises, 0, answered(ana, course, exercises, 0, "RESPUESTA-SECRETA-DEL-ALUMNO"), "INCORRECT");
        selfResult(ana, course, exercises, 1, answered(ana, course, exercises, 1, "otra respuesta privada"), "PARTIAL");
        selfResult(ana, course, exercises, 2, answered(ana, course, exercises, 2, "una tercera"), "CORRECT");

        String data = data(ana, course, lesson(course, 0));

        assertThat(data).contains("<fallos>").contains("Primer ejercicio").contains("Segundo ejercicio").doesNotContain("Tercer ejercicio");
        String fallos = data.substring(data.indexOf("<fallos>"), data.indexOf("</fallos>"));
        assertThat(fallos.toLowerCase()).contains("fallado").contains("a medias");
    }

    @Test
    void ac4_whatTheStudentWroteAndTheSolutionsNeverReachTheModel() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID exercises = piece(course, "EXERCISES", 0);
        selfResult(ana, course, exercises, 0, answered(ana, course, exercises, 0, "RESPUESTA-SECRETA-DEL-ALUMNO"), "INCORRECT");

        String data = data(ana, course, lesson(course, 0));

        assertThat(data).doesNotContain("RESPUESTA-SECRETA-DEL-ALUMNO").doesNotContain("Solución uno").doesNotContain("Explicación uno");
    }

    @Test
    void ac4_withoutFailuresNothingIsSent() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID exercises = piece(course, "EXERCISES", 0);
        selfResult(ana, course, exercises, 0, answered(ana, course, exercises, 0, "bien"), "CORRECT");

        assertThat(data(ana, course, lesson(course, 0))).doesNotContain("<fallos>");
    }

    @Test
    void ac4_aCourseWithoutAnyAttemptSendsNothing() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");

        assertThat(data(ana, course, lesson(course, 0))).doesNotContain("<fallos>");
    }

    @Test
    void ac4_onlyTheExercisesOfThatChapterAreUsed() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID otherChapter = piece(course, "EXERCISES", 1); // capítulo 1.2
        selfResult(ana, course, otherChapter, 0, answered(ana, course, otherChapter, 0, "x"), "INCORRECT");

        assertThat(data(ana, course, lesson(course, 0))).doesNotContain("<fallos>");
        assertThat(data(ana, course, lesson(course, 1))).contains("<fallos>").contains("Primer ejercicio");
    }

    @Test
    void ac4_theLatestAttemptOfEachExerciseDecidesWhetherItCountsAsFailed() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID exercises = piece(course, "EXERCISES", 0);
        String first = answered(ana, course, exercises, 0, "mal");
        selfResult(ana, course, exercises, 0, first, "INCORRECT");
        owner.update("UPDATE exercise_attempt SET created_at = now() - interval '2 hours' WHERE id = ?::uuid", first);
        String second = answered(ana, course, exercises, 0, "ahora bien");
        selfResult(ana, course, exercises, 0, second, "CORRECT");

        assertThat(data(ana, course, lesson(course, 0))).doesNotContain("<fallos>");
    }

    @Test
    void ac4_theAiVerdictCountsWhenTheStudentDidNotMarkTheAttempt() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID exercises = piece(course, "EXERCISES", 0);
        String attempt = answered(ana, course, exercises, 1, "dudosa");
        owner.update("UPDATE exercise_attempt SET ai_result = 'INCORRECT' WHERE id = ?::uuid", attempt);

        assertThat(data(ana, course, lesson(course, 0))).contains("<fallos>").contains("Segundo ejercicio");
    }

    @Test
    void ac4_theStudentsOwnMarkWinsOverTheAiVerdict() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID exercises = piece(course, "EXERCISES", 0);
        String attempt = answered(ana, course, exercises, 1, "dudosa");
        owner.update("UPDATE exercise_attempt SET ai_result = 'INCORRECT', self_result = 'CORRECT' WHERE id = ?::uuid", attempt);

        assertThat(data(ana, course, lesson(course, 0))).doesNotContain("<fallos>");
    }

    @Test
    void ac4_aSummaryNeverGrowsWithoutLimit() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = readyCourse("ana@example.com");
        UUID exercises = piece(course, "EXERCISES", 0);
        owner.update("UPDATE course_piece SET content_json = CAST(? AS jsonb) WHERE id = ?", longExercises(), exercises);
        for (int i = 0; i < 8; i++) selfResult(ana, course, exercises, i, answered(ana, course, exercises, i, "x"), "INCORRECT");

        String data = data(ana, course, lesson(course, 0));

        String fallos = data.substring(data.indexOf("<fallos>"), data.indexOf("</fallos>"));
        assertThat(fallos.length()).isLessThan(2_500);
    }

    private static String longExercises() {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"exercises\":[");
        for (int i = 0; i < 8; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"difficulty\":\"LOW\",\"statement\":\"").append(("Enunciado muy largo " + i + " ").repeat(80)).append("\",\"solution\":\"s\",\"explanation\":\"e\"}");
        }
        return sb.append("]}").toString();
    }
}
