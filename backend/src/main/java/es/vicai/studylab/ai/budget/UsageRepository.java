package es.vicai.studylab.ai.budget;

import es.vicai.studylab.ai.AiTask;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Registro de uso de IA (tabla de solo añadir, sin contenido) y consultas de gasto. El mes es el natural en hora de Madrid
 * (ADR-19); todo el SQL va parametrizado.
 */
@Repository
public class UsageRepository {

    private static final String MONTH_START = "(date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid')";
    private static final String MONTH_END =
            "((date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') + interval '1 month') AT TIME ZONE 'Europe/Madrid')";
    private static final String IN_MONTH = "occurred_at >= " + MONTH_START + " AND occurred_at < " + MONTH_END;

    /** Un intento de llamada. {@code userId} es nulo en el benchmark. */
    public record Row(UUID requestId, int attempt, UUID userId, UUID courseId, String source, AiTask task, String provider,
                      String model, int inputTokens, int outputTokens, boolean tokensEstimated, BigDecimal costUsd,
                      String status, String errorKind, String finishReason, long durationMs) {}

    public record UserUsage(long calls, BigDecimal spendUsd) {}

    public record MinorUsage(UUID id, String displayName, long calls, BigDecimal spendUsd) {}

    private final JdbcClient jdbc;

    public UsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Row r) {
        jdbc.sql("""
                INSERT INTO ai_usage (request_id, attempt, user_id, course_id, source, task, provider, model, input_tokens,
                                      output_tokens, tokens_estimated, cost_usd, status, error_kind, finish_reason, duration_ms)
                VALUES (:request, :attempt, :user, :course, :source, :task, :provider, :model, :in, :out, :estimated, :cost,
                        :status, :errorKind, :finish, :duration)""")
                .param("request", r.requestId()).param("attempt", r.attempt()).param("user", r.userId())
                .param("course", r.courseId()).param("source", r.source()).param("task", r.task().name())
                .param("provider", r.provider()).param("model", r.model()).param("in", r.inputTokens())
                .param("out", r.outputTokens()).param("estimated", r.tokensEstimated()).param("cost", r.costUsd())
                .param("status", r.status()).param("errorKind", r.errorKind()).param("finish", r.finishReason())
                .param("duration", (int) Math.min(r.durationMs(), Integer.MAX_VALUE))
                .update();
    }

    /** Gasto de todos los usuarios y del benchmark en el mes natural de Madrid. */
    public BigDecimal monthSpend() {
        return jdbc.sql("SELECT COALESCE(SUM(cost_usd), 0) FROM ai_usage WHERE " + IN_MONTH).query(BigDecimal.class).single();
    }

    public String currentMonth() {
        return jdbc.sql("SELECT to_char(now() AT TIME ZONE 'Europe/Madrid', 'YYYY-MM')").query(String.class).single();
    }

    /** Intenta registrar el aviso del mes: true solo la primera vez (así se avisa una vez por mes y umbral). */
    public boolean raiseAlert(String month, int threshold) {
        return jdbc.sql("INSERT INTO ai_budget_alert (month, threshold) VALUES (:month, :threshold) ON CONFLICT DO NOTHING")
                .param("month", month).param("threshold", threshold).update() > 0;
    }

    /** Llamadas con éxito y gasto de un usuario en el mes. Los intentos fallidos o bloqueados no cuentan como llamadas. */
    public UserUsage userMonth(UUID userId) {
        return jdbc.sql("SELECT count(*) FILTER (WHERE status = 'OK') AS calls, COALESCE(SUM(cost_usd), 0) AS spend "
                        + "FROM ai_usage WHERE user_id = :user AND " + IN_MONTH)
                .param("user", userId)
                .query((rs, i) -> new UserUsage(rs.getLong("calls"), rs.getBigDecimal("spend")))
                .single();
    }

    /** Uso de los menores de un tutor (todos, aunque no hayan usado la IA). */
    public List<MinorUsage> minorsMonth(UUID tutorId) {
        return jdbc.sql("""
                SELECT u.id, u.display_name,
                       count(a.id) FILTER (WHERE a.status = 'OK') AS calls,
                       COALESCE(SUM(a.cost_usd), 0) AS spend
                FROM guardianship g
                JOIN app_user u ON u.id = g.student_id
                LEFT JOIN ai_usage a ON a.user_id = u.id AND a.occurred_at >= %s AND a.occurred_at < %s
                WHERE g.tutor_id = :tutor
                GROUP BY u.id, u.display_name, g.created_at
                ORDER BY g.created_at, u.id""".formatted(MONTH_START, MONTH_END))
                .param("tutor", tutorId)
                .query((rs, i) -> new MinorUsage(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getLong("calls"), rs.getBigDecimal("spend")))
                .list();
    }
}
