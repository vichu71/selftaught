package es.vicai.studylab.tutor;

import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados del tutor: estado, código y mensaje genéricos y en castellano. Nunca llevan lo que se dijo en el chat. */
public final class TutorException {

    private TutorException() {}

    public static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TUTOR_RATE_LIMITED", "Has enviado muchos mensajes seguidos. Espera un rato e inténtalo de nuevo");
    }

    public static ApiException busy() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TUTOR_BUSY", "Tu tutor está terminando de contestar. Espera a que acabe");
    }

    public static ApiException failed() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "TUTOR_FAILED", "No he podido responder. Inténtalo de nuevo");
    }

    public static ApiException protectedThread() {
        return new ApiException(HttpStatus.FORBIDDEN, "THREAD_PROTECTED", "Tu adulto tutor puede ver esta conversación, así que no se puede borrar");
    }
}
