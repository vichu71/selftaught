package es.vicai.studylab.ai;

import java.math.BigDecimal;
import java.util.UUID;

/** Resultado de una llamada de chat. {@code costUsd} es una estimación con la tabla de precios (ADR-21). */
public record AiResponse(String text, int inputTokens, int outputTokens, boolean tokensEstimated, BigDecimal costUsd,
                         FinishReason finishReason, String provider, String model, UUID requestId, long durationMs) {

    @Override
    public String toString() {
        return "AiResponse[provider=" + provider + ", model=" + model + ", finish=" + finishReason + "]"; // sin el texto
    }
}
