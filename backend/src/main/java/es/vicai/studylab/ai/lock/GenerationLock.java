package es.vicai.studylab.ai.lock;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.audit.AuditService;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Una sola generación de curso a la vez por usuario (ADR-20). Es una fila en BD con propietario y caducidad: sobrevive a
 * reinicios, se libera sola si el backend se cae y un lease antiguo nunca libera el cerrojo de una generación nueva.
 * Uso:  {@code try (var lease = locks.acquire(userId)) { ...generar... }}
 */
@Component
public class GenerationLock {

    /** Cerrojo tomado. Cerrarlo lo libera (idempotente). */
    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    private final JdbcClient jdbc;
    private final AiProperties props;
    private final AuditService audit;

    public GenerationLock(JdbcClient jdbc, AiProperties props, AuditService audit) {
        this.jdbc = jdbc;
        this.props = props;
        this.audit = audit;
    }

    public Lease acquire(UUID userId) {
        String status = jdbc.sql("SELECT status FROM app_user WHERE id = :id").param("id", userId)
                .query(String.class).optional().orElse(null);
        if (!"ACTIVE".equals(status)) throw AiException.of(AiErrorKind.USER_NOT_ALLOWED);

        UUID owner = UUID.randomUUID();
        // Atómico: solo toma el cerrojo si no existe o si el existente ya caducó
        int taken = jdbc.sql("""
                INSERT INTO ai_generation_lock (user_id, owner, acquired_at, expires_at)
                VALUES (:user, :owner, now(), now() + make_interval(secs => :ttl))
                ON CONFLICT (user_id) DO UPDATE
                   SET owner = EXCLUDED.owner, acquired_at = EXCLUDED.acquired_at, expires_at = EXCLUDED.expires_at
                 WHERE ai_generation_lock.expires_at < now()""")
                .param("user", userId).param("owner", owner)
                .param("ttl", (double) props.generationLockTtl().toSeconds())
                .update();
        if (taken == 0) {
            audit.record("AI_GENERATION_DENIED", AuditService.DENIED, userId, userId, null);
            throw new GenerationBusyException();
        }
        return new Release(userId, owner);
    }

    private final class Release implements Lease {
        private final UUID userId;
        private final UUID owner;
        private boolean released;

        Release(UUID userId, UUID owner) {
            this.userId = userId;
            this.owner = owner;
        }

        @Override
        public synchronized void close() {
            if (released) return;
            released = true;
            // Solo borra su propia fila: si caducó y otra generación la tomó, no se toca
            jdbc.sql("DELETE FROM ai_generation_lock WHERE user_id = :user AND owner = :owner")
                    .param("user", userId).param("owner", owner).update();
        }
    }
}
