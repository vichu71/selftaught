package es.vicai.studylab.generation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lectura estricta del JSON que devuelve un modelo (patrón de {@code IntakeParser}, ADR-43): un solo objeto, sin texto antes ni después
 * (solo se tolera un bloque de código que lo envuelva) y textos acotados. Todo incumplimiento lanza {@link IllegalArgumentException}, que los
 * parsers convierten en «no válido»; el motivo no importa ni se registra.
 */
public final class ModelJson {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ModelJson() {}

    public static JsonNode object(String raw) {
        try {
            JsonNode root = MAPPER.readTree(stripFences(raw));
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            return root;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    /** Si el objeto trae {@code schemaVersion}, debe ser 1. */
    public static void checkVersion(JsonNode root) {
        JsonNode version = root.get("schemaVersion");
        if (version != null && !(version.isInt() && version.asInt() == 1)) throw new IllegalArgumentException();
    }

    public static JsonNode array(JsonNode node, String name, int min, int max) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray() || value.size() < min || value.size() > max) throw new IllegalArgumentException();
        return value;
    }

    /** Un texto obligatorio de una línea: sin caracteres de control y sin marcado (< o >). */
    public static String plain(JsonNode node, String name, int max) {
        String text = required(node, name, max, false);
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) throw new IllegalArgumentException();
        return text;
    }

    /** Un texto obligatorio que puede tener varias líneas (enunciados, soluciones). */
    public static String multiline(JsonNode node, String name, int max) {
        return required(node, name, max, true);
    }

    /** Un texto que puede faltar (se devuelve vacío), pero si está debe cumplir los mismos límites. */
    public static String optionalMultiline(JsonNode node, String name, int max) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) return "";
        return required(node, name, max, true);
    }

    private static String required(JsonNode node, String name, int max, boolean newlines) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException();
        String text = value.asText().strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > max) throw new IllegalArgumentException();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = newlines && (c == '\n' || c == '\t');
            if (Character.isISOControl(c) && !allowed) throw new IllegalArgumentException();
        }
        return text;
    }

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
