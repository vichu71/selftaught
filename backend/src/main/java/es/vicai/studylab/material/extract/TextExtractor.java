package es.vicai.studylab.material.extract;

/** Extrae el texto de un documento ya clasificado por {@link FileTypeDetector}. Lanza {@link ExtractionException} con un motivo genérico. */
public interface TextExtractor {

    ExtractedText extract(byte[] content);
}
