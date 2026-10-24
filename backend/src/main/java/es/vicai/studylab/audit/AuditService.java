package es.vicai.studylab.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Registro de auditoría de solo añadir. Guarda quién hizo qué, cuándo, desde qué IP y con qué resultado.
 * Nunca se pasan contraseñas, tokens ni correos de cuentas inexistentes en {@code details}.
 */
@Service
public class AuditService {

    public static final String OK = "OK";
    public static final String DENIED = "DENIED";
    public static final String FAILED = "FAILED";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public AuditService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(String action, String outcome, UUID actor, UUID subject, String ip) {
        record(action, outcome, actor, subject, ip, Map.of());
    }

    public void record(String action, String outcome, UUID actor, UUID subject, String ip, Map<String, ?> details) {
        jdbc.sql("""
                INSERT INTO audit_event (actor_user_id, subject_user_id, action, outcome, ip, details)
                VALUES (:actor, :subject, :action, :outcome, :ip, CAST(:details AS jsonb))""")
                .param("actor", actor)
                .param("subject", subject)
                .param("action", action)
                .param("outcome", outcome)
                .param("ip", ip)
                .param("details", toJson(details))
                .update();
    }

    private String toJson(Map<String, ?> details) {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de auditoría", e);
        }
    }
}
