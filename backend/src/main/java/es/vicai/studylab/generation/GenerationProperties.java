package es.vicai.studylab.generation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros de la generación del curso. Son valores de ingeniería sin variable de entorno (ver el README): el hilo de fondo (que los tests apagan),
 * el máximo de piezas por curso y el enfriamiento de «Reanudar» (ADR-42, 44).
 */
@ConfigurationProperties(prefix = "app.generation")
public record GenerationProperties(
        @DefaultValue("150") int maxPieces,
        @DefaultValue("60s") Duration resumeCooldown,
        @DefaultValue Worker worker) {

    /** Hilos que vacían la cola y cada cuánto la miran. */
    public record Worker(@DefaultValue("true") boolean enabled, @DefaultValue("3s") Duration pollInterval, @DefaultValue("2") int threads) {}
}
