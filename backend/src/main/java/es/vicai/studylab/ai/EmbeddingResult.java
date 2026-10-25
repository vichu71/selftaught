package es.vicai.studylab.ai;

import java.util.List;

/** Un vector por texto, en el mismo orden, todos de la dimensión configurada. */
public record EmbeddingResult(List<float[]> vectors, int inputTokens) {}
