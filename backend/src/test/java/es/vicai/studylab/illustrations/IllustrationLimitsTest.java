package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * F12 AC-6 y AC-13: límites por usuario contados en la BD (no por IP). Dos imágenes por hora y una regeneración por tema (los valores por defecto son 20 y 3).
 * Una petición rechazada por límite no deja filas ni llama al proveedor.
 */
@TestPropertySource(properties = {"app.illustrations.max-per-hour=2", "app.illustrations.max-regenerations-per-topic=1"})
class IllustrationLimitsTest extends IllustrationTestBase {

    @BeforeEach
    void clean() {
        owner.update("DELETE FROM course_image");
        owner.update("DELETE FROM course_action WHERE kind = 'AI_IMAGE'");
        fake.reset();
    }

    @Test
    void ac6_onlyTheConfiguredNumberOfImagesIsGeneratedPerHourAndTheRestWaitsAsPending() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        UUID extra = insertTopic(course, idOf("luis@example.com"), 3, "Tema tres");

        drain();

        assertThat(fake.imageCalls()).hasSize(2);
        assertThat(imageCount(course, "DONE")).isEqualTo(2);
        assertThat(imageStatus(extra)).isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM course_action WHERE kind = 'AI_IMAGE' AND user_id = ?", idOf("luis@example.com"))).isEqualTo(2);
    }

    @Test
    void ac6_theHourlyCountIsPerUserAndNotGlobal() throws Exception {
        UUID luis = courseWithIllustrations("luis@example.com");
        UUID marta = courseWithIllustrations("marta@example.com");

        drain();

        assertThat(imageCount(luis, "DONE")).isEqualTo(2);
        assertThat(imageCount(marta, "DONE")).isEqualTo(2);
    }

    @Test
    void ac6_afterAnHourTheWaitingImagesAreGenerated() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        UUID extra = insertTopic(course, idOf("luis@example.com"), 3, "Tema tres");
        drain();
        owner.update("UPDATE course_action SET created_at = now() - interval '61 minutes' WHERE kind = 'AI_IMAGE'");

        drain();

        assertThat(imageStatus(extra)).isEqualTo("DONE");
    }

    @Test
    void ac13_aTopicCanBeRegeneratedOnlyAsManyTimesAsTheLimitSaysAndTheRefusalChangesNothing() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        String luis = session("luis@example.com");
        UUID topic = topicIds(course).get(0);
        drain();

        regenerate(luis, course, topic).andExpect(status().isAccepted());
        drain();
        int calls = fake.imageCalls().size();

        regenerate(luis, course, topic).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("REGENERATION_LIMIT"));

        assertThat(imageStatus(topic)).isEqualTo("DONE");
        assertThat(fake.imageCalls()).hasSize(calls);
        assertThat(count("SELECT regenerations FROM course_image WHERE topic_id = ?", topic)).isEqualTo(1);
    }

    @Test
    void ac13_theLimitOfRegenerationsIsPerTopic() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        String luis = session("luis@example.com");
        drain();

        regenerate(luis, course, topicIds(course).get(0)).andExpect(status().isAccepted());
        regenerate(luis, course, topicIds(course).get(1)).andExpect(status().isAccepted());
    }

    @Test
    void ac6_theRefusalByLimitOfTheHourCountsAsAnAnswerThatNeverCallsTheProvider() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        String luis = session("luis@example.com");
        UUID topic = topicIds(course).get(0);
        drain();
        owner.update("UPDATE course_image SET regenerations = 0 WHERE course_id = ?", course);
        // el límite de ediciones con IA de F09 (20 por hora) también se aplica a regenerar
        for (int i = 0; i < 20; i++) {
            owner.update("INSERT INTO course_action (user_id, kind) VALUES (?, 'AI_EDIT')", idOf("luis@example.com"));
        }

        regenerate(luis, course, topic).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("IMAGE_RATE_LIMITED"));

        assertThat(imageStatus(topic)).isEqualTo("DONE");
    }
}
