package es.vicai.studylab.safety;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Capa local del filtro de contenido para menores (ADR-39, cierra A3). Responde sin llamar a ningún modelo y sin coste. Se aplica a
 * un MENOR antes de enviar su texto a un modelo de pago: F04 la usa para el tema y F05 y F07 la usarán para lo suyo.
 *
 * <p>Normaliza el texto (sin tildes ni mayúsculas, dígitos y símbolos habituales cambiados por letras, letras sueltas separadas por
 * espacios o signos unidas) y busca frases de un fichero de recursos. Son frases y no palabras sueltas para que los temas escolares
 * delicados (guerras, reproducción, drogas y salud) pasen. No es una moderación completa: la valoración del modelo y el tutor son las otras capas.
 */
@Component
public class ContentGuard {

    private static final String RESOURCE = "safety/blocked-terms.txt";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final int MIN_SINGLE_LETTER_RUN = 3;

    private record Term(BlockCategory category, String text) {}

    private final List<Term> terms;

    public ContentGuard() {
        this.terms = load();
    }

    /** La categoría del primer término que coincide, o vacío si el texto es aceptable (o está vacío). */
    public Optional<BlockCategory> localCheck(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String haystack = " " + normalise(text) + " ";
        for (Term term : terms) {
            if (haystack.contains(term.text())) return Optional.of(term.category());
        }
        return Optional.empty();
    }

    /** Minúsculas sin tildes, dígitos y símbolos habituales sustituidos, y una sola cadena de palabras separadas por un espacio. */
    static String normalise(String text) {
        String lowered = DIACRITICS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
        StringBuilder spelled = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            spelled.append(switch (c) {
                case '0' -> 'o';
                case '1', '!' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }
        // Palabras: letras y dígitos restantes; todo lo demás separa
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < spelled.length(); i++) {
            char c = spelled.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) words.add(current.toString());

        // Una racha de 3 o más letras sueltas («p o r n o», «p.o.r.n.o») se une en una sola palabra
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < words.size()) {
            int j = i;
            while (j < words.size() && words.get(j).length() == 1) j++;
            if (j - i >= MIN_SINGLE_LETTER_RUN) {
                for (int k = i; k < j; k++) out.append(words.get(k));
                out.append(' ');
                i = j;
            } else {
                out.append(words.get(i)).append(' ');
                i++;
            }
        }
        return out.toString().strip();
    }

    private static List<Term> load() {
        InputStream in = ContentGuard.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) throw new IllegalStateException("Falta el fichero de recursos " + RESOURCE);
        List<Term> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int colon = trimmed.indexOf(':');
                if (colon < 1) throw new IllegalStateException("Línea no válida en " + RESOURCE);
                BlockCategory category = BlockCategory.valueOf(trimmed.substring(0, colon).strip());
                String phrase = normalise(trimmed.substring(colon + 1));
                if (!phrase.isEmpty()) loaded.add(new Term(category, phrase));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(loaded);
    }
}
