package es.vicai.studylab.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valida los ejercicios del modelo (ADR-43): JSON estricto con 3 a 8 ejercicios, cada uno con dificultad conocida, enunciado y solución acotados.
 * F06 los corregirá, así que necesitan estructura. Devuelve vacío ante cualquier incumplimiento; nunca lanza.
 */
@Component
public class ExercisesParser {

    public static final int MIN_ITEMS = 3;
    public static final int MAX_ITEMS = 8;
    private static final Set<String> DIFFICULTIES = Set.of("LOW", "MEDIUM", "HIGH");

    public record Item(String difficulty, String statement, String solution, String explanation) {}

    public record Exercises(List<Item> items) {}

    public Optional<Exercises> parse(String raw) {
        try {
            return Optional.of(doParse(raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Exercises doParse(String raw) {
        JsonNode root = ModelJson.object(raw);
        ModelJson.checkVersion(root);
        List<Item> items = new ArrayList<>();
        for (JsonNode node : ModelJson.array(root, "exercises", MIN_ITEMS, MAX_ITEMS)) {
            if (!node.isObject()) throw new IllegalArgumentException();
            JsonNode difficulty = node.get("difficulty");
            if (difficulty == null || !difficulty.isTextual() || !DIFFICULTIES.contains(difficulty.asText())) throw new IllegalArgumentException();
            items.add(new Item(difficulty.asText(), ModelJson.multiline(node, "statement", 1000), ModelJson.multiline(node, "solution", 2000),
                    ModelJson.optionalMultiline(node, "explanation", 1000)));
        }
        return new Exercises(List.copyOf(items));
    }
}
