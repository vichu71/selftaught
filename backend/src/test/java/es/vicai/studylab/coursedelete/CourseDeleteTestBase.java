package es.vicai.studylab.coursedelete;

import es.vicai.studylab.illustrations.IllustrationGenerator;
import es.vicai.studylab.share.ShareTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de F13. Contrato (ART-003): {@code DELETE /api/courses/{id}} y {@code POST /api/courses/delete} {ids:[…]} (1–50 ids) borran en una
 * transacción, todo o nada; responden 204. Un menor recibe 403 {@code CHILD_CANNOT_DELETE}; ajeno o inexistente, 404; en cola o generándose, 409 {@code COURSE_BUSY}.
 */
public abstract class CourseDeleteTestBase extends ShareTestBase {

    @Autowired protected IllustrationGenerator illustrations;

    protected static final String BUSY = "COURSE_BUSY";
    protected static final String UNIQUE_TITLE = "Titulo-unico-borrable-ZZ";

    /** Las tablas que cuelgan de un curso por {@code course_id} y que el borrado se lleva con él. */
    protected static final List<String> DEPENDENT_TABLES = List.of("course_document", "course_topic", "course_chapter", "course_piece", "lesson_progress", "exercise_attempt",
            "exam_attempt", "tutor_message", "share_code", "course_image");

    protected ResultActions deleteOne(String session, UUID course) throws Exception {
        return deleteAs(session, "/api/courses/" + course);
    }

    protected ResultActions deleteMany(String session, Object ids) throws Exception {
        return postAs(session, "/api/courses/delete", Map.of("ids", ids));
    }

    /** Filas de un curso en todas las tablas que dependen de él, más la suya. */
    protected int rowsOf(UUID course) {
        int total = count("SELECT count(*) FROM course WHERE id = ?", course);
        for (String table : DEPENDENT_TABLES) total += count("SELECT count(*) FROM " + table + " WHERE course_id = ?", course);
        return total;
    }

    protected boolean exists(UUID course) {
        return count("SELECT count(*) FROM course WHERE id = ?", course) > 0;
    }

    /** Un curso de {@code email} con progreso, intentos, conversación, código de compartir e imágenes: lo que el borrado debe llevarse. */
    protected UUID loaded(String session, String email) throws Exception {
        UUID course = ready(email);
        UUID user = idOf(email);
        List<UUID> topics = topicIds(course);
        UUID chapter = chapterIds(topics.get(0)).get(0);
        giveDependents(session, user, course, chapter, topics.get(0));
        createCode(session, course);
        insertImage(course, user, topics.get(0), "DONE");
        insertImage(course, user, topics.get(1), "FAILED");
        insertUsage(user, course, "0.0100");
        return course;
    }

    protected void insertImage(UUID course, UUID user, UUID topic, String status) {
        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status) VALUES (gen_random_uuid(), ?, ?, ?, ?)", course, user, topic, status);
    }

    protected void insertUsage(UUID user, UUID course, String usd) {
        owner.update("INSERT INTO ai_usage (request_id, attempt, user_id, course_id, source, task, provider, model, cost_usd, status) "
                + "VALUES (gen_random_uuid(), 1, ?, ?, 'APP', 'LESSON', 'fake', 'fake-1', ?, 'OK')", user, course, new BigDecimal(usd));
    }

    protected int usageRows(UUID course) {
        return count("SELECT count(*) FROM ai_usage WHERE course_id = ?", course);
    }

    protected BigDecimal totalSpend(UUID user) {
        return owner.queryForObject("SELECT coalesce(sum(cost_usd), 0) FROM ai_usage WHERE user_id = ?", BigDecimal.class, user);
    }

    protected UUID intakeOf(UUID course) {
        return owner.queryForObject("SELECT intake_id FROM course WHERE id = ?", UUID.class, course);
    }

    protected List<String> deletedAudit() {
        return owner.queryForList("SELECT details::text FROM audit_event WHERE action = 'COURSE_DELETED' ORDER BY id", String.class);
    }

    /** Un curso de {@code email} con un título reconocible, generado por la cola con el modelo falso. */
    protected UUID titled(String email, String title) throws Exception {
        UUID course = insertCourse(idOf(email), title, 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        return course;
    }
}
