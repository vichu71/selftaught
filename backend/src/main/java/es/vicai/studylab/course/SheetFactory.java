package es.vicai.studylab.course;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Construye la ficha del curso (ADR-40) a partir de las preguntas que guardó el servidor y de las respuestas del usuario. El cliente solo
 * aporta ids de opciones: los valores salen siempre de lo guardado. Lo que no se contesta, o «Como tú veas», usa la opción por defecto.
 */
@Component
public class SheetFactory {

    public static final int MIN_TOPICS = 3;
    public static final int MAX_TOPICS = 12;
    static final int DEFAULT_TOPICS = 6;
    static final String DEFAULT_FOCUS = "General";
    static final String DEFAULT_LEVEL = "Desde cero";
    static final String DEFAULT_GOAL = "Aprender el tema";

    /**
     * @param answers id de pregunta → id de opción elegida
     * @throws IllegalArgumentException si una respuesta no corresponde a una pregunta ni a una opción de esa pregunta
     */
    public CourseSheet build(String topic, List<IntakeParser.Question> questions, Map<String, String> answers, List<UUID> documentIds) {
        Set<String> known = new HashSet<>();
        for (IntakeParser.Question q : questions) known.add(q.id());
        for (String questionId : answers.keySet()) {
            if (!known.contains(questionId)) throw new IllegalArgumentException("Pregunta desconocida");
        }

        String focus = DEFAULT_FOCUS;
        String level = DEFAULT_LEVEL;
        String goal = DEFAULT_GOAL;
        int topics = DEFAULT_TOPICS;
        boolean exam = true;
        for (IntakeParser.Question q : questions) {
            String chosenId = answers.get(q.id());
            IntakeParser.Option chosen = chosenId == null ? q.defaultOption() : q.options().stream()
                    .filter(o -> o.id().equals(chosenId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Opción desconocida"));
            switch (q.field()) {
                case "focus" -> focus = chosen.value();
                case "startLevel" -> level = chosen.value();
                case "goal" -> goal = chosen.value();
                case "topicsCount" -> topics = Integer.parseInt(chosen.value().trim());
                case "examPerTopic" -> exam = Boolean.parseBoolean(chosen.value());
                default -> throw new IllegalArgumentException("Campo desconocido");
            }
        }
        return new CourseSheet(CourseSheet.SCHEMA_VERSION, topic.strip(), focus, level, goal, topics, exam, documentIds);
    }
}
