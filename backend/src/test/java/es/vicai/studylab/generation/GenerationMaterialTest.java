package es.vicai.studylab.generation;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.provider.ChatCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** AC-6 (ADR-46): con material, los fragmentos vienen solo de los documentos del curso, van como datos, se citan y se verifican. */
class GenerationMaterialTest extends GenerationTestBase {

    private static String material(String marker) {
        return (marker + " es un concepto que se explica en los apuntes con ejemplos y definiciones. ").repeat(25);
    }

    private List<ChatCall> lessonCalls() {
        return fake.calls().stream().filter(c -> "leccion".equals(piece(c))).toList();
    }

    @Test
    void ac6_withMaterialEveryLessonReceivesNumberedFragmentsAsData() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text(material("CONCEPTO-PROPIO-XYZ")));
        insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of(doc));
        modelDoes(happy(2));

        generator.processPending();

        assertThat(lessonCalls()).hasSize(4).allSatisfy(call -> {
            assertThat(call.dataMessage()).contains("<fuentes>").contains("[1]").contains("CONCEPTO-PROPIO-XYZ");
            assertThat(call.instructions()).doesNotContain("CONCEPTO-PROPIO-XYZ"); // nunca en las instrucciones
        });
    }

    @Test
    void ac6_theLessonKeepsTheCitationsItUsedAsSourcesWithPageAndDocument() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text(material("CONCEPTO")));
        UUID course = insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of(doc));
        modelDoes(happy(2));

        generator.processPending();

        Map<String, Object> lesson = owner.queryForMap("SELECT * FROM course_piece WHERE course_id = ? AND kind = 'LESSON' ORDER BY seq LIMIT 1", course);
        assertThat((String) lesson.get("content_md")).contains("[1]");
        assertThat(lesson.get("sources").toString()).contains(doc).contains("\"n\": 1");
    }

    @Test
    void ac6_aLessonThatCitesAFragmentThatWasNeverSentIsRejectedAndRetried() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text(material("CONCEPTO")));
        UUID course = insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of(doc));
        AtomicInteger lessons = new AtomicInteger();
        modelDoes(call -> {
            if ("leccion".equals(piece(call)) && lessons.incrementAndGet() == 1) return lessonMd("Cita inventada", 9);
            return happy(2).apply(call);
        });

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(lessonCalls()).hasSize(4 + 1);
    }

    @Test
    void ac6_aLessonWithMaterialButNoCitationFailsAfterTheRetryAndTheCoursePausesForTheContent() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text(material("CONCEPTO")));
        UUID course = insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of(doc));
        modelDoes(call -> "leccion".equals(piece(call)) ? lessonMd("Sin citas") : happy(2).apply(call));

        generator.processPending();

        assertThat(pieces(course, "FAILED")).isEqualTo(4);
        assertThat(stopReasonOf(course)).isEqualTo("CONTENT");
    }

    @Test
    void ac6_withoutMaterialThereAreNoSourcesAndAnyCitationIsRejected() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, false);
        modelDoes(call -> "leccion".equals(piece(call)) ? lessonMd("Cita sin material", 1) : happy(2).apply(call));

        generator.processPending();

        assertThat(lessonCalls()).allSatisfy(c -> assertThat(c.dataMessage()).doesNotContain("<fuentes>"));
        assertThat(pieces(course, "FAILED")).isEqualTo(4);
    }

    @Test
    void ac6_onlyTheDocumentsLinkedToTheCourseAndOwnedByTheUserAreEverSearched() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String linked = uploadedAndProcessed(ana, "enlazado.txt", text(material("CONTENIDO-ENLAZADO")));
        uploadedAndProcessed(ana, "no-enlazado.txt", text(material("CONTENIDO-NO-ENLAZADO")));
        uploadedAndProcessed(luis, "ajeno.txt", text(material("CONTENIDO-AJENO")));
        insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of(linked));
        modelDoes(happy(2));

        generator.processPending();

        String all = fake.calls().stream().map(ChatCall::dataMessage).reduce("", (a, b) -> a + b);
        assertThat(all).contains("CONTENIDO-ENLAZADO").doesNotContain("CONTENIDO-NO-ENLAZADO").doesNotContain("CONTENIDO-AJENO");
    }

    @Test
    void ac6_noDocumentNameEverReachesTheModel() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "diario-personal-secreto.txt", text(material("CONCEPTO")));
        insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of(doc));
        modelDoes(happy(2));

        generator.processPending();

        String all = fake.calls().stream().map(c -> c.instructions() + c.dataMessage()).reduce("", (a, b) -> a + b);
        assertThat(all).doesNotContain("diario-personal-secreto");
    }

    @Test
    void ac6_aDocumentDeletedInTheMiddleDoesNotStopTheCourseTheNextLessonsGoWithoutSources() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text(material("CONCEPTO")));
        UUID course = insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of(doc));
        AtomicInteger lessons = new AtomicInteger();
        List<Boolean> withSources = new ArrayList<>();
        modelDoes(call -> {
            if ("leccion".equals(piece(call))) {
                withSources.add(call.dataMessage().contains("<fuentes>"));
                if (lessons.incrementAndGet() == 1) owner.update("DELETE FROM source_document WHERE id = ?::uuid", doc); // el usuario lo borra
            }
            return happy(2).apply(call);
        });

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(withSources.get(0)).isTrue();
        assertThat(withSources.subList(1, withSources.size())).containsOnly(false);
    }

    @Test
    void ac6_aDocumentWithoutAnyIndexedFragmentGeneratesLessonsWithoutSources() throws Exception {
        String ana = logged("ana@example.com");
        String pending = uploaded(ana, "pendiente.txt", text(material("CONCEPTO"))); // sin procesar: aún no hay fragmentos
        UUID course = insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, false, List.of());
        owner.update("INSERT INTO course_document (course_id, document_id, owner_id) VALUES (?, ?::uuid, ?)", course, pending, idOf("ana@example.com"));
        modelDoes(happy(2));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(lessonCalls()).allSatisfy(c -> assertThat(c.dataMessage()).doesNotContain("<fuentes>"));
    }

    @Test
    void ac6_theLessonRequestsAskForNoLatexAndForTheComplementMark() throws Exception {
        logged("ana@example.com");
        insertCourse(idOf("ana@example.com"), 2, false);
        modelDoes(happy(2));

        generator.processPending();

        String instructions = lessonCalls().get(0).instructions().toLowerCase();
        assertThat(instructions).contains("latex").contains("complemento");
    }
}
