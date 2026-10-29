package es.vicai.studylab.material.image;

/** Una foto que no se admite: {@code kind} es {@code IMAGE_INVALID} o {@code IMAGE_TOO_LARGE}. Sin mensaje de librería ni datos de la imagen. */
public final class PhotoRejectedException extends RuntimeException {

    public static final String INVALID = "IMAGE_INVALID";
    public static final String TOO_LARGE = "IMAGE_TOO_LARGE";

    private final String kind;

    public PhotoRejectedException(String kind) {
        super(kind, null, false, false);
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }
}
