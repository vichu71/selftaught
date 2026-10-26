package es.vicai.studylab.share;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros de compartir (F09): valores de ingeniería sin variable de entorno. Caducidad por defecto y máxima de un código, códigos activos por curso, y los límites por
 * usuario y hora de canjes fallidos y de copias (contados en la BD, no por IP: D1) (ADR-64).
 */
@ConfigurationProperties(prefix = "app.share")
public record ShareProperties(
        @DefaultValue("7") int defaultDays,
        @DefaultValue("90") int maxDays,
        @DefaultValue("5") int maxActive,
        @DefaultValue("10") int maxFailuresPerHour,
        @DefaultValue("10") int maxCopiesPerHour) {}
