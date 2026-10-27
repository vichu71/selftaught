package es.vicai.studylab.ai;

/** Motivo por el que una operación de IA falla, tal como lo ve la feature (genérico: nunca el mensaje del proveedor). */
public enum AiErrorKind {
    /** Modo BLOCK: el tope mensual se ha alcanzado o esta llamada lo superaría. */
    BUDGET_BLOCKED,
    /** El usuario alcanzó su tope mensual personal (A1), o esta llamada lo superaría. Corta también en modo WARN. */
    USER_LIMIT,
    /** El proveedor no respondió bien tras los reintentos permitidos. */
    PROVIDER_UNAVAILABLE,
    /** El proveedor dice que se agotó la cuota o el tope de gasto de su cuenta. */
    PROVIDER_QUOTA,
    /** Clave inválida o sin permiso. */
    PROVIDER_AUTH,
    /** Petición o modelo rechazados por el proveedor. */
    PROVIDER_REJECTED,
    /** La respuesta no tiene el formato esperado. */
    INVALID_RESPONSE,
    INPUT_TOO_LARGE,
    USER_NOT_ALLOWED,
    /** Ya hay una generación en curso para este usuario. */
    GENERATION_BUSY,
    /** El proveedor rechaza la imagen por su moderación: no es un error técnico y no se reintenta (F12). */
    MODERATED,
    CONFIG
}
