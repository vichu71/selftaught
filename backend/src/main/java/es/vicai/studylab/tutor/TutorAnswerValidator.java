package es.vicai.studylab.tutor;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valida la respuesta del modelo al tutor (ADR-58): texto no vacío y acotado a 4 000 caracteres (si se pasa, se corta), sin HTML crudo ni enlaces
 * peligrosos y con citas {@code [n]} que existan. El visor del frontend no pinta HTML aunque se colara: esta es la primera capa. Devuelve vacío ante
 * cualquier incumplimiento; nunca lanza.
 */
@Component
public class TutorAnswerValidator {

    public static final int MAX_CHARS = 4000;

    public record Answer(String text, List<Integer> citations) {}

    private static final Pattern FENCED_CODE = Pattern.compile("(?s)```.*?```");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");
    private static final Pattern HTML_TAG = Pattern.compile("<(?:/?[a-zA-Z]|[!?])");
    private static final Pattern DANGEROUS_LINK = Pattern.compile("(?i)\\]\\(\\s*<?\\s*(javascript|data|vbscript|file)\\s*:");
    private static final Pattern LINK = Pattern.compile("\\[(?:[^\\[\\]]|\\[[^\\]]*\\])*\\]\\([^)\\n]*\\)");
    private static final Pattern CITATION = Pattern.compile("(?<![!\\\\])\\[(\\d{1,3})\\](?![(:])");

    public Optional<Answer> validate(String raw, int fragmentCount) {
        try {
            return Optional.of(doValidate(raw, fragmentCount));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Por qué no sirve una respuesta (solo la categoría, nunca el texto), para el registro; {@code null} si sirve. */
    public String reasonFor(String raw, int fragmentCount) {
        try {
            doValidate(raw, fragmentCount);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return "ERROR";
        }
    }

    private Answer doValidate(String raw, int fragmentCount) {
        if (raw == null) throw new IllegalArgumentException("EMPTY");
        String text = raw.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("EMPTY");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') throw new IllegalArgumentException("CONTROL");
        }

        // Lo que hay dentro de código se muestra como texto: no cuenta como HTML ni como cita
        String prose = INLINE_CODE.matcher(FENCED_CODE.matcher(text).replaceAll(" ")).replaceAll(" ");
        if (HTML_TAG.matcher(prose).find()) throw new IllegalArgumentException("HTML");
        if (DANGEROUS_LINK.matcher(prose).find()) throw new IllegalArgumentException("LINK");

        TreeSet<Integer> cited = new TreeSet<>();
        Matcher m = CITATION.matcher(LINK.matcher(prose).replaceAll(" "));
        while (m.find()) cited.add(Integer.parseInt(m.group(1)));
        if (fragmentCount == 0 ? !cited.isEmpty() : !cited.isEmpty() && (cited.first() < 1 || cited.last() > fragmentCount)) throw new IllegalArgumentException("CITATION");

        String bounded = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        return new Answer(bounded, List.copyOf(cited));
    }
}
