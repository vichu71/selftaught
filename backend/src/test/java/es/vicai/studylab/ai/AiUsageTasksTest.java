package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * F15 (ART-003, ADR-92): {@code GET /api/ai/usage/tasks}, el gasto del mes de quien llama repartido por tarea, de más a menos (a igual gasto, por nombre de tarea).
 * Un menor recibe solo {@code task} y {@code calls}: nunca importes. El usuario sale siempre de la sesión. {@code GET /api/ai/usage} no cambia.
 * Con el proveedor falso cada llamada cuesta 0,20 USD.
 */
class AiUsageTasksTest extends AiTestBase {

    private void call(UUID user, AiTask task) {
        respondWithCost20Cents();
        gateway.complete(AiRequest.of(task, user, "test").data("tema", "x"));
    }

    @Test
    void ac6_itRequiresASession() throws Exception {
        mvc.perform(get("/api/ai/usage/tasks")).andExpect(status().isUnauthorized());
    }

    @Test
    void ac6_anAdultSeesTheirSpendByTaskFromMoreToLessAndTiesByTaskName() throws Exception {
        String ana = adultSession("ana@example.com");
        UUID anaId = userIdByEmail("ana@example.com");
        call(anaId, AiTask.TUTOR);
        call(anaId, AiTask.LESSON);
        call(anaId, AiTask.LESSON);
        call(anaId, AiTask.OUTLINE);

        getAs(ana, "/api/ai/usage/tasks").andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(matchesPattern("\\d{4}-\\d{2}")))
                .andExpect(jsonPath("$.tasks", hasSize(3)))
                .andExpect(jsonPath("$.tasks[0].task").value("LESSON")).andExpect(jsonPath("$.tasks[0].calls").value(2)).andExpect(jsonPath("$.tasks[0].spendUsd").value(0.4))
                .andExpect(jsonPath("$.tasks[1].task").value("OUTLINE")).andExpect(jsonPath("$.tasks[1].calls").value(1)).andExpect(jsonPath("$.tasks[1].spendUsd").value(0.2))
                .andExpect(jsonPath("$.tasks[2].task").value("TUTOR")).andExpect(jsonPath("$.tasks[2].spendUsd").value(0.2));
    }

    @Test
    void ac6_theResponseExposesExactlyTheAgreedFieldsForAnAdult() throws Exception {
        String ana = adultSession("ana@example.com");
        call(userIdByEmail("ana@example.com"), AiTask.LESSON);

        String body = getAs(ana, "/api/ai/usage/tasks").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder("month", "tasks");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.tasks[0]").keySet()).containsExactlyInAnyOrder("task", "calls", "spendUsd");
    }

    @Test
    void ac3_aMinorGetsOnlyTheTaskAndTheNumberOfTimesNeverAnAmount() throws Exception {
        String ana = adultSession("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        String hugo = login(loginIdOf(hugoId), PASSWORD);
        call(UUID.fromString(hugoId), AiTask.LESSON);
        call(UUID.fromString(hugoId), AiTask.TUTOR);

        String body = getAs(hugo, "/api/ai/usage/tasks").andExpect(status().isOk()).andExpect(jsonPath("$.tasks", hasSize(2))).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$.tasks[0]").keySet()).containsExactlyInAnyOrder("task", "calls");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder("month", "tasks");
        assertThat(body).doesNotContainIgnoringCase("spend").doesNotContainIgnoringCase("usd").doesNotContainIgnoringCase("limit").doesNotContain("0.2");
    }

    @Test
    void ac6_everyoneSeesOnlyTheirOwnTasksNeverAnotherUsersNorTheirMinors() throws Exception {
        String ana = adultSession("ana@example.com");
        String luis = adultSession("luis@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        call(userIdByEmail("ana@example.com"), AiTask.OUTLINE);
        call(UUID.fromString(hugoId), AiTask.TUTOR);

        getAs(luis, "/api/ai/usage/tasks").andExpect(status().isOk()).andExpect(jsonPath("$.tasks", hasSize(0)));
        getAs(ana, "/api/ai/usage/tasks").andExpect(jsonPath("$.tasks", hasSize(1))).andExpect(jsonPath("$.tasks[0].task").value("OUTLINE"));
    }

    @Test
    void ac6_aUserWhoNeverUsedTheAiGetsAnEmptyList() throws Exception {
        String ana = adultSession("ana@example.com");

        getAs(ana, "/api/ai/usage/tasks").andExpect(status().isOk()).andExpect(jsonPath("$.tasks", hasSize(0)));
    }

    @Test
    void ac6_callsOfOtherMonthsDoNotCountAndAFailedCallCountsItsCostButNotAsACall() throws Exception {
        String ana = adultSession("ana@example.com");
        UUID anaId = userIdByEmail("ana@example.com");
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status, duration_ms, occurred_at)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'LESSON', 'fake', 'fake-1', 7.00, 'OK', 1, now() - interval '2 months'),
                       (gen_random_uuid(), 1, ?, 'APP', 'TUTOR', 'fake', 'fake-1', 0.05, 'ERROR', 1, now())""", anaId, anaId);

        getAs(ana, "/api/ai/usage/tasks").andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].task").value("TUTOR")).andExpect(jsonPath("$.tasks[0].calls").value(0)).andExpect(jsonPath("$.tasks[0].spendUsd").value(0.05));
    }

    @Test
    void ac6_theTasksAreTheNamesOfTheTaskEnumerationAndNothingFromTheProvider() throws Exception {
        String ana = adultSession("ana@example.com");
        UUID anaId = userIdByEmail("ana@example.com");
        for (AiTask task : List.of(AiTask.OUTLINE, AiTask.INTAKE, AiTask.ASSESSMENT)) call(anaId, task);

        String body = getAs(ana, "/api/ai/usage/tasks").andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(body, "$.tasks[*].task");
        assertThat(names).containsExactlyInAnyOrder("OUTLINE", "INTAKE", "ASSESSMENT");
        assertThat(body).doesNotContain("fake").doesNotContain("provider").doesNotContain("model");
    }

    @Test
    void ac11_theExistingUsageEndpointKeepsItsExactContractForAnAdultAndForAMinor() throws Exception {
        String ana = adultSession("ana@example.com");
        String hugoId = createStudent(ana, "Hugo", "hugo");
        String hugo = login(loginIdOf(hugoId), PASSWORD);

        String adult = getAs(ana, "/api/ai/usage").andReturn().getResponse().getContentAsString();
        String minor = getAs(hugo, "/api/ai/usage").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(adult, "$.you").keySet()).containsExactlyInAnyOrder("calls", "spendUsd", "limitUsd", "limitReached");
        assertThat(JsonPath.<Map<String, Object>>read(minor, "$.you").keySet()).containsExactlyInAnyOrder("calls", "limitReached");
    }

    @Test
    void ac6_readingNeverChangesTheUsageRows() throws Exception {
        String ana = adultSession("ana@example.com");
        call(userIdByEmail("ana@example.com"), AiTask.LESSON);
        int rows = usage().size();

        getAs(ana, "/api/ai/usage/tasks").andExpect(status().isOk());

        assertThat(usage()).hasSize(rows);
    }
}
