package es.vicai.studylab.material.extract;

/** Fallo de extracción con un motivo genérico. Sin causa ni traza: nada de la librería llega al usuario ni a los logs. */
public class ExtractionException extends RuntimeException {

    private final ExtractionError kind;

    public ExtractionException(ExtractionError kind) {
        super(kind.message(), null, false, false);
        this.kind = kind;
    }

    public ExtractionError kind() {
        return kind;
    }
}
