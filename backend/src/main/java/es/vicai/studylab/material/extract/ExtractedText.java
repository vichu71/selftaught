package es.vicai.studylab.material.extract;

import java.util.List;

/**
 * Texto extraído de un documento. En un PDF, {@code pageStarts} guarda el desplazamiento donde empieza cada página (la primera,
 * en la posición 0); en el resto de tipos no hay páginas.
 */
public record ExtractedText(String text, Integer pageCount, List<Integer> pageStarts) {

    public ExtractedText {
        pageStarts = pageStarts == null ? List.of() : List.copyOf(pageStarts);
    }

    /** Número de página (desde 1) en la que cae un desplazamiento, o {@code null} si el documento no tiene páginas. */
    public Integer pageAt(int offset) {
        if (pageCount == null || pageStarts.isEmpty()) return null;
        int page = 1;
        for (int i = 0; i < pageStarts.size(); i++) {
            if (pageStarts.get(i) <= offset) page = i + 1;
            else break;
        }
        return page;
    }
}
