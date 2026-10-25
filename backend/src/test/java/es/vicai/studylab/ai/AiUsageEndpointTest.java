package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-15: GET /api/ai/usage, con los permisos de F01 (cada uno ve lo suyo; el tutor, lo de sus menores). */
class AiUsageEndpointTest extends AiTestBase {

    private void call(UUID user, AiTask task) {
        respondWithCost20Cents();
        gateway.complete(AiRequest.of(task, user, "test").data("tema", "x"));
    }

    @Test
    void ac15_itRequiresASession() throws Exception {
        mvc.perform(get("/api/ai/usage")).andExpect(status().isUnauthorized());
    }

    @Test
    void ac15_anAdultSeesTheirOwnSpendTheGlobalCapStateAndTheirMinorsSpend() throws Exception {
        String ana = adultSession("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        call(userIdByEmail("ana@example.com"), AiTask.OUTLINE);
        call(UUID.fromString(hugoId), AiTask.LESSON);

        getAs(ana, "/api/ai/usage")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(matchesPattern("\\d{4}-\\d{2}")))
                .andExpect(jsonPath("$.you.calls").value(1))
                .andExpect(jsonPath("$.you.spendUsd").value(0.2))
                .andExpect(jsonPath("$.budget.percent").value(4.0)) // 0,40 de 10 USD
                .andExpect(jsonPath("$.budget.level").value("OK"))
                .andExpect(jsonPath("$.minors", hasSize(1)))
                .andExpect(jsonPath("$.minors[0].displayName").value("Hugo"))
                .andExpect(jsonPath("$.minors[0].calls").value(1))
                .andExpect(jsonPath("$.minors[0].spendUsd").value(0.2));
    }

    @Test
    void ac15_theResponseExposesExactlyTheAgreedFields() throws Exception {
        String ana = adultSession("ana@example.com");
        createStudent(ana, "Hugo", "hugo");

        String body = getAs(ana, "/api/ai/usage").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder("month", "you", "budget", "minors");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.you").keySet())
                .containsExactlyInAnyOrder("calls", "spendUsd", "limitUsd", "limitReached");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.budget").keySet()).containsExactlyInAnyOrder("percent", "level");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.minors[0]").keySet())
                .containsExactlyInAnyOrder("id", "displayName", "calls", "spendUsd", "limitUsd", "limitReached");
    }

    @Test
    void a1_anAdultSeesTheirLimitAndEachMinorsLimitAndWhetherItWasReached() throws Exception {
        String ana = adultSession("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens,
                                      output_tokens, tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, 1.00, 'OK', 1)""",
                UUID.fromString(hugoId));

        getAs(ana, "/api/ai/usage")
                .andExpect(jsonPath("$.you.limitUsd").value(5.0))
                .andExpect(jsonPath("$.you.limitReached").value(false))
                .andExpect(jsonPath("$.minors[0].limitUsd").value(1.0))
                .andExpect(jsonPath("$.minors[0].limitReached").value(true));
    }

    @Test
    void a1_aMinorOnlyLearnsWhetherTheyReachedTheirLimitNeverTheAmount() throws Exception {
        String ana = adultSession("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens,
                                      output_tokens, tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, 1.00, 'OK', 1)""",
                UUID.fromString(hugoId));

        String body = getAs(login("hugo", PASSWORD), "/api/ai/usage").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Boolean>read(body, "$.you.limitReached")).isTrue();
        assertThat(body).doesNotContain("limitUsd").doesNotContain("spendUsd");
    }

    @Test
    void ac15_theBudgetLevelFollowsTheThresholds() throws Exception {
        String ana = adultSession("ana@example.com");

        spend("8.50");
        getAs(ana, "/api/ai/usage").andExpect(jsonPath("$.budget.level").value("WARNING"));
        spend("2.00");
        getAs(ana, "/api/ai/usage").andExpect(jsonPath("$.budget.level").value("OVER"));
    }

    @Test
    void ac15_anAdultNeverSeesTheMinorsOfAnotherAdult() throws Exception {
        String ana = adultSession("ana@example.com");
        String luis = adultSession("luis@example.com");
        String pabloId = createStudent(luis, "Pablo", "pablo");
        call(UUID.fromString(pabloId), AiTask.LESSON);

        getAs(ana, "/api/ai/usage").andExpect(jsonPath("$.minors", hasSize(0)));
        getAs(luis, "/api/ai/usage").andExpect(jsonPath("$.minors", hasSize(1)));
    }

    @Test
    void ac15_aMinorOnlySeesTheirNumberOfCallsNeverMoneyOrBudget() throws Exception {
        String ana = adultSession("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        call(UUID.fromString(hugoId), AiTask.LESSON);
        String hugo = login("hugo", PASSWORD);

        String body = getAs(hugo, "/api/ai/usage").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder("month", "you");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.you").keySet()).containsExactlyInAnyOrder("calls", "limitReached");
        assertThat(body).doesNotContain("spendUsd").doesNotContain("budget");
    }

    @Test
    void ac15_failedAndBlockedAttemptsAreNotCountedAsCalls() throws Exception {
        String ana = adultSession("ana@example.com");
        UUID anaId = userIdByEmail("ana@example.com");
        fake.failNext(es.vicai.studylab.ai.provider.ProviderErrorKind.AUTH, 1);
        try {
            gateway.complete(request(anaId));
        } catch (AiException expected) {
            // intento fallido: no cuenta como llamada con éxito
        }

        getAs(ana, "/api/ai/usage").andExpect(jsonPath("$.you.calls").value(0));
    }

    @Test
    void ac15_theEndpointIsReadOnly() throws Exception {
        String ana = adultSession("ana@example.com");

        postAs(ana, "/api/ai/usage", Map.of()).andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }
}
