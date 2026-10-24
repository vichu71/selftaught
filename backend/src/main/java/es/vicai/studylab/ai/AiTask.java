package es.vicai.studylab.ai;

/** Tareas de IA que piden las features. Qué proveedor y modelo las atienden es solo configuración. */
public enum AiTask {
    /** Ficha del curso y temario. */
    OUTLINE,
    /** Lecciones. */
    LESSON,
    /** Ejercicios y exámenes. */
    ASSESSMENT,
    /** Chat del tutor IA. */
    TUTOR,
    /** Vectores para la búsqueda en el material propio. */
    EMBEDDING
}
