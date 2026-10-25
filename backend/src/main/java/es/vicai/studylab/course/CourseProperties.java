package es.vicai.studylab.course;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Límites del asistente de creación de curso, por usuario (A2, ADR-41). Los dos primeros llegan por entorno
 * (COURSE_INTAKES_PER_HOUR, COURSE_MAX_PENDING); la caducidad de una consulta es un valor de ingeniería.
 */
@ConfigurationProperties(prefix = "app.course")
public record CourseProperties(
        @DefaultValue("30") int intakesPerHour,
        @DefaultValue("5") int maxPending,
        @DefaultValue("1h") Duration intakeTtl) {

    public static CourseProperties defaults() {
        return new CourseProperties(30, 5, Duration.ofHours(1));
    }
}
