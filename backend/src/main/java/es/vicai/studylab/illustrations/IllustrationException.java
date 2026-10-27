package es.vicai.studylab.illustrations;

import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados de las ilustraciones: estado, código y mensaje genéricos y en castellano. Nunca llevan títulos, prompts ni mensajes del proveedor. */
public final class IllustrationException {

    private IllustrationException() {}

    /** Sin proveedor de imagen, o quien lo pide es un menor: la opción no existe para ese usuario. */
    public static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_REQUEST, "ILLUSTRATIONS_UNAVAILABLE", "Las ilustraciones no están disponibles");
    }

    public static ApiException regenerationLimit() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "REGENERATION_LIMIT", "Esta ilustración ya se ha vuelto a generar demasiadas veces");
    }

    public static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "IMAGE_RATE_LIMITED", "Has pedido muchas cosas a la IA seguidas. Espera un rato e inténtalo de nuevo");
    }
}
