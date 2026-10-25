package es.vicai.studylab.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-5 y AC-6: la ficha se construye con lo que guardó el servidor y los valores por defecto; nunca con valores que traiga el cliente. */
class SheetFactoryTest {

    private final SheetFactory factory = new SheetFactory();

    private static IntakeParser.Question focus() {
        return new IntakeParser.Question("q1", "focus", "¿En qué centrarte?", List.of(
                new IntakeParser.Option("o1", "Gramática", "Gramática y vocabulario", true),
                new IntakeParser.Option("o2", "Conversación", "Conversación", false),
                new IntakeParser.Option("auto", "Como tú veas", "Gramática y vocabulario", false)));
    }

    private static IntakeParser.Question topics() {
        return new IntakeParser.Question("q2", "topicsCount", "¿Cuántos temas?", List.of(
                new IntakeParser.Option("o1", "Pocos", "4", false),
                new IntakeParser.Option("o2", "Normal", "6", true),
                new IntakeParser.Option("auto", "Como tú veas", "6", false)));
    }

    private static IntakeParser.Question exam() {
        return new IntakeParser.Question("q3", "examPerTopic", "¿Examen por tema?", List.of(
                new IntakeParser.Option("o1", "Sí", "true", true),
                new IntakeParser.Option("o2", "No", "false", false),
                new IntakeParser.Option("auto", "Como tú veas", "true", false)));
    }

    @Test
    void ac5_withoutQuestionsTheSheetUsesTheDefaults() {
        CourseSheet sheet = factory.build("Inglés de 2º de la ESO", List.of(), Map.of(), List.of());

        assertThat(sheet.schemaVersion()).isEqualTo(1);
        assertThat(sheet.topic()).isEqualTo("Inglés de 2º de la ESO");
        assertThat(sheet.focus()).isEqualTo("General");
        assertThat(sheet.startLevel()).isEqualTo("Desde cero");
        assertThat(sheet.goal()).isEqualTo("Aprender el tema");
        assertThat(sheet.topicsCount()).isEqualTo(6);
        assertThat(sheet.examPerTopic()).isTrue();
        assertThat(sheet.documentIds()).isEmpty();
    }

    @Test
    void ac5_theChosenOptionsFillTheirFields() {
        CourseSheet sheet = factory.build("Inglés", List.of(focus(), topics(), exam()), Map.of("q1", "o2", "q2", "o1", "q3", "o2"), List.of());

        assertThat(sheet.focus()).isEqualTo("Conversación");
        assertThat(sheet.topicsCount()).isEqualTo(4);
        assertThat(sheet.examPerTopic()).isFalse();
    }

    @Test
    void ac5_asYouSeeFitAndUnansweredQuestionsUseTheDefaultOption() {
        CourseSheet sheet = factory.build("Inglés", List.of(focus(), topics(), exam()), Map.of("q1", "auto"), List.of());

        assertThat(sheet.focus()).isEqualTo("Gramática y vocabulario");
        assertThat(sheet.topicsCount()).isEqualTo(6); // sin contestar: la opción por defecto
        assertThat(sheet.examPerTopic()).isTrue();
    }

    @Test
    void ac5_theTopicIsTrimmedAndTheDocumentsAreKept() {
        UUID doc = UUID.randomUUID();

        CourseSheet sheet = factory.build("  Cocina básica  ", List.of(), Map.of(), List.of(doc));

        assertThat(sheet.topic()).isEqualTo("Cocina básica");
        assertThat(sheet.documentIds()).containsExactly(doc);
    }

    @Test
    void ac13_anAnswerToAQuestionThatDoesNotExistIsRejected() {
        assertThatThrownBy(() -> factory.build("x tema", List.of(focus()), Map.of("q9", "o1"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ac13_anOptionThatBelongsToAnotherQuestionIsRejected() {
        // «o2» de q1 es «Conversación»; una opción inventada no existe en ninguna pregunta
        assertThatThrownBy(() -> factory.build("x tema", List.of(focus(), topics()), Map.of("q1", "o99"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.build("x tema", List.of(focus()), Map.of("q1", "<script>"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ac6_theSheetSerialisesToTheDocumentedContract() throws Exception {
        UUID doc = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CourseSheet sheet = factory.build("Inglés", List.of(focus()), Map.of("q1", "o1"), List.of(doc));

        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sheet));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "schemaVersion", "topic", "focus", "startLevel", "goal", "topicsCount", "examPerTopic", "documentIds");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("documentIds").get(0).asText()).isEqualTo(doc.toString());
    }

    @Test
    void ac6_theFieldsAreShortTextNeverInstructionsLikeStructures() {
        CourseSheet sheet = factory.build("Inglés", List.of(focus()), Map.of("q1", "o1"), List.of());

        assertThat(sheet.focus().length()).isLessThanOrEqualTo(120);
        assertThat(sheet.goal().length()).isLessThanOrEqualTo(120);
    }
}
