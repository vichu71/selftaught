package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.AiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ART-002: V7__tutor.sql impone en la BD lo que el código promete. */
class TutorSchemaTest extends AiTestBase {

    private UUID course(UUID ownerId) {
        UUID intake = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, expires_at) VALUES (?, ?, 'Inglés', 'CONSUMED', now() + interval '1 hour')", intake, ownerId);
        owner.update("INSERT INTO course (id, owner_id, intake_id, title, status, sheet) VALUES (?, ?, ?, 'Inglés', 'READY', CAST('{}' AS jsonb))", id, ownerId, intake);
        return id;
    }

    private UUID lesson(UUID courseId, UUID ownerId) {
        UUID topic = UUID.randomUUID();
        owner.update("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (?, ?, ?, 1, 'T', 's')", topic, courseId, ownerId);
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO course_piece (id, course_id, owner_id, topic_id, kind, seq, status) VALUES (?, ?, ?, ?, 'LESSON', 1, 'DONE')", id, courseId, ownerId, topic);
        return id;
    }

    private void message(UUID ownerId, UUID courseId, UUID pieceId, String role, String status, String content) {
        owner.update("INSERT INTO tutor_message (id, owner_id, course_id, piece_id, role, status, content) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)",
                ownerId, courseId, pieceId, role, status, content);
    }

    @Test
    void rolesAndStatusesAreConstrained() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID lesson = lesson(course, ana);

        for (String status : new String[] {"PENDING", "OK", "FAILED", "BLOCKED", "FILTERED"}) message(ana, course, lesson, "USER", status, "x");
        message(ana, course, lesson, "TUTOR", "OK", "x");
        assertThatThrownBy(() -> message(ana, course, lesson, "ROBOT", "OK", "x")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> message(ana, course, lesson, "USER", "A_MEDIAS", "x")).isInstanceOf(Exception.class);
    }

    @Test
    void theContentIsBoundedAndNeverNull() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID lesson = lesson(course, ana);

        message(ana, course, lesson, "TUTOR", "OK", "x".repeat(6000));
        assertThatThrownBy(() -> message(ana, course, lesson, "TUTOR", "OK", "x".repeat(6001))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> message(ana, course, lesson, "USER", "OK", null)).isInstanceOf(Exception.class);
    }

    @Test
    void aMessageCannotBelongToAnotherOwnerThanItsLessonAndCourse() {
        UUID ana = newUser();
        UUID luis = newUser();
        UUID course = course(ana);
        UUID lesson = lesson(course, ana);

        assertThatThrownBy(() -> message(luis, course, lesson, "USER", "OK", "x")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> message(luis, course(luis), lesson, "USER", "OK", "x")).isInstanceOf(Exception.class);
    }

    @Test
    void deletingTheCourseOrTheLessonDeletesItsMessages() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID lesson = lesson(course, ana);
        message(ana, course, lesson, "USER", "OK", "x");

        owner.update("DELETE FROM course WHERE id = ?", course);

        assertThat(owner.queryForObject("SELECT count(*) FROM tutor_message", Integer.class)).isZero();
    }

    @Test
    void theAppRoleCanReadAndWriteButNeverChangeTheSchema() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID lesson = lesson(course, ana);
        message(ana, course, lesson, "USER", "PENDING", "x");

        app.update("UPDATE tutor_message SET status = 'OK' WHERE owner_id = ?", ana);
        app.update("DELETE FROM tutor_message WHERE owner_id = ?", ana);
        assertThat(app.queryForObject("SELECT count(*) FROM tutor_message", Integer.class)).isZero();
        assertThatThrownBy(() -> app.execute("DROP TABLE tutor_message")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> app.execute("ALTER TABLE tutor_message ADD COLUMN extra text")).isInstanceOf(Exception.class);
    }

    @Test
    void theLimitCountsAndTheThreadReadHaveTheirIndexes() {
        Integer indexes = owner.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename = 'tutor_message' AND indexdef LIKE '%owner_id%'", Integer.class);

        assertThat(indexes).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theTutorTaskIsAlreadyAllowedInTheUsageTable() {
        UUID ana = newUser();

        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'TUTOR', 'fake', 'fake-1', 1, 1, false, 0, 'OK', 1)""", ana);

        assertThat(owner.queryForObject("SELECT count(*) FROM ai_usage WHERE task = 'TUTOR'", Integer.class)).isEqualTo(1);
    }
}
