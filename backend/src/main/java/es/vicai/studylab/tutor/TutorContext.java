package es.vicai.studylab.tutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiRequest;
import es.vicai.studylab.ai.AiTask;
import es.vicai.studylab.course.CourseSheet;
import es.vicai.studylab.generation.SourcePack;
import es.vicai.studylab.material.Fragment;
import es.vicai.studylab.material.MaterialSearch;
import es.vicai.studylab.safety.ContentGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lo que el modelo recibe con cada pregunta (ADR-56): todo va como DATO, nunca en las instrucciones. La lección y el capítulo, un resumen del curso, un resumen
 * corto de lo fallado en los ejercicios del capítulo (solo enunciados: ni lo que escribió el alumno ni las soluciones), fragmentos de su material, los últimos
 * intercambios y la pregunta. Los exámenes no entran nunca. Para un menor, los fragmentos pasan por {@link ContentGuard} antes de enviarse (condición de ADR-34).
 */
@Component
public class TutorContext {

    static final int MAX_LESSON_CHARS = 12_000;
    static final int MAX_COURSE_CHARS = 4_000;
    static final int MAX_FAILURES = 5;
    static final int MAX_FAILURE_CHARS = 300;
    static final int MAX_OUTPUT_TOKENS = 1000;

    /** Lo que se pide al modelo y los fragmentos que viajan (para verificar y guardar las citas). */
    public record Prepared(AiRequest request, SourcePack pack) {}

    private final TutorRepository repo;
    private final TutorProperties props;
    private final MaterialSearch search;
    private final ContentGuard guard;
    private final ObjectMapper json;

    public TutorContext(TutorRepository repo, TutorProperties props, MaterialSearch search, ContentGuard guard, ObjectMapper json) {
        this.repo = repo;
        this.props = props;
        this.search = search;
        this.guard = guard;
        this.json = json;
    }

    public Prepared prepare(UUID userId, boolean minor, UUID courseId, TutorRepository.LessonRow lesson, String question) {
        CourseSheet sheet = sheet(courseId, userId);
        SourcePack pack = sources(userId, minor, courseId, sheet, lesson, question);

        AiRequest request = AiRequest.of(AiTask.TUTOR, userId, "tutor").course(courseId).data("audiencia", minor ? "escolar" : "general")
                .data("curso", course(sheet, courseId, userId)).data("capitulo", lesson.chapterTitle() + "\nObjetivo: " + lesson.chapterObjective())
                .data("leccion", cut(lesson.contentMd() == null ? "" : lesson.contentMd(), MAX_LESSON_CHARS));
        String failures = failures(courseId, lesson.chapterId(), userId);
        if (!failures.isEmpty()) request = request.data("fallos", failures);
        if (!pack.isEmpty()) request = request.data("fuentes", pack.text());
        String history = history(lesson.id(), userId);
        if (!history.isEmpty()) request = request.data("historial", history);
        return new Prepared(request.data("pregunta", question).maxOutputTokens(MAX_OUTPUT_TOKENS), pack);
    }

    // ---------- el curso ----------

    private CourseSheet sheet(UUID courseId, UUID userId) {
        String raw = repo.sheet(courseId, userId).orElseThrow(IllegalStateException::new);
        try {
            return json.readValue(raw, CourseSheet.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha de curso no válida");
        }
    }

    private String course(CourseSheet sheet, UUID courseId, UUID userId) {
        StringBuilder text = new StringBuilder();
        text.append("Tema: ").append(sheet.topic()).append("\nEnfoque: ").append(sheet.focus()).append("\nNivel de partida: ").append(sheet.startLevel())
                .append("\nObjetivo: ").append(sheet.goal()).append("\nTemario:");
        int topic = 0;
        for (TutorRepository.OutlineRow row : repo.outline(courseId, userId)) {
            if (row.position() != topic) {
                topic = row.position();
                text.append('\n').append(topic).append(". ").append(row.topicTitle()).append(" — ").append(row.topicSummary());
            }
            if (row.chapterTitle() != null) text.append("\n   - ").append(row.chapterTitle());
        }
        return cut(text.toString(), MAX_COURSE_CHARS);
    }

    // ---------- lo fallado en el capítulo ----------

    /** Los enunciados de los ejercicios del capítulo que el alumno falló o dejó a medias; vacío si no hay ninguno. */
    private String failures(UUID courseId, UUID chapterId, UUID userId) {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (TutorRepository.ExerciseOutcome outcome : repo.exerciseOutcomes(courseId, chapterId, userId)) {
            boolean failed = "INCORRECT".equals(outcome.result());
            if (!failed && !"PARTIAL".equals(outcome.result())) continue;
            String statement = statement(outcome);
            if (statement.isEmpty()) continue;
            if (count++ == 0) text.append("Ejercicios de este capítulo que el alumno no ha resuelto bien (solo los enunciados):");
            if (count > MAX_FAILURES) break;
            text.append("\n- (").append(failed ? "fallado" : "a medias").append(") ").append(cut(statement, MAX_FAILURE_CHARS));
        }
        return text.toString();
    }

    private String statement(TutorRepository.ExerciseOutcome outcome) {
        try {
            JsonNode exercises = json.readTree(outcome.contentJson()).path("exercises");
            return exercises.path(outcome.item()).path("statement").asText("").replaceAll("\\s+", " ").strip();
        } catch (JsonProcessingException | RuntimeException e) {
            return "";
        }
    }

    // ---------- material propio ----------

    /** Los fragmentos del material del curso más cercanos a la pregunta, numerados; para un menor, sin los que el filtro rechaza. */
    private SourcePack sources(UUID userId, boolean minor, UUID courseId, CourseSheet sheet, TutorRepository.LessonRow lesson, String question) {
        SourcePack none = SourcePack.of(List.of(), text -> true);
        List<UUID> documents = repo.documents(courseId, userId);
        if (documents.isEmpty()) return none;
        String query = cut(question + ". " + sheet.topic() + ". " + lesson.chapterTitle(), MaterialSearch.MAX_QUERY_CHARS - 100);
        try {
            List<Fragment> fragments = search.search(userId, query, SourcePack.MAX_FRAGMENTS * 2, documents);
            return SourcePack.of(fragments, text -> !minor || guard.localCheck(text).isEmpty());
        } catch (AiException e) {
            // Sin los vectores (Ollama caído) el tutor sigue respondiendo con la lección y sin fuentes
            return none;
        }
    }

    // ---------- historial ----------

    /** Los últimos intercambios correctos, sin pasar del tope de caracteres: lo más antiguo es lo primero que se descarta. */
    private String history(UUID pieceId, UUID userId) {
        List<TutorRepository.MessageRow> rows = new ArrayList<>(repo.recentOk(pieceId, userId, props.historyExchanges() * 2));
        int total = rows.stream().mapToInt(r -> r.content().length()).sum();
        while (!rows.isEmpty() && (total > props.historyChars() || "TUTOR".equals(rows.get(0).role()))) total -= rows.remove(0).content().length();
        StringBuilder text = new StringBuilder();
        for (TutorRepository.MessageRow row : rows) {
            if (text.length() > 0) text.append("\n\n");
            text.append("USER".equals(row.role()) ? "Alumno: " : "Tutor: ").append(row.content());
        }
        return text.toString();
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
