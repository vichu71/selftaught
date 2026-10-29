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
    EMBEDDING,
    /** Asistente de creación de curso: valora el tema y decide de 0 a 3 preguntas (F04). */
    INTAKE,
    /** Ilustración de portada de un tema (F12): tarea opcional; sin proveedor configurado las ilustraciones quedan desactivadas. */
    IMAGE,
    /** Lectura de fotos de apuntes (F19): tarea opcional; sin proveedor configurado las fotos quedan desactivadas. */
    OCR
}
