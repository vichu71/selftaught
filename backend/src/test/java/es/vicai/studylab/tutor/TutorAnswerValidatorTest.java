package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** AC-3, AC-5 y AC-16 (ADR-58): la respuesta del modelo solo se muestra si es texto útil, acotado, sin HTML crudo y con citas que existan. */
class TutorAnswerValidatorTest {

    private final TutorAnswerValidator validator = new TutorAnswerValidator();

    @Test
    void ac16_aPlainAnswerIsAcceptedAsItIs() {
        TutorAnswerValidator.Answer answer = validator.validate("El JDK sirve para compilar y el JRE para ejecutar.", 0).orElseThrow();

        assertThat(answer.text()).isEqualTo("El JDK sirve para compilar y el JRE para ejecutar.");
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void ac16_emptyOrBlankOrMissingIsInvalid() {
        for (String raw : new String[] {"", "   ", "\n\n", null}) assertThat(validator.validate(raw, 0)).as(String.valueOf(raw)).isEmpty();
    }

    @Test
    void ac5_aVeryLongAnswerIsCutAtFourThousandCharactersNotRejected() {
        TutorAnswerValidator.Answer answer = validator.validate("a".repeat(5000), 0).orElseThrow();

        assertThat(answer.text()).hasSize(4000);
    }

    @Test
    void ac16_rawHtmlIsRejectedButItIsFineInsideCode() {
        for (String html : new String[] {"<script>alert(1)</script>", "<iframe src=x></iframe>", "<img src=x onerror=alert(1)>", "<div onclick=\"x()\">hola</div>", "<a href=\"http://x\">enlace</a>"}) {
            assertThat(validator.validate("Mira esto: " + html, 0)).as(html).isEmpty();
        }
        assertThat(validator.validate("Un ejemplo en código: `<b>negrita</b>` o\n```html\n<p>hola</p>\n```\n", 0)).isPresent();
        assertThat(validator.validate("Si x < y entonces y > x.", 0)).isPresent();
    }

    @Test
    void ac16_dangerousLinksAreRejectedButOrdinaryOnesAreFine() {
        for (String link : new String[] {"[clic](javascript:alert(1))", "[clic](data:text/html,x)", "![x](data:image/png;base64,AAAA)"}) {
            assertThat(validator.validate("Mira " + link, 0)).as(link).isEmpty();
        }
        assertThat(validator.validate("Más en [la web](https://example.com/tema).", 0)).isPresent();
    }

    @Test
    void ac16_controlCharactersAreRejectedButNewlinesAreFine() {
        assertThat(validator.validate("con\u0000control", 0)).isEmpty();
        assertThat(validator.validate("línea 1\nlínea 2\n\n- punto", 0)).isPresent();
    }

    // ---------- citas (AC-3) ----------

    @Test
    void ac3_withMaterialEveryMarkerMustExistButCitingIsNotRequired() {
        assertThat(validator.validate("Según el material [1] y [3], es así.", 3).orElseThrow().citations()).containsExactlyInAnyOrder(1, 3);
        assertThat(validator.validate("Sin citar nada, pero con fuentes disponibles.", 3).orElseThrow().citations()).isEmpty();
    }

    @Test
    void ac3_aMarkerThatMatchesNoFragmentIsRejected() {
        assertThat(validator.validate("Según el material [4] es así.", 3)).isEmpty();
        assertThat(validator.validate("Según el material [0] es así.", 3)).isEmpty();
    }

    @Test
    void ac3_withoutMaterialAnyMarkerIsRejected() {
        assertThat(validator.validate("Según el material [1] es así.", 0)).isEmpty();
    }

    @Test
    void ac3_aLinkWithBracketsOrAListNumberIsNotMistakenForACitation() {
        TutorAnswerValidator.Answer answer = validator.validate("Mira [el [2] manual](https://example.com/2) y:\n\n1. Primer paso\n2. Segundo paso\n\nSegún [1] se hace así.", 1).orElseThrow();

        assertThat(answer.citations()).isEqualTo(List.of(1));
    }

    @Test
    void ac3_theSameCitationRepeatedCountsOnce() {
        assertThat(validator.validate("Dice [2], y otra vez [2], y más [2].", 2).orElseThrow().citations()).isEqualTo(List.of(2));
    }

    @Test
    void ac3_aMarkerInsideCodeIsText() {
        assertThat(validator.validate("En Java `lista[5]` es un acceso a un array.", 0)).isPresent();
    }
}
