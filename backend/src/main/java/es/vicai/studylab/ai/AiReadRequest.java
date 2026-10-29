package es.vicai.studylab.ai;

import java.util.UUID;

/**
 * Lo que una feature pide a la tarea OCR (F19): una imagen ya re-codificada y la audiencia (adulto o menor) como único dato. La plantilla es fija
 * ({@code prompts/read-image.txt}); la capa no registra la imagen, no la escribe en logs y no la incluye en ninguna excepción.
 */
public final class AiReadRequest {

    private final UUID userId;
    private final byte[] image;
    private final String mime;
    private final String audience;

    private AiReadRequest(UUID userId, byte[] image, String mime, String audience) {
        this.userId = userId;
        this.image = image;
        this.mime = mime;
        this.audience = audience;
    }

    /** Petición de un usuario: toda llamada se registra a su nombre. {@code audience} es {@code adulto} o {@code menor}. */
    public static AiReadRequest of(UUID userId, byte[] image, String mime, String audience) {
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        if (image == null || image.length == 0) throw new IllegalArgumentException("La imagen es obligatoria");
        if (mime == null || mime.isBlank()) throw new IllegalArgumentException("El tipo de la imagen es obligatorio");
        if (!"adulto".equals(audience) && !"menor".equals(audience)) throw new IllegalArgumentException("Audiencia no válida");
        return new AiReadRequest(userId, image, mime, audience);
    }

    public UUID userId() {
        return userId;
    }

    public byte[] image() {
        return image;
    }

    public String mime() {
        return mime;
    }

    public String audience() {
        return audience;
    }

    @Override
    public String toString() {
        return "AiReadRequest[user=" + userId + "]"; // nunca la imagen ni la audiencia
    }
}
