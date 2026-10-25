package es.vicai.studylab.generation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lectura del contenido generado de un curso (temario y piezas). SQL siempre parametrizado y siempre filtrado por propietario. */
@Repository
public class CourseContentRepository {

    public record TopicRow(UUID id, String title, String summary) {}

    public record ChapterRow(UUID id, UUID topicId, String title, String objective) {}

    /** Una pieza sin su contenido: lo justo para pintar el temario con su estado. */
    public record PieceStatus(UUID id, String kind, String status, UUID topicId, UUID chapterId) {}

    /** Una pieza con su contenido; {@code title} es el del capítulo (lección y ejercicios) o el del tema (examen). */
    public record PieceContent(UUID id, String kind, String status, UUID topicId, UUID chapterId, String title, String markdown, String contentJson,
                               String sourcesJson) {}

    private final JdbcClient jdbc;

    public CourseContentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> stopReason(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT stop_reason FROM course WHERE id = :id AND owner_id = :owner")
                .param("id", courseId).param("owner", ownerId).query(String.class).optional();
    }

    public Optional<String> status(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT status FROM course WHERE id = :id AND owner_id = :owner")
                .param("id", courseId).param("owner", ownerId).query(String.class).optional();
    }

    public List<TopicRow> topics(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT id, title, summary FROM course_topic WHERE course_id = :course AND owner_id = :owner ORDER BY position")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new TopicRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("summary"))).list();
    }

    public List<ChapterRow> chapters(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT id, topic_id, title, objective FROM course_chapter WHERE course_id = :course AND owner_id = :owner ORDER BY position, id")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new ChapterRow(rs.getObject("id", UUID.class), rs.getObject("topic_id", UUID.class), rs.getString("title"),
                        rs.getString("objective"))).list();
    }

    public List<PieceStatus> pieces(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT id, kind, status, topic_id, chapter_id FROM course_piece WHERE course_id = :course AND owner_id = :owner ORDER BY seq")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new PieceStatus(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"),
                        rs.getObject("topic_id", UUID.class), rs.getObject("chapter_id", UUID.class))).list();
    }

    public Optional<PieceContent> piece(UUID courseId, UUID pieceId, UUID ownerId) {
        return jdbc.sql("""
                SELECT p.id, p.kind, p.status, p.topic_id, p.chapter_id, COALESCE(ch.title, t.title) AS title, p.content_md,
                       p.content_json::text AS content_json, p.sources::text AS sources
                FROM course_piece p
                JOIN course_topic t ON t.id = p.topic_id AND t.owner_id = p.owner_id
                LEFT JOIN course_chapter ch ON ch.id = p.chapter_id AND ch.owner_id = p.owner_id
                WHERE p.id = :id AND p.course_id = :course AND p.owner_id = :owner""")
                .param("id", pieceId).param("course", courseId).param("owner", ownerId)
                .query(CourseContentRepository::content).optional();
    }

    /** Todas las piezas ya hechas, en orden: lo único que se exporta. */
    public List<PieceContent> donePieces(UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT p.id, p.kind, p.status, p.topic_id, p.chapter_id, COALESCE(ch.title, t.title) AS title, p.content_md,
                       p.content_json::text AS content_json, p.sources::text AS sources
                FROM course_piece p
                JOIN course_topic t ON t.id = p.topic_id AND t.owner_id = p.owner_id
                LEFT JOIN course_chapter ch ON ch.id = p.chapter_id AND ch.owner_id = p.owner_id
                WHERE p.course_id = :course AND p.owner_id = :owner AND p.status = 'DONE' ORDER BY p.seq""")
                .param("course", courseId).param("owner", ownerId)
                .query(CourseContentRepository::content).list();
    }

    private static PieceContent content(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PieceContent(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("status"), rs.getObject("topic_id", UUID.class),
                rs.getObject("chapter_id", UUID.class), rs.getString("title"), rs.getString("content_md"), rs.getString("content_json"),
                rs.getString("sources"));
    }

    /** Un curso con sus piezas por estado y el coste y las llamadas registradas. */
    public record ResultRow(UUID id, String title, String status, String stopReason, int done, int failed, int pending, long calls,
                            BigDecimal costUsd, java.time.Instant createdAt) {}

    /** Todos los cursos de un propietario, el más reciente primero, con el recuento de sus piezas y su gasto. */
    public List<ResultRow> results(UUID ownerId) {
        return jdbc.sql("""
                SELECT c.id, c.title, c.status, c.stop_reason, c.created_at,
                       count(p.id) FILTER (WHERE p.status = 'DONE') AS done,
                       count(p.id) FILTER (WHERE p.status = 'FAILED') AS failed,
                       count(p.id) FILTER (WHERE p.status = 'PENDING') AS pending,
                       (SELECT count(*) FROM ai_usage u WHERE u.course_id = c.id AND u.user_id = c.owner_id) AS calls,
                       (SELECT COALESCE(sum(u.cost_usd), 0) FROM ai_usage u WHERE u.course_id = c.id AND u.user_id = c.owner_id) AS cost
                FROM course c LEFT JOIN course_piece p ON p.course_id = c.id AND p.owner_id = c.owner_id
                WHERE c.owner_id = :owner GROUP BY c.id ORDER BY c.created_at DESC, c.id""")
                .param("owner", ownerId)
                .query((rs, row) -> new ResultRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("status"),
                        rs.getString("stop_reason"), rs.getInt("done"), rs.getInt("failed"), rs.getInt("pending"), rs.getLong("calls"),
                        rs.getBigDecimal("cost"), rs.getTimestamp("created_at").toInstant())).list();
    }

    /** Si la misma persona tiene otro curso generándose: el suyo espera su turno (una generación a la vez por usuario). */
    public boolean otherGenerating(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM course WHERE owner_id = :owner AND id <> :id AND status = 'GENERATING')")
                .param("owner", ownerId).param("id", courseId).query(Boolean.class).single();
    }

    /** El coste de un curso es la suma de sus llamadas registradas: una sola fuente de verdad. */
    public BigDecimal cost(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT COALESCE(sum(cost_usd), 0) FROM ai_usage WHERE course_id = :course AND user_id = :owner")
                .param("course", courseId).param("owner", ownerId).query(BigDecimal.class).single();
    }

    /**
     * Reanuda un curso en pausa: vuelve a la cola. Solo si está en pausa y no se pulsó hace menos del enfriamiento.
     * @return las filas cambiadas (0 si no se cumplía alguna condición)
     */
    public int resume(UUID courseId, UUID ownerId, double cooldownSeconds) {
        return jdbc.sql("""
                UPDATE course SET status = 'PENDING_GENERATION', stop_reason = NULL, resumed_at = now(), updated_at = now()
                WHERE id = :id AND owner_id = :owner AND status = 'PAUSED'
                  AND (resumed_at IS NULL OR resumed_at < now() - make_interval(secs => :cooldown))""")
                .param("id", courseId).param("owner", ownerId).param("cooldown", cooldownSeconds).update();
    }
}
