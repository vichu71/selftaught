package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AC-6 (ADR-52): el veredicto del modelo solo se usa si pasa un esquema estricto. */
class GradeParserTest {

    private final GradeParser parser = new GradeParser();

    private static String grade(String verdict, String comment) {
        return "{\"verdict\":\"" + verdict + "\",\"comment\":\"" + comment + "\"}";
    }

    @Test
    void ac6_aValidVerdictIsReadWithItsComment() {
        GradeParser.Grade result = parser.parse(grade("PARTIAL", "Vas bien, pero falta mencionar la memoria.")).orElseThrow();

        assertThat(result.verdict()).isEqualTo("PARTIAL");
        assertThat(result.comment()).isEqualTo("Vas bien, pero falta mencionar la memoria.");
    }

    @Test
    void ac6_theThreeVerdictsAreTheOnlyOnesAccepted() {
        for (String ok : new String[] {"CORRECT", "PARTIAL", "INCORRECT"}) assertThat(parser.parse(grade(ok, "ok"))).as(ok).isPresent();
        for (String bad : new String[] {"correct", "BIEN", "", "100", "CORRECT PARTIAL"}) assertThat(parser.parse(grade(bad, "ok"))).as(bad).isEmpty();
    }

    @Test
    void ac6_theCommentIsRequiredAndBoundedAndPlain() {
        assertThat(parser.parse("{\"verdict\":\"CORRECT\"}")).isEmpty();
        assertThat(parser.parse(grade("CORRECT", ""))).isEmpty();
        assertThat(parser.parse(grade("CORRECT", "   "))).isEmpty();
        assertThat(parser.parse(grade("CORRECT", "c".repeat(500)))).isPresent();
        assertThat(parser.parse(grade("CORRECT", "c".repeat(501)))).isEmpty();
        assertThat(parser.parse(grade("CORRECT", "<script>alert(1)</script>"))).isEmpty();
        assertThat(parser.parse(grade("CORRECT", "con\\u0000control"))).isEmpty();
    }

    @Test
    void ac6_aJsonInsideFencesIsAcceptedButTextAroundItIsNot() {
        assertThat(parser.parse("```json\n" + grade("CORRECT", "ok") + "\n```")).isPresent();
        assertThat(parser.parse("Mi valoración: " + grade("CORRECT", "ok"))).isEmpty();
        assertThat(parser.parse(grade("CORRECT", "ok") + " ¡suerte!")).isEmpty();
    }

    @Test
    void ac6_garbageIsInvalidWithoutThrowing() {
        for (String raw : new String[] {"", "   ", "bien", "{", "[]", "null", "{\"verdict\":1,\"comment\":\"x\"}", "{\"verdict\":\"CORRECT\",\"comment\":5}"}) {
            assertThat(parser.parse(raw)).as(raw).isEmpty();
        }
        assertThat(parser.parse(null)).isEmpty();
    }
}
