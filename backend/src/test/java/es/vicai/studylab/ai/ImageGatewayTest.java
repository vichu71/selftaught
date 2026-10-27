package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.provider.FakeProvider;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * F12 AC-1, AC-2 y AC-8 (ADR-78): la imagen es una tercera modalidad de la capa de IA. Precio ficticio: 0,05 USD por imagen. El proveedor falso devuelve
 * una imagen generada en memoria (nunca se llama a una API de pago) y tiene ganchos para fallos, negativas y respuestas inválidas.
 */
class ImageGatewayTest extends AiTestBase {

    private AiImageRequest imageRequest(UUID user) {
        return AiImageRequest.of(user, null, "Ilustración plana sobre las fracciones");
    }

    private Map<String, Object> lastUsage() {
        List<Map<String, Object>> rows = usage();
        return rows.get(rows.size() - 1);
    }

    @Test
    void ac1_theImageTaskIsOneMoreTaskOfTheLayerAndGoesThroughTheGateway() {
        assertThat(AiTask.valueOf("IMAGE")).isNotNull();
        assertThat(gateway.providerFor(AiTask.IMAGE)).isEqualTo("fake");
    }

    @Test
    void ac1_theGatewayReturnsAnImageWithItsCostAndTheModelOfTheConfiguration() {
        AiImageResponse response = gateway.generateImage(imageRequest(newUser()));

        assertThat(response.bytes()).isNotEmpty();
        assertThat(response.mime()).isEqualTo("image/png");
        assertThat(response.costUsd()).isEqualByComparingTo(new BigDecimal("0.050000"));
        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("fake-image");
        assertThat(fake.imageCalls()).hasSize(1);
        assertThat(fake.imageCalls().get(0).model()).isEqualTo("fake-image");
    }

    @Test
    void ac2_everyImageCallIsRecordedAsTheImageTaskWithItsPricePerImageAndNeverItsContent() {
        UUID user = newUser();

        gateway.generateImage(imageRequest(user));

        Map<String, Object> row = lastUsage();
        assertThat(row.get("task")).isEqualTo("IMAGE");
        assertThat(row.get("status")).isEqualTo("OK");
        assertThat(row.get("user_id")).isEqualTo(user);
        assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.05");
        assertThat(row.get("input_tokens")).isEqualTo(0);
        assertThat(row.get("output_tokens")).isEqualTo(0);
        assertThat(row.values().toString()).doesNotContain("fracciones");
    }

    @Test
    void ac2_theCostOfImagesCountsTowardsTheUsersMonthlyLimitAndStopsTheCallsWhenItIsReached() {
        UUID user = newUser();
        owner.update("INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status) "
                + "VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 4.97, 'OK')", user);

        assertThatThrownBy(() -> gateway.generateImage(imageRequest(user))).isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(((AiException) e).kind()).isEqualTo(AiErrorKind.USER_LIMIT));

        assertThat(fake.imageCalls()).isEmpty();
        assertThat(lastUsage().get("status")).isEqualTo("BLOCKED");
    }

    @Test
    void ac2_aCallTheProviderChargedForIsAccountedEvenIfTheImageComesBackEmpty() {
        UUID user = newUser();
        fake.imageRespondsWith(new byte[0]);

        assertThatThrownBy(() -> gateway.generateImage(imageRequest(user))).isInstanceOf(AiException.class)
                .satisfies(e -> {
                    AiException ex = (AiException) e;
                    assertThat(ex.kind()).isEqualTo(AiErrorKind.INVALID_RESPONSE);
                    assertThat(ex.costUsd()).isEqualByComparingTo("0.05");
                });

        Map<String, Object> row = lastUsage();
        assertThat(row.get("status")).isEqualTo("ERROR");
        assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.05");
    }

    @Test
    void ac2_aTransientFailureIsRetriedWithinTheLimitAndOnlyTheSuccessfulCallIsCharged() {
        UUID user = newUser();
        fake.imageFailsTimes(1, ProviderErrorKind.SERVER_ERROR);

        AiImageResponse response = gateway.generateImage(imageRequest(user));

        assertThat(response.costUsd()).isEqualByComparingTo("0.05");
        assertThat(fake.imageCalls()).hasSize(2);
        BigDecimal total = owner.queryForObject("SELECT sum(cost_usd) FROM ai_usage WHERE task = 'IMAGE'", BigDecimal.class);
        assertThat(total).isEqualByComparingTo("0.05");
    }

    @Test
    void ac8_aModerationRefusalIsNotATechnicalErrorAndIsNotRetried() {
        UUID user = newUser();
        fake.imageIsModerated();

        assertThatThrownBy(() -> gateway.generateImage(imageRequest(user))).isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(((AiException) e).kind()).isEqualTo(AiErrorKind.MODERATED));

        assertThat(fake.imageCalls()).hasSize(1);
        Map<String, Object> row = lastUsage();
        assertThat(row.get("task")).isEqualTo("IMAGE");
        assertThat(row.get("error_kind")).isEqualTo("MODERATION");
    }

    @Test
    void ac8_theMessageOfTheProviderNeverReachesTheExceptionOrTheRecord() {
        UUID user = newUser();
        fake.imageFailMessage("PROMPT-SECRETO-DEL-PROVEEDOR sk-no-debe-salir");
        fake.imageFailsTimes(10, ProviderErrorKind.SERVER_ERROR);

        assertThatThrownBy(() -> gateway.generateImage(imageRequest(user))).isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getMessage()) + e).doesNotContain("PROMPT-SECRETO").doesNotContain("sk-no-debe-salir"));

        assertThat(usage().toString()).doesNotContain("PROMPT-SECRETO");
    }

    @Test
    void ac1_thePriceOfTheImageIsInTheConfigurationAndNotInTheCode() {
        assertThat(gateway.imagePriceUsd()).isEqualByComparingTo("0.05");
    }

    @Test
    void ac1_theFakeProviderBuildsAValidImageOfTheRequestedSize() throws Exception {
        byte[] png = FakeProvider.png(300, 200);

        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(200);
    }
}
