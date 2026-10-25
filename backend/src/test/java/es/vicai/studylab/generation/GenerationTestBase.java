package es.vicai.studylab.generation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import es.vicai.studylab.ai.provider.ChatCall;
import es.vicai.studylab.course.CourseTestBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F05. Contrato (ART-003, ART-004): {@code CourseGenerator.processPending()} genera de forma síncrona los cursos en cola (en los
 * tests el hilo de fondo está apagado: app.generation.worker.enabled=false) y cada petición a la IA lleva un dato {@code pieza} con el valor
 * {@code temario}, {@code leccion}, {@code ejercicios} o {@code examen}; con eso el proveedor falso decide qué contestar: {@code fake.respondUsing(...)}.
 * Los cursos se insertan directamente en la BD (como los dejaría F04) para no depender del asistente.
 */
public abstract class GenerationTestBase extends CourseTestBase {

    @Autowired protected CourseGenerator generator;

    // ---------- el modelo falso ----------

    protected static String piece(ChatCall call) {
        String data = call.dataMessage();
        for (String kind : new String[] {"temario", "leccion", "ejercicios", "examen"}) {
            if (data.contains("<pieza>\n" + kind + "\n</pieza>")) return kind;
        }
        return "desconocida";
    }

    protected static String outlineJson(int topics, int chapters, boolean appropriate) {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"appropriate\":" + appropriate + ",\"category\":\"" + (appropriate ? "NONE" : "VIOLENCE")
                + "\",\"topics\":[");
        for (int t = 1; t <= topics; t++) {
            if (t > 1) sb.append(',');
            sb.append("{\"title\":\"Tema ").append(t).append(" de prueba\",\"summary\":\"Resumen del tema ").append(t).append("\",\"chapters\":[");
            for (int c = 1; c <= chapters; c++) {
                if (c > 1) sb.append(',');
                sb.append("{\"title\":\"Capítulo ").append(t).append('.').append(c).append("\",\"objective\":\"Objetivo del capítulo ").append(t).append('.').append(c).append("\"}");
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    /** Una lección válida (más de 400 caracteres) con los marcadores de cita que se pidan. */
    protected static String lessonMd(String title, int... citations) {
        StringBuilder sb = new StringBuilder("# ").append(title).append("\n\n");
        sb.append("Esta lección explica el concepto paso a paso, con un ejemplo sencillo y una idea clave que conviene recordar. ".repeat(4)).append("\n\n");
        for (int n : citations) sb.append("Según el material, esto se justifica así [").append(n).append("].\n\n");
        return sb.append("**Complemento:** una idea adicional que no sale del material.\n").toString();
    }

    protected static String exercisesJson() {
        return "{\"schemaVersion\":1,\"exercises\":[{\"difficulty\":\"LOW\",\"statement\":\"Primer ejercicio\",\"solution\":\"Solución uno\",\"explanation\":\"Explicación uno\"},"
                + "{\"difficulty\":\"MEDIUM\",\"statement\":\"Segundo ejercicio\",\"solution\":\"Solución dos\",\"explanation\":\"Explicación dos\"},"
                + "{\"difficulty\":\"HIGH\",\"statement\":\"Tercer ejercicio\",\"solution\":\"Solución tres\",\"explanation\":\"Explicación tres\"}]}";
    }

    protected static String examJson() {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":1,\"questions\":[");
        for (int i = 1; i <= 5; i++) {
            if (i > 1) sb.append(',');
            sb.append("{\"text\":\"Pregunta ").append(i).append("\",\"options\":[\"a\",\"b\",\"c\",\"d\"],\"correct\":").append(i % 4)
                    .append(",\"explanation\":\"Porque sí ").append(i).append("\"}");
        }
        return sb.append("]}").toString();
    }

    /** El modelo que lo hace todo bien: un temario de {@code topics} temas con dos capítulos y piezas válidas; cita [1] solo si hay fuentes. */
    protected Function<ChatCall, String> happy(int topics) {
        return call -> switch (piece(call)) {
            case "temario" -> outlineJson(topics, 2, true);
            case "leccion" -> call.dataMessage().contains("<fuentes>") ? lessonMd("Lección con fuentes", 1) : lessonMd("Lección sin fuentes");
            case "ejercicios" -> exercisesJson();
            case "examen" -> examJson();
            default -> "no sé qué piden";
        };
    }

    protected void modelDoes(Function<ChatCall, String> responder) {
        fake.respondUsing(responder);
    }

    // ---------- cursos en la BD ----------

    protected UUID insertCourse(UUID ownerId, String topic, int topics, boolean exam, List<String> documentIds) {
        UUID intake = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, expires_at) VALUES (?, ?, ?, 'CONSUMED', now() + interval '1 hour')", intake, ownerId, topic);
        String docs = documentIds.stream().map(d -> "\"" + d + "\"").reduce((a, b) -> a + "," + b).orElse("");
        owner.update("INSERT INTO course (id, owner_id, intake_id, title, status, sheet) VALUES (?, ?, ?, ?, 'PENDING_GENERATION', CAST(? AS jsonb))",
                course, ownerId, intake, topic, "{\"schemaVersion\":1,\"topic\":\"" + topic + "\",\"focus\":\"General\",\"startLevel\":\"Desde cero\","
                        + "\"goal\":\"Aprender el tema\",\"topicsCount\":" + topics + ",\"examPerTopic\":" + exam + ",\"documentIds\":[" + docs + "]}");
        for (String doc : documentIds) owner.update("INSERT INTO course_document (course_id, document_id, owner_id) VALUES (?, ?::uuid, ?)", course, doc, ownerId);
        return course;
    }

    protected UUID insertCourse(UUID ownerId, int topics, boolean exam) {
        return insertCourse(ownerId, "Curso de prueba", topics, exam, List.of());
    }

    // ---------- estado ----------

    protected String statusOf(UUID course) {
        return owner.queryForObject("SELECT status FROM course WHERE id = ?", String.class, course);
    }

    protected String stopReasonOf(UUID course) {
        return owner.queryForObject("SELECT stop_reason FROM course WHERE id = ?", String.class, course);
    }

    protected int pieces(UUID course, String status) {
        return owner.queryForObject("SELECT count(*) FROM course_piece WHERE course_id = ? AND status = ?", Integer.class, course, status);
    }

    protected int allPieces(UUID course) {
        return owner.queryForObject("SELECT count(*) FROM course_piece WHERE course_id = ?", Integer.class, course);
    }

    protected List<String> pieceKindsCalled() {
        return fake.calls().stream().map(GenerationTestBase::piece).toList();
    }

    protected List<Map<String, Object>> pieceRows(UUID course) {
        return owner.queryForList("SELECT * FROM course_piece WHERE course_id = ? ORDER BY seq", course);
    }

    // ---------- HTTP ----------

    protected ResultActions detail(String session, Object courseId) throws Exception {
        return mvc.perform(get("/api/courses/" + courseId).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions pieceOf(String session, Object courseId, Object pieceId) throws Exception {
        return mvc.perform(get("/api/courses/" + courseId + "/pieces/" + pieceId).cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions export(String session, Object courseId) throws Exception {
        return mvc.perform(get("/api/courses/" + courseId + "/export").cookie(new Cookie(SESSION_COOKIE, session)));
    }

    protected ResultActions resume(String session, Object courseId) throws Exception {
        return postAs(session, "/api/courses/" + courseId + "/resume", null);
    }

    protected UUID firstPiece(UUID course, String kind) {
        return owner.queryForObject("SELECT id FROM course_piece WHERE course_id = ? AND kind = ? ORDER BY seq LIMIT 1", UUID.class, course, kind);
    }
}
