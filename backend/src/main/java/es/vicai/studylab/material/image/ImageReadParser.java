package es.vicai.studylab.material.image;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * La salida del modelo que lee una foto es entrada no fiable (ADR-101): se acepta solo un sobre JSON {@code {"appropriate": bool, "text": "..."}} de forma conocida.
 * Devuelve vacío ante cualquier otra cosa y nunca lanza. El texto sale sin caracteres de control ni HTML crudo, con el espacio normalizado y acotado; sigue siendo
 * un dato no fiable que las features solo lo pasan al modelo como dato etiquetado.
 */
public final class ImageReadParser {

    /** {@code text} puede quedar vacío (foto sin texto o contenido no apto): quien llama decide qué significa. */
    public record ReadResult(boolean appropriate, String text) {}

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, false);
    private static final Pattern FENCE = Pattern.compile("^```[a-zA-Z]*\\s*(.*?)\\s*```$", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("</?[a-zA-Z!][^>]*>");
    private static final Pattern DANGLING_TAG = Pattern.compile("<(?=[a-zA-Z/!])");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern MANY_BREAKS = Pattern.compile("\\n{3,}");

    private ImageReadParser() {}

    public static Optional<ReadResult> parse(String raw, int maxChars) {
        try {
            if (raw == null || raw.isBlank() || maxChars <= 0) return Optional.empty();
            String body = raw.strip();
            java.util.regex.Matcher fenced = FENCE.matcher(body);
            if (fenced.matches()) body = fenced.group(1);

            JsonNode root = JSON.readTree(body);
            if (root == null || !root.isObject()) return Optional.empty();
            JsonNode appropriate = root.get("appropriate");
            JsonNode text = root.get("text");
            if (appropriate == null || !appropriate.isBoolean() || text == null || !text.isTextual()) return Optional.empty();
            return Optional.of(new ReadResult(appropriate.booleanValue(), clean(text.textValue(), maxChars)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String clean(String value, int maxChars) {
        String text = Normalizer.normalize(value, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder printable = new StringBuilder(text.length());
        text.codePoints().filter(cp -> cp == '\n' || cp == '\t' || (!Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT))
                .forEach(printable::appendCodePoint);
        String plain = TAG.matcher(printable).replaceAll(" ");
        plain = DANGLING_TAG.matcher(plain).replaceAll("");
        plain = SPACES.matcher(plain).replaceAll(" ");
        plain = plain.lines().map(String::strip).collect(java.util.stream.Collectors.joining("\n"));
        plain = MANY_BREAKS.matcher(plain).replaceAll("\n\n").strip();
        if (plain.codePoints().noneMatch(Character::isLetterOrDigit)) return ""; // «.», «—»: no es una lectura
        if (plain.length() > maxChars) {
            int end = maxChars;
            if (Character.isHighSurrogate(plain.charAt(end - 1))) end--;
            plain = plain.substring(0, end).strip();
        }
        return plain;
    }
}
