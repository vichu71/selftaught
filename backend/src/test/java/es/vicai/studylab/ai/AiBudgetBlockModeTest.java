package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-8 en modo BLOCK (opcional, apagado por defecto: decisión del usuario, ADR-19). Contexto propio de Spring
 * porque cambia la configuración.
 */
@TestPropertySource(properties = "app.ai.budget.mode=BLOCK")
class AiBudgetBlockModeTest extends AiTestBase {

    private AiException blocked(UUID user) {
        return org.junit.jupiter.api.Assertions.assertThrows(AiException.class, () -> gateway.complete(request(user)));
    }

    @Test
    void ac8_atOrOverTheLimitCallsAreRejectedWithoutReachingTheProvider() {
        spend("10.00");
        UUID user = newUser();

        AiException e = blocked(user);

        assertThat(e.kind()).isEqualTo(AiErrorKind.BUDGET_BLOCKED);
        assertThat(fake.calls()).isEmpty();
        var row = usage().get(usage().size() - 1); // la primera fila es el gasto previo insertado por el test
        assertThat(row.get("status")).isEqualTo("BLOCKED");
        assertThat(row.get("error_kind")).isEqualTo("BUDGET_BLOCKED");
        assertThat(auditActions()).contains("AI_BUDGET_BLOCKED");
    }

    @Test
    void ac8_aCallWhoseWorstCaseWouldCrossTheLimitIsRejectedBeforehand() {
        // Peor caso de una llamada OUTLINE: 200 tokens de salida · 2 USD/M = 0,0004 USD más la entrada estimada
        spend("9.9997");

        assertThat(blocked(newUser()).kind()).isEqualTo(AiErrorKind.BUDGET_BLOCKED);
        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void ac8_belowTheLimitWithRoomForTheWorstCaseTheCallGoesThrough() {
        spend("9.00");

        assertThat(gateway.complete(request(newUser())).text()).isNotBlank();
    }

    @Test
    void ac8_blockingDoesNotDependOnTheEmbeddingTaskWhichIsFree() {
        spend("10.00");

        assertThat(gateway.embed(newUser(), java.util.List.of("uno")).vectors()).hasSize(1);
    }

    @Test
    void ac8_theBlockedMessageIsClearAndGeneric() {
        spend("10.00");

        assertThatThrownBy(() -> gateway.complete(request(newUser()))).hasMessageContaining("tope");
    }
}
