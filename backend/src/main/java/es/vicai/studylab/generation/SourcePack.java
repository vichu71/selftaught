package es.vicai.studylab.generation;

import es.vicai.studylab.material.Fragment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Los fragmentos de material que se mandan al modelo para escribir una lección (ADR-46): numerados desde 1 para poder citarlos, solo su texto
 * (nunca el nombre del documento) y, para un menor, sin los que el filtro rechaza. Guarda el mapa {@code [n] → documento y página}.
 */
public final class SourcePack {

    public static final int MAX_FRAGMENTS = 4;

    public record Source(int n, UUID documentId, Integer page) {}

    private final List<Source> sources;
    private final String text;

    private SourcePack(List<Source> sources, String text) {
        this.sources = sources;
        this.text = text;
    }

    /** @param accepts decide si un texto puede enviarse al modelo (para un menor, el filtro de contenido) */
    public static SourcePack of(List<Fragment> fragments, Predicate<String> accepts) {
        List<Source> sources = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Fragment fragment : fragments) {
            if (sources.size() >= MAX_FRAGMENTS) break;
            if (!accepts.test(fragment.text())) continue;
            int n = sources.size() + 1;
            sources.add(new Source(n, fragment.documentId(), fragment.page()));
            if (text.length() > 0) text.append("\n\n");
            text.append('[').append(n).append("] ").append(fragment.text());
        }
        return new SourcePack(List.copyOf(sources), text.toString());
    }

    public int size() {
        return sources.size();
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }

    /** El texto numerado, tal cual irá en el dato {@code fuentes}. */
    public String text() {
        return text;
    }

    public List<Source> sources() {
        return sources;
    }
}
