package es.vicai.studylab.ai;

import java.util.UUID;

/**
 * Lo que una feature pide al proveedor de imagen (F12). El {@code prompt} lo construye la propia feature con su plantilla y el motivo saneado (ADR-81); la capa
 * no lo registra, no lo escribe en logs y no lo incluye en ninguna excepción.
 */
public final class AiImageRequest {

    private final UUID userId;
    private final UUID courseId;
    private final String prompt;

    private AiImageRequest(UUID userId, UUID courseId, String prompt) {
        this.userId = userId;
        this.courseId = courseId;
        this.prompt = prompt;
    }

    /** Petición de un usuario: toda llamada se registra a su nombre. */
    public static AiImageRequest of(UUID userId, UUID courseId, String prompt) {
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        return create(userId, courseId, prompt);
    }

    /** Petición del benchmark: sin usuario, se registra como BENCHMARK. */
    public static AiImageRequest benchmark(String prompt) {
        return create(null, null, prompt);
    }

    private static AiImageRequest create(UUID userId, UUID courseId, String prompt) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("El prompt es obligatorio");
        return new AiImageRequest(userId, courseId, prompt);
    }

    public UUID userId() {
        return userId;
    }

    public UUID courseId() {
        return courseId;
    }

    public String prompt() {
        return prompt;
    }

    public boolean isBenchmark() {
        return userId == null;
    }

    @Override
    public String toString() {
        return "AiImageRequest[user=" + userId + ", course=" + courseId + "]";
    }
}
