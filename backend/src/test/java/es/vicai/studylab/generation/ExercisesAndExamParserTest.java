package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AC-4 y AC-5 (ADR-43): ejercicios y examen son JSON estricto; F06 los corregirá, así que necesitan estructura. */
class ExercisesAndExamParserTest {

    private final ExercisesParser exercises = new ExercisesParser();
    private final ExamParser exam = new ExamParser();

    private static String exercise(String difficulty, String statement, String solution, String explanation) {
        return "{\"difficulty\":\"" + difficulty + "\",\"statement\":\"" + statement + "\",\"solution\":\"" + solution + "\",\"explanation\":\"" + explanation + "\"}";
    }

    private static String exercises(String... items) {
        return "{\"schemaVersion\":1,\"exercises\":[" + String.join(",", items) + "]}";
    }

    private static String question(String text, String options, int correct, String explanation) {
        return "{\"text\":\"" + text + "\",\"options\":" + options + ",\"correct\":" + correct + ",\"explanation\":\"" + explanation + "\"}";
    }

    private static String exam(String... items) {
        return "{\"schemaVersion\":1,\"questions\":[" + String.join(",", items) + "]}";
    }

    private static String fiveQuestions() {
        String[] items = new String[5];
        for (int i = 0; i < 5; i++) items[i] = question("Pregunta " + i, "[\"a\",\"b\",\"c\"]", i % 3, "Porque sí");
        return exam(items);
    }

    // ---------- ejercicios ----------

    @Test
    void ac4_validExercisesAreParsedWithTheirSolution() {
        ExercisesParser.Exercises result = exercises.parse(GenerationTestBase.exercisesJson()).orElseThrow();

        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).difficulty()).isEqualTo("LOW");
        assertThat(result.items().get(0).statement()).isEqualTo("Primer ejercicio");
        assertThat(result.items().get(0).solution()).isEqualTo("Solución uno");
        assertThat(result.items().get(0).explanation()).isEqualTo("Explicación uno");
    }

    @Test
    void ac5_theNumberOfExercisesMustBeBetweenThreeAndEight() {
        String one = exercise("LOW", "e", "s", "x");
        assertThat(exercises.parse(exercises(one, one))).isEmpty();
        assertThat(exercises.parse(exercises(one, one, one))).isPresent();
        assertThat(exercises.parse(exercises(one, one, one, one, one, one, one, one))).isPresent();
        assertThat(exercises.parse(exercises(one, one, one, one, one, one, one, one, one))).isEmpty();
    }

    @Test
    void ac5_everyExerciseNeedsAStatementAndASolution() {
        String ok = exercise("LOW", "e", "s", "x");
        assertThat(exercises.parse(exercises(ok, ok, exercise("LOW", "", "s", "x")))).isEmpty();
        assertThat(exercises.parse(exercises(ok, ok, exercise("LOW", "e", "", "x")))).isEmpty();
        assertThat(exercises.parse(exercises(ok, ok, "{\"difficulty\":\"LOW\",\"statement\":\"e\"}"))).isEmpty();
    }

    @Test
    void ac5_difficultyMustBeOneOfTheThreeLevelsAndLengthsAreBounded() {
        String ok = exercise("LOW", "e", "s", "x");
        assertThat(exercises.parse(exercises(ok, ok, exercise("IMPOSIBLE", "e", "s", "x")))).isEmpty();
        assertThat(exercises.parse(exercises(ok, ok, exercise("LOW", "e".repeat(1001), "s", "x")))).isEmpty();
        assertThat(exercises.parse(exercises(ok, ok, exercise("LOW", "e", "s".repeat(2001), "x")))).isEmpty();
        assertThat(exercises.parse(exercises(ok, ok, exercise("LOW", "e", "s", "x".repeat(1001))))).isEmpty();
    }

    @Test
    void ac5_exercisesInFencesAreAcceptedAndGarbageIsNot() {
        assertThat(exercises.parse("```json\n" + GenerationTestBase.exercisesJson() + "\n```")).isPresent();
        for (String raw : new String[] {"", "texto", "{", "[]", "{\"exercises\":\"no\"}", "Aquí: " + GenerationTestBase.exercisesJson()}) assertThat(exercises.parse(raw)).as(raw).isEmpty();
        assertThat(exercises.parse(null)).isEmpty();
    }

    // ---------- examen ----------

    @Test
    void ac4_aValidExamHasQuestionsWithOptionsOneCorrectAndAnExplanation() {
        ExamParser.Exam result = exam.parse(GenerationTestBase.examJson()).orElseThrow();

        assertThat(result.questions()).hasSize(5);
        ExamParser.Question q = result.questions().get(1);
        assertThat(q.text()).isEqualTo("Pregunta 2");
        assertThat(q.options()).containsExactly("a", "b", "c", "d");
        assertThat(q.correct()).isEqualTo(2);
        assertThat(q.explanation()).isEqualTo("Porque sí 2");
    }

    @Test
    void ac5_theNumberOfQuestionsMustBeBetweenFiveAndTwelve() {
        String q = question("p", "[\"a\",\"b\",\"c\"]", 0, "x");
        assertThat(exam.parse(exam(q, q, q, q))).isEmpty();
        assertThat(exam.parse(exam(q, q, q, q, q))).isPresent();
        assertThat(exam.parse(exam(q, q, q, q, q, q, q, q, q, q, q, q))).isPresent();
        assertThat(exam.parse(exam(q, q, q, q, q, q, q, q, q, q, q, q, q))).isEmpty();
    }

    @Test
    void ac5_eachQuestionNeedsThreeOrFourDistinctOptionsAndOneCorrectAnswerThatExists() {
        String good = question("p", "[\"a\",\"b\",\"c\"]", 0, "x");
        for (String bad : new String[] {
                question("p", "[\"a\",\"b\"]", 0, "x"),
                question("p", "[\"a\",\"b\",\"c\",\"d\",\"e\"]", 0, "x"),
                question("p", "[\"a\",\"a\",\"c\"]", 0, "x"),
                question("p", "[\"a\",\"b\",\"c\"]", 3, "x"),
                question("p", "[\"a\",\"b\",\"c\"]", -1, "x"),
                question("p", "[\"a\",\"b\",\"\"]", 0, "x"),
                "{\"text\":\"p\",\"options\":[\"a\",\"b\",\"c\"],\"explanation\":\"x\"}",
                "{\"text\":\"p\",\"options\":[\"a\",\"b\",\"c\"],\"correct\":[0,1],\"explanation\":\"x\"}",
                "{\"text\":\"p\",\"options\":[\"a\",\"b\",\"c\"],\"correct\":\"a\",\"explanation\":\"x\"}"}) {
            assertThat(exam.parse(exam(good, good, good, good, bad))).as(bad).isEmpty();
        }
    }

    @Test
    void ac5_examLengthsAreBoundedAndFencesAreAccepted() {
        String good = question("p", "[\"a\",\"b\",\"c\"]", 0, "x");
        assertThat(exam.parse(exam(good, good, good, good, question("p".repeat(301), "[\"a\",\"b\",\"c\"]", 0, "x")))).isEmpty();
        assertThat(exam.parse(exam(good, good, good, good, question("p", "[\"" + "o".repeat(151) + "\",\"b\",\"c\"]", 0, "x")))).isEmpty();
        assertThat(exam.parse(exam(good, good, good, good, question("p", "[\"a\",\"b\",\"c\"]", 0, "x".repeat(501))))).isEmpty();
        assertThat(exam.parse("```json\n" + fiveQuestions() + "\n```")).isPresent();
        assertThat(exam.parse("basura")).isEmpty();
        assertThat(exam.parse(null)).isEmpty();
    }
}
