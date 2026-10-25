package es.vicai.studylab.course;

import java.util.List;
import java.util.UUID;

/**
 * La ficha del curso (ADR-40): el contrato con F05. Texto corto y valores acotados; nunca datos personales ni texto de documentos.
 * Si cambia, sube {@code schemaVersion}.
 */
public record CourseSheet(int schemaVersion, String topic, String focus, String startLevel, String goal, int topicsCount,
                          boolean examPerTopic, List<UUID> documentIds) {

    public static final int SCHEMA_VERSION = 1;

    public CourseSheet {
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }
}
