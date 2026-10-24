package es.vicai.studylab.ai;

import es.vicai.studylab.ai.lock.GenerationLock;
import es.vicai.studylab.ai.provider.FakeProvider;
import es.vicai.studylab.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base de los tests de F02. Contrato de la capa (ART-003): AiGateway.complete/embed, GenerationLock.acquire y un
 * FakeProvider con ganchos de prueba. Precios ficticios: fake-1 = 1 USD/M entrada y 2 USD/M salida.
 * Con 100.000 tokens de entrada y 50.000 de salida una llamada cuesta exactamente 0,200000 USD.
 */
public abstract class AiTestBase extends IntegrationTestBase {

    @Autowired protected AiGateway gateway;
    @Autowired protected FakeProvider fake;
    @Autowired protected GenerationLock locks;

    @BeforeEach
    void cleanAiState() {
        fake.reset();
        owner.execute("TRUNCATE ai_usage, ai_budget_alert RESTART IDENTITY");
    }

    /** Crea un adulto activo directamente en BD y devuelve su id. */
    protected UUID newUser() {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO app_user (id, kind, display_name, email, password_hash, status)
                VALUES (?, 'ADULT', 'Ana', CAST(? AS citext), 'hash', 'ACTIVE')""", id, id + "@example.com");
        return id;
    }

    protected UUID userIdByEmail(String email) {
        return owner.queryForObject("SELECT id FROM app_user WHERE email = CAST(? AS citext)", UUID.class, email);
    }

    protected AiRequest request(UUID user) {
        return AiRequest.of(AiTask.OUTLINE, user, "test").data("tema", "Fracciones");
    }

    /** Una llamada que cuesta 0,20 USD con los precios ficticios. */
    protected void respondWithCost20Cents() {
        fake.respondWith("texto", 100_000, 50_000);
    }

    protected List<Map<String, Object>> usage() {
        return owner.queryForList("SELECT * FROM ai_usage ORDER BY id");
    }

    protected List<String> auditActions() {
        return owner.queryForList("SELECT action FROM audit_event ORDER BY id", String.class);
    }

    /** Inserta gasto previo del mes actual (se cuenta en el tope). */
    protected void spend(String usd) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens,
                                      output_tokens, tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, NULL, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, ?, 'OK', 1)""",
                new BigDecimal(usd));
    }
}
