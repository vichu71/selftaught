package es.vicai.studylab.material.extract;

/** Por qué no se pudo extraer texto de un documento. El nombre se guarda como motivo del error (nunca el mensaje de una librería). */
public enum ExtractionError {
    ENCRYPTED("El documento está protegido con contraseña"),
    CORRUPT("El documento está dañado o no se puede leer"),
    NO_TEXT("El documento no contiene texto (los escaneados no se admiten)"),
    TOO_MANY_PAGES("El documento tiene demasiadas páginas"),
    TOO_LARGE("El documento es demasiado grande una vez descomprimido"),
    TOO_MUCH_TEXT("El documento contiene demasiado texto");

    private final String message;

    ExtractionError(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
