package es.vicai.studylab.ai.provider;

import java.time.Duration;

/**
 * Una petición de chat ya ensamblada. {@code instructions} es la plantilla fija y {@code dataMessage} los datos del usuario,
 * siempre separados: los adaptadores los envían en campos distintos (system/user).
 */
public record ChatCall(String model, String instructions, String dataMessage, int maxOutputTokens, Duration timeout) {}
