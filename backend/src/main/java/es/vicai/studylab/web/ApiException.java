package es.vicai.studylab.web;

import org.springframework.http.HttpStatus;

/** Error esperado de la API: se traduce a {"code", "message"} con el estado indicado. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    // Respuestas constantes: iguales para cuentas que existen y que no, para no revelar nada
    public static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciales no válidas");
    }

    public static ApiException locked() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOCKED",
                "Demasiados intentos. Inténtalo de nuevo más tarde");
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso no encontrado");
    }

    public static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "El enlace no es válido o ha caducado");
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", message);
    }
}
