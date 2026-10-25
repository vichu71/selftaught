package es.vicai.studylab.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valida el examen del modelo (ADR-43): JSON estricto con 5 a 12 preguntas de 3 o 4 opciones distintas y una única respuesta correcta que exista.
 * Devuelve vacío ante cualquier incumplimiento; nunca lanza.
 */
@Component
public class ExamParser {

    public static final int MIN_QUESTIONS = 5;
    public static final int MAX_QUESTIONS = 12;

    public record Question(String text, List<String> options, int correct, String explanation) {}

    public record Exam(List<Question> questions) {}

    public Optional<Exam> parse(String raw) {
        try {
            return Optional.of(doParse(raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Exam doParse(String raw) {
        JsonNode root = ModelJson.object(raw);
        ModelJson.checkVersion(root);
        List<Question> questions = new ArrayList<>();
        for (JsonNode node : ModelJson.array(root, "questions", MIN_QUESTIONS, MAX_QUESTIONS)) {
            if (!node.isObject()) throw new IllegalArgumentException();
            String text = ModelJson.multiline(node, "text", 300);

            List<String> options = new ArrayList<>();
            Set<String> distinct = new HashSet<>();
            for (JsonNode option : ModelJson.array(node, "options", 3, 4)) {
                if (!option.isTextual()) throw new IllegalArgumentException();
                String value = option.asText().strip();
                if (value.isEmpty() || value.codePointCount(0, value.length()) > 150 || value.chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException();
                }
                if (!distinct.add(value.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
                options.add(value);
            }
            JsonNode correct = node.get("correct");
            if (correct == null || !correct.isInt() || correct.asInt() < 0 || correct.asInt() >= options.size()) throw new IllegalArgumentException();
            questions.add(new Question(text, List.copyOf(options), correct.asInt(), ModelJson.optionalMultiline(node, "explanation", 500)));
        }
        return new Exam(List.copyOf(questions));
    }
}
