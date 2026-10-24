package es.vicai.studylab.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tokens de un solo uso enviados por correo (verificación y recuperación). Solo se guarda su hash. */
@Repository
public class EmailTokenRepository {

    public static final String VERIFY_EMAIL = "VERIFY_EMAIL";
    public static final String RESET_PASSWORD = "RESET_PASSWORD";

    private final JdbcClient jdbc;
    private final TokenCodec codec;

    public EmailTokenRepository(JdbcClient jdbc, TokenCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    /** Crea un token nuevo e invalida los anteriores sin usar del mismo propósito. Devuelve el token en claro. */
    public String issue(UUID userId, String purpose, Duration ttl) {
        jdbc.sql("UPDATE email_token SET used_at = now() WHERE user_id = :user AND purpose = :purpose AND used_at IS NULL")
                .param("user", userId).param("purpose", purpose).update();
        String token = codec.newToken();
        jdbc.sql("""
                INSERT INTO email_token (user_id, purpose, token_hash, expires_at)
                VALUES (:user, :purpose, :hash, now() + make_interval(secs => :secs))""")
                .param("user", userId).param("purpose", purpose)
                .param("hash", TokenCodec.sha256Hex(token)).param("secs", (double) ttl.toSeconds())
                .update();
        return token;
    }

    /** Gasta el token de forma atómica. Devuelve el usuario si era válido, no usado, no caducado y del propósito. */
    public Optional<UUID> consume(String token, String purpose) {
        return jdbc.sql("""
                UPDATE email_token SET used_at = now()
                WHERE token_hash = :hash AND purpose = :purpose AND used_at IS NULL AND expires_at > now()
                RETURNING user_id""")
                .param("hash", TokenCodec.sha256Hex(token)).param("purpose", purpose)
                .query(UUID.class).optional();
    }
}
