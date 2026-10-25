package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AC-3, AC-5 y AC-7 (ADR-43, 47): el temario del modelo solo se usa si pasa un esquema estricto. */
class OutlineParserTest {

    private final OutlineParser parser = new OutlineParser();

    private static String outline(int topics, int chapters, boolean appropriate) {
        return GenerationTestBase.outlineJson(topics, chapters, appropriate);
    }

    @Test
    void ac3_aValidOutlineBecomesTopicsAndChapters() {
        OutlineParser.Outline result = parser.parse(outline(3, 2, true), 3).orElseThrow();

        assertThat(result.appropriate()).isTrue();
        assertThat(result.topics()).hasSize(3);
        OutlineParser.Topic first = result.topics().get(0);
        assertThat(first.title()).isEqualTo("Tema 1 de prueba");
        assertThat(first.summary()).isEqualTo("Resumen del tema 1");
        assertThat(first.chapters()).hasSize(2);
        assertThat(first.chapters().get(0).title()).isEqualTo("Capítulo 1.1");
        assertThat(first.chapters().get(0).objective()).isEqualTo("Objetivo del capítulo 1.1");
    }

    @Test
    void ac3_theNumberOfTopicsMustBeExactlyTheOneInTheSheet() {
        assertThat(parser.parse(outline(3, 2, true), 4)).isEmpty();
        assertThat(parser.parse(outline(5, 2, true), 4)).isEmpty();
        assertThat(parser.parse(outline(4, 2, true), 4)).isPresent();
    }

    @Test
    void ac3_eachTopicNeedsBetweenTwoAndFourChapters() {
        assertThat(parser.parse(outline(2, 1, true), 2)).isEmpty();
        assertThat(parser.parse(outline(2, 5, true), 2)).isEmpty();
        assertThat(parser.parse(outline(2, 2, true), 2)).isPresent();
        assertThat(parser.parse(outline(2, 4, true), 2)).isPresent();
    }

    @Test
    void ac5_aJsonInsideMarkdownFencesIsAcceptedButTextAroundItIsNot() {
        assertThat(parser.parse("```json\n" + outline(2, 2, true) + "\n```", 2)).isPresent();
        assertThat(parser.parse("Aquí tienes el temario: " + outline(2, 2, true), 2)).isEmpty();
        assertThat(parser.parse(outline(2, 2, true) + " ¡espero que te guste!", 2)).isEmpty();
    }

    @Test
    void ac5_titlesMustBeUniqueWithinTheCourse() {
        String duplicated = "{\"appropriate\":true,\"topics\":[{\"title\":\"Igual\",\"summary\":\"s\",\"chapters\":[{\"title\":\"A\",\"objective\":\"o\"},{\"title\":\"B\",\"objective\":\"o\"}]},"
                + "{\"title\":\"Igual\",\"summary\":\"s\",\"chapters\":[{\"title\":\"C\",\"objective\":\"o\"},{\"title\":\"D\",\"objective\":\"o\"}]}]}";
        String chapterTwice = "{\"appropriate\":true,\"topics\":[{\"title\":\"T1\",\"summary\":\"s\",\"chapters\":[{\"title\":\"A\",\"objective\":\"o\"},{\"title\":\"A\",\"objective\":\"o\"}]}]}";

        assertThat(parser.parse(duplicated, 2)).isEmpty();
        assertThat(parser.parse(chapterTwice, 1)).isEmpty();
    }

    @Test
    void ac5_lengthsAreBoundedAndTextsCannotBeBlankOrHaveControlCharacters() {
        String longTitle = outline(1, 2, true).replace("Tema 1 de prueba", "T".repeat(121));
        String longSummary = outline(1, 2, true).replace("Resumen del tema 1", "r".repeat(301));
        String longObjective = outline(1, 2, true).replace("Objetivo del capítulo 1.1", "o".repeat(201));
        String blank = outline(1, 2, true).replace("Tema 1 de prueba", "   ");
        String control = outline(1, 2, true).replace("Tema 1 de prueba", "Tema\\u0000raro");

        for (String raw : new String[] {longTitle, longSummary, longObjective, blank, control}) assertThat(parser.parse(raw, 1)).as(raw).isEmpty();
    }

    @Test
    void ac5_markupInTheTitlesIsRejectedBecauseTitlesAreShortAndPlain() {
        assertThat(parser.parse(outline(1, 2, true).replace("Tema 1 de prueba", "<script>alert(1)</script>"), 1)).isEmpty();
        assertThat(parser.parse(outline(1, 2, true).replace("Capítulo 1.1", "<img src=x onerror=alert(1)>"), 1)).isEmpty();
    }

    @Test
    void ac5_garbageIsInvalidWithoutThrowing() {
        for (String raw : new String[] {"", "   ", "no es json", "{", "[]", "null", "{\"topics\":[]}", "{\"appropriate\":true}", "{\"appropriate\":\"sí\",\"topics\":[]}",
                "{\"appropriate\":true,\"topics\":\"nada\"}"}) {
            assertThat(parser.parse(raw, 1)).as(raw).isEmpty();
        }
        assertThat(parser.parse(null, 1)).isEmpty();
    }

    @Test
    void ac7_theModelAssessmentOfSuitabilityIsReadWithItsCategory() {
        OutlineParser.Outline result = parser.parse(outline(2, 2, false), 2).orElseThrow();

        assertThat(result.appropriate()).isFalse();
        assertThat(result.category()).isEqualTo("VIOLENCE");
    }

    @Test
    void ac7_anUnsuitableOutlineMayComeWithoutTopicsAndIsStillRead() {
        OutlineParser.Outline result = parser.parse("{\"appropriate\":false,\"category\":\"EXPLICIT\",\"topics\":[]}", 3).orElseThrow();

        assertThat(result.appropriate()).isFalse();
        assertThat(result.topics()).isEmpty();
    }

    @Test
    void ac3_theSchemaVersionIfPresentMustBeOne() {
        assertThat(parser.parse(outline(2, 2, true).replace("\"schemaVersion\":1", "\"schemaVersion\":2"), 2)).isEmpty();
        assertThat(parser.parse(outline(2, 2, true).replace("\"schemaVersion\":1,", ""), 2)).isPresent();
    }
}
