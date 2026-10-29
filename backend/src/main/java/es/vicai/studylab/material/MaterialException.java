package es.vicai.studylab.material;

import es.vicai.studylab.web.ApiException;
import org.springframework.http.HttpStatus;

/** Errores esperados de la subida y gestión de material: estado, código y mensaje genéricos y en castellano. */
public final class MaterialException {

    private MaterialException() {}

    public static ApiException empty() {
        return new ApiException(HttpStatus.BAD_REQUEST, "MATERIAL_EMPTY", "El fichero está vacío");
    }

    public static ApiException tooLarge(int maxMb) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MATERIAL_TOO_LARGE",
                "El fichero supera el tamaño máximo de " + maxMb + " MB");
    }

    public static ApiException typeNotAllowed() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MATERIAL_TYPE_NOT_ALLOWED",
                "Solo se admiten PDF, Word (.docx), texto (.txt, .md) en UTF-8 y fotos JPEG o PNG (no HEIC, WebP ni GIF)");
    }

    public static ApiException limitReached(int max) {
        return new ApiException(HttpStatus.CONFLICT, "MATERIAL_LIMIT_REACHED",
                "Has alcanzado el límite de " + max + " documentos. Borra alguno para subir otro");
    }

    public static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "MATERIAL_DUPLICATE", "Ya has subido un documento con este mismo contenido");
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para hacer esto");
    }

    // ---------- fotos (F19) ----------

    public static ApiException childImagesNotEnabled() {
        return new ApiException(HttpStatus.FORBIDDEN, "CHILD_IMAGES_NOT_ENABLED", "Tu adulto tutor aún no ha activado las fotos. Pídeselo si quieres subir una");
    }

    public static ApiException imageReadRateLimit() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "IMAGE_READ_RATE_LIMIT", "Has alcanzado el límite diario de fotos. Inténtalo mañana");
    }

    public static ApiException imageTooLargeInMb(int maxMb) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MATERIAL_TOO_LARGE", "La foto supera el tamaño máximo de " + maxMb + " MB");
    }

    public static ApiException imageTooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "La foto tiene demasiados píxeles. Hazla con menos resolución");
    }

    public static ApiException imageInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_INVALID", "No se puede leer la foto. Prueba con otra en JPEG o PNG");
    }
}
