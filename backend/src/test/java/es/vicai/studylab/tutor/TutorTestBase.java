package es.vicai.studylab.tutor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.ai.provider.ChatCall;
import es.vicai.studylab.study.StudyTestBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F07. Contrato (ART-003): {@code /api/courses/{curso}/pieces/{lección}/tutor} con GET (hilo), POST {@code {question}} (una pregunta, 201 con
 * {@code user} y {@code tutor}) y DELETE (vaciar). Cada petición al modelo del tutor lleva el dato {@code pregunta}; el modelo es siempre el proveedor falso.
 */
public abstract class TutorTestBase extends StudyTestBase {

    protected static final String ANSWER = "Esto es una explicación del tutor, con un ejemplo y una pista para seguir.";

    protected static String tutorPath(UUID course, UUID lesson) {
        return piecePath(course, lesson) + "/tutor";
    }

    protected ResultActions ask(String session, UUID course, UUID lesson, String question) throws Exception {
        return postAs(session, tutorPath(course, lesson), Map.of("question", question));
    }

    protected ResultActions thread(String session, UUID course, UUID lesson) throws Exception {
        return getAs(session, tutorPath(course, lesson));
    }

    protected ResultActions clear(String session, UUID course, UUID lesson) throws Exception {
        return mvc.perform(delete(tutorPath(course, lesson)).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions clearWithoutCsrf(String session, UUID course, UUID lesson) throws Exception {
        return mvc.perform(delete(tutorPath(course, lesson)).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    /** El modelo contesta siempre lo mismo (y se olvidan las llamadas anteriores, incluida la generación del curso). */
    protected void tutorAnswers(String text) {
        fake.reset();
        fake.respondWith(text, 100, 50);
    }

    /** Las llamadas al modelo que son del tutor (llevan la pregunta como dato). */
    protected List<ChatCall> tutorCalls() {
        return fake.calls().stream().filter(c -> c.dataMessage().contains("<pregunta>")).toList();
    }

    protected ChatCall lastTutorCall() {
        List<ChatCall> calls = tutorCalls();
        return calls.get(calls.size() - 1);
    }

    protected List<Map<String, Object>> messages() {
        return owner.queryForList("SELECT * FROM tutor_message ORDER BY created_at, id");
    }

    protected int messageCount(String status) {
        return owner.queryForObject("SELECT count(*) FROM tutor_message WHERE status = ?", Integer.class, status);
    }

    /** Una lección ya generada por completo (2 temas, 4 lecciones) y el id de la primera. */
    protected UUID readyCourse(String email) throws Exception {
        return generated(email, 2, true);
    }

    protected UUID lesson(UUID course, int n) {
        return piece(course, "LESSON", n);
    }

    protected void spendPersonalLimit(UUID user) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, 5.00, 'OK', 1)""", user);
    }

    /** Inserta un mensaje directamente (para preparar hilos, límites y retención sin pasar por el modelo). */
    protected void insertMessage(UUID user, UUID course, UUID lesson, String role, String status, String content, String agoInterval) {
        owner.update("""
                INSERT INTO tutor_message (id, owner_id, course_id, piece_id, role, status, content, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now() - CAST(? AS interval))""", user, course, lesson, role, status, content, agoInterval);
    }

    protected void assertCreated(ResultActions result) throws Exception {
        result.andExpect(status().isCreated());
    }
}
