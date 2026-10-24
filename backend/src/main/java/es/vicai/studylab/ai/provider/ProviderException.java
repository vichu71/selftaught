package es.vicai.studylab.ai.provider;

/**
 * Fallo de un proveedor. El mensaje lo escribe el adaptador y es genérico: jamás copia el cuerpo del error del proveedor
 * (puede repetir el prompt) ni la clave.
 */
public class ProviderException extends RuntimeException {

    private final ProviderErrorKind kind;

    public ProviderException(ProviderErrorKind kind, String message) {
        super(message, null, false, false);
        this.kind = kind;
    }

    public ProviderErrorKind kind() {
        return kind;
    }

    public boolean retryable() {
        return kind.retryable();
    }
}
