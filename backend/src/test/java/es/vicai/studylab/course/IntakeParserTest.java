package es.vicai.studylab.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** AC-3 y AC-4 (ADR-38): la salida del modelo solo se usa si pasa un esquema estricto; los ids los genera el servidor. */
class IntakeParserTest {

    private final IntakeParser parser = new IntakeParser();

    static String question(String field, String text, String... options) {
        return "{\"field\":\"" + field + "\",\"text\":\"" + text + "\",\"options\":[" + String.join(",", options) + "]}";
    }

    static String option(String label, String value, boolean isDefault) {
        return "{\"label\":\"" + label + "\",\"value\":\"" + value + "\"" + (isDefault ? ",\"default\":true" : "") + "}";
    }

    static String json(boolean appropriate, String... questions) {
        return "{\"appropriate\":" + appropriate + ",\"category\":\"" + (appropriate ? "NONE" : "VIOLENCE") + "\",\"questions\":["
                + String.join(",", questions) + "]}";
    }

    private static final String FOCUS = question("focus", "¿En qué quieres centrarte?",
            option("Gramática", "Gramática y vocabulario", true), option("Conversación", "Conversación", false));

    @Test
    void ac3_aValidAnswerBecomesQuestionsWithServerGeneratedIds() {
        IntakeParser.Result result = parser.parse(json(true, FOCUS)).orElseThrow();

        assertThat(result.appropriate()).isTrue();
        assertThat(result.questions()).hasSize(1);
        IntakeParser.Question q = result.questions().get(0);
        assertThat(q.id()).isEqualTo("q1");
        assertThat(q.field()).isEqualTo("focus");
        assertThat(q.text()).isEqualTo("¿En qué quieres centrarte?");
        assertThat(q.options().stream().map(IntakeParser.Option::id)).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isNotBlank());
    }

    @Test
    void ac3_everyQuestionEndsWithTheAsYouSeeFitOptionWhichMeansTheDefault() {
        IntakeParser.Question q = parser.parse(json(true, FOCUS)).orElseThrow().questions().get(0);

        assertThat(q.options()).hasSize(3); // 2 del modelo + «Como tú veas»
        IntakeParser.Option auto = q.options().get(2);
        assertThat(auto.label()).isEqualTo("Como tú veas");
        assertThat(auto.value()).isEqualTo("Gramática y vocabulario"); // el valor de la opción por defecto
        assertThat(auto.isDefault()).isFalse();
        assertThat(q.options().stream().filter(IntakeParser.Option::isDefault)).hasSize(1);
    }

    @Test
    void ac3_zeroQuestionsIsValid() {
        assertThat(parser.parse(json(true)).orElseThrow().questions()).isEmpty();
    }

    @Test
    void ac3_idsAreSequentialPerQuestionAndAssignedByTheServerNotTheModel() {
        String withModelIds = "{\"appropriate\":true,\"questions\":[{\"id\":\"HACK\",\"field\":\"focus\",\"text\":\"a?\",\"options\":["
                + "{\"id\":\"ADMIN\",\"label\":\"x\",\"value\":\"x\",\"default\":true},{\"label\":\"y\",\"value\":\"y\"}]},"
                + "{\"field\":\"goal\",\"text\":\"b?\",\"options\":[{\"label\":\"p\",\"value\":\"p\",\"default\":true},{\"label\":\"q\",\"value\":\"q\"}]}]}";

        List<IntakeParser.Question> questions = parser.parse(withModelIds).orElseThrow().questions();

        assertThat(questions.stream().map(IntakeParser.Question::id)).containsExactly("q1", "q2");
        assertThat(questions.get(0).options().stream().map(IntakeParser.Option::id)).doesNotContain("ADMIN");
    }

    @Test
    void ac4_aJsonWrappedInMarkdownFencesIsAccepted() {
        assertThat(parser.parse("```json\n" + json(true, FOCUS) + "\n```")).isPresent();
        assertThat(parser.parse("```\n" + json(true, FOCUS) + "\n```")).isPresent();
        assertThat(parser.parse("  \n" + json(true, FOCUS) + "\n  ")).isPresent();
    }

    @Test
    void ac4_moreThanThreeQuestionsIsInvalidNotTruncated() {
        String goal = question("goal", "¿Para qué?", option("Aprobar", "Aprobar", true), option("Saber más", "Saber más", false));
        String level = question("startLevel", "¿Nivel?", option("Cero", "Desde cero", true), option("Algo", "Sé algo", false));
        String topics = question("topicsCount", "¿Cuántos temas?", option("Pocos", "4", true), option("Muchos", "8", false));

        assertThat(parser.parse(json(true, FOCUS, goal, level))).isPresent();
        assertThat(parser.parse(json(true, FOCUS, goal, level, topics))).isEmpty();
    }

    @Test
    void ac4_optionCountMustBeTwoToFourFromTheModel() {
        String one = question("focus", "¿?", option("Solo una", "Solo una", true));
        String five = question("focus", "¿?", option("a", "a", true), option("b", "b", false), option("c", "c", false), option("d", "d", false),
                option("e", "e", false));
        String four = question("focus", "¿?", option("a", "a", true), option("b", "b", false), option("c", "c", false), option("d", "d", false));

        assertThat(parser.parse(json(true, one))).isEmpty();
        assertThat(parser.parse(json(true, five))).isEmpty();
        assertThat(parser.parse(json(true, four))).isPresent();
    }

    @Test
    void ac4_exactlyOneOptionMustBeTheDefault() {
        String none = question("focus", "¿?", option("a", "a", false), option("b", "b", false));
        String two = question("focus", "¿?", option("a", "a", true), option("b", "b", true));

        assertThat(parser.parse(json(true, none))).isEmpty();
        assertThat(parser.parse(json(true, two))).isEmpty();
    }

    @Test
    void ac4_theFieldMustBeKnownAndUsedAtMostOnce() {
        String unknown = question("systemPrompt", "¿?", option("a", "a", true), option("b", "b", false));
        String second = question("focus", "¿Otra vez?", option("a", "a", true), option("b", "b", false));

        assertThat(parser.parse(json(true, unknown))).isEmpty();
        assertThat(parser.parse(json(true, FOCUS, second))).isEmpty();
    }

    @Test
    void ac4_topicsCountMustBeANumberBetween3And12() {
        String ok = question("topicsCount", "¿?", option("Pocos", "4", true), option("Muchos", "12", false));
        String low = question("topicsCount", "¿?", option("Pocos", "2", true), option("Muchos", "8", false));
        String high = question("topicsCount", "¿?", option("Pocos", "4", true), option("Muchos", "13", false));
        String text = question("topicsCount", "¿?", option("Pocos", "cuatro", true), option("Muchos", "8", false));

        assertThat(parser.parse(json(true, ok))).isPresent();
        assertThat(parser.parse(json(true, low))).isEmpty();
        assertThat(parser.parse(json(true, high))).isEmpty();
        assertThat(parser.parse(json(true, text))).isEmpty();
    }

    @Test
    void ac4_examPerTopicMustBeTrueOrFalse() {
        String ok = question("examPerTopic", "¿Examen?", option("Sí", "true", true), option("No", "false", false));
        String bad = question("examPerTopic", "¿Examen?", option("Sí", "quizá", true), option("No", "false", false));

        assertThat(parser.parse(json(true, ok))).isPresent();
        assertThat(parser.parse(json(true, bad))).isEmpty();
    }

    @Test
    void ac4_lengthsAreBounded() {
        String longText = question("focus", "x".repeat(161), option("a", "a", true), option("b", "b", false));
        String longLabel = question("focus", "¿?", option("l".repeat(81), "a", true), option("b", "b", false));
        String longValue = question("focus", "¿?", option("a", "v".repeat(121), true), option("b", "b", false));
        String emptyLabel = question("focus", "¿?", option("", "a", true), option("b", "b", false));

        assertThat(parser.parse(json(true, longText))).isEmpty();
        assertThat(parser.parse(json(true, longLabel))).isEmpty();
        assertThat(parser.parse(json(true, longValue))).isEmpty();
        assertThat(parser.parse(json(true, emptyLabel))).isEmpty();
    }

    @Test
    void ac4_garbageIsInvalidWithoutThrowing() {
        for (String raw : new String[] {"", "   ", "no es json", "{", "[]", "null", "{\"questions\":[]}", "{\"appropriate\":\"sí\",\"questions\":[]}",
                "{\"appropriate\":true,\"questions\":\"nada\"}", "Aquí tienes: " + json(true, FOCUS), json(true, FOCUS) + " fin"}) {
            Optional<IntakeParser.Result> result = parser.parse(raw);
            assertThat(result).as(raw).isEmpty();
        }
    }

    @Test
    void ac4_nullInputIsInvalid() {
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void ac8_theModelAssessmentIsReadEvenWhenThereAreNoQuestions() {
        IntakeParser.Result result = parser.parse(json(false)).orElseThrow();

        assertThat(result.appropriate()).isFalse();
        assertThat(result.category()).isEqualTo("VIOLENCE");
    }

    @Test
    void ac8_anUnknownCategoryBecomesOther() {
        IntakeParser.Result result = parser.parse("{\"appropriate\":false,\"category\":\"INVENTADA\",\"questions\":[]}").orElseThrow();

        assertThat(result.category()).isEqualTo("OTHER");
    }

    @Test
    void ac4_markupInTheTextsIsKeptAsPlainTextForTheFrontendToEscape() {
        String html = question("focus", "<img src=x onerror=alert(1)>", option("<b>a</b>", "a", true), option("b", "b", false));

        IntakeParser.Question q = parser.parse(json(true, html)).orElseThrow().questions().get(0);

        assertThat(q.text()).isEqualTo("<img src=x onerror=alert(1)>");
    }
}
