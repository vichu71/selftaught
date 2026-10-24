package es.vicai.studylab.ai;

/**
 * Error de la capa de IA. El mensaje es siempre genérico y en castellano; nunca lleva el mensaje del proveedor
 * (puede repetir el prompt) y se construye sin causa por el mismo motivo.
 */
public class AiException extends RuntimeException {

    private final AiErrorKind kind;

    public AiException(AiErrorKind kind, String message) {
        super(message, null, false, false); // sin causa ni traza: no hay nada del proveedor que filtrar
        this.kind = kind;
    }

    public AiErrorKind kind() {
        return kind;
    }

    public static AiException of(AiErrorKind kind) {
        return new AiException(kind, switch (kind) {
            case BUDGET_BLOCKED -> "Se ha alcanzado el tope de gasto mensual de IA";
            case PROVIDER_UNAVAILABLE -> "El servicio de IA no está disponible ahora mismo. Inténtalo más tarde";
            case PROVIDER_QUOTA -> "El proveedor de IA ha agotado su cuota o su tope de gasto";
            case PROVIDER_AUTH -> "El proveedor de IA rechaza las credenciales configuradas";
            case PROVIDER_REJECTED -> "El proveedor de IA ha rechazado la petición";
            case INVALID_RESPONSE -> "El proveedor de IA devolvió una respuesta no válida";
            case INPUT_TOO_LARGE -> "El texto enviado a la IA es demasiado largo";
            case USER_NOT_ALLOWED -> "Este usuario no puede usar la IA";
            case GENERATION_BUSY -> "Ya hay una generación en curso";
            case CONFIG -> "La IA no está bien configurada";
        });
    }
}
