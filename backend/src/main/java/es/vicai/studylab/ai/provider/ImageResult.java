package es.vicai.studylab.ai.provider;

/** Lo que devuelve un proveedor de imagen: los bytes tal cual (entrada no fiable, ADR-80) y el tipo que dice tener. */
public record ImageResult(byte[] bytes, String mime) {

    @Override
    public String toString() {
        return "ImageResult[" + (bytes == null ? 0 : bytes.length) + " bytes, " + mime + "]";
    }
}
