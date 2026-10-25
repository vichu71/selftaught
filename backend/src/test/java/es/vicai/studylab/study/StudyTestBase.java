package es.vicai.studylab.study;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import es.vicai.studylab.generation.GenerationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F06. Contrato (ART-003): las piezas son las de F05 (se generan de forma síncrona con el modelo falso) y el estudio cuelga de
 * {@code /api/courses/{curso}/pieces/{pieza}/…}. Un examen generado por {@code happy(n)} tiene 5 preguntas con la correcta en {@code [1,2,3,0,1]}
 * (índices desde 0); los ejercicios, 3 con su solución («Solución uno»…).
 */
public abstract class StudyTestBase extends GenerationTestBase {

    protected static final Integer[] ALL_RIGHT = {1, 2, 3, 0, 1};
    protected static final Integer[] ALL_WRONG = {0, 0, 0, 1, 0};

    /** Un curso de {@code topics} temas (2 capítulos por tema, con examen o sin él) ya generado por completo. */
    protected UUID generated(String email, int topics, boolean exam) throws Exception {
        UUID course = insertCourse(idOf(email), "Curso de " + email, topics, exam, List.of());
        modelDoes(happy(topics));
        generator.processPending();
        return course;
    }

    protected List<UUID> pieceIds(UUID course, String kind) {
        return owner.queryForList("SELECT id FROM course_piece WHERE course_id = ? AND kind = ? ORDER BY seq", UUID.class, course, kind);
    }

    protected UUID piece(UUID course, String kind, int n) {
        return pieceIds(course, kind).get(n);
    }

    protected static String piecePath(UUID course, UUID piece) {
        return "/api/courses/" + course + "/pieces/" + piece;
    }

    // ---------- peticiones ----------

    protected ResultActions putAs(String session, String path, Object body) throws Exception {
        var req = put(path).with(csrf()).cookie(new Cookie(SESSION_COOKIE, session));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    protected ResultActions putAsWithoutCsrf(String session, String path, Object body) throws Exception {
        var req = put(path).cookie(new Cookie(SESSION_COOKIE, session));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    protected ResultActions studyOf(String session, UUID course) throws Exception {
        return getAs(session, "/api/courses/" + course + "/study");
    }

    protected ResultActions markDone(String session, UUID course, UUID lesson, boolean done) throws Exception {
        return putAs(session, piecePath(course, lesson) + "/done", Map.of("done", done));
    }

    protected ResultActions answer(String session, UUID course, UUID exercises, int item, String text) throws Exception {
        return postAs(session, piecePath(course, exercises) + "/exercises/" + item + "/attempts", Map.of("answer", text));
    }

    /** Envía una respuesta y devuelve el id del intento. */
    protected String answered(String session, UUID course, UUID exercises, int item, String text) throws Exception {
        String body = answer(session, course, exercises, item, text).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.attemptId");
    }

    protected ResultActions selfResult(String session, UUID course, UUID exercises, int item, String attempt, String result) throws Exception {
        return putAs(session, piecePath(course, exercises) + "/exercises/" + item + "/attempts/" + attempt + "/result", Map.of("result", result));
    }

    protected ResultActions aiGrade(String session, UUID course, UUID exercises, int item, String attempt) throws Exception {
        return postAs(session, piecePath(course, exercises) + "/exercises/" + item + "/attempts/" + attempt + "/ai-grade", null);
    }

    protected ResultActions startExam(String session, UUID course, UUID exam) throws Exception {
        return postAs(session, piecePath(course, exam) + "/exam/attempts", null);
    }

    protected String examStarted(String session, UUID course, UUID exam) throws Exception {
        String body = startExam(session, course, exam).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.attemptId");
    }

    protected ResultActions submitExam(String session, UUID course, UUID exam, String attempt, Integer... answers) throws Exception {
        return postAs(session, piecePath(course, exam) + "/exam/attempts/" + attempt + "/submit", Map.of("answers", Arrays.asList(answers)));
    }

    /** Empieza y entrega un examen de una vez; devuelve el id del intento. */
    protected String examTaken(String session, UUID course, UUID exam, Integer... answers) throws Exception {
        String attempt = examStarted(session, course, exam);
        submitExam(session, course, exam, attempt, answers).andExpect(status().isOk());
        return attempt;
    }

    protected ResultActions attemptsOf(String session, UUID course, UUID piece) throws Exception {
        return getAs(session, piecePath(course, piece) + "/attempts");
    }

    protected int rows(String table) {
        return owner.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    /** Sustituye el examen de la pieza por uno de 4 preguntas con la correcta siempre en la opción 0 (para probar el 50 % exacto). */
    protected void fourQuestionExam(UUID exam) {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"questions\":[");
        for (int i = 1; i <= 4; i++) {
            if (i > 1) sb.append(',');
            sb.append("{\"text\":\"Pregunta ").append(i).append("\",\"options\":[\"a\",\"b\",\"c\"],\"correct\":0,\"explanation\":\"Porque sí ").append(i).append("\"}");
        }
        owner.update("UPDATE course_piece SET content_json = CAST(? AS jsonb) WHERE id = ?", sb.append("]}").toString(), exam);
    }
}
