package es.vicai.studylab.material;

/** Tipos de documento admitidos. Se deciden por el contenido del fichero, nunca por su extensión (ADR-31). */
public enum MaterialType {
    PDF,
    DOCX,
    TEXT,
    /** Foto de apuntes en JPEG o PNG (F19): su texto lo lee un modelo de visión. */
    IMAGE
}
