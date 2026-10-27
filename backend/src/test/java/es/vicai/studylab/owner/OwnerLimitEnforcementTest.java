package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-7 (D8, ADR-88): el tope fijado por el propietario manda sobre el de la configuración (menor 1,00 y adulto 5,00 en test), lo lee el {@code BudgetService} real en cada llamada y
 * sigue cortando siempre. Lo ven igual el propio usuario, su tutor y el panel.
 */
class OwnerLimitEnforcementTest extends OwnerTestBase {

    private AiException rejected(UUID user) {
        return org.junit.jupiter.api.Assertions.assertThrows(AiException.class, () -> gateway.complete(request(user)));
    }

    private void setLimit(String session, UUID user, Object usd) throws Exception {
        putJson(session, userPath(user, "/limit"), Map.of("limitUsd", usd)).andExpect(status().isNoContent());
    }

    @Test
    void ac7_aLimitOfZeroStopsEveryCallOfThatUserWithoutReachingTheProvider() throws Exception {
        Family f = quietFamily();
        setLimit(f.ana(), f.luisId(), 0);
        fake.reset();

        assertThat(rejected(f.luisId()).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void ac7_aHigherLimitLetsAMinorKeepWorkingPastTheDefault() throws Exception {
        Family f = quietFamily();
        usageRow(f.hugoId(), "OUTLINE", "1.50", "1 hour"); // por encima del tope de menor (1,00)
        fake.reset();
        assertThat(rejected(f.hugoId()).kind()).isEqualTo(AiErrorKind.USER_LIMIT);

        setLimit(f.ana(), f.hugoId(), 20);

        assertThatCode(() -> gateway.complete(request(f.hugoId()))).doesNotThrowAnyException();
        assertThat(fake.calls()).hasSize(1);
    }

    @Test
    void ac7_aLimitBelowWhatIsAlreadySpentCutsImmediately() throws Exception {
        Family f = quietFamily();
        usageRow(f.luisId(), "OUTLINE", "2.00", "1 hour");
        fake.reset();
        assertThatCode(() -> gateway.complete(request(f.luisId()))).doesNotThrowAnyException();
        fake.reset();

        setLimit(f.ana(), f.luisId(), 1);

        assertThat(rejected(f.luisId()).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void ac7_clearingTheLimitGoesBackToTheDefaultWhichCutsAgain() throws Exception {
        Family f = quietFamily();
        usageRow(f.hugoId(), "OUTLINE", "1.50", "1 hour");
        setLimit(f.ana(), f.hugoId(), 20);
        assertThatCode(() -> gateway.complete(request(f.hugoId()))).doesNotThrowAnyException();

        deleteWithCsrf(f.ana(), userPath(f.hugoId(), "/limit")).andExpect(status().isNoContent());
        fake.reset();

        assertThat(rejected(f.hugoId()).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
    }

    @Test
    void ac7_theLimitOfOneUserDoesNotChangeAnyoneElsesAndNeverLiftsTheGlobalCap() throws Exception {
        Family f = quietFamily();
        setLimit(f.ana(), f.luisId(), 0);
        fake.reset();

        assertThatCode(() -> gateway.complete(request(f.anaId()))).doesNotThrowAnyException(); // Ana mantiene el suyo
        assertThat(rejected(f.luisId()).kind()).isEqualTo(AiErrorKind.USER_LIMIT);
        // y el tope global (10 USD en test, modo WARN) sigue siendo solo un aviso: un tope personal de 500 no lo convierte en otra cosa
        setLimit(f.ana(), f.luisId(), 500);
        spend("11.00"); // gasto de nadie en concreto: supera el global
        assertThatCode(() -> gateway.complete(request(f.luisId()))).doesNotThrowAnyException();
    }

    @Test
    void ac7_theUserTheirTutorAndTheirOwnUsageScreenShowTheRealLimit() throws Exception {
        Family f = quietFamily();
        setLimit(f.ana(), f.hugoId(), 7.5);
        setLimit(f.ana(), f.luisId(), 9);

        getAs(f.luis(), "/api/ai/usage").andExpect(status().isOk()).andExpect(jsonPath("$.you.limitUsd").value(9.0));
        getAs(f.ana(), "/api/ai/usage").andExpect(jsonPath("$.minors[0].limitUsd").value(7.5));
        panelGet(f.ana(), f.hugoId(), "/overview").andExpect(jsonPath("$.usage.limitUsd").value(7.5));
    }

    @Test
    void ac7_theOverrideSurvivesTheMonthChangeBecauseItIsAStandingValueNotAMonthlyOne() throws Exception {
        Family f = quietFamily();
        setLimit(f.ana(), f.luisId(), 8);
        usageRow(f.luisId(), "OUTLINE", "7.00", "2 months");

        assertThat(overrideOf(f.luisId())).isEqualByComparingTo("8.00");
        assertThatCode(() -> gateway.complete(request(f.luisId()))).doesNotThrowAnyException(); // el gasto de otro mes no cuenta
    }
}
