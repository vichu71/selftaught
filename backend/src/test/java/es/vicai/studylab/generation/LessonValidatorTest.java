package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** AC-5, AC-6 y AC-13 (ADR-43, 46): la lección es Markdown acotado, sin HTML crudo, y sus citas deben existir. */
class LessonValidatorTest {

    private final LessonValidator validator = new LessonValidator();

    private static String lesson(int... citations) {
        return GenerationTestBase.lessonMd("Título de la lección", citations);
    }

    @Test
    void ac5_aWellFormedLessonIsAccepted() {
        LessonValidator.Lesson result = validator.validate(lesson(), 0).orElseThrow();

        assertThat(result.markdown()).startsWith("# Título de la lección");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void ac5_lengthIsBounded() {
        assertThat(validator.validate("# Corto\n\nPoco texto.", 0)).isEmpty();
        assertThat(validator.validate("# Largo\n\n" + "palabra ".repeat(5000), 0)).isEmpty(); // > 30 000 caracteres
        assertThat(validator.validate("", 0)).isEmpty();
        assertThat(validator.validate("   \n\n  ", 0)).isEmpty();
        assertThat(validator.validate(null, 0)).isEmpty();
    }

    @Test
    void ac5_aLessonNeedsSomeStructureAHeadingOrAtLeastTwoParagraphs() {
        String oneBlock = "Un solo bloque de texto sin título ni separación. ".repeat(15);
        String twoParagraphs = "Primer párrafo con bastante contenido para llegar al mínimo. ".repeat(5) + "\n\n" + "Segundo párrafo que también tiene su texto. ".repeat(5);

        assertThat(validator.validate(oneBlock, 0)).isEmpty();
        assertThat(validator.validate(twoParagraphs, 0)).isPresent();
    }

    @Test
    void ac5_rawHtmlIsRejected() {
        for (String html : new String[] {"<script>alert(1)</script>", "<iframe src=\"http://x\"></iframe>", "<img src=x onerror=alert(1)>", "<div onclick=\"x()\">", "<style>body{}</style>",
                "<a href=\"http://x\">enlace</a>", "<SCRIPT>alert(1)</SCRIPT>"}) {
            assertThat(validator.validate(lesson() + "\n\n" + html, 0)).as(html).isEmpty();
        }
    }

    @Test
    void ac13_linksWithDangerousSchemesAreRejected() {
        for (String link : new String[] {"[clic](javascript:alert(1))", "[clic](JavaScript:alert(1))", "[clic](data:text/html,<b>x</b>)", "[clic](vbscript:x)", "![img](data:image/png;base64,AAAA)"}) {
            assertThat(validator.validate(lesson() + "\n\n" + link, 0)).as(link).isEmpty();
        }
    }

    @Test
    void ac13_ordinaryHttpsLinksAndLatexAsPlainTextAreFine() {
        assertThat(validator.validate(lesson() + "\n\nMás en [la web](https://example.com/tema).", 0)).isPresent();
        assertThat(validator.validate(lesson() + "\n\nLa fórmula $x^2 + y^2 = z^2$ se escribe así.", 0)).isPresent();
    }

    @Test
    void ac5_aWholeLessonWrappedInAMarkdownFenceIsUnwrapped() {
        LessonValidator.Lesson result = validator.validate("```markdown\n" + lesson() + "\n```", 0).orElseThrow();

        assertThat(result.markdown()).startsWith("# Título").doesNotContain("```markdown");
    }

    @Test
    void ac5_aCodeBlockInsideTheLessonIsKept() {
        String withCode = lesson() + "\n\n```java\nSystem.out.println(1);\n```\n";

        assertThat(validator.validate(withCode, 0).orElseThrow().markdown()).contains("```java");
    }

    // ---------- citas (AC-6) ----------

    @Test
    void ac6_withMaterialTheLessonMustCiteAndEveryMarkerMustExist() {
        LessonValidator.Lesson result = validator.validate(lesson(1, 3), 3).orElseThrow();

        assertThat(result.citations()).containsExactlyInAnyOrder(1, 3);
    }

    @Test
    void ac6_aMarkerThatMatchesNoFragmentIsRejected() {
        assertThat(validator.validate(lesson(1, 4), 3)).isEmpty();
        assertThat(validator.validate(lesson(0), 3)).isEmpty();
    }

    @Test
    void ac6_withMaterialAndNoCitationTheLessonIsRejected() {
        assertThat(validator.validate(lesson(), 3)).isEmpty();
    }

    @Test
    void ac6_withoutMaterialAnyMarkerIsRejected() {
        assertThat(validator.validate(lesson(1), 0)).isEmpty();
    }

    @Test
    void ac6_aMarkdownLinkOrAListNumberIsNotMistakenForACitation() {
        String text = lesson(1) + "\n\n[ver el [2] manual](https://example.com/2)\n\n1. Primer paso\n2. Segundo paso\n";

        Optional<LessonValidator.Lesson> result = validator.validate(text, 1);

        assertThat(result).isPresent();
        assertThat(result.get().citations()).containsExactly(1);
    }

    @Test
    void ac6_theSameCitationRepeatedCountsOnce() {
        LessonValidator.Lesson result = validator.validate(lesson(2, 2, 2), 2).orElseThrow();

        assertThat(result.citations()).isEqualTo(List.of(2));
    }
}
