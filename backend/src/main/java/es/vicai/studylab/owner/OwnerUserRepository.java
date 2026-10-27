package es.vicai.studylab.owner;

import es.vicai.studylab.owner.OwnerReports.UserItem;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * La lista de usuarios del panel y los dos únicos datos que el propietario escribe: el tope por usuario y el bloqueo de la fila al actuar sobre una cuenta (F14, ADR-88 y 91).
 * Es el único sitio del paquete que lee nombres, correos e identificadores de acceso, y de un menor nunca sale el nombre ni el correo. SQL siempre parametrizado; la ordenación
 * es una lista cerrada que se traduce con un {@code switch}, nunca el texto que llega.
 */
@Repository
public class OwnerUserRepository {

    private static final String MONTH_START = "(date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid')";
    private static final String MONTH_END = "((date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') + interval '1 month') AT TIME ZONE 'Europe/Madrid')";

    private static final String LIST = """
            SELECT u.id, u.kind, u.status,
                   CASE WHEN u.kind = 'ADULT' THEN u.display_name END AS shown_name,
                   CASE WHEN u.kind = 'ADULT' THEN u.email::text END AS shown_email,
                   CASE WHEN u.kind = 'MINOR' THEN u.login_id::text END AS shown_access_id,
                   u.created_at,
                   greatest(la.last_login, ua.last_call) AS last_activity_at,
                   CAST(COALESCE(sp.spend, 0) AS numeric(14,6)) AS month_spend,
                   o.limit_usd AS custom_limit,
                   (SELECT count(*) FROM course c WHERE c.owner_id = u.id) AS courses,
                   CASE WHEN u.kind = 'MINOR' THEN g.tutor_id END AS tutor_id
            FROM app_user u
            LEFT JOIN LATERAL (SELECT max(occurred_at) AS last_login FROM audit_event WHERE action = 'LOGIN_OK' AND actor_user_id = u.id) la ON true
            LEFT JOIN LATERAL (SELECT max(occurred_at) AS last_call FROM ai_usage WHERE user_id = u.id) ua ON true
            LEFT JOIN LATERAL (SELECT sum(cost_usd) AS spend FROM ai_usage WHERE user_id = u.id AND occurred_at >= {ms} AND occurred_at < {me}) sp ON true
            LEFT JOIN user_limit_override o ON o.user_id = u.id
            LEFT JOIN guardianship g ON g.student_id = u.id
            WHERE (CAST(:kind AS varchar) IS NULL OR u.kind = CAST(:kind AS varchar)) AND (CAST(:status AS varchar) IS NULL OR u.status = CAST(:status AS varchar))"""
            .replace("{ms}", MONTH_START).replace("{me}", MONTH_END);

    private static final String COUNT = """
            SELECT count(*) FROM app_user u WHERE (CAST(:kind AS varchar) IS NULL OR u.kind = CAST(:kind AS varchar)) AND (CAST(:status AS varchar) IS NULL OR u.status = CAST(:status AS varchar))""";

    /** Lo que hace falta saber de una cuenta para actuar sobre ella. */
    public record Target(UUID id, String kind, String status) {}

    private final JdbcClient jdbc;

    public OwnerUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Bloquea la fila de la cuenta mientras dura la transacción de la acción. */
    public Optional<Target> lock(UUID id) {
        return jdbc.sql("SELECT id, kind, status FROM app_user WHERE id = :id FOR UPDATE").param("id", id)
                .query((rs, n) -> new Target(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"))).optional();
    }

    public List<UserItem> page(String kind, String status, String sort, String dir, int page, int size, BigDecimal minorDefault, BigDecimal adultDefault) {
        String query = LIST + orderBy(sort, dir) + " LIMIT :size OFFSET :offset";
        return jdbc.sql(query).param("kind", kind).param("status", status).param("size", size).param("offset", (long) page * size).query((rs, n) -> {
            boolean minor = "MINOR".equals(rs.getString("kind"));
            BigDecimal custom = rs.getBigDecimal("custom_limit");
            Timestamp last = rs.getTimestamp("last_activity_at");
            return new UserItem(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"), rs.getString("shown_name"), rs.getString("shown_email"),
                    rs.getString("shown_access_id"), rs.getTimestamp("created_at").toInstant(), last == null ? null : last.toInstant(), rs.getBigDecimal("month_spend"),
                    custom != null ? custom : minor ? minorDefault : adultDefault, custom != null, rs.getLong("courses"), rs.getObject("tutor_id", UUID.class));
        }).list();
    }

    public long total(String kind, String status) {
        return jdbc.sql(COUNT).param("kind", kind).param("status", status).query(Long.class).single();
    }

    /** Etiqueta de quien gasta: el nombre de un adulto o el identificador de acceso de un menor (nunca su nombre). */
    public Map<UUID, String> labels(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.sql("SELECT id, CASE WHEN kind = 'ADULT' THEN display_name ELSE login_id::text END AS label FROM app_user WHERE id = ANY(:ids)")
                .param("ids", ids.toArray(new UUID[0])).query((rs, n) -> {
                    out.put(rs.getObject("id", UUID.class), rs.getString("label"));
                    return null;
                }).list();
        return out;
    }

    public void upsertLimit(UUID user, BigDecimal limitUsd, UUID setBy) {
        jdbc.sql("""
                INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (:user, :limit, :by)
                ON CONFLICT (user_id) DO UPDATE SET limit_usd = EXCLUDED.limit_usd, set_by = EXCLUDED.set_by, set_at = now()""")
                .param("user", user).param("limit", limitUsd).param("by", setBy).update();
    }

    /** Quita el tope personalizado; devuelve si había uno. */
    public boolean clearLimit(UUID user) {
        return jdbc.sql("DELETE FROM user_limit_override WHERE user_id = :user").param("user", user).update() > 0;
    }

    private static String orderBy(String sort, String dir) {
        String column = switch (sort) {
            case "spend" -> "month_spend";
            case "activity" -> "last_activity_at";
            default -> "u.created_at";
        };
        return " ORDER BY " + column + ("asc".equals(dir) ? " ASC" : " DESC") + " NULLS LAST, u.id";
    }
}
