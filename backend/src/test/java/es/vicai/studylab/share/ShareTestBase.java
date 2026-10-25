package es.vicai.studylab.share;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import es.vicai.studylab.courseedit.CourseEditTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de compartir (F09). Contrato (ART-003): {@code POST /api/courses/{c}/share} {days?} crea un código (201 {id, code, expiresAt}, el código solo se
 * devuelve esa vez), {@code GET} lista su estado, {@code DELETE …/share/{id}} lo revoca y {@code POST /api/courses/redeem} {code} crea la copia de quien canjea.
 */
public abstract class ShareTestBase extends CourseEditTestBase {

    /** 16 caracteres de un alfabeto sin ambiguos, en 4 grupos. */
    protected static final Pattern CODE = Pattern.compile("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){3}");

    protected ResultActions createCodeRequest(String session, UUID course, Object body) throws Exception {
        return postAs(session, coursePath(course, "/share"), body);
    }

    /** Crea un código de siete días y lo devuelve. */
    protected String createCode(String session, UUID course) throws Exception {
        String body = createCodeRequest(session, course, Map.of()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    protected ResultActions redeem(String session, String code) throws Exception {
        return postAs(session, "/api/courses/redeem", Map.of("code", code));
    }

    /** Canjea y devuelve el id de la copia. */
    protected UUID redeemed(String session, String code) throws Exception {
        String body = redeem(session, code).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected UUID codeId(UUID course) {
        return owner.queryForObject("SELECT id FROM share_code WHERE course_id = ? ORDER BY created_at DESC LIMIT 1", UUID.class, course);
    }

    protected String normalized(String code) {
        return code.replace("-", "");
    }

    protected UUID courseOwnedBy(String email) {
        return owner.queryForObject("SELECT id FROM course WHERE owner_id = ? ORDER BY created_at DESC LIMIT 1", UUID.class, idOf(email));
    }

    /** Un curso con material propio (las lecciones citan sus fuentes), progreso, intentos y conversación: todo lo que NO debe viajar en una copia. */
    protected UUID fullCourse(String session, String email) throws Exception {
        String doc = uploadedAndProcessed(session, "apuntes-privados.txt", text(("CONCEPTO-PRIVADO es un concepto que se explica en los apuntes con ejemplos. ").repeat(30)));
        UUID course = insertCourse(idOf(email), "Curso con apuntes", 2, true, List.of(doc));
        modelDoes(happy(2));
        generator.processPending();
        UUID topic = topicIds(course).get(0);
        giveDependents(session, idOf(email), course, chapterIds(topic).get(0), topic);
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, expires_at) VALUES (gen_random_uuid(), ?, 'otra consulta', 'OPEN', now() + interval '1 hour')", idOf(email));
        return course;
    }

    /** Todo el texto que guarda un curso, para buscar en él lo que no debe estar. */
    protected String everythingOf(UUID course) {
        return owner.queryForObject("""
                SELECT concat_ws(' ', (SELECT sheet::text FROM course WHERE id = ?),
                       (SELECT coalesce(string_agg(title || ' ' || summary, ' '), '') FROM course_topic WHERE course_id = ?),
                       (SELECT coalesce(string_agg(title || ' ' || objective, ' '), '') FROM course_chapter WHERE course_id = ?),
                       (SELECT coalesce(string_agg(coalesce(content_md, '') || ' ' || coalesce(content_json::text, '') || ' ' || coalesce(sources::text, ''), ' '), '') FROM course_piece WHERE course_id = ?))
                """, String.class, course, course, course, course);
    }
}
