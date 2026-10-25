package es.vicai.studylab.study;

import es.vicai.studylab.course.CourseException;
import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados del estudio: estado, código y mensaje genéricos y en castellano. Nunca llevan lo que escribió el alumno. */
public final class StudyException {

    private StudyException() {}

    public static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "STUDY_RATE_LIMITED", "Has enviado muchos intentos seguidos. Espera un rato e inténtalo de nuevo");
    }

    public static ApiException notReady() {
        return new ApiException(HttpStatus.CONFLICT, "PIECE_NOT_READY", "Esta parte del curso todavía no está lista");
    }

    public static ApiException aiLimit() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_GRADING_LIMIT", "Has usado todas las correcciones de IA de hoy. Puedes autocorregirte");
    }

    public static ApiException busy() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "GRADING_BUSY", "Ya tienes una corrección en marcha. Espera a que termine");
    }

    public static ApiException alreadyGraded() {
        return new ApiException(HttpStatus.CONFLICT, "AI_GRADING_ALREADY", "Este intento ya se ha corregido con IA");
    }

    public static ApiException answerNotAllowed() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ANSWER_NOT_ALLOWED", CourseException.BLOCKED_MESSAGE);
    }

    public static ApiException gradingFailed() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "GRADING_FAILED", "No se pudo corregir con IA. Puedes autocorregirte");
    }

    public static ApiException notSubmitted() {
        return new ApiException(HttpStatus.CONFLICT, "ATTEMPT_NOT_SUBMITTED", "Este examen todavía no se ha entregado");
    }
}
