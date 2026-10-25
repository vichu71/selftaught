package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.AiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ART-002: V5__generation.sql impone en la BD lo que el código promete. */
class GenerationSchemaTest extends AiTestBase {

    private UUID course(UUID ownerId, String status) {
        UUID intake = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, expires_at) VALUES (?, ?, 'Inglés', 'CONSUMED', now() + interval '1 hour')", intake, ownerId);
        owner.update("INSERT INTO course (id, owner_id, intake_id, title, status, sheet) VALUES (?, ?, ?, 'Inglés', ?, CAST('{}' AS jsonb))", id, ownerId, intake, status);
        return id;
    }

    private UUID topic(UUID courseId, UUID ownerId, int position) {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (?, ?, ?, ?, 'T', 's')", id, courseId, ownerId, position);
        return id;
    }

    private UUID chapter(UUID topicId, UUID courseId, UUID ownerId, int position) {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO course_chapter (id, topic_id, course_id, owner_id, position, title, objective) VALUES (?, ?, ?, ?, ?, 'C', 'o')", id, topicId, courseId, ownerId, position);
        return id;
    }

    private void piece(UUID courseId, UUID ownerId, UUID topicId, UUID chapterId, String kind, int seq, String status) {
        owner.update("INSERT INTO course_piece (id, course_id, owner_id, topic_id, chapter_id, kind, seq, status) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?)",
                courseId, ownerId, topicId, chapterId, kind, seq, status);
    }

    @Test
    void aCourseNowAcceptsThePausedStateAndKeepsRejectingInventedOnes() {
        UUID ana = newUser();

        for (String ok : new String[] {"PENDING_GENERATION", "GENERATING", "PAUSED", "READY", "FAILED"}) course(ana, ok);

        assertThatThrownBy(() -> course(ana, "INVENTADO")).isInstanceOf(Exception.class);
    }

    @Test
    void theStopReasonIsConstrained() {
        UUID ana = newUser();
        UUID course = course(ana, "PAUSED");

        for (String ok : new String[] {"BUDGET", "PROVIDER", "CONTENT", "TOPIC_NOT_ALLOWED", "LIMIT"}) owner.update("UPDATE course SET stop_reason = ? WHERE id = ?", ok, course);

        assertThatThrownBy(() -> owner.update("UPDATE course SET stop_reason = 'OTRA' WHERE id = ?", course)).isInstanceOf(Exception.class);
    }

    @Test
    void topicsChaptersAndPiecesCannotBelongToAnotherOwnerThanTheirCourse() {
        UUID ana = newUser();
        UUID luis = newUser();
        UUID course = course(ana, "GENERATING");
        UUID topic = topic(course, ana, 1);
        UUID chapter = chapter(topic, course, ana, 1);

        assertThatThrownBy(() -> topic(course, luis, 2)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> chapter(topic, course, luis, 2)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> piece(course, luis, topic, chapter, "LESSON", 1, "PENDING")).isInstanceOf(Exception.class);
    }

    @Test
    void positionsAndSequenceNumbersAreUniquePerParent() {
        UUID ana = newUser();
        UUID course = course(ana, "GENERATING");
        UUID topic = topic(course, ana, 1);
        UUID chapter = chapter(topic, course, ana, 1);
        piece(course, ana, topic, chapter, "LESSON", 1, "PENDING");

        assertThatThrownBy(() -> topic(course, ana, 1)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> chapter(topic, course, ana, 1)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> piece(course, ana, topic, chapter, "EXERCISES", 1, "PENDING")).isInstanceOf(Exception.class);
    }

    @Test
    void pieceKindAndStatusAreConstrained() {
        UUID ana = newUser();
        UUID course = course(ana, "GENERATING");
        UUID topic = topic(course, ana, 1);
        UUID chapter = chapter(topic, course, ana, 1);

        assertThatThrownBy(() -> piece(course, ana, topic, chapter, "VIDEO", 1, "PENDING")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> piece(course, ana, topic, chapter, "LESSON", 2, "A_MEDIAS")).isInstanceOf(Exception.class);
        piece(course, ana, topic, null, "EXAM", 3, "DONE"); // el examen no tiene capítulo
    }

    @Test
    void deletingACourseDeletesItsWholeContent() {
        UUID ana = newUser();
        UUID course = course(ana, "READY");
        UUID topic = topic(course, ana, 1);
        UUID chapter = chapter(topic, course, ana, 1);
        piece(course, ana, topic, chapter, "LESSON", 1, "DONE");

        owner.update("DELETE FROM course WHERE id = ?", course);

        for (String table : new String[] {"course_topic", "course_chapter", "course_piece"}) {
            assertThat(owner.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
        }
    }

    @Test
    void theAppRoleCanReadAndWriteTheContentButNeverChangeTheSchema() {
        UUID ana = newUser();
        UUID course = course(ana, "GENERATING");
        UUID topic = topic(course, ana, 1);
        UUID chapter = chapter(topic, course, ana, 1);
        piece(course, ana, topic, chapter, "LESSON", 1, "PENDING");

        app.update("UPDATE course_piece SET status = 'DONE', content_md = 'texto' WHERE course_id = ?", course);
        assertThat(app.queryForObject("SELECT status FROM course_piece WHERE course_id = ?", String.class, course)).isEqualTo("DONE");

        assertThatThrownBy(() -> app.execute("DROP TABLE course_piece")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> app.execute("ALTER TABLE course_piece ADD COLUMN extra text")).isInstanceOf(Exception.class);
    }

    @Test
    void theCostOfACourseHasAnIndexOnTheUsageTable() {
        Integer indexes = owner.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename = 'ai_usage' AND indexdef LIKE '%course_id%'", Integer.class);

        assertThat(indexes).isGreaterThanOrEqualTo(1);
    }
}
