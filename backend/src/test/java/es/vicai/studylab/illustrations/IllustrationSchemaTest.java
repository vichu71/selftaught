package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** F12 AC-9 y AC-2: el esquema de V10: una imagen por tema, con propietario, acotada y que desaparece con su tema o su curso. */
class IllustrationSchemaTest extends IllustrationTestBase {

    private UUID[] course() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        return new UUID[] {course, idOf("luis@example.com"), topicIds(course).get(0)};
    }

    private void insert(UUID course, UUID ownerId, UUID topic, String status, Integer size) {
        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status, size_bytes) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?)", course, ownerId, topic, status, size);
    }

    @Test
    void ac9_theTableExistsWithItsColumnsAndTheCourseHasTheSwitchOffByDefault() throws Exception {
        UUID[] c = course();

        assertThat(owner.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'course_image'", String.class))
                .contains("id", "course_id", "owner_id", "topic_id", "status", "error_kind", "attempts", "regenerations", "alt_text", "mime", "size_bytes", "content", "created_at", "generated_at");
        assertThat(owner.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_name = 'course' AND column_name = 'illustrations'", String.class)).contains("false");
        assertThat(c[0]).isNotNull();
    }

    @Test
    void ac9_aTopicHasAtMostOneImage() throws Exception {
        UUID[] c = course();
        insert(c[0], c[1], c[2], "PENDING", null);

        assertThatThrownBy(() -> insert(c[0], c[1], c[2], "PENDING", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ac9_onlyTheFiveKnownStatesAreAccepted() throws Exception {
        UUID[] c = course();
        assertThatThrownBy(() -> insert(c[0], c[1], c[2], "OTRO", null)).isInstanceOf(DataIntegrityViolationException.class);

        for (String status : new String[] {"PENDING", "DONE", "FAILED", "BLOCKED", "REMOVED"}) {
            owner.update("DELETE FROM course_image WHERE topic_id = ?", c[2]);
            insert(c[0], c[1], c[2], status, null);
        }
    }

    @Test
    void ac9_theSizeOfTheImageIsBoundedByTheDatabaseToo() throws Exception {
        UUID[] c = course();

        assertThatThrownBy(() -> insert(c[0], c[1], c[2], "DONE", 307201)).isInstanceOf(DataIntegrityViolationException.class);
        insert(c[0], c[1], c[2], "DONE", 307200);
    }

    @Test
    void ac9_theImageCannotBelongToAnotherOwnerThanTheCourseAndTheTopic() throws Exception {
        UUID[] c = course();
        session("marta@example.com");
        final UUID stranger = idOf("marta@example.com");

        assertThatThrownBy(() -> insert(c[0], stranger, c[2], "PENDING", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ac9_theTopicMustBelongToTheCourse() throws Exception {
        UUID[] c = course();
        session("marta@example.com");
        UUID otherCourse = ready("marta@example.com");
        UUID foreignTopic = topicIds(otherCourse).get(0);

        assertThatThrownBy(() -> insert(c[0], c[1], foreignTopic, "PENDING", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ac2_theUsageAndActionTablesAcceptTheNewValues() throws Exception {
        UUID[] c = course();

        owner.update("INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status) VALUES (gen_random_uuid(), 1, ?, 'APP', 'IMAGE', 'fake', 'fake-image', 0.05, 'OK')", c[1]);
        owner.update("INSERT INTO course_action (user_id, kind) VALUES (?, 'AI_IMAGE')", c[1]);

        assertThat(count("SELECT count(*) FROM ai_usage WHERE task = 'IMAGE'")).isEqualTo(1);
        // las tareas anteriores siguen valiendo
        owner.update("INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, cost_usd, status) VALUES (gen_random_uuid(), 1, ?, 'APP', 'INTAKE', 'fake', 'fake-1', 0, 'OK')", c[1]);
        owner.update("INSERT INTO course_action (user_id, kind) VALUES (?, 'AI_EDIT')", c[1]);
    }

    @Test
    void ac9_theApplicationRoleCanReadAndWriteImagesButNotChangeTheTable() throws Exception {
        UUID[] c = course();
        insert(c[0], c[1], c[2], "PENDING", null);

        assertThat(app.queryForObject("SELECT count(*) FROM course_image", Integer.class)).isEqualTo(1);
        app.update("UPDATE course_image SET status = 'FAILED' WHERE topic_id = ?", c[2]);
        assertThatThrownBy(() -> app.execute("DROP TABLE course_image")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> app.execute("TRUNCATE course_image")).isInstanceOf(Exception.class);
    }

    @Test
    void ac9_theMigrationDoesNotDeleteDataAndItIsTheNextOne() throws Exception {
        String file = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V10__illustrations.sql"));

        assertThat(file).doesNotContainIgnoringCase("DROP TABLE").doesNotContainIgnoringCase("TRUNCATE").doesNotContainIgnoringCase("DELETE FROM");
        assertThat(owner.queryForObject("SELECT max(version::int) FROM flyway_schema_history WHERE version ~ '^[0-9]+$'", Integer.class)).isGreaterThanOrEqualTo(10);
    }
}
