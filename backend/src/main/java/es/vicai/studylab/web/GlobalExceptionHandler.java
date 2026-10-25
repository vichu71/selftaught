package es.vicai.studylab.web;

import es.vicai.studylab.ai.AiException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Respuestas de error uniformes. Nunca se devuelve el detalle de una excepción inesperada. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Errores de la capa de IA que una feature deja salir por un endpoint: cada tipo tiene su estado y un mensaje genérico
     * (nunca el del proveedor, que puede repetir el prompt).
     */
    @ExceptionHandler(AiException.class)
    ResponseEntity<Map<String, String>> handleAi(AiException e) {
        HttpStatus status = switch (e.kind()) {
            case BUDGET_BLOCKED, USER_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case GENERATION_BUSY -> HttpStatus.CONFLICT;
            case INPUT_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case USER_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case PROVIDER_QUOTA, PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROVIDER_AUTH, PROVIDER_REJECTED, INVALID_RESPONSE, CONFIG -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(Map.of("code", "AI_" + e.kind().name(), "message", e.getMessage()));
    }

    /** Una subida que el servidor corta por tamaño antes de llegar al servicio de material (F03, AC-3). */
    ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("code", "MATERIAL_TOO_LARGE", "message", "El fichero supera el tamaño máximo permitido"));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return (ResponseEntity) handleMaxUpload(ex);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Solo se nombran los campos inválidos, nunca los valores enviados
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField()).distinct().sorted().reduce((a, b) -> a + ", " + b).orElse("");
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION", "message", "Datos no válidos: " + fields));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION", "message", "Cuerpo de la petición no válido"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "INTERNAL", "message", "Error interno"));
    }
}
