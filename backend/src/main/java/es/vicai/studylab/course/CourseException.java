package es.vicai.studylab.course;

import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados del asistente: estado, código y mensaje genéricos y en castellano. Nunca revelan el motivo de un bloqueo. */
public final class CourseException {

    /** El mensaje acordado para un menor cuyo tema no se crea. Sin categoría ni motivo. */
    public static final String BLOCKED_MESSAGE = "Contenido inapropiado. Consulta con tu adulto.";

    private CourseException() {}

    public static ApiException topicNotAllowed() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPIC_NOT_ALLOWED", BLOCKED_MESSAGE);
    }

    public static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Has hecho muchas consultas seguidas. Espera un rato e inténtalo de nuevo");
    }

    public static ApiException courseLimitReached(int max) {
        return new ApiException(HttpStatus.CONFLICT, "COURSE_LIMIT_REACHED",
                "Tienes " + max + " cursos pendientes de preparar. Espera a que terminen para crear otro");
    }

    public static ApiException intakeExpired() {
        return new ApiException(HttpStatus.GONE, "INTAKE_EXPIRED", "La consulta ha caducado. Empieza de nuevo");
    }
}
