package es.vicai.studylab.material.chunk;

/** Un fragmento del texto de un documento, con la posición exacta para poder citarlo. */
public record Chunk(int ordinal, Integer page, int startOffset, int endOffset, String text) {}
