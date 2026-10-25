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
                "Solo se admiten PDF, Word (.docx) y texto (.txt, .md) en UTF-8");
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
}
