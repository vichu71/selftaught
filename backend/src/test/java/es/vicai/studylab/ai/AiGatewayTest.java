package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.provider.ChatCall;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AiGatewayTest extends AiTestBase {

    // ---------- AC-1 · AC-3: interfaz única y proveedor falso ----------

    @Test
    void ac1_aFeatureAsksForATaskAndGetsTheTextWithoutKnowingTheProvider() {
        fake.respondWith("Una ficha de fracciones", 120, 40);

        AiResponse response = gateway.complete(request(newUser()));

        assertThat(response.text()).isEqualTo("Una ficha de fracciones");
        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(fake.calls()).hasSize(1);
    }

    @Test
    void ac3_theFakeProviderIsTheOnlyOneInTheTestProfile() {
        for (AiTask task : AiTask.values()) {
            assertThat(gateway.providerFor(task)).isEqualTo("fake");
        }
    }

    // ---------- AC-2 · AC-10: configuración por tarea y límite de salida ----------

    @Test
    void ac2_ac10_eachTaskUsesItsOwnModelAndOutputLimitFromConfiguration() {
        UUID user = newUser();
        gateway.complete(AiRequest.of(AiTask.OUTLINE, user, "test").data("tema", "x"));
        gateway.complete(AiRequest.of(AiTask.TUTOR, user, "test").data("tema", "x"));

        ChatCall outline = fake.calls().get(0);
        ChatCall tutor = fake.calls().get(1);
        assertThat(outline.model()).isEqualTo("fake-1");
        assertThat(outline.maxOutputTokens()).isEqualTo(200); // app.ai.tasks.outline.max-output-tokens
        assertThat(tutor.maxOutputTokens()).isEqualTo(150); // app.ai.tasks.tutor.max-output-tokens
        assertThat(outline.timeout().toSeconds()).isEqualTo(5);
    }

    @Test
    void ac10_aRequestCanLowerButNeverRaiseTheConfiguredOutputLimit() {
        UUID user = newUser();
        gateway.complete(request(user).maxOutputTokens(50));
        gateway.complete(request(user).maxOutputTokens(100_000));

        assertThat(fake.calls().get(0).maxOutputTokens()).isEqualTo(50);
        assertThat(fake.calls().get(1).maxOutputTokens()).isEqualTo(200);
    }

    // ---------- AC-6 · AC-7: registro de uso y coste ----------

    @Test
    void ac6_everyCallIsRecordedWithoutAnyContent() {
        UUID user = newUser();
        UUID course = UUID.randomUUID();
        fake.respondWith("RESPUESTA-CONFIDENCIAL-777", 1000, 500);

        AiRequest request = AiRequest.of(AiTask.LESSON, user, "test").data("tema", "DATO-CONFIDENCIAL-555").course(course);
        AiResponse response = gateway.complete(request);

        Map<String, Object> row = usage().get(0);
        assertThat(row.get("user_id")).isEqualTo(user);
        assertThat(row.get("course_id")).isEqualTo(course);
        assertThat(row.get("source")).isEqualTo("APP");
        assertThat(row.get("task")).isEqualTo("LESSON");
        assertThat(row.get("provider")).isEqualTo("fake");
        assertThat(row.get("model")).isEqualTo("fake-1");
        assertThat(row.get("input_tokens")).isEqualTo(1000);
        assertThat(row.get("output_tokens")).isEqualTo(500);
        assertThat(row.get("status")).isEqualTo("OK");
        assertThat(((Number) row.get("attempt")).intValue()).isEqualTo(1);
        assertThat((Integer) row.get("duration_ms")).isGreaterThanOrEqualTo(0);
        assertThat(row.get("request_id")).isEqualTo(response.requestId());
        assertThat(row.toString()).doesNotContain("RESPUESTA-CONFIDENCIAL-777").doesNotContain("DATO-CONFIDENCIAL-555");
    }

    @Test
    void ac7_costComesFromThePerModelPriceTable() {
        fake.respondWith("x", 1000, 500); // 1000·1/1M + 500·2/1M = 0,002000

        AiResponse response = gateway.complete(request(newUser()));

        assertThat(response.costUsd()).isEqualByComparingTo(new BigDecimal("0.002000"));
        assertThat((BigDecimal) usage().get(0).get("cost_usd")).isEqualByComparingTo(new BigDecimal("0.002000"));
    }

    @Test
    void edge_missingTokenCountsAreEstimatedAndMarkedAsEstimated() {
        fake.respondWithoutUsage("texto de respuesta bastante largo para estimar tokens");

        AiResponse response = gateway.complete(request(newUser()));

        assertThat(response.tokensEstimated()).isTrue();
        assertThat(response.inputTokens()).isGreaterThan(0);
        assertThat(response.outputTokens()).isGreaterThan(0);
        assertThat(usage().get(0).get("tokens_estimated")).isEqualTo(true);
    }

    @Test
    void edge_aTruncatedAnswerIsReportedAsLength() {
        fake.respondWithFinish("texto cortado", FinishReason.LENGTH);

        AiResponse response = gateway.complete(request(newUser()));

        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(usage().get(0).get("finish_reason")).isEqualTo("LENGTH");
    }

    @Test
    void edge_anEmptyAnswerCutByTheLimitIsReturnedAndItsBilledTokensAreRecorded() {
        fake.respondWithFinish("", FinishReason.LENGTH); // vacía por agotar el límite de salida (p. ej. un modelo que piensa mucho)

        AiResponse response = gateway.complete(request(newUser()));

        assertThat(response.text()).isEmpty();
        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(response.costUsd()).isEqualByComparingTo(new BigDecimal("0.000200")); // 100·1/M + 50·2/M
        assertThat(usage().get(0).get("status")).isEqualTo("OK");
        assertThat(usage().get(0).get("finish_reason")).isEqualTo("LENGTH");
    }

    @Test
    void edge_anEmptyAnswerThatDidNotHitTheLimitIsAnErrorButItsCostIsStillCounted() {
        fake.respondWithFinish("", FinishReason.OTHER); // 100 tokens de entrada y 50 de salida: 0,0002 USD

        assertThatThrownBy(() -> gateway.complete(request(newUser())))
                .isInstanceOfSatisfying(AiException.class, e -> {
                    assertThat(e.kind()).isEqualTo(AiErrorKind.INVALID_RESPONSE);
                    assertThat(e.costUsd()).isEqualByComparingTo(new BigDecimal("0.000200"));
                });

        Map<String, Object> row = usage().get(0);
        assertThat(row.get("status")).isEqualTo("ERROR");
        assertThat(row.get("error_kind")).isEqualTo("INVALID_RESPONSE");
        assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo(new BigDecimal("0.000200")); // el proveedor lo cobró
        assertThat(fake.calls()).hasSize(1); // y no se reintenta
    }

    // ---------- AC-12 · AC-13: reintentos acotados y errores genéricos ----------

    @ParameterizedTest
    @EnumSource(value = ProviderErrorKind.class, names = {"TIMEOUT", "RATE_LIMITED", "SERVER_ERROR", "UNAVAILABLE"})
    void ac12_transientErrorsAreRetriedAndTheCallSucceedsOnTheThirdAttempt(ProviderErrorKind kind) {
        fake.failNext(kind, 2);

        AiResponse response = gateway.complete(request(newUser()));

        assertThat(response.text()).isNotBlank();
        assertThat(fake.calls()).hasSize(3);
        assertThat(usage()).hasSize(3);
        assertThat(usage().stream().map(r -> r.get("status"))).containsExactly("ERROR", "ERROR", "OK");
        assertThat(usage().stream().map(r -> ((Number) r.get("attempt")).intValue())).containsExactly(1, 2, 3);
        assertThat(usage().stream().map(r -> r.get("request_id")).distinct()).hasSize(1);
    }

    @Test
    void ac12_retriesStopAfterTwoRetriesNoMatterHowLongTheProviderKeepsFailing() {
        fake.failNext(ProviderErrorKind.SERVER_ERROR, 50);

        assertThatThrownBy(() -> gateway.complete(request(newUser())))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.kind()).isEqualTo(AiErrorKind.PROVIDER_UNAVAILABLE));

        assertThat(fake.calls()).hasSize(3); // 1 intento + 2 reintentos, nunca un bucle
    }

    @ParameterizedTest
    @EnumSource(value = ProviderErrorKind.class, names = {"QUOTA_EXCEEDED", "AUTH", "BAD_REQUEST", "MODEL_NOT_FOUND", "INVALID_RESPONSE"})
    void ac12_permanentErrorsAreNeverRetried(ProviderErrorKind kind) {
        fake.failNext(kind, 50);

        assertThatThrownBy(() -> gateway.complete(request(newUser()))).isInstanceOf(AiException.class);

        assertThat(fake.calls()).hasSize(1);
        assertThat(usage().get(0).get("error_kind")).isEqualTo(kind.name());
    }

    @Test
    void ac12_theProviderQuotaCapIsReportedAsAClearErrorNotARetryLoop() {
        fake.failNext(ProviderErrorKind.QUOTA_EXCEEDED, 5);

        assertThatThrownBy(() -> gateway.complete(request(newUser())))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.kind()).isEqualTo(AiErrorKind.PROVIDER_QUOTA));
    }

    @Test
    void ac13_theErrorSeenByTheFeatureNeverContainsTheProvidersMessage() {
        fake.failNextWithMessage(ProviderErrorKind.SERVER_ERROR, 10, "sk-CLAVE-FILTRADA y eco del prompt: DATO-SECRETO");

        assertThatThrownBy(() -> gateway.complete(request(newUser())))
                .satisfies(e -> assertThat(e.getMessage() + String.valueOf(e.getCause()))
                        .doesNotContain("sk-CLAVE-FILTRADA").doesNotContain("DATO-SECRETO"));
    }

    // ---------- casos borde de entrada y usuario ----------

    @Test
    void edge_aHugeInputIsRejectedBeforeCallingTheProviderAndIsRecordedAsBlocked() {
        String huge = "a".repeat(20_001);

        assertThatThrownBy(() -> gateway.complete(AiRequest.of(AiTask.OUTLINE, newUser(), "test").data("tema", huge)))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.kind()).isEqualTo(AiErrorKind.INPUT_TOO_LARGE));

        assertThat(fake.calls()).isEmpty();
        assertThat(usage().get(0).get("status")).isEqualTo("BLOCKED");
        assertThat(usage().get(0).get("error_kind")).isEqualTo("INPUT_TOO_LARGE");
    }

    @Test
    void edge_aDeactivatedUserCannotGenerate() {
        UUID user = newUser();
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", user);

        assertThatThrownBy(() -> gateway.complete(request(user)))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.kind()).isEqualTo(AiErrorKind.USER_NOT_ALLOWED));

        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void edge_anUnknownUserCannotGenerateAndTheUserIsMandatory() {
        assertThatThrownBy(() -> gateway.complete(request(UUID.randomUUID()))).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> AiRequest.of(AiTask.OUTLINE, null, "test")).isInstanceOf(IllegalArgumentException.class);
        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void edge_anUnknownTemplateIsAConfigurationErrorAndNothingIsSent() {
        assertThatThrownBy(() -> gateway.complete(AiRequest.of(AiTask.OUTLINE, newUser(), "no-existe").data("tema", "x")))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.kind()).isEqualTo(AiErrorKind.CONFIG));
        assertThat(fake.calls()).isEmpty();
    }

    // ---------- AC-14: embeddings ----------

    @Test
    void ac14_embeddingsReturnOneVectorPerTextWithTheConfiguredDimension() {
        UUID user = newUser();

        EmbeddingResult result = gateway.embed(user, java.util.List.of("uno", "dos", "tres"));

        assertThat(result.vectors()).hasSize(3);
        assertThat(result.vectors()).allSatisfy(v -> assertThat(v).hasSize(8));
        assertThat(usage().get(0).get("task")).isEqualTo("EMBEDDING");
        assertThat((BigDecimal) usage().get(0).get("cost_usd")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ac14_aVectorWithAnUnexpectedDimensionIsRejected() {
        fake.embeddingDimensions(5);

        assertThatThrownBy(() -> gateway.embed(newUser(), java.util.List.of("uno")))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.kind()).isEqualTo(AiErrorKind.INVALID_RESPONSE));
    }

    @Test
    void ac14_aMissingEmbeddingModelOrServerFailsClearly() {
        fake.failNext(ProviderErrorKind.MODEL_NOT_FOUND, 1);

        assertThatThrownBy(() -> gateway.embed(newUser(), java.util.List.of("uno")))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.kind()).isEqualTo(AiErrorKind.PROVIDER_REJECTED));
    }
}
