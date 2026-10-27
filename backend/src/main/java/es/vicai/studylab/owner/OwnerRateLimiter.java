package es.vicai.studylab.owner;

import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Límite de frecuencia del panel (ADR-89): 120 consultas y 60 acciones por hora y propietario, contadas en la BD (nunca por IP). La petición que pasa del límite responde 429
 * y no deja fila ni ejecuta nada; las filas de más de 2 horas se podan al escribir una nueva.
 */
@Component
public class OwnerRateLimiter {

    public enum Kind {
        QUERY(120, "Demasiadas consultas. Espera un poco antes de volver a pedir datos."),
        ACTION(60, "Demasiadas acciones. Espera un poco antes de seguir.");

        private final int perHour;
        private final String message;

        Kind(int perHour, String message) {
            this.perHour = perHour;
            this.message = message;
        }
    }

    private final JdbcClient jdbc;

    public OwnerRateLimiter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void check(AuthUser owner, Kind kind) {
        long done = jdbc.sql("SELECT count(*) FROM owner_action WHERE owner_id = :owner AND kind = :kind AND created_at > now() - interval '1 hour'")
                .param("owner", owner.id()).param("kind", kind.name()).query(Long.class).single();
        if (done >= kind.perHour) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OWNER_RATE_LIMIT", kind.message);
        jdbc.sql("INSERT INTO owner_action (owner_id, kind) VALUES (:owner, :kind)").param("owner", owner.id()).param("kind", kind.name()).update();
        jdbc.sql("DELETE FROM owner_action WHERE created_at < now() - interval '2 hours'").update();
    }
}
