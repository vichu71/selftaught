package es.vicai.studylab.courseedit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Parámetros de la edición (F09): valores de ingeniería sin variable de entorno. Añadir con IA y regenerar cuentan para un límite por hora y usuario (ADR-63). */
@ConfigurationProperties(prefix = "app.course-edit")
public record CourseEditProperties(@DefaultValue("20") int aiActionsPerHour) {}
