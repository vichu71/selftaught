package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * F19 AC-4 y AC-5 (ADR-100): la lectura de una foto es una cuarta modalidad de la capa de IA, la tarea OCR. Precio ficticio por tokens: fake-vision = 1 USD/M de
 * entrada y 2 USD/M de salida (1.000 + 100 tokens = 0,0012 USD). El proveedor falso nunca llama a una API de pago.
 */
class VisionGatewayTest extends AiTestBase {

    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 9, 9};

    private AiReadRequest read(UUID user) {
        return AiReadRequest.of(user, IMAGE, "image/jpeg", "adulto");
    }

    private Map<String, Object> lastUsage() {
        List<Map<String, Object>> rows = usage();
        return rows.get(rows.size() - 1);
    }

    @Test
    void ac4_theOcrTaskIsOneMoreTaskOfTheLayerAndGoesThroughTheGateway() {
        assertThat(AiTask.valueOf("OCR")).isNotNull();
        assertThat(gateway.providerFor(AiTask.OCR)).isEqualTo("fake");
        assertThat(gateway.readEnabled()).isTrue();
    }

    @Test
    void ac4_theGatewayReturnsTheRawAnswerWithItsCostByTokensAndTheModelOfTheConfiguration() {
        fake.visionRespondsWith("{\"appropriate\":true,\"text\":\"hola\"}", 1_000, 100);

        AiReadResponse response = gateway.readImage(read(newUser()));

        assertThat(response.raw()).isEqualTo("{\"appropriate\":true,\"text\":\"hola\"}");
        assertThat(response.costUsd()).isEqualByComparingTo(new BigDecimal("0.001200"));
        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("fake-vision");
        assertThat(fake.visionCalls()).hasSize(1);
        assertThat(fake.visionCalls().get(0).maxOutputTokens()).isEqualTo(2000); // el tope de salida propio de la tarea
    }

    @Test
    void ac4_everyCallIsRecordedAsTheOcrTaskAndNeverItsImageNorItsText() {
        UUID user = newUser();
        fake.visionRespondsWith("{\"appropriate\":true,\"text\":\"texto-secreto-del-alumno\"}", 1_000, 100);

        gateway.readImage(read(user));

        Map<String, Object> row = lastUsage();
        assertThat(row.get("task")).isEqualTo("OCR");
        assertThat(row.get("status")).isEqualTo("OK");
        assertThat(row.get("user_id")).isEqualTo(user);
        assertThat(row.get("source")).isEqualTo("APP");
        assertThat(row.get("input_tokens")).isEqualTo(1_000);
        assertThat(row.get("output_tokens")).isEqualTo(100);
        assertThat(row.values().toString()).doesNotContain("texto-secreto");
    }

    @Test
    void ac4_theCostCountsTowardsTheUsersMonthlyLimitAndStopsTheCallsWhenItIsReached() {
        UUID user = newUser();
        owner.update("INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status) "
                + "VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 9.9999, 'OK')", user);

        assertThatThrownBy(() -> gateway.readImage(read(user))).isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(((AiException) e).kind()).isEqualTo(AiErrorKind.USER_LIMIT));

        assertThat(fake.visionCalls()).isEmpty();
        assertThat(lastUsage().get("status")).isEqualTo("BLOCKED");
    }

    @Test
    void ac4_aCallTheProviderChargedForIsAccountedEvenIfTheAnswerComesBackEmpty() {
        UUID user = newUser();
        fake.visionRespondsWith("", 1_000, 0);

        assertThatThrownBy(() -> gateway.readImage(read(user))).isInstanceOf(AiException.class).satisfies(e -> {
            assertThat(((AiException) e).kind()).isEqualTo(AiErrorKind.INVALID_RESPONSE);
            assertThat(((AiException) e).costUsd()).isEqualByComparingTo("0.001");
        });

        assertThat(lastUsage().get("status")).isEqualTo("ERROR");
        assertThat((BigDecimal) lastUsage().get("cost_usd")).isEqualByComparingTo("0.001");
    }

    @Test
    void ac4_aRetryableFailureIsRetriedInsideTheGatewayAndEveryAttemptIsRecorded() {
        UUID user = newUser();
        fake.visionFailsTimes(2, ProviderErrorKind.SERVER_ERROR);
        fake.visionRespondsWith("{\"appropriate\":true,\"text\":\"hola\"}", 1_000, 100);

        gateway.readImage(read(user));

        assertThat(usage()).extracting(r -> r.get("status")).containsExactly("ERROR", "ERROR", "OK");
        assertThat(usage()).extracting(r -> r.get("request_id")).containsOnly(usage().get(0).get("request_id"));
    }

    @Test
    void ac4_aDeactivatedUserCannotReadAndNothingIsSent() {
        UUID user = newUser();
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", user);

        assertThatThrownBy(() -> gateway.readImage(read(user))).isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(((AiException) e).kind()).isEqualTo(AiErrorKind.USER_NOT_ALLOWED));
        assertThat(fake.visionCalls()).isEmpty();
    }

    @Test
    void ac4_theRequestNeverPrintsTheImageNorTheAudience() {
        assertThat(read(UUID.randomUUID()).toString()).doesNotContain("[B@").doesNotContain("adulto");
    }
}
