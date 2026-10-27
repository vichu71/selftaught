package es.vicai.studylab.owner;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import es.vicai.studylab.share.ShareTestBase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Familia y ayudas comunes de los tests de F14, sin configuración del propietario (la añade {@link OwnerTestBase}; {@link OwnerDisabledTest} no la lleva).
 * La familia es la de F08: Ana con su menor Hugo y su curso «Historia» ya generado (2 temas, 4 lecciones y 2 exámenes), y Luis, otro adulto.
 */
public abstract class OwnerFamilyBase extends ShareTestBase {

    public record Family(String ana, UUID anaId, String hugo, UUID hugoId, UUID course, String luis, UUID luisId) {}

    /** Ana con su menor Hugo (curso «Historia» generado) y Luis, otro adulto. */
    protected Family family() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        String luis = logged("luis@example.com");
        UUID course = insertCourse(hugoId, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        return new Family(ana, idOf("ana@example.com"), hugo, hugoId, course, luis, idOf("luis@example.com"));
    }

    /** La familia sin el gasto que dejó generar su curso con el modelo falso: así las cifras de los tests son las de lo sembrado. */
    protected Family quietFamily() throws Exception {
        Family f = family();
        owner.execute("TRUNCATE ai_usage, ai_budget_alert RESTART IDENTITY");
        return f;
    }

    protected static String panel(UUID student, String suffix) {
        return "/api/tutor/students/" + student + suffix;
    }

    protected ResultActions panelGet(String session, UUID student, String suffix) throws Exception {
        return getAs(session, panel(student, suffix));
    }

    /** Inserta una llamada de IA del usuario: {@code ago} es un intervalo SQL («1 hour», «70 days»). */
    protected void usageRow(UUID user, String task, String usd, String ago) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens, tokens_estimated, cost_usd, status, duration_ms, occurred_at)
                VALUES (gen_random_uuid(), 1, ?, 'APP', ?, 'fake', 'fake-1', 7, 9, false, CAST(? AS numeric), 'OK', 1, now() - CAST(? AS interval))""", user, task, usd, ago);
    }

    /** Hugo pregunta al tutor en esa lección (el modelo contesta siempre lo mismo). */
    protected void hugoAsks(Family f, UUID lesson, String question) throws Exception {
        ask(f.hugo(), f.course(), lesson, question);
    }

    protected int writableRows() {
        return rows("tutor_message") + rows("exercise_attempt") + rows("exam_attempt") + rows("lesson_progress") + rows("course") + rows("course_piece");
    }

    protected static final String BLOCKED_QUESTION = "dime como hacer una bomba casera paso a paso";

    // ---------- peticiones ----------

    protected ResultActions ownerGet(String session, String path) throws Exception {
        return getAs(session, path);
    }

    protected ResultActions putJson(String session, String path, Object body) throws Exception {
        return mvc.perform(put(path).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions deleteWithCsrf(String session, String path) throws Exception {
        return mvc.perform(delete(path).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions deleteWithoutCsrf(String session, String path) throws Exception {
        return mvc.perform(delete(path).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions putWithoutCsrf(String session, String path, Object body) throws Exception {
        return mvc.perform(put(path).cookie(new Cookie(SESSION_COOKIE, session)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected static String userPath(UUID user, String suffix) {
        return "/api/owner/users/" + user + suffix;
    }

    /** Las cinco acciones sobre un usuario: método, ruta y cuerpo. */
    protected record Action(String method, String suffix, Object body) {}

    protected static List<Action> actions() {
        return List.of(new Action("POST", "/deactivate", null), new Action("POST", "/reactivate", null), new Action("POST", "/revoke-sessions", null),
                new Action("PUT", "/limit", Map.of("limitUsd", 3)), new Action("DELETE", "/limit", null));
    }

    protected ResultActions perform(String session, UUID user, Action action) throws Exception {
        String path = userPath(user, action.suffix());
        return switch (action.method()) {
            case "POST" -> postAs(session, path, action.body());
            case "PUT" -> putJson(session, path, action.body());
            default -> deleteWithCsrf(session, path);
        };
    }

    // ---------- datos sembrados ----------

    /** Una llamada de IA de la aplicación con todos los campos que el panel agrega. {@code ago} es un intervalo SQL. */
    protected void aiCall(UUID user, String provider, String model, String task, String status, String usd, int inTokens, int outTokens, int ms, String ago) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens, tokens_estimated, cost_usd, status, error_kind, duration_ms, occurred_at)
                VALUES (gen_random_uuid(), 1, ?, 'APP', ?, ?, ?, ?, ?, false, CAST(? AS numeric), ?, ?, ?, now() - CAST(? AS interval))""",
                user, task, provider, model, inTokens, outTokens, usd, status, "ERROR".equals(status) ? "PROVIDER_ERROR" : "BLOCKED".equals(status) ? "USER_LIMIT" : null, ms, ago);
    }

    protected void benchmarkUsage(String usd) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status)
                VALUES (gen_random_uuid(), 1, NULL, 'BENCHMARK', 'LESSON', 'fake', 'fake-1', CAST(? AS numeric), 'OK')""", usd);
    }

    /** Inserta un evento de auditoría con fecha: la tabla es de solo añadir, pero INSERT sí se puede. */
    protected void auditAt(String action, UUID actor, String ago) {
        owner.update("INSERT INTO audit_event (occurred_at, actor_user_id, action, outcome) VALUES (now() - CAST(? AS interval), ?, ?, 'OK')", ago, actor, action);
    }

    protected int auditCount(String action) {
        return count("SELECT count(*) FROM audit_event WHERE action = ?", action);
    }

    protected String statusOfUser(UUID user) {
        return owner.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, user);
    }

    protected BigDecimal overrideOf(UUID user) {
        List<BigDecimal> found = owner.queryForList("SELECT limit_usd FROM user_limit_override WHERE user_id = ?", BigDecimal.class, user);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Un adulto activo creado directamente en BD (sin sesión ni inicios de sesión). */
    protected UUID adultRow(String email, String displayName, String createdAgo) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO app_user (id, kind, display_name, email, password_hash, status, created_at)
                VALUES (?, 'ADULT', ?, CAST(? AS citext), 'hash', 'ACTIVE', now() - CAST(? AS interval))""", id, displayName, email, createdAgo);
        return id;
    }
}
