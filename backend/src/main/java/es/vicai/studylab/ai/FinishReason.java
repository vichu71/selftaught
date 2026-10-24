package es.vicai.studylab.ai;

public enum FinishReason {
    /** El modelo terminó por sí mismo. */
    STOP,
    /** Se cortó por el límite de tokens de salida: el texto está incompleto. */
    LENGTH,
    /** Cualquier otro motivo (filtro de seguridad, rechazo…). */
    OTHER
}
