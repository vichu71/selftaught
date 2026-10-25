package es.vicai.studylab.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.AiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ART-002: V6__study.sql impone en la BD lo que el código promete. */
class StudySchemaTest extends AiTestBase {

    private UUID course(UUID ownerId) {
        UUID intake = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, expires_at) VALUES (?, ?, 'Inglés', 'CONSUMED', now() + interval '1 hour')", intake, ownerId);
        owner.update("INSERT INTO course (id, owner_id, intake_id, title, status, sheet) VALUES (?, ?, ?, 'Inglés', 'READY', CAST('{}' AS jsonb))", id, ownerId, intake);
        return id;
    }

    private UUID piece(UUID courseId, UUID ownerId, String kind, int seq) {
        UUID topic = UUID.randomUUID();
        owner.update("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (?, ?, ?, ?, 'T', 's')", topic, courseId, ownerId, seq);
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO course_piece (id, course_id, owner_id, topic_id, kind, seq, status) VALUES (?, ?, ?, ?, ?, ?, 'DONE')", id, courseId, ownerId, topic, kind, seq);
        return id;
    }

    private void done(UUID pieceId, UUID courseId, UUID ownerId) {
        owner.update("INSERT INTO lesson_progress (owner_id, piece_id, course_id) VALUES (?, ?, ?)", ownerId, pieceId, courseId);
    }

    private void exercise(UUID pieceId, UUID courseId, UUID ownerId, int item, String selfResult) {
        owner.update("INSERT INTO exercise_attempt (id, owner_id, course_id, piece_id, item_index, answer, self_result) VALUES (gen_random_uuid(), ?, ?, ?, ?, 'r', ?)",
                ownerId, courseId, pieceId, item, selfResult);
    }

    private void exam(UUID pieceId, UUID courseId, UUID ownerId, String status, Integer correct, Integer total) {
        owner.update("INSERT INTO exam_attempt (id, owner_id, course_id, piece_id, status, correct, total) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)",
                ownerId, courseId, pieceId, status, correct, total);
    }

    @Test
    void aLessonIsMarkedOncePerUserAndHasACompletionDate() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID lesson = piece(course, ana, "LESSON", 1);
        done(lesson, course, ana);

        assertThatThrownBy(() -> done(lesson, course, ana)).isInstanceOf(Exception.class);
        assertThat(owner.queryForObject("SELECT completed_at IS NOT NULL FROM lesson_progress", Boolean.class)).isTrue();
    }

    @Test
    void studyDataCannotBelongToAnotherOwnerThanItsPieceAndCourse() {
        UUID ana = newUser();
        UUID luis = newUser();
        UUID course = course(ana);
        UUID lesson = piece(course, ana, "LESSON", 1);
        UUID exercises = piece(course, ana, "EXERCISES", 2);
        UUID exam = piece(course, ana, "EXAM", 3);

        assertThatThrownBy(() -> done(lesson, course, luis)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> exercise(exercises, course, luis, 0, null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> exam(exam, course, luis, "STARTED", null, null)).isInstanceOf(Exception.class);
    }

    @Test
    void exerciseAttemptsAreConstrained() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID exercises = piece(course, ana, "EXERCISES", 1);

        exercise(exercises, course, ana, 0, null);
        exercise(exercises, course, ana, 7, "PARTIAL");
        assertThatThrownBy(() -> exercise(exercises, course, ana, 8, null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> exercise(exercises, course, ana, -1, null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> exercise(exercises, course, ana, 0, "MAL")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> owner.update("UPDATE exercise_attempt SET ai_result = 'TAL'")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> owner.update("UPDATE exercise_attempt SET answer = ?", "x".repeat(2001))).isInstanceOf(Exception.class);
    }

    @Test
    void examAttemptsAreConstrained() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID exam = piece(course, ana, "EXAM", 1);

        exam(exam, course, ana, "STARTED", null, null);
        exam(exam, course, ana, "SUBMITTED", 3, 5);
        assertThatThrownBy(() -> exam(exam, course, ana, "A_MEDIAS", null, null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> exam(exam, course, ana, "SUBMITTED", 6, 5)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> exam(exam, course, ana, "SUBMITTED", -1, 5)).isInstanceOf(Exception.class);
    }

    @Test
    void deletingACourseDeletesItsWholeStudyData() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID lesson = piece(course, ana, "LESSON", 1);
        UUID exercises = piece(course, ana, "EXERCISES", 2);
        UUID exam = piece(course, ana, "EXAM", 3);
        done(lesson, course, ana);
        exercise(exercises, course, ana, 0, null);
        exam(exam, course, ana, "SUBMITTED", 5, 5);

        owner.update("DELETE FROM course WHERE id = ?", course);

        for (String table : new String[] {"lesson_progress", "exercise_attempt", "exam_attempt"}) assertThat(owner.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
    }

    @Test
    void theAppRoleCanReadAndWriteButNeverChangeTheSchema() {
        UUID ana = newUser();
        UUID course = course(ana);
        UUID lesson = piece(course, ana, "LESSON", 1);
        done(lesson, course, ana);

        app.update("DELETE FROM lesson_progress WHERE piece_id = ?", lesson);
        app.update("INSERT INTO lesson_progress (owner_id, piece_id, course_id) VALUES (?, ?, ?)", ana, lesson, course);
        assertThat(app.queryForObject("SELECT count(*) FROM lesson_progress", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> app.execute("DROP TABLE exam_attempt")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> app.execute("ALTER TABLE exercise_attempt ADD COLUMN extra text")).isInstanceOf(Exception.class);
    }

    @Test
    void theCountsOfSubmissionsHaveTheirIndexes() {
        Integer indexes = owner.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename IN ('exercise_attempt', 'exam_attempt') AND indexdef LIKE '%owner_id%'", Integer.class);

        assertThat(indexes).isGreaterThanOrEqualTo(2);
    }
}
