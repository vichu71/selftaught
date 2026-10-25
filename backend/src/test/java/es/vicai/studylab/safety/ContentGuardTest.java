package es.vicai.studylab.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-8 (capa local del filtro de menores, ADR-39): bloquea lo explícito aunque se disfrace y deja pasar los temas escolares delicados. */
class ContentGuardTest {

    private final ContentGuard guard = new ContentGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "pornografía", "porno", "PORNO", "Pornografía infantil", "videos xxx", "sexo explícito",
            "como hacer una bomba casera", "cómo matar a alguien", "como torturar a un animal",
            "cómo fabricar metanfetamina", "como comprar droga sin que se enteren", "cómo cocinar cocaína",
            "como suicidarme", "cómo cortarme las venas", "formas de hacerme daño"})
    void ac8_explicitOrHarmfulTopicsAreBlocked(String topic) {
        assertThat(guard.localCheck(topic)).as(topic).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"p o r n o", "p.o.r.n.o", "p-o-r-n-o", "p0rn0", "PÓRNO", "pornooo", "  porno  ", "p0rn0graf1a"})
    void ac8_disguisesWithSpacingSymbolsDigitsAccentsAndCaseAreNormalised(String topic) {
        assertThat(guard.localCheck(topic)).as(topic).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "la Segunda Guerra Mundial", "la reproducción humana", "educación sexual en la ESO", "los efectos de las drogas en la salud",
            "la historia del opio", "la bomba atómica y la Guerra Fría", "anatomía del cuerpo humano", "cómo se hace una tortilla de patatas",
            "Romeo y Julieta", "la prevención del suicidio en adolescentes", "el sistema reproductor", "la Revolución francesa y la guillotina",
            "inglés de 2º de la ESO", "matemáticas: fracciones", "la violencia de género y cómo prevenirla", "el holocausto",
            "química de los medicamentos", "primeros auxilios"})
    void ac8_legitimateSchoolTopicsThatTouchDelicateSubjectsPass(String topic) {
        assertThat(guard.localCheck(topic)).as(topic).isEmpty();
    }

    @Test
    void ac8_ordinaryTextWithSeparatedLettersIsNotBlocked() {
        assertThat(guard.localCheck("tabla de la A B C y la D E F")).isEmpty();
        assertThat(guard.localCheck("el sistema solar y sus planetas")).isEmpty();
    }

    @Test
    void ac8_theCategoryReflectsWhatWasMatched() {
        assertThat(guard.localCheck("pornografía")).contains(BlockCategory.EXPLICIT);
        assertThat(guard.localCheck("como suicidarme")).contains(BlockCategory.SELF_HARM);
        assertThat(guard.localCheck("cómo fabricar metanfetamina")).contains(BlockCategory.DRUGS);
        assertThat(guard.localCheck("como hacer una bomba casera")).contains(BlockCategory.VIOLENCE);
    }

    @Test
    void edge_nullOrBlankTextIsNeverBlocked() {
        assertThat(guard.localCheck(null)).isEmpty();
        assertThat(guard.localCheck("")).isEmpty();
        assertThat(guard.localCheck("   ")).isEmpty();
    }

    @Test
    void edge_aVeryLongTextIsHandledQuickly() {
        String huge = "la fotosíntesis de las plantas ".repeat(5000);

        long start = System.nanoTime();
        assertThat(guard.localCheck(huge)).isEmpty();
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
    }

    @Test
    void adr39_theTermListIsAResourceFileWithEveryCategory() throws IOException {
        Path file = Path.of("src/main/resources/safety/blocked-terms.txt");
        assertThat(file).exists();
        String content = Files.readString(file);
        for (BlockCategory category : EnumSet.complementOf(EnumSet.of(BlockCategory.OTHER))) {
            assertThat(content).as("categoría " + category).contains(category.name());
        }
    }

    @Test
    void adr39_theGuardIsReusableAsAComponentForOtherFeatures() throws IOException {
        // F05 y F07 la usarán antes de enviar texto de un menor a un modelo de pago: no puede depender del asistente
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/es/vicai/studylab/safety"))) {
            Set<String> code = Set.copyOf(files.filter(p -> p.toString().endsWith(".java")).map(p -> {
                try {
                    return Files.readString(p);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList());
            assertThat(code).isNotEmpty().allSatisfy(c -> assertThat(c).doesNotContain("studylab.course").doesNotContain("studylab.ai.provider"));
        }
    }
}
