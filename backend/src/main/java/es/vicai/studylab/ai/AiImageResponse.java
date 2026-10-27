package es.vicai.studylab.ai;

import java.math.BigDecimal;
import java.util.UUID;

/** Una imagen del proveedor con su coste, tal como la devuelve el proveedor (aún sin validar: eso lo hace la feature, ADR-80). */
public record AiImageResponse(byte[] bytes, String mime, BigDecimal costUsd, String provider, String model, UUID requestId, long durationMs) {

    @Override
    public String toString() {
        return "AiImageResponse[" + (bytes == null ? 0 : bytes.length) + " bytes, " + provider + "/" + model + ", " + costUsd + " USD]";
    }
}
