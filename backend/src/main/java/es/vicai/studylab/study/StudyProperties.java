package es.vicai.studylab.study;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros del estudio (F06): valores de ingeniería sin variable de entorno. La nota mínima del examen, los envíos por hora y por usuario entre ejercicios y
 * exámenes, las correcciones de IA por día y por usuario y los intentos que se conservan de cada pieza (ADR-51 a 53). Los envíos por hora se cuentan sobre los
 * intentos conservados: {@code maxSubmissionsPerHour} no debe superar a {@code maxAttemptsPerPiece} o la poda dejaría el límite sin efecto.
 */
@ConfigurationProperties(prefix = "app.study")
public record StudyProperties(
        @DefaultValue("50") int passPercent,
        @DefaultValue("120") int maxSubmissionsPerHour,
        @DefaultValue("20") int aiGradesPerDay,
        @DefaultValue("200") int maxAttemptsPerPiece) {}
