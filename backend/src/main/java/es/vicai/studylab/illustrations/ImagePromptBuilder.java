package es.vicai.studylab.illustrations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * El prompt de la imagen (ADR-81): la plantilla fija {@code prompts/illustration.txt} (estilo común y reglas, sin marcadores para texto del usuario) seguida del
 * motivo (título y resumen del tema) ya saneado, acotado y entre comillas. Las APIs de imagen no separan instrucciones y datos, así que el motivo va siempre
 * <b>después</b> de la plantilla y sin nada que pueda cerrar sus comillas. Si el motivo no sobrevive al saneado no hay prompt y por tanto no hay llamada.
 * El texto alternativo es determinista: el modelo de texto no ve la imagen y no podría describirla de verdad.
 */
@Component
public class ImagePromptBuilder {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
    private static final Pattern MARKUP = Pattern.compile("[<>`{}\\[\\]\\\\|«»]");
    private static final Pattern QUOTES = Pattern.compile("[\"“”„]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int MIN_TITLE = 3;

    private final IllustrationProperties props;
    private final String template;

    public ImagePromptBuilder(IllustrationProperties props) {
        this.props = props;
        this.template = load();
    }

    private static String load() {
        try (InputStream in = new ClassPathResource("prompts/illustration.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Falta la plantilla prompts/illustration.txt");
        }
    }

    public Optional<String> build(String title, String summary) {
        String cleanTitle = clean(title, props.maxTitleChars());
        if (cleanTitle.codePointCount(0, cleanTitle.length()) < MIN_TITLE) return Optional.empty();
        String cleanSummary = clean(summary, props.maxSummaryChars());
        String subject = cleanSummary.isEmpty() ? cleanTitle : cleanTitle + ". " + cleanSummary;
        return Optional.of(template + "\n\nMotivo: \"" + subject + "\"");
    }

    /** «Ilustración generada por IA sobre «título»»: el mismo texto para el mismo tema, sin llamar a nadie. */
    public String altText(String title) {
        return "Ilustración generada por IA sobre «" + clean(title, props.maxTitleChars()) + "»";
    }

    /** Sin caracteres de control ni de formato, sin marcas ni comillas, con los espacios colapsados y acotado a {@code max} caracteres. */
    static String clean(String raw, int max) {
        if (raw == null) return "";
        String text = CONTROL.matcher(raw).replaceAll(" ");
        text = MARKUP.matcher(text).replaceAll(" ");
        text = QUOTES.matcher(text).replaceAll("'");
        text = SPACES.matcher(text).replaceAll(" ").strip();
        if (text.codePointCount(0, text.length()) > max) text = text.substring(0, text.offsetByCodePoints(0, max)).strip();
        return text;
    }
}
