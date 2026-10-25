package es.vicai.studylab.generation;

import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados de la generación: estado, código y mensaje genéricos y en castellano. */
public final class GenerationException {

    private GenerationException() {}

    public static ApiException notPaused() {
        return new ApiException(HttpStatus.CONFLICT, "COURSE_NOT_PAUSED", "El curso no está en pausa");
    }

    public static ApiException resumeTooSoon() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESUME_TOO_SOON", "Acabas de reanudar este curso. Espera un minuto e inténtalo de nuevo");
    }
}
