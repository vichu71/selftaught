package es.vicai.studylab.coursedelete;

import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados del borrado: estado, código y mensaje genéricos y en castellano. Nunca llevan títulos ni contenido. */
public final class CourseDeleteException {

    private CourseDeleteException() {}

    public static ApiException busy() {
        return new ApiException(HttpStatus.CONFLICT, "COURSE_BUSY", "Hay cursos que se están generando. Espera a que terminen para borrarlos");
    }

    public static ApiException childCannotDelete() {
        return new ApiException(HttpStatus.FORBIDDEN, "CHILD_CANNOT_DELETE", "Tu adulto tutor puede ver tu actividad, por eso no puedes borrar cursos. Pídeselo a él");
    }
}
