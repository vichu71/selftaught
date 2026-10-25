package es.vicai.studylab.generation;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.provider.ChatCall;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** AC-7 (ADR-47, cierra F4-5 y la parte de F05 de F3-1): lo que se manda al modelo y lo que se guarda para un menor pasa por el filtro. */
class GenerationMinorTest extends GenerationTestBase {

    private UUID minor(String ana) throws Exception {
        return UUID.fromString(createStudent(ana, "Hugo", "hugo"));
    }

    private static String blockedLesson() {
        return lessonMd("Lección") + "\n\nAquí se explica como hacer una bomba casera paso a paso.\n";
    }

    @Test
    void ac7_everyRequestForAMinorSaysTheAudienceIsSchool() throws Exception {
        String ana = logged("ana@example.com");
        insertCourse(minor(ana), 2, false);
        modelDoes(happy(2));

        generator.processPending();

        assertThat(fake.calls()).isNotEmpty().allSatisfy(c -> assertThat(c.dataMessage()).contains("<audiencia>\nescolar\n</audiencia>"));
    }

    @Test
    void ac7_ifTheOutlineSaysTheTopicIsNotSuitableTheCourseFailsAndNothingElseIsGenerated() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(minor(ana), 2, true);
        modelDoes(call -> outlineJson(2, 2, false));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("FAILED");
        assertThat(stopReasonOf(course)).isEqualTo("TOPIC_NOT_ALLOWED");
        assertThat(chatCalls()).isEqualTo(1);
        assertThat(allPieces(course)).isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM course_topic WHERE course_id = ?", Integer.class, course)).isZero();
        assertThat(auditActions()).contains("COURSE_GENERATION_FAILED");
    }

    @Test
    void ac7_anAdultWithTheSameAssessmentIsNotBlockedBecauseTheFilterOnlyAppliesToMinors() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, false);
        modelDoes(call -> "temario".equals(piece(call)) ? outlineJson(2, 2, false) : happy(2).apply(call));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac7_materialFragmentsThatTheFilterRejectsNeverReachTheModel() throws Exception {
        String ana = logged("ana@example.com");
        UUID minorId = minor(ana);
        String hugoSession = login("hugo", PASSWORD);
        String good = uploadedAndProcessed(hugoSession, "bueno.txt", text(("Apuntes de química de los medicamentos y de la salud. ").repeat(30)));
        String bad = uploadedAndProcessed(hugoSession, "malo.txt", text(("Aquí se explica como hacer una bomba casera paso a paso. ").repeat(30)));
        insertCourse(minorId, "Curso con apuntes", 2, false, List.of(good, bad));
        modelDoes(happy(2));

        generator.processPending();

        String all = fake.calls().stream().map(ChatCall::dataMessage).reduce("", (a, b) -> a + b);
        assertThat(all).contains("medicamentos").doesNotContain("bomba");
    }

    @Test
    void ac7_ifEveryFragmentIsRejectedTheLessonIsGeneratedWithoutSourcesAndNeedsNoCitation() throws Exception {
        String ana = logged("ana@example.com");
        UUID minorId = minor(ana);
        String hugoSession = login("hugo", PASSWORD);
        String bad = uploadedAndProcessed(hugoSession, "malo.txt", text(("Aquí se explica como hacer una bomba casera paso a paso. ").repeat(30)));
        UUID course = insertCourse(minorId, "Curso con apuntes", 2, false, List.of(bad));
        modelDoes(happy(2));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(fake.calls().stream().filter(c -> "leccion".equals(piece(c)))).allSatisfy(c -> assertThat(c.dataMessage()).doesNotContain("<fuentes>"));
    }

    @Test
    void ac7_generatedTextThatTripsTheFilterIsRetriedWithAReinforcedInstructionAndNeverSavedFirst() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(minor(ana), 2, false);
        AtomicInteger lessons = new AtomicInteger();
        modelDoes(call -> {
            if ("leccion".equals(piece(call)) && lessons.incrementAndGet() == 1) return blockedLesson();
            return happy(2).apply(call);
        });

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        ChatCall retry = fake.calls().stream().filter(c -> "leccion".equals(piece(c))).skip(1).findFirst().orElseThrow();
        assertThat(retry.dataMessage()).contains("<refuerzo>");
        assertThat(owner.queryForList("SELECT content_md FROM course_piece WHERE content_md IS NOT NULL").toString()).doesNotContain("bomba");
    }

    @Test
    void ac7_ifTheRetryAlsoTripsTheFilterThePieceFailsAndTheBlockIsAuditedWithoutTheContent() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(minor(ana), 2, false);
        modelDoes(call -> "leccion".equals(piece(call)) ? blockedLesson() : happy(2).apply(call));

        generator.processPending();

        assertThat(pieces(course, "FAILED")).isEqualTo(4);
        assertThat(stopReasonOf(course)).isEqualTo("CONTENT");
        assertThat(auditActions()).contains("COURSE_CONTENT_BLOCKED");
        String details = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action = 'COURSE_CONTENT_BLOCKED'", String.class);
        assertThat(details).doesNotContain("bomba").doesNotContain("Lección");
        assertThat(owner.queryForList("SELECT * FROM course_piece").toString()).doesNotContain("bomba");
    }

    @Test
    void ac7_theSameTextForAnAdultIsSavedBecauseTheFilterOnlyAppliesToMinors() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, false);
        modelDoes(call -> "leccion".equals(piece(call)) ? blockedLesson() : happy(2).apply(call));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac7_exercisesAndExamsForAMinorAreFilteredToo() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(minor(ana), 2, true);
        String bad = exercisesJson().replace("Primer ejercicio", "Explica como hacer una bomba casera");
        modelDoes(call -> "ejercicios".equals(piece(call)) ? bad : happy(2).apply(call));

        generator.processPending();

        assertThat(pieces(course, "FAILED")).isEqualTo(4);
        assertThat(owner.queryForList("SELECT content_json::text FROM course_piece WHERE content_json IS NOT NULL").toString()).doesNotContain("bomba");
    }
}
