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
 * Valida el temario del modelo contra un esquema estricto (ADR-43): exactamente los temas de la ficha, de 2 a 4 capítulos por tema, títulos únicos
 * y textos cortos y sin marcado. Lleva además la valoración de idoneidad del modelo (ADR-47). Devuelve vacío ante cualquier incumplimiento; nunca lanza.
 */
@Component
public class OutlineParser {

    public static final int MIN_CHAPTERS = 2;
    public static final int MAX_CHAPTERS = 4;
    private static final Set<String> CATEGORIES = Set.of("EXPLICIT", "VIOLENCE", "DRUGS", "SELF_HARM", "OTHER");

    public record Chapter(String title, String objective) {}

    public record Topic(String title, String summary, List<Chapter> chapters) {}

    public record Outline(boolean appropriate, String category, List<Topic> topics) {}

    public Optional<Outline> parse(String raw, int expectedTopics) {
        try {
            return Optional.of(doParse(raw, expectedTopics));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Outline doParse(String raw, int expectedTopics) {
        JsonNode root = ModelJson.object(raw);
        ModelJson.checkVersion(root);
        JsonNode appropriate = root.get("appropriate");
        if (appropriate == null || !appropriate.isBoolean()) throw new IllegalArgumentException();
        String category = root.hasNonNull("category") && root.get("category").isTextual() ? root.get("category").asText() : "NONE";
        if (!"NONE".equals(category) && !CATEGORIES.contains(category)) category = "OTHER";

        List<Topic> topics;
        try {
            topics = topics(root, expectedTopics);
        } catch (IllegalArgumentException e) {
            // Un temario que el modelo juzga no apto puede venir sin temas o mal formado: se lee la valoración y se descartan los temas
            if (!appropriate.asBoolean()) return new Outline(false, category, List.of());
            throw e;
        }
        return new Outline(appropriate.asBoolean(), category, topics);
    }

    private static List<Topic> topics(JsonNode root, int expected) {
        JsonNode array = root.get("topics");
        if (array == null || !array.isArray()) throw new IllegalArgumentException();
        if (array.isEmpty() && !isFalse(root.get("appropriate"))) throw new IllegalArgumentException();
        if (array.isEmpty()) return List.of();
        if (array.size() != expected) throw new IllegalArgumentException();

        List<Topic> topics = new ArrayList<>();
        Set<String> topicTitles = new HashSet<>();
        for (JsonNode node : array) {
            if (!node.isObject()) throw new IllegalArgumentException();
            String title = ModelJson.plain(node, "title", 120);
            if (!topicTitles.add(title.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
            String summary = ModelJson.plain(node, "summary", 300);

            List<Chapter> chapters = new ArrayList<>();
            Set<String> chapterTitles = new HashSet<>();
            for (JsonNode c : ModelJson.array(node, "chapters", MIN_CHAPTERS, MAX_CHAPTERS)) {
                if (!c.isObject()) throw new IllegalArgumentException();
                String chapterTitle = ModelJson.plain(c, "title", 120);
                if (!chapterTitles.add(chapterTitle.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
                chapters.add(new Chapter(chapterTitle, ModelJson.plain(c, "objective", 200)));
            }
            topics.add(new Topic(title, summary, List.copyOf(chapters)));
        }
        return List.copyOf(topics);
    }

    private static boolean isFalse(JsonNode node) {
        return node != null && node.isBoolean() && !node.asBoolean();
    }
}
