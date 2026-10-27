package es.vicai.studylab.illustrations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import es.vicai.studylab.share.ShareTestBase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F12. Contrato (ART-003, ART-004): {@code IllustrationGenerator.processPending()} genera de forma síncrona (el hilo de fondo está apagado con
 * app.illustrations.worker.enabled=false) una imagen por tema de los cursos {@code READY} con {@code course.illustrations = true} que aún no tienen fila, con el
 * proveedor falso ({@code fake.imageCalls()}, precio ficticio 0,05 USD por imagen). La imagen de un tema cuelga de {@code /api/courses/{curso}/topics/{tema}/image}.
 * Un curso de {@code ready(email)} tiene 2 temas × 2 capítulos.
 */
public abstract class IllustrationTestBase extends ShareTestBase {

    @Autowired protected IllustrationGenerator illustrations;

    private final java.util.Map<String, String> sessions = new java.util.HashMap<>();

    /** La BD se comparte entre clases: los cursos con el interruptor de tests anteriores no deben entrar en la cola de este. */
    @org.junit.jupiter.api.BeforeEach
    void noOtherCourseWantsIllustrations() {
        owner.update("UPDATE course SET illustrations = false");
        fake.reset();
    }

    /** La sesión de un adulto: se registra la primera vez y se reutiliza después (registrar dos veces el mismo correo no vale). */
    protected String session(String email) throws Exception {
        String s = sessions.get(email);
        if (s == null) {
            s = logged(email);
            sessions.put(email, s);
        }
        return s;
    }

    /** Un curso ya generado de {@code email} con las ilustraciones activadas (la cola aún no ha trabajado). */
    protected UUID courseWithIllustrations(String email) throws Exception {
        session(email);
        UUID course = ready(email);
        owner.update("UPDATE course SET illustrations = true WHERE id = ?", course);
        return course;
    }

    /** Vacía la cola de ilustraciones; devuelve cuántas imágenes se han procesado. */
    protected int drain() {
        int total = 0;
        for (int i = 0; i < 50; i++) {
            int n = illustrations.processPending();
            if (n == 0) break;
            total += n;
        }
        return total;
    }

    protected static String imagePath(UUID course, UUID topic) {
        return "/api/courses/" + course + "/topics/" + topic + "/image";
    }

    protected ResultActions imageOf(String session, UUID course, UUID topic) throws Exception {
        return mvc.perform(get(imagePath(course, topic)).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions imageOf(String session, UUID course, UUID topic, String etag) throws Exception {
        return mvc.perform(get(imagePath(course, topic)).header("If-None-Match", etag).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions regenerate(String session, UUID course, UUID topic) throws Exception {
        return postAs(session, imagePath(course, topic) + "/regenerate", null);
    }

    protected ResultActions removeImage(String session, UUID course, UUID topic) throws Exception {
        return deleteAs(session, imagePath(course, topic));
    }

    protected ResultActions createCourseRequest(String session, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/courses").with(csrf()).cookie(new Cookie(SESSION_COOKIE, session))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    // ---------- estado de la BD ----------

    protected List<Map<String, Object>> imageRows(UUID course) {
        return owner.queryForList("SELECT * FROM course_image WHERE course_id = ? ORDER BY created_at, id", course);
    }

    protected String imageStatus(UUID topic) {
        List<String> rows = owner.queryForList("SELECT status FROM course_image WHERE topic_id = ?", String.class, topic);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected int imageCount(UUID course, String status) {
        return count("SELECT count(*) FROM course_image WHERE course_id = ? AND status = ?", course, status);
    }

    protected BigDecimal imageSpend(UUID user) {
        BigDecimal sum = owner.queryForObject("SELECT coalesce(sum(cost_usd), 0) FROM ai_usage WHERE task = 'IMAGE' AND user_id = ?", BigDecimal.class, user);
        return sum;
    }

    protected int imageUsageRows() {
        return count("SELECT count(*) FROM ai_usage WHERE task = 'IMAGE'");
    }

    /** Gasto previo de un usuario en el mes (cuenta para su tope personal). */
    protected void spendFor(UUID user, String usd) {
        owner.update("INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status) "
                + "VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', ?, 'OK')", user, new BigDecimal(usd));
    }

    protected UUID insertTopic(UUID course, UUID ownerId, int position, String title) {
        UUID topic = UUID.randomUUID();
        owner.update("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (?, ?, ?, ?, ?, 'Resumen')", topic, course, ownerId, position, title);
        return topic;
    }
}
