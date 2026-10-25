package es.vicai.studylab.generation;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valida la lección del modelo (ADR-43, 46): Markdown de longitud acotada, con algo de estructura, sin HTML crudo ni enlaces peligrosos, y con citas
 * {@code [n]} que existan. El visor del frontend no pinta HTML aunque se colara (ADR-45): esto es la primera capa, no la única.
 * Devuelve vacío ante cualquier incumplimiento; nunca lanza.
 */
@Component
public class LessonValidator {

    public static final int MIN_CHARS = 400;
    public static final int MAX_CHARS = 30_000;

    public record Lesson(String markdown, List<Integer> citations) {}

    private static final Pattern WRAPPING_FENCE = Pattern.compile("\\A```(?:markdown|md)?[ \\t]*\\R(.*)\\R```\\z", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCED_CODE = Pattern.compile("(?s)```.*?```");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");
    private static final Pattern HTML_TAG = Pattern.compile("<(?:/?[a-zA-Z]|[!?])");
    private static final Pattern DANGEROUS_LINK = Pattern.compile("(?i)\\]\\(\\s*<?\\s*(javascript|data|vbscript|file)\\s*:");
    private static final Pattern DANGEROUS_REFERENCE = Pattern.compile("(?im)^\\s{0,3}\\[[^\\]]+\\]:\\s*<?\\s*(javascript|data|vbscript|file)\\s*:");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+\\S");
    private static final Pattern BLANK_LINE = Pattern.compile("\\R\\s*\\R");
    private static final Pattern LINK = Pattern.compile("\\[(?:[^\\[\\]]|\\[[^\\]]*\\])*\\]\\([^)\\n]*\\)");
    private static final Pattern CITATION = Pattern.compile("(?<![!\\\\])\\[(\\d{1,3})\\](?![(:])");

    public Optional<Lesson> validate(String raw, int fragmentCount) {
        try {
            return Optional.of(doValidate(raw, fragmentCount));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Lesson doValidate(String raw, int fragmentCount) {
        if (raw == null) throw new IllegalArgumentException();
        String text = raw.strip();
        Matcher wrapped = WRAPPING_FENCE.matcher(text);
        if (wrapped.matches()) text = wrapped.group(1).strip();
        if (text.length() < MIN_CHARS || text.length() > MAX_CHARS) throw new IllegalArgumentException();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') throw new IllegalArgumentException();
        }

        // Lo que hay dentro de bloques y fragmentos de código se muestra como texto: no cuenta como HTML ni como cita
        String prose = INLINE_CODE.matcher(FENCED_CODE.matcher(text).replaceAll(" ")).replaceAll(" ");
        if (HTML_TAG.matcher(prose).find()) throw new IllegalArgumentException();
        if (DANGEROUS_LINK.matcher(prose).find() || DANGEROUS_REFERENCE.matcher(prose).find()) throw new IllegalArgumentException();
        if (!HEADING.matcher(prose).find() && paragraphs(prose) < 2) throw new IllegalArgumentException();

        TreeSet<Integer> cited = new TreeSet<>();
        Matcher m = CITATION.matcher(LINK.matcher(prose).replaceAll(" "));
        while (m.find()) cited.add(Integer.parseInt(m.group(1)));
        if (fragmentCount == 0) {
            if (!cited.isEmpty()) throw new IllegalArgumentException(); // sin material no hay nada que citar
        } else {
            if (cited.isEmpty() || cited.first() < 1 || cited.last() > fragmentCount) throw new IllegalArgumentException();
        }
        return new Lesson(text, List.copyOf(cited));
    }

    private static int paragraphs(String text) {
        int count = 0;
        for (String block : BLANK_LINE.split(text.strip())) {
            if (!block.isBlank()) count++;
        }
        return count;
    }
}
