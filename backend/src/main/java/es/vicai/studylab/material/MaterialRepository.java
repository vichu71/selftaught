package es.vicai.studylab.material;

import es.vicai.studylab.material.chunk.Chunk;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a {@code source_document} y {@code source_chunk}. Todo el SQL va parametrizado; cada consulta de fragmentos filtra por propietario. */
@Repository
public class MaterialRepository {

    /** Un documento tal como lo ve la API (sin contenido ni hash). */
    public record DocumentRow(UUID id, UUID ownerId, String name, MaterialType type, int sizeBytes, String status,
                              String errorKind, int chunkCount, Integer pageCount, Instant createdAt) {}

    /** Un documento reclamado por el worker, con su original. */
    public record Claimed(UUID id, UUID ownerId, MaterialType type, byte[] content) {}

    private static final String COLUMNS = "id, owner_id, display_name, media_type, size_bytes, status, error_kind, chunk_count, "
            + "page_count, created_at";

    private static final String FIND = "SELECT " + COLUMNS + " FROM source_document WHERE id = :id";
    private static final String LIST = "SELECT " + COLUMNS + " FROM source_document WHERE owner_id = :owner ORDER BY created_at, id";

    private final JdbcClient jdbc;

    public MaterialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static DocumentRow map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new DocumentRow(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("display_name"),
                MaterialType.valueOf(rs.getString("media_type")), rs.getInt("size_bytes"), rs.getString("status"),
                rs.getString("error_kind"), rs.getInt("chunk_count"), rs.getObject("page_count", Integer.class),
                rs.getTimestamp("created_at").toInstant());
    }

    /** ¿Es un menor el propietario? Decide la audiencia que se declara al leer una foto. */
    public boolean isMinor(UUID ownerId) {
        return jdbc.sql("SELECT kind = 'MINOR' FROM app_user WHERE id = :id").param("id", ownerId).query(Boolean.class).optional().orElse(true);
    }

    public int count(UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM source_document WHERE owner_id = :owner").param("owner", ownerId)
                .query(Integer.class).single();
    }

    /** Inserta un documento pendiente; {@code false} si el propietario ya subió este mismo contenido. */
    public boolean insert(UUID id, UUID ownerId, String name, MaterialType type, byte[] content, String sha256) {
        try {
            jdbc.sql("""
                    INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status, content)
                    VALUES (:id, :owner, :name, :type, :size, :sha, 'UPLOADED', :content)""")
                    .param("id", id).param("owner", ownerId).param("name", name).param("type", type.name())
                    .param("size", content.length).param("sha", sha256).param("content", content).update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<DocumentRow> find(UUID id) {
        return jdbc.sql(FIND).param("id", id)
                .query(MaterialRepository::map).optional();
    }

    public List<DocumentRow> listOf(UUID ownerId) {
        return jdbc.sql(LIST)
                .param("owner", ownerId).query(MaterialRepository::map).list();
    }

    public boolean delete(UUID id, UUID ownerId) {
        return jdbc.sql("DELETE FROM source_document WHERE id = :id AND owner_id = :owner")
                .param("id", id).param("owner", ownerId).update() > 0;
    }

    // ---------- cola de procesado (ADR-32) ----------

    /**
     * Reclama el documento pendiente más antiguo de un propietario activo; varios workers no pisan el mismo documento. {@code skip} son los que esta ronda ya dejó
     * esperando (una foto con el cerrojo de su usuario ocupado): no se reclaman otra vez hasta la siguiente ronda.
     */
    public Optional<Claimed> claimNext(java.util.Collection<UUID> skip) {
        return jdbc.sql("""
                UPDATE source_document SET status = 'PROCESSING', attempts = attempts + 1, processing_started_at = now(), updated_at = now()
                WHERE id = (SELECT d.id FROM source_document d JOIN app_user u ON u.id = d.owner_id
                            WHERE d.status = 'UPLOADED' AND u.status = 'ACTIVE' AND d.id::text <> ALL (string_to_array(:skip, ','))
                            ORDER BY d.created_at, d.id FOR UPDATE OF d SKIP LOCKED LIMIT 1)
                RETURNING id, owner_id, media_type, content""")
                .param("skip", skip.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")))
                .query((rs, row) -> new Claimed(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                        MaterialType.valueOf(rs.getString("media_type")), rs.getBytes("content")))
                .optional();
    }

    /**
     * Tras un reinicio: lo que quedó en PROCESSING vuelve a la cola, salvo si ya se intentó 3 veces (devuelve esos ids). Una foto (IMAGE) que quedó leyéndose NO vuelve a la
     * cola: la lectura es de pago y pudo haberse cobrado ya (ADR-102), así que acaba en ERROR READ_FAILED, y su id también se devuelve.
     */
    public List<UUID> recoverInterrupted() {
        List<UUID> ids = new java.util.ArrayList<>(jdbc.sql("""
                UPDATE source_document SET status = 'ERROR', error_kind = 'READ_FAILED', content = NULL, updated_at = now()
                WHERE status = 'PROCESSING' AND media_type = 'IMAGE' RETURNING id""").query(UUID.class).list());
        jdbc.sql("UPDATE source_document SET status = 'UPLOADED', updated_at = now() WHERE status = 'PROCESSING' AND attempts < 3").update();
        ids.addAll(jdbc.sql("""
                UPDATE source_document SET status = 'ERROR', error_kind = 'INDEXING_FAILED', content = NULL, updated_at = now()
                WHERE status = 'PROCESSING' AND attempts >= 3 RETURNING id""").query(UUID.class).list());
        return ids;
    }

    /** Un fallo inesperado: de nuevo a la cola si quedan intentos, o a ERROR. Una foto (IMAGE) nunca se reencola. Devuelve true si acabó en ERROR. */
    public boolean requeueOrFail(UUID id) {
        jdbc.sql("UPDATE source_document SET status = 'UPLOADED', updated_at = now() WHERE id = :id AND status = 'PROCESSING' AND attempts < 3 AND media_type <> 'IMAGE'")
                .param("id", id).update();
        return markFailed(id, "INDEXING_FAILED");
    }

    /** El cerrojo del usuario estaba ocupado: la foto vuelve a la cola sin gastar el intento que reclamarla sumó (ADR-103). */
    public void releaseBusy(UUID id) {
        jdbc.sql("UPDATE source_document SET status = 'UPLOADED', attempts = GREATEST(attempts - 1, 0), updated_at = now() WHERE id = :id AND status = 'PROCESSING'")
                .param("id", id).update();
    }

    // ---------- contador diario de fotos (ADR-105) ----------

    /** Fotos aceptadas por el usuario en las últimas 24 horas (también las borradas después). */
    public int countImageUploads(UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM material_action WHERE owner_id = :owner AND kind = 'IMAGE_UPLOAD' AND created_at > now() - interval '24 hours'")
                .param("owner", ownerId).query(Integer.class).single();
    }

    public void insertImageUpload(UUID ownerId) {
        jdbc.sql("INSERT INTO material_action (owner_id, kind) VALUES (:owner, 'IMAGE_UPLOAD')").param("owner", ownerId).update();
    }

    /** Pasa a ERROR (solo si seguía procesándose) y descarta el original. */
    public boolean markFailed(UUID id, String errorKind) {
        return jdbc.sql("""
                UPDATE source_document SET status = 'ERROR', error_kind = :kind, content = NULL, updated_at = now()
                WHERE id = :id AND status = 'PROCESSING'""").param("id", id).param("kind", errorKind).update() > 0;
    }

    /** Bloquea la fila del documento para guardar los fragmentos; falso si ya no existe (se borró mientras se procesaba). */
    public boolean lockProcessing(UUID id) {
        return jdbc.sql("SELECT id FROM source_document WHERE id = :id AND status = 'PROCESSING' FOR UPDATE")
                .param("id", id).query(UUID.class).optional().isPresent();
    }

    public void insertChunk(UUID documentId, UUID ownerId, Chunk chunk, float[] embedding, String model) {
        jdbc.sql("""
                INSERT INTO source_chunk (document_id, owner_id, ordinal, page, start_offset, end_offset, text, embedding, embedding_model)
                VALUES (:doc, :owner, :ordinal, :page, :start, :end, :text, CAST(:vector AS vector), :model)""")
                .param("doc", documentId).param("owner", ownerId).param("ordinal", chunk.ordinal()).param("page", chunk.page())
                .param("start", chunk.startOffset()).param("end", chunk.endOffset()).param("text", chunk.text())
                .param("vector", toVectorLiteral(embedding)).param("model", model).update();
    }

    public void markReady(UUID id, int chunkCount, Integer pageCount) {
        jdbc.sql("""
                UPDATE source_document SET status = 'READY', error_kind = NULL, chunk_count = :chunks, page_count = :pages,
                       content = NULL, updated_at = now() WHERE id = :id""")
                .param("id", id).param("chunks", chunkCount).param("pages", pageCount).update();
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
