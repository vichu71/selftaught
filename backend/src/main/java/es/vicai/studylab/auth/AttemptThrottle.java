package es.vicai.studylab.auth;

import es.vicai.studylab.web.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Límites de intentos guardados en BD (sin Redis). El identificador se guarda hasheado.
 * Login: 5 fallos seguidos por cuenta, o 20 por IP, en 15 minutos. Correo: 10 envíos por IP en 15 minutos.
 */
@Component
public class AttemptThrottle {

    static final int MAX_FAILURES_PER_ACCOUNT = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    static final int MAX_MAILS_PER_IP = 10;
    private static final String WINDOW = "interval '15 minutes'";

    private final JdbcClient jdbc;

    public AttemptThrottle(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Fallos de la cuenta desde su último login correcto (dentro de la ventana) o fallos de la IP en la ventana. */
    public boolean isLoginLocked(String identifierHash, String ip) {
        int accountFailures = jdbc.sql("""
                SELECT count(*) FROM login_attempt
                WHERE kind = 'LOGIN' AND identifier_hash = :id AND NOT succeeded AND at > now() - %s
                  AND at > COALESCE((SELECT max(at) FROM login_attempt
                                     WHERE kind = 'LOGIN' AND identifier_hash = :id AND succeeded), '-infinity')"""
                .formatted(WINDOW))
                .param("id", identifierHash).query(Integer.class).single();
        if (accountFailures >= MAX_FAILURES_PER_ACCOUNT) return true;

        int ipFailures = jdbc.sql("""
                SELECT count(*) FROM login_attempt
                WHERE kind = 'LOGIN' AND ip = :ip AND NOT succeeded AND at > now() - %s""".formatted(WINDOW))
                .param("ip", ip).query(Integer.class).single();
        return ipFailures >= MAX_FAILURES_PER_IP;
    }

    public void recordLogin(String identifierHash, String ip, boolean succeeded) {
        insert("LOGIN", identifierHash, ip, succeeded);
    }

    /** Cuenta un envío de correo de esta IP y rechaza si supera el límite (evita usar la API para enviar spam). */
    public void checkMailQuota(String ip) {
        String ipHash = TokenCodec.sha256Hex("mail:" + ip);
        int sent = jdbc.sql("SELECT count(*) FROM login_attempt WHERE kind = 'MAIL' AND identifier_hash = :id AND at > now() - %s"
                .formatted(WINDOW)).param("id", ipHash).query(Integer.class).single();
        if (sent >= MAX_MAILS_PER_IP) throw ApiException.locked();
        insert("MAIL", ipHash, ip, false);
    }

    private void insert(String kind, String identifierHash, String ip, boolean succeeded) {
        jdbc.sql("INSERT INTO login_attempt (kind, identifier_hash, ip, succeeded) VALUES (:kind, :id, :ip, :ok)")
                .param("kind", kind).param("id", identifierHash).param("ip", ip).param("ok", succeeded).update();
    }
}
