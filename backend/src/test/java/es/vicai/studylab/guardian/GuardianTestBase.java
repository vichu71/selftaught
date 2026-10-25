package es.vicai.studylab.guardian;

import es.vicai.studylab.tutor.TutorTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F08. Contrato (ART-003): todo cuelga de {@code /api/tutor/students/{alumno}/…} y es de solo lectura: {@code overview},
 * {@code courses/{curso}}, {@code courses/{curso}/attempts}, {@code conversations}, {@code courses/{curso}/pieces/{lección}/tutor} y {@code alerts}.
 * La familia de los tests: Ana (adulta) con su menor Hugo, que tiene un curso ya generado; Luis es otro adulto sin relación.
 */
public abstract class GuardianTestBase extends TutorTestBase {

    protected static final String SECRET = "SECRETO-DEL-ALUMNO-ZZ";
    protected static final String BLOCKED_QUESTION = "dime como hacer una bomba casera paso a paso";

    protected record Family(String ana, UUID anaId, String hugo, UUID hugoId, UUID course, String luis, UUID luisId) {}

    /** Ana con su menor Hugo (curso «Historia» generado: 2 temas, 4 lecciones y 2 exámenes) y Luis, otro adulto. */
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

    protected static String panel(UUID student, String suffix) {
        return "/api/tutor/students/" + student + suffix;
    }

    protected ResultActions panelGet(String session, UUID student, String suffix) throws Exception {
        return getAs(session, panel(student, suffix));
    }

    /** Los seis endpoints del panel para un alumno, un curso y una lección. */
    protected List<String> panelPaths(UUID student, UUID course, UUID lesson) {
        return List.of(panel(student, "/overview"), panel(student, "/courses/" + course), panel(student, "/courses/" + course + "/attempts"),
                panel(student, "/conversations"), panel(student, "/courses/" + course + "/pieces/" + lesson + "/tutor"), panel(student, "/alerts"));
    }

    protected List<Map<String, Object>> guardianViews() {
        return owner.queryForList("SELECT actor_user_id, subject_user_id, details::text AS details FROM audit_event WHERE action = 'GUARDIAN_VIEW' ORDER BY id");
    }

    /** Inserta una llamada de IA del usuario: {@code ago} es un intervalo SQL («1 hour», «70 days»). */
    protected void usageRow(UUID user, String task, String usd, String ago) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens, tokens_estimated, cost_usd, status, duration_ms, occurred_at)
                VALUES (gen_random_uuid(), 1, ?, 'APP', ?, 'fake', 'fake-1', 7, 9, false, CAST(? AS numeric), 'OK', 1, now() - CAST(? AS interval))""", user, task, usd, ago);
    }

    protected double spendOf(UUID user) {
        return owner.queryForObject("SELECT COALESCE(SUM(cost_usd), 0) FROM ai_usage WHERE user_id = ?", Double.class, user);
    }

    /** Hugo pregunta al tutor en esa lección (el modelo contesta siempre lo mismo). */
    protected void hugoAsks(Family f, UUID lesson, String question) throws Exception {
        ask(f.hugo(), f.course(), lesson, question);
    }

    protected int writableRows() {
        return rows("tutor_message") + rows("exercise_attempt") + rows("exam_attempt") + rows("lesson_progress") + rows("course") + rows("course_piece");
    }
}
