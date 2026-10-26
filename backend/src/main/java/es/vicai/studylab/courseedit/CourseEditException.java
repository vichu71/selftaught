package es.vicai.studylab.courseedit;

import es.vicai.studylab.course.CourseException;
import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados de la edición: estado, código y mensaje genéricos y en castellano. Nunca llevan lo que escribió el usuario. */
public final class CourseEditException {

    private CourseEditException() {}

    public static ApiException busy() {
        return new ApiException(HttpStatus.CONFLICT, "COURSE_BUSY", "El curso se está generando. Espera a que termine para editarlo");
    }

    public static ApiException tooBig() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COURSE_TOO_BIG", "El curso tendría demasiadas partes. Quita alguna antes de añadir más");
    }

    public static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EDIT_RATE_LIMITED", "Has pedido muchas cosas a la IA seguidas. Espera un rato e inténtalo de nuevo");
    }

    /** Lo que escribe un menor y el filtro rechaza: el mensaje acordado, sin categoría ni motivo. */
    public static ApiException notAllowed() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EDIT_NOT_ALLOWED", CourseException.BLOCKED_MESSAGE);
    }
}
