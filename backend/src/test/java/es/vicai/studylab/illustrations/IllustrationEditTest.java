package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** F12 AC-13 y AC-15: el propietario puede quitar y volver a generar la ilustración de un tema, sin cambiar la versión ni el estado del curso. */
class IllustrationEditTest extends IllustrationTestBase {

    private record Setup(String luis, UUID course, UUID topic) {}

    private Setup done() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        drain();
        return new Setup(session("luis@example.com"), course, topicIds(course).get(0));
    }

    // ---------- quitar ----------

    @Test
    void ac13_removingTheImageKeepsTheRowWithoutBytesAndItDisappearsFromEverything() throws Exception {
        Setup s = done();

        removeImage(s.luis(), s.course(), s.topic()).andExpect(status().isOk()).andExpect(jsonPath("$.outline[0].image").doesNotExist());

        assertThat(imageStatus(s.topic())).isEqualTo("REMOVED");
        assertThat(owner.queryForObject("SELECT content FROM course_image WHERE topic_id = ?", byte[].class, s.topic())).isNull();
        imageOf(s.luis(), s.course(), s.topic()).andExpect(status().isNotFound());
    }

    @Test
    void ac13_aRemovedImageIsNotQueuedAgainByTheQueue() throws Exception {
        Setup s = done();
        removeImage(s.luis(), s.course(), s.topic()).andExpect(status().isOk());
        int calls = fake.imageCalls().size();

        assertThat(drain()).isZero();

        assertThat(fake.imageCalls()).hasSize(calls);
        assertThat(imageStatus(s.topic())).isEqualTo("REMOVED");
    }

    @Test
    void ac13_removingTwiceIsIdempotent() throws Exception {
        Setup s = done();

        removeImage(s.luis(), s.course(), s.topic()).andExpect(status().isOk());
        removeImage(s.luis(), s.course(), s.topic()).andExpect(status().isOk());

        assertThat(imageStatus(s.topic())).isEqualTo("REMOVED");
    }

    @Test
    void ac13_removingDoesNotCallTheProviderNorCostAnything() throws Exception {
        Setup s = done();
        int calls = fake.imageCalls().size();
        int rows = imageUsageRows();

        removeImage(s.luis(), s.course(), s.topic()).andExpect(status().isOk());

        assertThat(fake.imageCalls()).hasSize(calls);
        assertThat(imageUsageRows()).isEqualTo(rows);
    }

    // ---------- volver a generar ----------

    @Test
    void ac13_regeneratingAnswersAcceptedLeavesItPendingAndTheQueueDoesTheWork() throws Exception {
        Setup s = done();
        byte[] before = owner.queryForObject("SELECT content FROM course_image WHERE topic_id = ?", byte[].class, s.topic());
        int calls = fake.imageCalls().size();

        regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(s.course().toString()));

        assertThat(imageStatus(s.topic())).isEqualTo("PENDING");
        assertThat(fake.imageCalls()).hasSize(calls); // la petición HTTP no llama a ningún proveedor
        fake.imageRespondsWith(es.vicai.studylab.ai.provider.FakeProvider.png(640, 480));
        drain();
        assertThat(imageStatus(s.topic())).isEqualTo("DONE");
        assertThat(fake.imageCalls()).hasSize(calls + 1);
        assertThat(owner.queryForObject("SELECT content FROM course_image WHERE topic_id = ?", byte[].class, s.topic())).isNotEqualTo(before);
    }

    @Test
    void ac13_regeneratingCanBringBackARemovedOrFailedOrBlockedImage() throws Exception {
        Setup s = done();
        for (String state : new String[] {"REMOVED", "FAILED", "BLOCKED"}) {
            owner.update("UPDATE course_image SET status = ?, content = NULL WHERE topic_id = ?", state, s.topic());

            regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isAccepted());
            assertThat(imageStatus(s.topic())).as(state).isEqualTo("PENDING");
            drain();
            assertThat(imageStatus(s.topic())).as(state).isEqualTo("DONE");
            owner.update("UPDATE course_image SET regenerations = 0 WHERE topic_id = ?", s.topic());
        }
    }

    @Test
    void ac13_regeneratingCountsForTheLimitOfEditsWithAiAndAsAnImage() throws Exception {
        Setup s = done();
        int edits = count("SELECT count(*) FROM course_action WHERE kind = 'AI_EDIT' AND user_id = ?", idOf("luis@example.com"));

        regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isAccepted());

        assertThat(count("SELECT count(*) FROM course_action WHERE kind = 'AI_EDIT' AND user_id = ?", idOf("luis@example.com"))).isEqualTo(edits + 1);
        assertThat(count("SELECT regenerations FROM course_image WHERE topic_id = ?", s.topic())).isEqualTo(1);
    }

    @Test
    void ac13_regeneratingTwiceInARowDoesNotCallTheProviderTwiceNorChargeTwice() throws Exception {
        Setup s = done();
        int calls = fake.imageCalls().size();

        regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isAccepted());
        regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isAccepted()); // ya está pendiente: no hace nada más
        drain();

        assertThat(fake.imageCalls()).hasSize(calls + 1);
        assertThat(count("SELECT regenerations FROM course_image WHERE topic_id = ?", s.topic())).isEqualTo(1);
    }

    @Test
    void ac13_theCourseVersionAndStateDoNotChangeWhenRemovingOrRegenerating() throws Exception {
        Setup s = done();
        int version = version(s.course());

        removeImage(s.luis(), s.course(), s.topic()).andExpect(status().isOk());
        regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isAccepted());
        drain();

        assertThat(version(s.course())).isEqualTo(version);
        assertThat(statusOf(s.course())).isEqualTo("READY");
    }

    @Test
    void ac13_whileTheCourseIsBeingGeneratedBothAreRejectedWithCourseBusy() throws Exception {
        Setup s = done();
        for (String status : new String[] {"PENDING_GENERATION", "GENERATING"}) {
            setStatus(s.course(), status);

            regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_BUSY"));
            removeImage(s.luis(), s.course(), s.topic()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_BUSY"));
        }
        assertThat(imageStatus(s.topic())).isEqualTo("DONE");
    }

    @Test
    void ac13_aTopicThatNeverHadAnImageCannotBeRegeneratedIfTheCourseHasNoIllustrations() throws Exception {
        session("luis@example.com");
        UUID course = ready("luis@example.com");

        regenerate(session("luis@example.com"), course, topicIds(course).get(0)).andExpect(status().isNotFound());
    }

    // ---------- permisos ----------

    @Test
    void ac15_theChangesNeedSessionAndCsrf() throws Exception {
        Setup s = done();

        postAsWithoutCsrf(s.luis(), imagePath(s.course(), s.topic()) + "/regenerate", null).andExpect(status().isForbidden());
        deleteAsWithoutCsrf(s.luis(), imagePath(s.course(), s.topic())).andExpect(status().isForbidden());
        assertThat(imageStatus(s.topic())).isEqualTo("DONE");
    }

    @Test
    void ac15_onlyTheOwnerCanRegenerateOrRemoveAndTheOthersGetTheSame404() throws Exception {
        Setup s = done();
        String marta = session("marta@example.com");
        String ana = session("ana@example.com");

        for (String other : new String[] {marta, ana}) {
            regenerate(other, s.course(), s.topic()).andExpect(status().isNotFound());
            removeImage(other, s.course(), s.topic()).andExpect(status().isNotFound());
        }
        regenerate(s.luis(), s.course(), UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(imageStatus(s.topic())).isEqualTo("DONE");
    }

    @Test
    void ac15_aMinorCannotRegenerateEvenIfTheirOwnCourseHadARowForcedInTheDatabase() throws Exception {
        String ana = session("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(hugoId, 2, true);
        modelDoes(happy(2));
        generator.processPending();
        UUID topic = topicIds(course).get(0);
        owner.update("UPDATE course SET illustrations = true WHERE id = ?", course);
        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status) VALUES (gen_random_uuid(), ?, ?, ?, 'FAILED')", course, hugoId, topic);

        regenerate(hugo, course, topic).andExpect(status().isNotFound());

        assertThat(fake.imageCalls()).isEmpty();
    }
}
