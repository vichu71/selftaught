package es.vicai.studylab.course;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a {@code course_intake}, {@code course} y {@code course_document}. SQL siempre parametrizado y siempre filtrado por propietario. */
@Repository
public class CourseRepository {

    public record IntakeRow(UUID id, String topic, String status, String documentIdsJson, String questionsJson, boolean expired) {}

    public record CourseRow(UUID id, String title, String status, String sheetJson, Instant createdAt) {}

    public record DocumentRef(UUID id, String name) {}

    private final JdbcClient jdbc;

    public CourseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- límites por usuario (A2) ----------

    public int intakesInTheLastHour(UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM course_intake WHERE owner_id = :owner AND created_at > now() - interval '1 hour'")
                .param("owner", ownerId).query(Integer.class).single();
    }

    public int pendingCourses(UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM course WHERE owner_id = :owner AND status IN ('PENDING_GENERATION', 'GENERATING')")
                .param("owner", ownerId).query(Integer.class).single();
    }

    // ---------- documentos elegidos ----------

    /** Los documentos del usuario que están listos (nombre incluido), de entre los pedidos. Solo nombres: nunca el contenido. */
    public Map<UUID, String> readyDocuments(UUID ownerId, Collection<UUID> ids) {
        Map<UUID, String> found = new LinkedHashMap<>();
        if (ids.isEmpty()) return found;
        jdbc.sql("SELECT id, display_name FROM source_document WHERE owner_id = :owner AND status = 'READY' AND id IN (:ids)")
                .param("owner", ownerId).param("ids", ids)
                .query((rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("display_name")))
                .list().forEach(e -> found.put(e.getKey(), e.getValue()));
        return found;
    }

    // ---------- consultas ----------

    public void insertIntake(UUID id, UUID ownerId, String topic, String documentIdsJson, String status, String questionsJson,
                             String blockedCategory, long ttlSeconds) {
        jdbc.sql("""
                INSERT INTO course_intake (id, owner_id, topic, document_ids, status, questions, blocked_category, expires_at)
                VALUES (:id, :owner, :topic, CAST(:docs AS jsonb), :status, CAST(:questions AS jsonb), :category,
                        now() + make_interval(secs => :ttl))""")
                .param("id", id).param("owner", ownerId).param("topic", topic).param("docs", documentIdsJson).param("status", status)
                .param("questions", questionsJson).param("category", blockedCategory).param("ttl", (double) ttlSeconds).update();
    }

    public Optional<IntakeRow> findIntake(UUID id, UUID ownerId) {
        return jdbc.sql("""
                SELECT id, topic, status, document_ids::text AS docs, questions::text AS questions, (expires_at <= now()) AS expired
                FROM course_intake WHERE id = :id AND owner_id = :owner""")
                .param("id", id).param("owner", ownerId)
                .query((rs, row) -> new IntakeRow(rs.getObject("id", UUID.class), rs.getString("topic"), rs.getString("status"),
                        rs.getString("docs"), rs.getString("questions"), rs.getBoolean("expired")))
                .optional();
    }

    public boolean consumeIntake(UUID id, UUID ownerId) {
        return jdbc.sql("UPDATE course_intake SET status = 'CONSUMED' WHERE id = :id AND owner_id = :owner AND status = 'OPEN'")
                .param("id", id).param("owner", ownerId).update() > 0;
    }

    // ---------- cursos ----------

    /** Crea el curso de una consulta; {@code false} si esa consulta ya tenía el suyo (doble «Aceptar»). */
    public boolean insertCourse(UUID id, UUID ownerId, UUID intakeId, String title, String sheetJson, boolean illustrations) {
        return jdbc.sql("""
                INSERT INTO course (id, owner_id, intake_id, title, status, sheet, illustrations)
                VALUES (:id, :owner, :intake, :title, 'PENDING_GENERATION', CAST(:sheet AS jsonb), :illustrations)
                ON CONFLICT (intake_id) DO NOTHING""")
                .param("id", id).param("owner", ownerId).param("intake", intakeId).param("title", title).param("sheet", sheetJson).param("illustrations", illustrations)
                .update() > 0;
    }

    public void linkDocument(UUID courseId, UUID documentId, UUID ownerId) {
        jdbc.sql("INSERT INTO course_document (course_id, document_id, owner_id) VALUES (:course, :doc, :owner)")
                .param("course", courseId).param("doc", documentId).param("owner", ownerId).update();
    }

    private static CourseRow map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new CourseRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("status"), rs.getString("sheet"),
                created.toInstant());
    }

    public Optional<CourseRow> findCourse(UUID id, UUID ownerId) {
        return jdbc.sql("SELECT id, title, status, sheet::text AS sheet, created_at FROM course WHERE id = :id AND owner_id = :owner")
                .param("id", id).param("owner", ownerId).query(CourseRepository::map).optional();
    }

    public Optional<CourseRow> findCourseByIntake(UUID intakeId, UUID ownerId) {
        return jdbc.sql("SELECT id, title, status, sheet::text AS sheet, created_at FROM course WHERE intake_id = :intake AND owner_id = :owner")
                .param("intake", intakeId).param("owner", ownerId).query(CourseRepository::map).optional();
    }

    public List<CourseRow> listCourses(UUID ownerId) {
        return jdbc.sql("SELECT id, title, status, sheet::text AS sheet, created_at FROM course WHERE owner_id = :owner ORDER BY created_at DESC, id")
                .param("owner", ownerId).query(CourseRepository::map).list();
    }

    /** Los documentos que siguen existiendo y están enlazados al curso (si el usuario borró alguno, ya no está). */
    public List<DocumentRef> courseDocuments(UUID courseId, UUID ownerId) {
        return jdbc.sql("""
                SELECT d.id, d.display_name FROM course_document cd JOIN source_document d ON d.id = cd.document_id AND d.owner_id = cd.owner_id
                WHERE cd.course_id = :course AND cd.owner_id = :owner ORDER BY d.created_at, d.id""")
                .param("course", courseId).param("owner", ownerId)
                .query((rs, row) -> new DocumentRef(rs.getObject("id", UUID.class), rs.getString("display_name"))).list();
    }
}
