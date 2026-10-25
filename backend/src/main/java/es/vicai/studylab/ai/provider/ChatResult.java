package es.vicai.studylab.ai.provider;

import es.vicai.studylab.ai.FinishReason;

/** Respuesta de un proveedor de chat. Si el proveedor no informa de los tokens, {@code tokensEstimated} es true y valen 0. */
public record ChatResult(String text, int inputTokens, int outputTokens, boolean tokensEstimated, FinishReason finish) {}
