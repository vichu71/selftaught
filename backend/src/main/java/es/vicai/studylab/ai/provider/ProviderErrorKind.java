package es.vicai.studylab.ai.provider;

/** Clasificación de los fallos de un proveedor. Solo los transitorios se reintentan (ADR-23). */
public enum ProviderErrorKind {
    TIMEOUT(true),
    RATE_LIMITED(true),
    SERVER_ERROR(true),
    UNAVAILABLE(true),
    /** Cuota o tope de gasto de la cuenta del proveedor agotados: reintentar no sirve. */
    QUOTA_EXCEEDED(false),
    AUTH(false),
    BAD_REQUEST(false),
    MODEL_NOT_FOUND(false),
    INVALID_RESPONSE(false);

    private final boolean retryable;

    ProviderErrorKind(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
