package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** F12 AC-1 y casos borde: sin proveedor de imagen configurado las ilustraciones están apagadas y la aplicación funciona igual. */
@TestPropertySource(properties = {"app.ai.tasks.image.provider=", "app.ai.tasks.image.model="})
class IllustrationDisabledTest extends IllustrationTestBase {

    @Test
    void ac1_theOfferSaysItIsNotAvailable() throws Exception {
        String luis = session("luis@example.com");

        getAs(luis, "/api/illustrations/offer").andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void ac1_aCourseCannotBeCreatedWithIllustrations() throws Exception {
        String luis = session("luis@example.com");
        model(TWO_QUESTIONS);
        String intake = started(luis, "Los volcanes", List.of());

        createCourseRequest(luis, Map.of("intakeId", intakeId(intake), "answers", defaultAnswers(intake), "illustrations", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ILLUSTRATIONS_UNAVAILABLE"));
    }

    @Test
    void ac1_aCourseThatAlreadyHadTheSwitchNeverCallsAnyoneAndDoesNotBreak() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");

        assertThat(drain()).isZero();

        assertThat(fake.imageCalls()).isEmpty();
        assertThat(imageRows(course)).isEmpty();
        assertThat(statusOf(course)).isEqualTo("READY");
        detailOf(session("luis@example.com"), course).andExpect(status().isOk());
    }

    @Test
    void ac1_regeneratingAnImageIsNotPossible() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        String luis = session("luis@example.com");

        regenerate(luis, course, topicIds(course).get(0)).andExpect(status().isNotFound());
    }
}
