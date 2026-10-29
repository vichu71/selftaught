package es.vicai.studylab.owner;

import es.vicai.studylab.owner.OwnerReports.AiError;
import es.vicai.studylab.owner.OwnerReports.Benchmark;
import es.vicai.studylab.owner.OwnerReports.Count;
import es.vicai.studylab.owner.OwnerReports.Day;
import es.vicai.studylab.owner.OwnerReports.LoginDay;
import es.vicai.studylab.owner.OwnerReports.ModelRow;
import es.vicai.studylab.owner.OwnerReports.ProviderRow;
import es.vicai.studylab.owner.OwnerReports.TableSize;
import es.vicai.studylab.owner.OwnerReports.TaskRow;
import es.vicai.studylab.owner.OwnerReports.Totals;
import es.vicai.studylab.owner.OwnerReports.UserSpend;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Informes del panel del propietario (F14, ADR-89): todo es SQL agregado de solo lectura, con parámetros nombrados y sin traer filas a memoria. Los días y los rangos son en UTC;
 * el mes es el natural de Madrid, el mismo de los topes (ADR-19), para que «el mes» del panel y el del tope sean uno solo. Nunca se lee contenido de nadie: solo contadores.
 */
@Repository
public class OwnerStatsRepository {

    /** Primer instante del rango: el comienzo (UTC) del día de hace {@code :back} días. */
    private static final String FROM = "((date_trunc('day', now() AT TIME ZONE 'utc') - make_interval(days => :back)) AT TIME ZONE 'utc')";
    /** Los días del rango, de más antiguo a hoy. */
    private static final String DAYS = "generate_series(((now() AT TIME ZONE 'utc')::date - :back)::timestamp, (now() AT TIME ZONE 'utc')::date::timestamp, interval '1 day') AS d(day)";
    private static final String MONTH_START = "(date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid')";
    private static final String MONTH_END = "((date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') + interval '1 month') AT TIME ZONE 'Europe/Madrid')";

    private static final List<String> COURSE_STATUSES = List.of("PENDING_GENERATION", "GENERATING", "PAUSED", "READY", "FAILED");
    private static final List<String> USER_STATUSES = List.of("ACTIVE", "PENDING_VERIFICATION", "DEACTIVATED");
    private static final List<String> DOCUMENT_STATUSES = List.of("UPLOADED", "PROCESSING", "READY", "ERROR");
    private static final List<String> IMAGE_STATUSES = List.of("PENDING", "DONE", "FAILED", "BLOCKED", "REMOVED");
    private static final List<String> BIG_TABLES = List.of("ai_usage", "audit_event", "source_chunk", "source_document", "course_piece", "tutor_message", "auth_session");

    private static final String TOTALS = """
            SELECT count(*) AS calls, COALESCE(sum(cost_usd), 0) AS usd,
                   count(*) FILTER (WHERE status = 'ERROR') AS errors, count(*) FILTER (WHERE status = 'BLOCKED') AS blocked,
                   COALESCE(sum(input_tokens), 0) AS tokens_in, COALESCE(sum(output_tokens), 0) AS tokens_out, COALESCE(avg(duration_ms), 0) AS avg_ms
            FROM ai_usage WHERE source = 'APP' AND occurred_at >= {from}""".replace("{from}", FROM);

    private static final String DAILY = """
            SELECT to_char(d.day, 'YYYY-MM-DD') AS day, COALESCE(sum(u.cost_usd), 0) AS usd, count(u.id) AS calls
            FROM {days} LEFT JOIN ai_usage u ON u.source = 'APP' AND u.occurred_at >= {from} AND (u.occurred_at AT TIME ZONE 'utc')::date = d.day::date
            GROUP BY d.day ORDER BY d.day""".replace("{days}", DAYS).replace("{from}", FROM);

    private static final String BY_MODEL = """
            SELECT provider, model, COALESCE(sum(cost_usd), 0) AS usd, count(*) AS calls,
                   count(*) FILTER (WHERE status = 'ERROR') AS errors, count(*) FILTER (WHERE status = 'BLOCKED') AS blocked,
                   COALESCE(avg(duration_ms), 0) AS avg_ms,
                   COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) FILTER (WHERE status = 'OK'), 0) AS p95_ms,
                   COALESCE(sum(input_tokens), 0) AS tokens_in, COALESCE(sum(output_tokens), 0) AS tokens_out
            FROM ai_usage WHERE source = 'APP' AND occurred_at >= {from}
            GROUP BY provider, model ORDER BY usd DESC, provider, model""".replace("{from}", FROM);

    private static final String BY_PROVIDER = """
            SELECT provider, COALESCE(sum(cost_usd), 0) AS usd, count(*) AS calls FROM ai_usage WHERE source = 'APP' AND occurred_at >= {from}
            GROUP BY provider ORDER BY usd DESC, provider""".replace("{from}", FROM);

    private static final String BY_TASK = """
            SELECT task, COALESCE(sum(cost_usd), 0) AS usd, count(*) AS calls FROM ai_usage WHERE source = 'APP' AND occurred_at >= {from}
            GROUP BY task ORDER BY usd DESC, task""".replace("{from}", FROM);

    private static final String TOP_USERS = """
            SELECT a.user_id, u.kind, COALESCE(sum(a.cost_usd), 0) AS usd FROM ai_usage a JOIN app_user u ON u.id = a.user_id
            WHERE a.source = 'APP' AND a.occurred_at >= {from} GROUP BY a.user_id, u.kind ORDER BY usd DESC, a.user_id LIMIT 10""".replace("{from}", FROM);

    private static final String BENCHMARK = """
            SELECT count(*) AS calls, COALESCE(sum(cost_usd), 0) AS usd FROM ai_usage WHERE source = 'BENCHMARK' AND occurred_at >= {from}""".replace("{from}", FROM);

    private static final String TODAY = "SELECT COALESCE(sum(cost_usd), 0) FROM ai_usage WHERE source = 'APP' AND occurred_at >= (date_trunc('day', now() AT TIME ZONE 'utc') AT TIME ZONE 'utc')";

    private static final String MONTH = "SELECT COALESCE(sum(cost_usd), 0) FROM ai_usage WHERE source = 'APP' AND occurred_at >= {ms} AND occurred_at < {me}"
            .replace("{ms}", MONTH_START).replace("{me}", MONTH_END);

    private static final String MONTH_CLOCK = """
            SELECT extract(epoch FROM (now() - {ms})) / 86400.0 AS elapsed,
                   extract(day FROM (date_trunc('month', now() AT TIME ZONE 'Europe/Madrid') + interval '1 month' - interval '1 day')) AS days_in_month,
                   (SELECT min(occurred_at) FROM ai_usage WHERE source = 'APP' AND occurred_at >= {ms} AND occurred_at < {me}) AS first_call,
                   now() AS db_now""".replace("{ms}", MONTH_START).replace("{me}", MONTH_END);

    private static final String COST_PER_COURSE = """
            SELECT COALESCE(sum(cost_usd) / NULLIF(count(DISTINCT course_id), 0), 0) FROM ai_usage
            WHERE source = 'APP' AND course_id IS NOT NULL AND occurred_at >= {from}""".replace("{from}", FROM);

    private static final String ACTIVE_IN_RANGE = "SELECT count(DISTINCT actor_user_id) FROM audit_event WHERE action = 'LOGIN_OK' AND occurred_at >= {from}".replace("{from}", FROM);

    private static final String ACTIVE_WINDOWS = """
            SELECT count(DISTINCT actor_user_id) FILTER (WHERE occurred_at >= now() - interval '1 day') AS d1,
                   count(DISTINCT actor_user_id) FILTER (WHERE occurred_at >= now() - interval '7 days') AS d7,
                   count(DISTINCT actor_user_id) AS d30
            FROM audit_event WHERE action = 'LOGIN_OK' AND occurred_at >= now() - interval '30 days'""";

    private static final String SIGNUPS = """
            SELECT to_char(d.day, 'YYYY-MM-DD') AS day, count(a.id) AS cnt
            FROM {days} LEFT JOIN app_user a ON a.created_at >= {from} AND (a.created_at AT TIME ZONE 'utc')::date = d.day::date
            GROUP BY d.day ORDER BY d.day""".replace("{days}", DAYS).replace("{from}", FROM);

    private static final String LOGINS = """
            SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
                   count(a.id) FILTER (WHERE a.action = 'LOGIN_OK') AS ok, count(a.id) FILTER (WHERE a.action = 'LOGIN_FAILED') AS failed,
                   count(a.id) FILTER (WHERE a.action = 'LOGIN_LOCKED') AS locked
            FROM {days} LEFT JOIN audit_event a ON a.action IN ('LOGIN_OK', 'LOGIN_FAILED', 'LOGIN_LOCKED') AND a.occurred_at >= {from}
                 AND (a.occurred_at AT TIME ZONE 'utc')::date = d.day::date
            GROUP BY d.day ORDER BY d.day""".replace("{days}", DAYS).replace("{from}", FROM);

    private static final String COURSES_CREATED = """
            SELECT to_char(d.day, 'YYYY-MM-DD') AS day, count(c.id) AS cnt
            FROM {days} LEFT JOIN course c ON c.created_at >= {from} AND (c.created_at AT TIME ZONE 'utc')::date = d.day::date
            GROUP BY d.day ORDER BY d.day""".replace("{days}", DAYS).replace("{from}", FROM);

    private static final String AUDIT_COUNT = "SELECT count(*) FROM audit_event WHERE action = :action AND occurred_at >= {from}".replace("{from}", FROM);

    private final JdbcClient jdbc;

    public OwnerStatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- gasto de IA ----------

    public Totals totals(int back) {
        return jdbc.sql(TOTALS).param("back", back).query((rs, n) -> {
            long calls = rs.getLong("calls");
            return new Totals(calls, percent(rs.getLong("errors"), calls), percent(rs.getLong("blocked"), calls), rs.getLong("tokens_in"), rs.getLong("tokens_out"), one(rs.getBigDecimal("avg_ms")));
        }).single();
    }

    public BigDecimal rangeUsd(int back) {
        return jdbc.sql(TOTALS).param("back", back).query((rs, n) -> rs.getBigDecimal("usd")).single();
    }

    public List<Day> daily(int back) {
        return jdbc.sql(DAILY).param("back", back).query((rs, n) -> new Day(rs.getString("day"), rs.getBigDecimal("usd"), rs.getLong("calls"))).list();
    }

    public List<ModelRow> byModel(int back) {
        return jdbc.sql(BY_MODEL).param("back", back).query((rs, n) -> {
            long calls = rs.getLong("calls");
            return new ModelRow(rs.getString("provider"), rs.getString("model"), rs.getBigDecimal("usd"), calls, percent(rs.getLong("errors"), calls), percent(rs.getLong("blocked"), calls),
                    one(rs.getBigDecimal("avg_ms")), one(rs.getBigDecimal("p95_ms")), rs.getLong("tokens_in"), rs.getLong("tokens_out"));
        }).list();
    }

    public List<ProviderRow> byProvider(int back) {
        return jdbc.sql(BY_PROVIDER).param("back", back).query((rs, n) -> new ProviderRow(rs.getString("provider"), rs.getBigDecimal("usd"), rs.getLong("calls"))).list();
    }

    public List<TaskRow> byTask(int back) {
        return jdbc.sql(BY_TASK).param("back", back).query((rs, n) -> new TaskRow(rs.getString("task"), rs.getBigDecimal("usd"), rs.getLong("calls"))).list();
    }

    /** Los 10 usuarios que más gastan; la etiqueta (nombre o identificador) la pone quien tiene permiso para leerla. */
    public List<UserSpend> topUsers(int back) {
        return jdbc.sql(TOP_USERS).param("back", back).query((rs, n) -> new UserSpend(rs.getObject("user_id", UUID.class), rs.getString("kind"), null, rs.getBigDecimal("usd"))).list();
    }

    public Benchmark benchmark(int back) {
        return jdbc.sql(BENCHMARK).param("back", back).query((rs, n) -> new Benchmark(rs.getLong("calls"), rs.getBigDecimal("usd"))).single();
    }

    public BigDecimal todayUsd() {
        return jdbc.sql(TODAY).query(BigDecimal.class).single();
    }

    public BigDecimal monthUsd() {
        return jdbc.sql(MONTH).query(BigDecimal.class).single();
    }

    /** Lo necesario para proyectar el mes: días transcurridos, días del mes, primera llamada del mes y la hora de la BD. */
    public record MonthClock(double elapsedDays, int daysInMonth, Instant firstCall, Instant now) {}

    public MonthClock monthClock() {
        return jdbc.sql(MONTH_CLOCK).query((rs, n) -> {
            Timestamp first = rs.getTimestamp("first_call");
            return new MonthClock(rs.getDouble("elapsed"), rs.getInt("days_in_month"), first == null ? null : first.toInstant(), rs.getTimestamp("db_now").toInstant());
        }).single();
    }

    public BigDecimal costPerCourse(int back) {
        return jdbc.sql(COST_PER_COURSE).param("back", back).query(BigDecimal.class).single();
    }

    public long activeUsersInRange(int back) {
        return jdbc.sql(ACTIVE_IN_RANGE).param("back", back).query(Long.class).single();
    }

    // ---------- cuentas y conexiones ----------

    public OwnerReports.Active active() {
        return jdbc.sql(ACTIVE_WINDOWS).query((rs, n) -> new OwnerReports.Active(rs.getLong("d1"), rs.getLong("d7"), rs.getLong("d30"))).single();
    }

    public long adults() {
        return jdbc.sql("SELECT count(*) FROM app_user WHERE kind = 'ADULT'").query(Long.class).single();
    }

    public long minors() {
        return jdbc.sql("SELECT count(*) FROM app_user WHERE kind = 'MINOR'").query(Long.class).single();
    }

    public Map<String, Long> usersByStatus() {
        return counts("SELECT status, count(*) AS n FROM app_user GROUP BY status", USER_STATUSES);
    }

    public long openSessions() {
        return jdbc.sql("SELECT count(*) FROM auth_session WHERE revoked_at IS NULL AND expires_at > now()").query(Long.class).single();
    }

    public List<Count> signupsDaily(int back) {
        return jdbc.sql(SIGNUPS).param("back", back).query((rs, n) -> new Count(rs.getString("day"), rs.getLong("cnt"))).list();
    }

    public List<LoginDay> logins(int back) {
        return jdbc.sql(LOGINS).param("back", back).query((rs, n) -> new LoginDay(rs.getString("day"), rs.getLong("ok"), rs.getLong("failed"), rs.getLong("locked"))).list();
    }

    // ---------- uso de la app ----------

    public Map<String, Long> coursesByStatus() {
        return counts("SELECT status, count(*) AS n FROM course GROUP BY status", COURSE_STATUSES);
    }

    public List<Count> coursesCreatedDaily(int back) {
        return jdbc.sql(COURSES_CREATED).param("back", back).query((rs, n) -> new Count(rs.getString("day"), rs.getLong("cnt"))).list();
    }

    public long piecesWith(String status) {
        return jdbc.sql("SELECT count(*) FROM course_piece WHERE status = :status").param("status", status).query(Long.class).single();
    }

    public long lessonsDone(int back) {
        return since("SELECT count(*) FROM lesson_progress WHERE completed_at >= {from}", back);
    }

    public long exerciseAttempts(int back) {
        return since("SELECT count(*) FROM exercise_attempt WHERE created_at >= {from}", back);
    }

    public long examAttempts(int back) {
        return since("SELECT count(*) FROM exam_attempt WHERE started_at >= {from}", back);
    }

    public long examsPassed(int back, int passPercent) {
        return jdbc.sql("SELECT count(*) FROM exam_attempt WHERE status = 'SUBMITTED' AND submitted_at >= {from} AND correct * 100 >= total * :pass".replace("{from}", FROM))
                .param("back", back).param("pass", passPercent).query(Long.class).single();
    }

    public long tutorQuestions(int back) {
        return since("SELECT count(*) FROM tutor_message WHERE role = 'USER' AND created_at >= {from}", back);
    }

    public long tutorWith(String status, int back) {
        return jdbc.sql("SELECT count(*) FROM tutor_message WHERE status = :status AND created_at >= {from}".replace("{from}", FROM)).param("status", status).param("back", back)
                .query(Long.class).single();
    }

    public Map<String, Long> documentsByStatus() {
        return counts("SELECT status, count(*) AS n FROM source_document GROUP BY status", DOCUMENT_STATUSES);
    }

    public Map<String, Long> imagesByStatus() {
        return counts("SELECT status, count(*) AS n FROM course_image GROUP BY status", IMAGE_STATUSES);
    }

    /** Eventos de auditoría de una acción en el rango (códigos creados, canjeados y revocados, cursos borrados). */
    public long auditEvents(String action, int back) {
        return jdbc.sql(AUDIT_COUNT).param("action", action).param("back", back).query(Long.class).single();
    }

    // ---------- salud ----------

    public long coursesWith(String status) {
        return jdbc.sql("SELECT count(*) FROM course WHERE status = :status").param("status", status).query(Long.class).single();
    }

    public long documentsIn(List<String> statuses) {
        return jdbc.sql("SELECT count(*) FROM source_document WHERE status = ANY(:statuses)").param("statuses", statuses.toArray(new String[0])).query(Long.class).single();
    }

    public long imagesWith(String status) {
        return jdbc.sql("SELECT count(*) FROM course_image WHERE status = :status").param("status", status).query(Long.class).single();
    }

    /** Errores de IA de las últimas 24 horas por proveedor y tipo (solo la enumeración del error, nunca el mensaje del proveedor). */
    public List<AiError> aiErrorsLastDay() {
        return jdbc.sql("""
                SELECT provider, COALESCE(error_kind, 'UNKNOWN') AS error_kind, count(*) AS n FROM ai_usage
                WHERE source = 'APP' AND status = 'ERROR' AND occurred_at >= now() - interval '24 hours'
                GROUP BY provider, error_kind ORDER BY n DESC, provider, error_kind""")
                .query((rs, n) -> new AiError(rs.getString("provider"), rs.getString("error_kind"), rs.getLong("n"))).list();
    }

    public long contentBlocksLastDay() {
        return jdbc.sql("""
                SELECT count(*) FROM audit_event WHERE occurred_at >= now() - interval '24 hours' AND action IN
                ('COURSE_TOPIC_BLOCKED', 'COURSE_CONTENT_BLOCKED', 'TUTOR_QUESTION_BLOCKED', 'TUTOR_ANSWER_FILTERED', 'STUDY_ANSWER_BLOCKED', 'COURSE_EDIT_BLOCKED', 'COURSE_COPY_BLOCKED', 'DOCUMENT_BLOCKED')""")
                .query(Long.class).single();
    }

    public long databaseBytes() {
        return jdbc.sql("SELECT pg_database_size(current_database())").query(Long.class).single();
    }

    /** Filas estimadas (sin recorrer las tablas) de las tablas que más crecen. */
    public List<TableSize> bigTables() {
        return jdbc.sql("""
                SELECT c.relname AS name, greatest(c.reltuples, 0)::bigint AS n FROM pg_class c JOIN pg_namespace s ON s.oid = c.relnamespace
                WHERE s.nspname = current_schema() AND c.relkind = 'r' AND c.relname = ANY(:names) ORDER BY c.relname""")
                .param("names", BIG_TABLES.toArray(new String[0])).query((rs, n) -> new TableSize(rs.getString("name"), rs.getLong("n"))).list();
    }

    // ---------- ayudas ----------

    private long since(String sql, int back) {
        return jdbc.sql(sql.replace("{from}", FROM)).param("back", back).query(Long.class).single();
    }

    private Map<String, Long> counts(String sql, List<String> known) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String status : known) out.put(status, 0L);
        jdbc.sql(sql).query((rs, n) -> {
            out.put(rs.getString("status"), rs.getLong("n"));
            return null;
        }).list();
        return out;
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole == 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal one(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(1) : value.setScale(1, RoundingMode.HALF_UP);
    }
}
