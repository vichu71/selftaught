package es.vicai.studylab.share;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Los códigos de compartir (F09, ADR-64). Del código solo existe su hash. Todo lo del propietario va con {@code owner_id}; la única lectura sin él es la del canje, que busca por
 * hash y comprueba en la misma consulta que no esté revocado ni caducado y que el curso esté terminado.
 */
@Repository
public class ShareRepository {

    public record CodeRow(UUID id, Instant createdAt, Instant expiresAt, Instant revokedAt, int uses, boolean active) {}

    /** El curso al que da acceso un código válido. */
    public record Target(UUID codeId, UUID courseId, UUID ownerId) {}

    private final JdbcClient jdbc;

    public ShareRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int activeCount(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM share_code WHERE course_id = :course AND owner_id = :owner AND revoked_at IS NULL AND expires_at > now()")
                .param("course", courseId).param("owner", ownerId).query(Integer.class).single();
    }

    public Instant insert(UUID id, UUID courseId, UUID ownerId, String hash, int days) {
        return jdbc.sql("""
                INSERT INTO share_code (id, course_id, owner_id, code_hash, expires_at) VALUES (:id, :course, :owner, :hash, now() + make_interval(days => :days))
                RETURNING expires_at""")
                .param("id", id).param("course", courseId).param("owner", ownerId).param("hash", hash).param("days", days)
                .query((rs, i) -> rs.getTimestamp("expires_at").toInstant()).single();
    }

    public List<CodeRow> list(UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT id, created_at, expires_at, revoked_at, uses, (revoked_at IS NULL AND expires_at > now()) AS active FROM share_code
                WHERE course_id = :course AND owner_id = :owner ORDER BY created_at DESC, id""")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, i) -> new CodeRow(rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("revoked_at")).map(Timestamp::toInstant).orElse(null), rs.getInt("uses"), rs.getBoolean("active"))).list();
    }

    public boolean exists(UUID id, UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM share_code WHERE id = :id AND course_id = :course AND owner_id = :owner)")
                .param("id", id).param("course", courseId).param("owner", ownerId).query(Boolean.class).single();
    }

    /** Revoca un código que sigue activo; devuelve 0 si ya estaba revocado (revocar es idempotente). */
    public int revoke(UUID id, UUID courseId, UUID ownerId) {
        return jdbc.sql("UPDATE share_code SET revoked_at = now() WHERE id = :id AND course_id = :course AND owner_id = :owner AND revoked_at IS NULL")
                .param("id", id).param("course", courseId).param("owner", ownerId).update();
    }

    /** El canje: por hash, sin revocar, sin caducar y de un curso terminado. Todo lo demás es «no existe». */
    public Optional<Target> findByHash(String hash) {
        return jdbc.sql("""
                SELECT s.id, s.course_id, s.owner_id FROM share_code s JOIN course c ON c.id = s.course_id AND c.owner_id = s.owner_id
                WHERE s.code_hash = :hash AND s.revoked_at IS NULL AND s.expires_at > now() AND c.status = 'READY'""")
                .param("hash", hash)
                .query((rs, i) -> new Target(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getObject("owner_id", UUID.class))).optional();
    }

    public void addUse(UUID id) {
        jdbc.sql("UPDATE share_code SET uses = uses + 1 WHERE id = :id").param("id", id).update();
    }
}
