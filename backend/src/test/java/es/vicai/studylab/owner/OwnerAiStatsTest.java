package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-3, AC-11 (ART-003): {@code GET /api/owner/ai?range=7|30|90} y {@code /prices}. Las cifras salen de {@code ai_usage} (solo {@code source = 'APP'}; el benchmark va aparte).
 * Sembrado: Ana 0,50 USD ahora (anthropic/m1, LESSON, 1 000 ms), Hugo 0,25 hace 3 días (m1, TUTOR, 3 000 ms), Ana 0,05 hace 10 días (gemini/g1, ERROR, 500 ms),
 * Ana 1,00 hace 40 días (m1) y 2,00 de benchmark.
 */
class OwnerAiStatsTest extends OwnerTestBase {

    private Family seeded() throws Exception {
        Family f = quietFamily();
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "0.50", 1000, 500, 1000, "1 minute");
        aiCall(f.hugoId(), "anthropic", "m1", "TUTOR", "OK", "0.25", 200, 100, 3000, "3 days");
        aiCall(f.anaId(), "gemini", "g1", "LESSON", "ERROR", "0.05", 0, 0, 500, "10 days");
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "1.00", 10, 10, 100, "40 days");
        benchmarkUsage("2.00");
        return f;
    }

    /** El día es en UTC; el mes es el natural de Madrid, el mismo de los topes (ADR-19). */
    private double sql(String since, boolean onlyApp) {
        String zone = "day".equals(since) ? "utc" : "Europe/Madrid";
        return owner.queryForObject("SELECT coalesce(sum(cost_usd), 0) FROM ai_usage WHERE " + (onlyApp ? "source = 'APP' AND " : "")
                + "occurred_at >= date_trunc('" + since + "', now() AT TIME ZONE '" + zone + "') AT TIME ZONE '" + zone + "'", Double.class);
    }

    private double sql(String since) {
        return sql(since, true);
    }

    @Test
    void ac3_theRangeFiguresAreExactAndTheBenchmarkStaysApart() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/ai?range=30").andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value(30))
                .andExpect(jsonPath("$.rangeUsd").value(0.80))
                .andExpect(jsonPath("$.totals.calls").value(3))
                .andExpect(jsonPath("$.totals.errorPercent").value(33.3))
                .andExpect(jsonPath("$.totals.blockedPercent").value(0.0))
                .andExpect(jsonPath("$.totals.inputTokens").value(1200))
                .andExpect(jsonPath("$.totals.outputTokens").value(600))
                .andExpect(jsonPath("$.benchmark.calls").value(1))
                .andExpect(jsonPath("$.benchmark.usd").value(2.00));
    }

    @Test
    void ac3_todayAndMonthUseTheUtcCalendar() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/ai").andExpect(status().isOk())
                .andExpect(jsonPath("$.todayUsd").value(closeTo(sql("day"), 0.000001)))
                .andExpect(jsonPath("$.monthUsd").value(closeTo(sql("month"), 0.000001)))
                .andExpect(jsonPath("$.range").value(30)); // sin range, 30
    }

    @Test
    void ac3_theBiggerRangesAddOlderCalls() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/ai?range=7").andExpect(jsonPath("$.rangeUsd").value(0.75)).andExpect(jsonPath("$.daily", hasSize(7)));
        ownerGet(f.ana(), "/api/owner/ai?range=90").andExpect(jsonPath("$.rangeUsd").value(1.80)).andExpect(jsonPath("$.daily", hasSize(90)))
                .andExpect(jsonPath("$.totals.calls").value(4));
    }

    @Test
    void ac3_theDailySeriesHasOneEntryPerDayWithZerosAndEndsToday() throws Exception {
        Family f = seeded();
        String today = owner.queryForObject("SELECT (now() AT TIME ZONE 'utc')::date::text", String.class);

        ownerGet(f.ana(), "/api/owner/ai?range=30").andExpect(jsonPath("$.daily", hasSize(30)))
                .andExpect(jsonPath("$.daily[29].day").value(today))
                .andExpect(jsonPath("$.daily[0].usd").value(0.0))
                .andExpect(jsonPath("$.daily[0].calls").value(0));
        double sum = json.readTree(ownerGet(f.ana(), "/api/owner/ai?range=30").andReturn().getResponse().getContentAsString()).get("daily")
                .findValues("usd").stream().mapToDouble(n -> n.asDouble()).sum();
        assertThat(sum).isCloseTo(0.80, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void ac3_theBreakdownsByModelProviderTaskAndUserAreOrderedByCost() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/ai?range=30").andExpect(status().isOk())
                .andExpect(jsonPath("$.byModel", hasSize(2)))
                .andExpect(jsonPath("$.byModel[0].provider").value("anthropic"))
                .andExpect(jsonPath("$.byModel[0].model").value("m1"))
                .andExpect(jsonPath("$.byModel[0].usd").value(0.75))
                .andExpect(jsonPath("$.byModel[0].calls").value(2))
                .andExpect(jsonPath("$.byModel[0].errorPercent").value(0.0))
                .andExpect(jsonPath("$.byModel[0].avgMs").value(2000.0))
                .andExpect(jsonPath("$.byModel[0].p95Ms").value(2900.0))
                .andExpect(jsonPath("$.byModel[1].model").value("g1"))
                .andExpect(jsonPath("$.byModel[1].errorPercent").value(100.0))
                .andExpect(jsonPath("$.byProvider[0].provider").value("anthropic"))
                .andExpect(jsonPath("$.byProvider[0].usd").value(0.75))
                .andExpect(jsonPath("$.byTask[0].task").value("LESSON"))
                .andExpect(jsonPath("$.byTask[0].usd").value(0.55))
                .andExpect(jsonPath("$.byTask[1].task").value("TUTOR"))
                .andExpect(jsonPath("$.byTask[1].usd").value(0.25))
                .andExpect(jsonPath("$.byUser", hasSize(2)))
                .andExpect(jsonPath("$.byUser[0].userId").value(f.anaId().toString()))
                .andExpect(jsonPath("$.byUser[0].usd").value(0.55))
                .andExpect(jsonPath("$.byUser[1].userId").value(f.hugoId().toString()))
                .andExpect(jsonPath("$.byUser[1].kind").value("MINOR"));
    }

    @Test
    void ac4_aMinorIsLabelledByTheirAccessIdAndNeverByTheirName() throws Exception {
        Family f = seeded();
        String hugoLoginId = loginIdOf(f.hugoId().toString());

        String body = ownerGet(f.ana(), "/api/owner/ai").andExpect(jsonPath("$.byUser[1].label").value(hugoLoginId)).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"Hugo\"");
    }

    @Test
    void ac3_onlyTheTenBiggestSpendersAreListed() throws Exception {
        Family f = quietFamily();
        for (int i = 0; i < 12; i++) aiCall(adultRow("u" + i + "@example.com", "U" + i, "1 day"), "anthropic", "m1", "LESSON", "OK", "0." + (10 + i), 1, 1, 1, "1 hour");

        ownerGet(f.ana(), "/api/owner/ai").andExpect(jsonPath("$.byUser", hasSize(10))).andExpect(jsonPath("$.byUser[0].usd").value(0.21));
    }

    @Test
    void ac3_blockedCallsCountInTheBlockedPercentAndNotAsErrors() throws Exception {
        Family f = quietFamily();
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "0.10", 1, 1, 100, "1 hour");
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "BLOCKED", "0.00", 0, 0, 0, "1 hour");
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "BLOCKED", "0.00", 0, 0, 0, "1 hour");
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "0.10", 1, 1, 100, "1 hour");

        ownerGet(f.ana(), "/api/owner/ai").andExpect(jsonPath("$.totals.calls").value(4)).andExpect(jsonPath("$.totals.blockedPercent").value(50.0))
                .andExpect(jsonPath("$.totals.errorPercent").value(0.0));
    }

    @Test
    void ac3_theCostPerCourseAndPerActiveUserUseTheRightDenominators() throws Exception {
        Family f = quietFamily();
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, course_id, source, task, provider, model, cost_usd, status)
                VALUES (gen_random_uuid(), 1, ?, ?, 'APP', 'LESSON', 'fake', 'fake-1', 0.60, 'OK'), (gen_random_uuid(), 1, ?, ?, 'APP', 'LESSON', 'fake', 'fake-1', 0.30, 'OK')""",
                f.hugoId(), f.course(), f.hugoId(), f.course());
        UUID other = insertCourse(f.hugoId(), "Otro", 1, false, java.util.List.of());
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, course_id, source, task, provider, model, cost_usd, status)
                VALUES (gen_random_uuid(), 1, ?, ?, 'APP', 'LESSON', 'fake', 'fake-1', 0.30, 'OK')""", f.hugoId(), other);

        // 1,20 USD repartidos en 2 cursos; 3 usuarios con inicio de sesión en el rango (Ana, Hugo y Luis)
        ownerGet(f.ana(), "/api/owner/ai").andExpect(jsonPath("$.costPerCourse").value(0.60)).andExpect(jsonPath("$.costPerActiveUser").value(closeTo(0.40, 0.0001)));
    }

    @Test
    void ac3_theProjectionIsNotNegativeAndOnlyReliableAfterThreeDaysOfData() throws Exception {
        Family f = quietFamily();
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "1.00", 1, 1, 1, "1 minute");

        ownerGet(f.ana(), "/api/owner/ai").andExpect(jsonPath("$.projection.reliable").value(false))
                .andExpect(jsonPath("$.projection.usd").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1.0)));

        // una llamada al principio del mes y otra ahora: la proyección es fiable si han pasado 3 días o más entre ellas
        // ai_usage es de solo añadir: se vacía y se vuelve a sembrar
        String sinceMonthStart = owner.queryForObject("SELECT (now() - (date_trunc('month', now() AT TIME ZONE 'utc') AT TIME ZONE 'utc' + interval '1 second'))::text", String.class);
        owner.execute("TRUNCATE ai_usage, ai_budget_alert RESTART IDENTITY");
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "1.00", 1, 1, 1, sinceMonthStart);
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "1.00", 1, 1, 1, "1 minute");
        boolean expected = owner.queryForObject("SELECT now() - min(occurred_at) >= interval '3 days' FROM ai_usage WHERE source = 'APP'", Boolean.class);
        ownerGet(f.ana(), "/api/owner/ai").andExpect(jsonPath("$.projection.reliable").value(expected));
    }

    @Test
    void ac3_theGlobalCapShowsTheLimitAndThePercentOfTheMonth() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/ai").andExpect(jsonPath("$.globalLimit.limitUsd").value(10.0))
                .andExpect(jsonPath("$.globalLimit.percent").value(closeTo(sql("month", false) * 10, 0.1))); // el tope global cuenta también el benchmark
    }

    @Test
    void ac3_anEmptyInstallationShowsZerosWithoutDividingByZero() throws Exception {
        Family f = quietFamily();

        ownerGet(f.ana(), "/api/owner/ai").andExpect(status().isOk())
                .andExpect(jsonPath("$.rangeUsd").value(0.0))
                .andExpect(jsonPath("$.totals.calls").value(0))
                .andExpect(jsonPath("$.totals.errorPercent").value(0.0))
                .andExpect(jsonPath("$.totals.blockedPercent").value(0.0))
                .andExpect(jsonPath("$.totals.avgMs").value(0.0))
                .andExpect(jsonPath("$.costPerCourse").value(0.0))
                .andExpect(jsonPath("$.projection.usd").value(0.0))
                .andExpect(jsonPath("$.projection.reliable").value(false))
                .andExpect(jsonPath("$.byModel", hasSize(0)))
                .andExpect(jsonPath("$.byUser", hasSize(0)))
                .andExpect(jsonPath("$.daily", hasSize(30)))
                .andExpect(jsonPath("$.benchmark.calls").value(0));
    }

    @Test
    void ac3_aModelWithNoCallsInTheRangeDoesNotAppear() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/ai?range=7").andExpect(jsonPath("$.byModel", hasSize(1))).andExpect(jsonPath("$.byModel[0].model").value("m1"))
                .andExpect(jsonPath("$.byModel[?(@.model=='g1')]").isEmpty());
    }

    @Test
    void ac3_thePanelOnlyReadsSoItNeverChangesTheUsageRows() throws Exception {
        Family f = seeded();
        int before = rows("ai_usage");
        BigDecimal spent = owner.queryForObject("SELECT sum(cost_usd) FROM ai_usage", BigDecimal.class);

        for (String path : REPORTS) ownerGet(f.ana(), path).andExpect(status().isOk());
        ownerGet(f.ana(), "/api/owner/users").andExpect(status().isOk());

        assertThat(rows("ai_usage")).isEqualTo(before);
        assertThat(owner.queryForObject("SELECT sum(cost_usd) FROM ai_usage", BigDecimal.class)).isEqualByComparingTo(spent);
    }

    @Test
    void ac11_thePriceTableComesFromTheConfigurationWithItsVerificationDate() throws Exception {
        Family f = quietFamily();

        ownerGet(f.ana(), "/api/owner/prices").andExpect(status().isOk())
                .andExpect(jsonPath("$.models[?(@.model=='fake-1')].provider").value(hasItem("fake")))
                .andExpect(jsonPath("$.models[?(@.model=='fake-1')].inputPerMillion").value(hasItem(1.0)))
                .andExpect(jsonPath("$.models[?(@.model=='fake-1')].outputPerMillion").value(hasItem(2.0)))
                .andExpect(jsonPath("$.models[?(@.model=='fake-1')].verifiedOn").value(hasItem("2020-01-01")))
                .andExpect(jsonPath("$.models[?(@.model=='fake-1')].stale").value(hasItem(true)))
                .andExpect(jsonPath("$.images").isArray());
    }

    @Test
    void ac11_aPriceWithoutDateHasNoDateAndCountsAsOld() throws Exception {
        Family f = quietFamily();

        ownerGet(f.ana(), "/api/owner/prices")
                .andExpect(jsonPath("$.models[?(@.model=='fake-undated')].verifiedOn").value(hasItem(nullValue())))
                .andExpect(jsonPath("$.models[?(@.model=='fake-undated')].stale").value(hasItem(true)))
                .andExpect(jsonPath("$.models[?(@.model=='fake-undated')].inputPerMillion").value(hasItem(3.0)))
                .andExpect(jsonPath("$.models[?(@.model=='fake-undated')].outputPerMillion").value(hasItem(not(nullValue()))));
    }

    @Test
    void ac11_theApplicationStillStartsWithPricesThatHaveNoDate() throws Exception {
        // el contexto de este test ya tiene `fake-undated` sin `verified-on`: llegar hasta aquí es la prueba; además, nunca se llama a un proveedor
        Family f = quietFamily();
        int calls = fake.calls().size();

        ownerGet(f.ana(), "/api/owner/prices").andExpect(status().isOk());

        assertThat(fake.calls()).hasSize(calls);
    }
}
