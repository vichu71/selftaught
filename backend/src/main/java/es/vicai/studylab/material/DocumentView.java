package es.vicai.studylab.material;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Lo único que la API cuenta de un documento: nunca su contenido, su hash ni el id de su propietario. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentView(UUID id, String name, MaterialType mediaType, int sizeBytes, String status, String errorKind,
                           int chunkCount, Integer pageCount, Instant createdAt, boolean ownedByMe) {}
