package es.vicai.studylab.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import es.vicai.studylab.material.MaterialTestBase;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F04. Contrato (ART-003): POST /api/courses/intake {topic, documentIds}, POST /api/courses {intakeId, answers},
 * GET /api/courses y GET /api/courses/{id}. El modelo es siempre el proveedor falso: {@code fake.respondWith(json…)} fija su respuesta
 * y {@code fake.respondWithSequence(…)} una respuesta distinta por llamada.
 */
public abstract class CourseTestBase extends MaterialTestBase {

    public static final String BLOCKED_MESSAGE = "Contenido inapropiado. Consulta con tu adulto.";

    /** Dos preguntas válidas: enfoque y número de temas. */
    protected static final String TWO_QUESTIONS = """
            {"appropriate":true,"category":"NONE","questions":[
              {"field":"focus","text":"¿En qué quieres centrarte?","options":[
                {"label":"Gramática y vocabulario","value":"Gramática y vocabulario","default":true},
                {"label":"Conversación","value":"Conversación"}]},
              {"field":"topicsCount","text":"¿Cuántos temas quieres?","options":[
                {"label":"Pocos (4)","value":"4"},{"label":"Normal (6)","value":"6","default":true},{"label":"Muchos (10)","value":"10"}]}]}""";

    protected static final String NO_QUESTIONS = "{\"appropriate\":true,\"category\":\"NONE\",\"questions\":[]}";

    protected static final String INAPPROPRIATE = "{\"appropriate\":false,\"category\":\"VIOLENCE\",\"questions\":[]}";

    protected void model(String json) {
        fake.respondWith(json, 200, 100);
    }

    // ---------- peticiones ----------

    protected ResultActions intake(String session, String topic) throws Exception {
        return intake(session, topic, List.of());
    }

    protected ResultActions intake(String session, String topic, List<String> documentIds) throws Exception {
        return postAs(session, "/api/courses/intake", Map.of("topic", topic, "documentIds", documentIds));
    }

    protected ResultActions create(String session, String intakeId, List<Map<String, String>> answers) throws Exception {
        return postAs(session, "/api/courses", Map.of("intakeId", intakeId, "answers", answers));
    }

    protected ResultActions courseOf(String session, String id) throws Exception {
        return mvc.perform(get("/api/courses/" + id).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    /** Inicia la consulta esperando 201 y devuelve el cuerpo JSON completo. */
    protected String started(String session, String topic, List<String> documentIds) throws Exception {
        return intake(session, topic, documentIds).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    protected String intakeId(String body) {
        return JsonPath.read(body, "$.intakeId");
    }

    /** Las respuestas que elige quien acepta lo que se le propone: la opción marcada por defecto de cada pregunta. */
    protected List<Map<String, String>> defaultAnswers(String intakeBody) {
        List<Map<String, String>> answers = new ArrayList<>();
        List<String> questions = JsonPath.read(intakeBody, "$.questions[*].id");
        for (int i = 0; i < questions.size(); i++) {
            List<String> ids = JsonPath.read(intakeBody, "$.questions[" + i + "].options[?(@.isDefault == true)].id");
            answers.add(Map.of("questionId", questions.get(i), "optionId", ids.get(0)));
        }
        return answers;
    }

    /** Consulta + aceptar con los valores por defecto; devuelve el cuerpo del curso. */
    protected String courseCreated(String session, String topic) throws Exception {
        String body = started(session, topic, List.of());
        return create(session, intakeId(body), defaultAnswers(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    // ---------- estado ----------

    protected List<Map<String, Object>> intakes() {
        return owner.queryForList("SELECT * FROM course_intake ORDER BY created_at, id");
    }

    protected List<Map<String, Object>> courses() {
        return owner.queryForList("SELECT * FROM course ORDER BY created_at, id");
    }

    protected int chatCalls() {
        return fake.calls().size();
    }
}
