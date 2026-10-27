package es.vicai.studylab.illustrations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@code course_image} (ADR-79). Toda consulta lleva {@code owner_id} en el mismo {@code WHERE} o en el {@code JOIN}: nadie lee ni escribe la imagen de otro.
 * Lo que se guarda nunca es el prompt ni un mensaje del proveedor, solo estados, tipos de error y la imagen ya validada.
 */
@Repository
public class ImageRepository {

    /** Una imagen pendiente con lo único que hace falta para pedirla: el tema (título y resumen) y su propietario. */
    public record Pending(UUID id, UUID courseId, UUID ownerId, UUID topicId, String title, String summary) {}

    public record Row(UUID id, String status, int regenerations, String altText, String mime, Instant generatedAt) {}

    public record Content(byte[] bytes, String mime, UUID id, Instant generatedAt) {}

    private final JdbcClient jdbc;

    public ImageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- cola ----------

    /**
     * Una fila {@code PENDING} por cada tema de un curso terminado de un adulto activo con las ilustraciones activadas que aún no la tiene. No recrea las
     * {@code REMOVED} ni reintenta las {@code FAILED} o {@code BLOCKED}: la fila existe y cada intento cuesta dinero.
     */
    public int enqueueMissing() {
        return jdbc.sql("""
                INSERT INTO course_image (id, course_id, owner_id, topic_id)
                SELECT gen_random_uuid(), t.course_id, t.owner_id, t.id
                FROM course_topic t
                JOIN course c ON c.id = t.course_id AND c.owner_id = t.owner_id
                JOIN app_user u ON u.id = c.owner_id
                WHERE c.status = 'READY' AND c.illustrations = true AND u.status = 'ACTIVE' AND u.kind = 'ADULT'
                  AND NOT EXISTS (SELECT 1 FROM course_image i WHERE i.topic_id = t.id AND i.owner_id = t.owner_id)
                ON CONFLICT (topic_id, owner_id) DO NOTHING""").update();
    }

    /** Las pendientes más antiguas de cursos terminados de adultos activos. */
    public List<Pending> pending(int limit) {
        return jdbc.sql("""
                SELECT i.id, i.course_id, i.owner_id, i.topic_id, t.title, t.summary
                FROM course_image i
                JOIN course c ON c.id = i.course_id AND c.owner_id = i.owner_id
                JOIN course_topic t ON t.id = i.topic_id AND t.owner_id = i.owner_id
                JOIN app_user u ON u.id = i.owner_id
                WHERE i.status = 'PENDING' AND c.status = 'READY' AND u.status = 'ACTIVE' AND u.kind = 'ADULT'
                ORDER BY i.created_at, i.id
                LIMIT :limit""").param("limit", limit)
                .query((rs, n) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getString(5), rs.getString(6)))
                .list();
    }

    public void saveDone(UUID id, UUID ownerId, byte[] content, String mime, String altText) {
        jdbc.sql("""
                UPDATE course_image SET status = 'DONE', content = :content, mime = :mime, size_bytes = :size, alt_text = :alt, error_kind = NULL,
                       attempts = attempts + 1, generated_at = now()
                WHERE id = :id AND owner_id = :owner""")
                .param("content", content).param("mime", mime).param("size", content.length).param("alt", altText).param("id", id).param("owner", ownerId).update();
    }

    /** {@code status} es {@code FAILED} o {@code BLOCKED}; el tipo de error nunca lleva texto del proveedor. */
    public void saveEnded(UUID id, UUID ownerId, String status, String errorKind) {
        jdbc.sql("""
                UPDATE course_image SET status = :status, error_kind = :kind, content = NULL, mime = NULL, size_bytes = NULL, attempts = attempts + 1
                WHERE id = :id AND owner_id = :owner""")
                .param("status", status).param("kind", errorKind).param("id", id).param("owner", ownerId).update();
    }

    // ---------- lectura ----------

    public Optional<Row> find(UUID topicId, UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT id, status, regenerations, alt_text, mime, generated_at FROM course_image
                WHERE topic_id = :topic AND course_id = :course AND owner_id = :owner""")
                .param("topic", topicId).param("course", courseId).param("owner", ownerId)
                .query((rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()))
                .optional();
    }

    /** La imagen lista de un tema del propio curso; cualquier otro estado, ajeno o inexistente es lo mismo: nada. */
    public Optional<Content> done(UUID topicId, UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT id, content, mime, generated_at FROM course_image
                WHERE topic_id = :topic AND course_id = :course AND owner_id = :owner AND status = 'DONE' AND content IS NOT NULL""")
                .param("topic", topicId).param("course", courseId).param("owner", ownerId)
                .query((rs, n) -> new Content(rs.getBytes(2), rs.getString(3), rs.getObject(1, UUID.class),
                        rs.getTimestamp(4) == null ? Instant.EPOCH : rs.getTimestamp(4).toInstant()))
                .optional();
    }

    // ---------- edición ----------

    public Optional<String> lockCourse(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT status FROM course WHERE id = :id AND owner_id = :owner FOR UPDATE").param("id", courseId).param("owner", ownerId)
                .query(String.class).optional();
    }

    /** Devuelve la fila a la cola y cuenta la regeneración. */
    public void requeue(UUID id, UUID ownerId) {
        jdbc.sql("""
                UPDATE course_image SET status = 'PENDING', error_kind = NULL, regenerations = regenerations + 1
                WHERE id = :id AND owner_id = :owner""").param("id", id).param("owner", ownerId).update();
    }

    /** Quitar conserva la fila (sin bytes) para que el hilo no la vuelva a encolar. */
    public void remove(UUID id, UUID ownerId) {
        jdbc.sql("""
                UPDATE course_image SET status = 'REMOVED', error_kind = NULL, content = NULL, mime = NULL, size_bytes = NULL
                WHERE id = :id AND owner_id = :owner""").param("id", id).param("owner", ownerId).update();
    }
}
