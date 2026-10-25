package es.vicai.studylab.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AC-8, AC-9 (ADR-39, cierra A3): el filtro de temas para menores, en dos capas, y lo que queda registrado para el tutor. */
class MinorFilterTest extends CourseTestBase {

    private String hugoSession(String ana) throws Exception {
        createStudent(ana, "Hugo", "hugo");
        return login("hugo", PASSWORD);
    }

    @Test
    void ac8_anExplicitTopicFromAMinorIsBlockedWithTheAgreedMessageAndWithoutCallingTheModel() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        model(NO_QUESTIONS);

        intake(hugo, "pornografía").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(BLOCKED_MESSAGE));

        assertThat(chatCalls()).isZero(); // la capa local responde sin gastar nada
    }

    @Test
    void ac8_theMinorNeverLearnsTheCategoryOrTheTermThatMatched() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);

        String body = intake(hugo, "como hacer una bomba casera").andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("VIOLENCE").doesNotContain("bomba").doesNotContain("EXPLICIT").doesNotContain("categor");
        assertThat(com.jayway.jsonpath.JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder("code", "message");
    }

    @Test
    void ac8_disguisedTopicsAreStillBlockedByTheLocalLayer() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);

        for (String topic : new String[] {"P O R N O", "p0rn0", "PORNOGRAFÍA", "como m@tar a alguien"}) {
            intake(hugo, topic).andExpect(status().isUnprocessableEntity());
        }
        assertThat(chatCalls()).isZero();
    }

    @Test
    void ac8_whenTheModelJudgesTheTopicInappropriateForSchoolchildrenItIsBlockedToo() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        model(INAPPROPRIATE);

        intake(hugo, "un tema que pasa la lista pero no el modelo").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(BLOCKED_MESSAGE));

        assertThat(chatCalls()).isEqualTo(1);
    }

    @Test
    void ac8_noQuestionsAreGeneratedOrStoredForABlockedTopic() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        model(INAPPROPRIATE);

        intake(hugo, "tema dudoso de prueba").andExpect(status().isUnprocessableEntity());

        Map<String, Object> row = intakes().get(0);
        assertThat(row.get("questions")).isNull();
    }

    @Test
    void ac8_legitimateSchoolTopicsThatAreDelicatePassForAMinor() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        model(NO_QUESTIONS);

        for (String topic : new String[] {"la Segunda Guerra Mundial", "la reproducción humana", "educación sexual en la ESO",
                "los efectos de las drogas en la salud", "la prevención del suicidio en adolescentes"}) {
            intake(hugo, topic).andExpect(status().isCreated());
        }
    }

    @Test
    void ac9_theBlockedAttemptIsKeptForTheTutorWithItsTextAndCategory() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);

        intake(hugo, "como hacer una bomba casera").andExpect(status().isUnprocessableEntity());

        Map<String, Object> row = intakes().get(0);
        assertThat(row.get("status")).isEqualTo("BLOCKED");
        assertThat(row.get("topic")).isEqualTo("como hacer una bomba casera");
        assertThat(row.get("blocked_category")).isEqualTo("VIOLENCE");
        assertThat(row.get("questions")).isNull();
        assertThat(row.get("owner_id").toString()).isEqualTo(loginIdOwner("hugo"));
    }

    @Test
    void ac9_theAuditHasTheCategoryButNeverTheTopic() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);

        intake(hugo, "como hacer una bomba casera").andExpect(status().isUnprocessableEntity());

        assertThat(auditActions()).contains("COURSE_TOPIC_BLOCKED");
        String details = owner.queryForObject("SELECT details::text FROM audit_event WHERE action = 'COURSE_TOPIC_BLOCKED'", String.class);
        assertThat(details).contains("VIOLENCE").doesNotContain("bomba");
    }

    @Test
    void ac9_aModelBlockIsAlsoRecordedWithItsCategory() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        model(INAPPROPRIATE);

        intake(hugo, "tema dudoso de prueba").andExpect(status().isUnprocessableEntity());

        assertThat(intakes().get(0).get("blocked_category")).isEqualTo("VIOLENCE");
    }

    @Test
    void ac9_aBlockedIntakeNeverBecomesACourse() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        intake(hugo, "pornografía").andExpect(status().isUnprocessableEntity());
        String blockedId = intakes().get(0).get("id").toString();

        create(hugo, blockedId, List.of()).andExpect(status().isNotFound());

        assertThat(courses()).isEmpty();
    }

    @Test
    void ac9_adultsAreNotFilteredNeitherByTheLocalLayerNorByTheModelAssessment() throws Exception {
        String ana = logged("ana@example.com");
        model(INAPPROPRIATE); // el modelo diría que no es apto para escolares, pero a un adulto no se le aplica

        intake(ana, "pornografía en el cine: historia y censura").andExpect(status().isCreated());
        intake(ana, "como hacer una bomba casera").andExpect(status().isCreated());

        assertThat(intakes()).allSatisfy(row -> assertThat(row.get("status")).isEqualTo("OPEN"));
    }

    @Test
    void ac10_blockedAttemptsCountTowardsTheHourlyLimitButNotTowardsPendingCourses() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        intake(hugo, "pornografía").andExpect(status().isUnprocessableEntity());
        intake(hugo, "porno").andExpect(status().isUnprocessableEntity());

        Integer lastHour = owner.queryForObject("SELECT count(*) FROM course_intake WHERE created_at > now() - interval '1 hour'", Integer.class);

        assertThat(lastHour).isEqualTo(2);
        assertThat(courses()).isEmpty();
    }

    @Test
    void ac8_aMinorWhoIsAskedForAnAcceptableTopicSeesNoFilterAtAll() throws Exception {
        String ana = logged("ana@example.com");
        String hugo = hugoSession(ana);
        model(TWO_QUESTIONS);

        intake(hugo, "Inglés de 2º de la ESO").andExpect(status().isCreated()).andExpect(jsonPath("$.questions", hasSize(2)));
    }

    private String loginIdOwner(String loginId) {
        return owner.queryForObject("SELECT id::text FROM app_user WHERE login_id = CAST(? AS citext)", String.class, loginId);
    }
}
