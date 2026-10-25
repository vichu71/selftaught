package es.vicai.studylab.ai.provider;

import java.util.List;

public record EmbedResult(List<float[]> vectors, int inputTokens) {}
