package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** F12 AC-3 y AC-15: el interruptor «Con ilustraciones» del asistente. Solo lo ofrece la API a un adulto y con proveedor de imagen configurado. */
class IllustrationOfferTest extends IllustrationTestBase {

    @Test
    void ac3_anAdultSeesTheOfferWithThePricePerImage() throws Exception {
        String luis = session("luis@example.com");

        getAs(luis, "/api/illustrations/offer").andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.pricePerImageUsd").value(0.05))
                .andExpect(jsonPath("$.maxTopics").isNumber());
    }

    @Test
    void ac3_aMinorNeverSeesTheOffer() throws Exception {
        String ana = session("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);

        getAs(hugo, "/api/illustrations/offer").andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void ac3_theOfferNeedsASession() throws Exception {
        mvc.perform(get("/api/illustrations/offer")).andExpect(status().isUnauthorized());
    }

    // ---------- el alta del curso ----------

    private Map<String, Object> body(String session, boolean illustrations) throws Exception {
        String intake = started(session, "Los volcanes", List.of());
        return Map.of("intakeId", intakeId(intake), "answers", defaultAnswers(intake), "illustrations", illustrations);
    }

    @Test
    void ac3_anAdultCreatesACourseWithIllustrationsAndTheChoiceIsStoredInTheCourse() throws Exception {
        String luis = session("luis@example.com");
        model(TWO_QUESTIONS);

        String created = createCourseRequest(luis, body(luis, true)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        UUID course = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));
        assertThat(owner.queryForObject("SELECT illustrations FROM course WHERE id = ?", Boolean.class, course)).isTrue();
    }

    @Test
    void ac3_theSwitchIsOffByDefaultAndTheSheetOfTheCourseDoesNotChange() throws Exception {
        String luis = session("luis@example.com");
        model(TWO_QUESTIONS);
        String intake = started(luis, "Los volcanes", List.of());

        String created = create(luis, intakeId(intake), defaultAnswers(intake)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        UUID course = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));
        assertThat(owner.queryForObject("SELECT illustrations FROM course WHERE id = ?", Boolean.class, course)).isFalse();
        assertThat(owner.queryForObject("SELECT sheet->>'schemaVersion' FROM course WHERE id = ?", String.class, course)).isEqualTo("1");
        assertThat(owner.queryForObject("SELECT sheet::text FROM course WHERE id = ?", String.class, course)).doesNotContain("illustrations");
    }

    @Test
    void ac3_aMinorCannotTurnItOnEvenCallingTheApiByHand() throws Exception {
        String ana = session("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        model(TWO_QUESTIONS);

        createCourseRequest(hugo, body(hugo, true)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ILLUSTRATIONS_UNAVAILABLE"));

        assertThat(courses()).isEmpty();
    }

    @Test
    void ac3_aValueThatIsNotABooleanIsRejected() throws Exception {
        String luis = session("luis@example.com");
        model(TWO_QUESTIONS);
        String intake = started(luis, "Los volcanes", List.of());

        createCourseRequest(luis, Map.of("intakeId", intakeId(intake), "answers", defaultAnswers(intake), "illustrations", "sí")).andExpect(status().isBadRequest());
    }

    @Test
    void ac3_theRefusalLeavesNoCourseAndTheIntakeStillUsable() throws Exception {
        String ana = session("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        model(TWO_QUESTIONS);
        Map<String, Object> request = body(hugo, true);

        createCourseRequest(hugo, request).andExpect(status().isBadRequest());
        createCourseRequest(hugo, Map.of("intakeId", request.get("intakeId"), "answers", request.get("answers"), "illustrations", false)).andExpect(status().isCreated());
    }
}
