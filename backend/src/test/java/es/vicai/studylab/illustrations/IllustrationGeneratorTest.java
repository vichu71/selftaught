package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.lock.GenerationLock;
import es.vicai.studylab.ai.provider.FakeProvider;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * F12 AC-4, AC-6, AC-7, AC-8 y AC-9 (ADR-79): la cola de ilustraciones. Una imagen por tema de los cursos terminados con {@code illustrations = true}, en segundo
 * plano, con el cerrojo de una generación por usuario y el control de gasto; ningún fallo toca el estado del curso.
 */
class IllustrationGeneratorTest extends IllustrationTestBase {

    @Autowired GenerationLock locks;

    @BeforeEach
    void cleanImages() {
        owner.update("DELETE FROM course_image");
        fake.reset();
    }

    @Test
    void ac4_everyTopicOfAReadyCourseWithIllustrationsGetsOneImageAndTheCourseIsNotTouched() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        int version = version(course);

        int done = drain();

        assertThat(done).isEqualTo(2);
        assertThat(imageRows(course)).hasSize(2);
        assertThat(imageCount(course, "DONE")).isEqualTo(2);
        assertThat(fake.imageCalls()).hasSize(2);
        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(version(course)).isEqualTo(version);
    }

    @Test
    void ac4_aCourseWithoutTheSwitchNeverGetsImagesOrCalls() throws Exception {
        session("luis@example.com");
        UUID course = ready("luis@example.com");

        assertThat(drain()).isZero();

        assertThat(imageRows(course)).isEmpty();
        assertThat(fake.imageCalls()).isEmpty();
    }

    @Test
    void ac4_onlyCoursesThatAreReadyAreProcessed() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        for (String status : List.of("PENDING_GENERATION", "GENERATING", "PAUSED", "FAILED")) {
            setStatus(course, status);
            assertThat(drain()).as(status).isZero();
        }
        assertThat(fake.imageCalls()).isEmpty();

        setStatus(course, "READY");
        assertThat(drain()).isEqualTo(2);
    }

    @Test
    void ac4_theGeneratedImageIsAJpegWithItsAlternativeTextAndItsSize() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        drain();

        Map<String, Object> row = imageRows(course).get(0);
        byte[] content = (byte[]) row.get("content");
        assertThat(row.get("mime")).isEqualTo("image/jpeg");
        assertThat(((Number) row.get("size_bytes")).intValue()).isEqualTo(content.length).isLessThanOrEqualTo(300 * 1024);
        assertThat(content[0] & 0xFF).isEqualTo(0xFF);
        assertThat((String) row.get("alt_text")).startsWith("Ilustración generada por IA sobre «");
        assertThat(row.get("generated_at")).isNotNull();
    }

    @Test
    void ac4_theRequestCarriesTheTemplateAndTheSubjectOfTheTopicAndNothingElseOfTheUser() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        drain();

        String prompt = fake.imageCalls().get(0).prompt();
        assertThat(prompt).contains("Tema 1 de prueba").contains("sin texto");
        assertThat(prompt).doesNotContain("luis@example.com").doesNotContain(course.toString());
    }

    @Test
    void ac4_aDoneImageIsNeverRequestedAgainAndTheQueueIsIdempotent() throws Exception {
        courseWithIllustrations("luis@example.com");
        drain();
        int calls = fake.imageCalls().size();

        assertThat(drain()).isZero();
        assertThat(drain()).isZero();

        assertThat(fake.imageCalls()).hasSize(calls);
    }

    @Test
    void ac4_aTopicAddedLaterGetsItsImageWithoutTouchingTheOthers() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        drain();
        UUID extra = insertTopic(course, idOf("luis@example.com"), 3, "Tema añadido");

        assertThat(drain()).isEqualTo(1);

        assertThat(imageStatus(extra)).isEqualTo("DONE");
        assertThat(fake.imageCalls()).hasSize(3);
    }

    @Test
    void ac4_everyImageIsChargedAtThePriceOfTheConfigurationAndCountsInTheUsage() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        drain();

        UUID user = idOf("luis@example.com");
        assertThat(imageSpend(user)).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(imageUsageRows()).isEqualTo(2);
        assertThat(owner.queryForObject("SELECT course_id FROM ai_usage WHERE task = 'IMAGE' LIMIT 1", UUID.class)).isEqualTo(course);
    }

    @Test
    void ac4_theQueueDoesNotCountAsPiecesOfTheCourseNorChangeItsProgress() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        int pieces = allPieces(course);
        drain();

        assertThat(allPieces(course)).isEqualTo(pieces);
        assertThat(owner.queryForObject("SELECT count(*) FROM course_piece WHERE course_id = ? AND kind NOT IN ('LESSON','EXERCISES','EXAM')", Integer.class, course)).isZero();
    }

    @Test
    void ac4_aRunningGenerationOfTheSameUserHoldsTheQueueBack() throws Exception {
        courseWithIllustrations("luis@example.com");
        UUID user = idOf("luis@example.com");

        try (GenerationLock.Lease lease = locks.acquire(user)) {
            assertThat(lease).isNotNull();
            assertThat(drain()).isZero();
            assertThat(fake.imageCalls()).isEmpty();
        }

        assertThat(drain()).isEqualTo(2);
    }

    @Test
    void ac4_theLockIsReleasedAfterEachImageSoTheUserCanGenerateAfterwards() throws Exception {
        courseWithIllustrations("luis@example.com");
        drain();

        locks.acquire(idOf("luis@example.com")).close();
    }

    // ---------- gasto y límites ----------

    @Test
    void ac6_withTheUsersMonthlyLimitReachedThereAreNoCallsAndTheImagesStayPendingWhileTheCourseDoesNotChange() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        spendFor(idOf("luis@example.com"), "4.99");

        drain();

        assertThat(fake.imageCalls()).isEmpty();
        assertThat(imageCount(course, "PENDING")).isEqualTo(2);
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac6_oncePendingImagesAreCompletedWhenTheBudgetAllowsItAgain() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        UUID user = idOf("luis@example.com");
        spendFor(user, "4.99");
        drain();
        owner.execute("TRUNCATE ai_usage, ai_budget_alert RESTART IDENTITY"); // el mes siguiente: el gasto previo ya no cuenta

        drain();

        assertThat(imageCount(course, "DONE")).isEqualTo(2);
    }

    @Test
    void ac6_aProviderFailureIsRetriedOnceAndThenTheImageIsFailedWithoutPausingTheCourse() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        fake.imageFailsTimes(100, ProviderErrorKind.SERVER_ERROR);

        drain();

        assertThat(imageCount(course, "FAILED")).isEqualTo(2);
        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(owner.queryForObject("SELECT error_kind FROM course_image WHERE course_id = ? LIMIT 1", String.class, course)).isNotBlank();
        assertThat(owner.queryForObject("SELECT stop_reason FROM course WHERE id = ?", String.class, course)).isNull();
    }

    @Test
    void ac6_aFailedImageIsNotRetriedByItselfBecauseEveryAttemptCostsMoney() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        fake.imageFailsTimes(100, ProviderErrorKind.SERVER_ERROR);
        drain();
        int calls = fake.imageCalls().size();
        fake.imageFailsTimes(0, ProviderErrorKind.SERVER_ERROR);

        assertThat(drain()).isZero();

        assertThat(fake.imageCalls()).hasSize(calls);
        assertThat(imageCount(course, "FAILED")).isEqualTo(2);
    }

    @Test
    void ac6_aTransientFailureThatRecoversEndsWithTheImageDone() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        fake.imageFailsTimes(1, ProviderErrorKind.SERVER_ERROR);

        drain();

        assertThat(imageCount(course, "DONE")).isEqualTo(2);
    }

    // ---------- imagen inválida y moderación ----------

    @Test
    void ac7_anImageThatDoesNotPassTheCheckIsAFailureThatWasAlreadyAccountedFor() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        fake.imageRespondsWith("no soy una imagen".getBytes());

        drain();

        assertThat(imageCount(course, "FAILED")).isEqualTo(2);
        assertThat(imageSpend(idOf("luis@example.com"))).isEqualByComparingTo("0.10");
        assertThat(owner.queryForObject("SELECT count(*) FROM course_image WHERE course_id = ? AND content IS NOT NULL", Integer.class, course)).isZero();
    }

    @Test
    void ac7_aBombOfPixelsIsRejectedAndNeverStored() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        fake.imageRespondsWith(FakeProvider.pngDeclaring(30_000, 30_000));

        drain();

        assertThat(imageCount(course, "FAILED")).isEqualTo(2);
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac8_aModerationRefusalLeavesTheImageBlockedWithoutRetryingAndWithoutAnyMessage() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        fake.imageIsModerated();

        drain();

        assertThat(imageCount(course, "BLOCKED")).isEqualTo(2);
        assertThat(fake.imageCalls()).hasSize(2); // una llamada por tema, ningún reintento
        assertThat(owner.queryForObject("SELECT error_kind FROM course_image WHERE course_id = ? LIMIT 1", String.class, course)).isEqualTo("MODERATION");
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac8_aBlockedImageIsNotRequestedAgainByItself() throws Exception {
        courseWithIllustrations("luis@example.com");
        fake.imageIsModerated();
        drain();
        int calls = fake.imageCalls().size();

        drain();

        assertThat(fake.imageCalls()).hasSize(calls);
    }

    // ---------- saneado y menores ----------

    @Test
    void ac5_aTopicThatDoesNotPassTheCleaningIsBlockedWithoutCallingTheProvider() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        owner.update("UPDATE course_topic SET title = '\u0001\u0002\u0003', summary = '<<<>>>' WHERE course_id = ?", course);

        drain();

        assertThat(fake.imageCalls()).isEmpty();
        assertThat(imageCount(course, "BLOCKED")).isEqualTo(2);
        assertThat(imageUsageRows()).isZero();
    }

    @Test
    void ac15_aMinorsCourseNeverGeneratesImagesEvenIfTheSwitchWasForcedInTheDatabase() throws Exception {
        String ana = session("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        UUID course = insertCourse(hugoId, 2, true);
        modelDoes(happy(2));
        generator.processPending();
        owner.update("UPDATE course SET illustrations = true WHERE id = ?", course);

        assertThat(drain()).isZero();

        assertThat(fake.imageCalls()).isEmpty();
        assertThat(imageRows(course)).isEmpty();
    }

    @Test
    void ac4_deactivatedUsersAreSkipped() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", idOf("luis@example.com"));

        assertThat(drain()).isZero();
        assertThat(imageRows(course)).isEmpty();
    }
}
