package es.vicai.studylab.material.chunk;

import es.vicai.studylab.material.MaterialProperties;
import es.vicai.studylab.material.extract.ExtractedText;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Trocea el texto en fragmentos de tamaño acotado, con solapamiento y cortando donde sea natural (párrafo, frase, palabra). Cada
 * fragmento guarda su desplazamiento y su página para poder citarlo. Nunca parte un par suplente (emojis) por la mitad.
 */
@Component
public class Chunker {

    private final int size;
    private final int overlap;

    public Chunker(MaterialProperties props) {
        this.size = Math.max(20, props.chunkChars());
        this.overlap = Math.max(0, Math.min(props.chunkOverlap(), this.size / 2));
    }

    public List<Chunk> split(ExtractedText extracted) {
        String text = extracted.text();
        int length = text.length();
        List<Chunk> chunks = new ArrayList<>();
        int position = 0;
        while (position < length) {
            int start = skipWhitespace(text, position);
            if (start >= length) break;
            int limit = Math.min(length, start + size);
            int end = limit >= length ? length : cutPoint(text, start, limit);
            end = trimEnd(text, start, end);
            if (end > start) {
                chunks.add(new Chunk(chunks.size(), extracted.pageAt(start), start, end, text.substring(start, end)));
            }
            if (limit >= length) break; // este fragmento ya llega al final del texto
            position = nextStart(text, start, end);
        }
        return chunks;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private static int trimEnd(String text, int start, int end) {
        int i = end;
        while (i > start && Character.isWhitespace(text.charAt(i - 1))) i--;
        if (i > start && Character.isHighSurrogate(text.charAt(i - 1))) i--;
        return i;
    }

    /** Mejor punto de corte entre la mitad del fragmento y su límite: párrafo, luego frase, luego palabra, y si no, el límite. */
    private int cutPoint(String text, int start, int limit) {
        int floor = start + size / 2;
        int paragraph = text.lastIndexOf("\n\n", limit - 1);
        if (paragraph >= floor) return paragraph;
        int sentence = -1;
        for (int i = limit - 1; i >= floor; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                sentence = i + 1;
                break;
            }
        }
        if (sentence > 0) return sentence;
        for (int i = limit - 1; i >= floor; i--) {
            if (Character.isWhitespace(text.charAt(i))) return i;
        }
        return Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
    }

    /** Siguiente inicio: retrocede {@code overlap} caracteres, busca el inicio de palabra más cercano y siempre avanza. */
    private int nextStart(String text, int start, int end) {
        int next = Math.max(start + 1, end - overlap);
        if (overlap > 0) {
            for (int i = next; i < end; i++) {
                if (Character.isWhitespace(text.charAt(i))) {
                    next = i + 1;
                    break;
                }
            }
        }
        if (next < text.length() && Character.isLowSurrogate(text.charAt(next))) next++;
        return Math.min(next, Math.max(end, start + 1));
    }
}
