package es.vicai.studylab.auth;

import es.vicai.studylab.config.AppProperties;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Sesiones en servidor: el navegador guarda un token opaco; aquí solo está su hash, y se pueden revocar. */
@Service
public class SessionService {

    private final JdbcClient jdbc;
    private final TokenCodec codec;
    private final AppProperties props;

    public SessionService(JdbcClient jdbc, TokenCodec codec, AppProperties props) {
        this.jdbc = jdbc;
        this.codec = codec;
        this.props = props;
    }

    /** Abre una sesión y devuelve el token en claro (solo se entrega una vez, en la cookie). */
    public String create(UUID userId) {
        String token = codec.newToken();
        jdbc.sql("""
                INSERT INTO auth_session (user_id, token_hash, expires_at)
                VALUES (:user, :hash, now() + make_interval(secs => :secs))""")
                .param("user", userId).param("hash", TokenCodec.sha256Hex(token))
                .param("secs", (double) props.sessionTtl().toSeconds())
                .update();
        return token;
    }

    public void revoke(UUID sessionId) {
        jdbc.sql("UPDATE auth_session SET revoked_at = now() WHERE id = :id AND revoked_at IS NULL")
                .param("id", sessionId).update();
    }

    public void revokeAll(UUID userId) {
        jdbc.sql("UPDATE auth_session SET revoked_at = now() WHERE user_id = :user AND revoked_at IS NULL")
                .param("user", userId).update();
    }

    public int ttlSeconds() {
        return (int) props.sessionTtl().toSeconds();
    }
}
