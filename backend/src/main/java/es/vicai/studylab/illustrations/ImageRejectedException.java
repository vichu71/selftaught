package es.vicai.studylab.illustrations;

/** La imagen del proveedor no pasa la comprobación (ADR-80). Solo lleva el motivo: nunca el contenido de la imagen. */
public class ImageRejectedException extends RuntimeException {

    public enum Reason { EMPTY, TOO_BIG, TYPE, PIXELS, TOO_SMALL, DECODE }

    private final Reason reason;

    public ImageRejectedException(Reason reason) {
        super("Imagen no válida: " + reason, null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
