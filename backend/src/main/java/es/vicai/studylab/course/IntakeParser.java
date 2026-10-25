package es.vicai.studylab.course;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valida la salida del modelo del asistente contra un esquema estricto antes de usarla (ADR-38). Nada del modelo llega al cliente sin
 * pasar por aquí: los ids los genera el servidor, los campos y las longitudes están acotados y siempre se añade «Como tú veas».
 * Devuelve vacío ante cualquier incumplimiento; nunca lanza.
 */
@Component
public class IntakeParser {

    public static final int MAX_QUESTIONS = 3;
    public static final String AUTO_ID = "auto";
    public static final String AUTO_LABEL = "Como tú veas";

    static final Set<String> FIELDS = Set.of("focus", "startLevel", "goal", "topicsCount", "examPerTopic");
    private static final Set<String> CATEGORIES = Set.of("EXPLICIT", "VIOLENCE", "DRUGS", "SELF_HARM", "OTHER");
    private static final int MAX_TEXT = 160;
    private static final int MAX_LABEL = 80;
    private static final int MAX_VALUE = 120;

    public record Option(String id, String label, String value, @JsonProperty("isDefault") boolean isDefault) {}

    public record Question(String id, String field, String text, List<Option> options) {

        /** La opción marcada por defecto: lo que significa «Como tú veas» y lo que se usa si no se contesta. */
        public Option defaultOption() {
            return options.stream().filter(Option::isDefault).findFirst().orElseThrow();
        }
    }

    public record Result(boolean appropriate, String category, List<Question> questions) {}

    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public Optional<Result> parse(String raw) {
        try {
            return Optional.of(doParse(raw));
        } catch (Exception e) {
            return Optional.empty(); // cualquier fallo de formato es «no válido»; el motivo no importa ni se registra
        }
    }

    private Result doParse(String raw) throws Exception {
        String json = stripFences(raw);
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) throw new IllegalArgumentException();

        JsonNode appropriate = root.get("appropriate");
        if (appropriate == null || !appropriate.isBoolean()) throw new IllegalArgumentException();
        String category = root.hasNonNull("category") && root.get("category").isTextual() ? root.get("category").asText() : "NONE";
        if (!"NONE".equals(category) && !CATEGORIES.contains(category)) category = "OTHER";

        JsonNode questions = root.get("questions");
        List<Question> parsed = new ArrayList<>();
        if (questions != null) {
            if (!questions.isArray() || questions.size() > MAX_QUESTIONS) throw new IllegalArgumentException();
            Set<String> usedFields = new java.util.HashSet<>();
            int index = 1;
            for (JsonNode node : questions) parsed.add(question(node, "q" + index++, usedFields));
        }
        return new Result(appropriate.asBoolean(), category, List.copyOf(parsed));
    }

    private Question question(JsonNode node, String id, Set<String> usedFields) {
        if (!node.isObject()) throw new IllegalArgumentException();
        String field = text(node, "field", 40);
        if (!FIELDS.contains(field) || !usedFields.add(field)) throw new IllegalArgumentException();
        String text = text(node, "text", MAX_TEXT);

        JsonNode options = node.get("options");
        if (options == null || !options.isArray() || options.size() < 2 || options.size() > 4) throw new IllegalArgumentException();
        List<Option> parsed = new ArrayList<>();
        Option defaultOption = null;
        int index = 1;
        for (JsonNode o : options) {
            if (!o.isObject()) throw new IllegalArgumentException();
            String label = text(o, "label", MAX_LABEL);
            String value = text(o, "value", MAX_VALUE);
            checkValue(field, value);
            boolean isDefault = o.has("default") && o.get("default").isBoolean() && o.get("default").asBoolean();
            if (o.has("default") && !o.get("default").isBoolean()) throw new IllegalArgumentException();
            Option option = new Option("o" + index++, label, value, isDefault);
            if (isDefault) {
                if (defaultOption != null) throw new IllegalArgumentException();
                defaultOption = option;
            }
            parsed.add(option);
        }
        if (defaultOption == null) throw new IllegalArgumentException();
        parsed.add(new Option(AUTO_ID, AUTO_LABEL, defaultOption.value(), false));
        return new Question(id, field, text, List.copyOf(parsed));
    }

    private static void checkValue(String field, String value) {
        switch (field) {
            case "topicsCount" -> {
                int n = Integer.parseInt(value.trim());
                if (n < SheetFactory.MIN_TOPICS || n > SheetFactory.MAX_TOPICS) throw new IllegalArgumentException();
            }
            case "examPerTopic" -> {
                if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException();
            }
            default -> { }
        }
    }

    /** Un texto obligatorio, sin caracteres de control y de longitud acotada. */
    private static String text(JsonNode node, String name, int max) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException();
        String text = value.asText().strip();
        if (text.isEmpty() || text.length() > max || text.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
        return text;
    }

    /** Algunos modelos envuelven el JSON en un bloque de código; es lo único que se tolera fuera del objeto. */
    private static String stripFences(String raw) {
        if (raw == null) throw new IllegalArgumentException();
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd < 0 || !text.endsWith("```")) throw new IllegalArgumentException();
            text = text.substring(firstLineEnd + 1, text.length() - 3).strip();
        }
        return text;
    }
}
