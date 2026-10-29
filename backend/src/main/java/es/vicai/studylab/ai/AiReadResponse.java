package es.vicai.studylab.ai;

import java.math.BigDecimal;
import java.util.UUID;

/** La salida de una lectura tal como la devuelve el proveedor, aún sin validar (eso lo hace la feature con su parser, ADR-101), con su coste. */
public record AiReadResponse(String raw, BigDecimal costUsd, String provider, String model, UUID requestId, long durationMs) {

    @Override
    public String toString() {
        return "AiReadResponse[" + provider + "/" + model + ", " + costUsd + " USD]"; // nunca el texto leído
    }
}
