package es.vicai.studylab.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.AiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ART-002: V4__course.sql impone en la base de datos lo que el código promete. */
class CourseSchemaTest extends AiTestBase {

    private UUID intake(UUID ownerId, String status) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO course_intake (id, owner_id, topic, status, expires_at)
                VALUES (?, ?, 'Inglés', ?, now() + interval '1 hour')""", id, ownerId, status);
        return id;
    }

    private UUID course(UUID ownerId, UUID intakeId, String status) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO course (id, owner_id, intake_id, title, status, sheet)
                VALUES (?, ?, ?, 'Inglés', ?, CAST('{"schemaVersion":1}' AS jsonb))""", id, ownerId, intakeId, status);
        return id;
    }

    private UUID document(UUID ownerId, String sha) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status)
                VALUES (?, ?, 'a.txt', 'TEXT', 10, ?, 'READY')""", id, ownerId, sha);
        return id;
    }

    @Test
    void aCourseKeepsItsSheetAsJsonAndStartsPending() {
        UUID ana = newUser();
        UUID id = course(ana, intake(ana, "CONSUMED"), "PENDING_GENERATION");

        assertThat(owner.queryForObject("SELECT sheet->>'schemaVersion' FROM course WHERE id = ?", String.class, id)).isEqualTo("1");
    }

    @Test
    void thereIsOnlyOneCoursePerIntakeSoAcceptingTwiceCannotDuplicate() {
        UUID ana = newUser();
        UUID intakeId = intake(ana, "CONSUMED");
        course(ana, intakeId, "PENDING_GENERATION");

        assertThatThrownBy(() -> course(ana, intakeId, "PENDING_GENERATION")).isInstanceOf(Exception.class);
    }

    @Test
    void statusesAreConstrained() {
        UUID ana = newUser();

        assertThatThrownBy(() -> course(ana, intake(ana, "CONSUMED"), "INVENTADO")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> intake(ana, "INVENTADO")).isInstanceOf(Exception.class);
        for (String ok : new String[] {"PENDING_GENERATION", "GENERATING", "READY", "FAILED"}) course(ana, intake(ana, "CONSUMED"), ok);
    }

    @Test
    void aCourseCanOnlyUseDocumentsOfItsOwnOwner() {
        UUID ana = newUser();
        UUID luis = newUser();
        UUID anaCourse = course(ana, intake(ana, "CONSUMED"), "PENDING_GENERATION");
        UUID luisDoc = document(luis, "a".repeat(64));

        assertThatThrownBy(() -> owner.update("INSERT INTO course_document (course_id, document_id, owner_id) VALUES (?, ?, ?)", anaCourse, luisDoc, ana))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> owner.update("INSERT INTO course_document (course_id, document_id, owner_id) VALUES (?, ?, ?)", anaCourse, luisDoc, luis))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deletingADocumentRemovesOnlyItsLinkAndDeletingACourseRemovesItsLinks() {
        UUID ana = newUser();
        UUID c = course(ana, intake(ana, "CONSUMED"), "PENDING_GENERATION");
        UUID d1 = document(ana, "a".repeat(64));
        UUID d2 = document(ana, "b".repeat(64));
        owner.update("INSERT INTO course_document (course_id, document_id, owner_id) VALUES (?, ?, ?), (?, ?, ?)", c, d1, ana, c, d2, ana);

        owner.update("DELETE FROM source_document WHERE id = ?", d1);
        assertThat(owner.queryForObject("SELECT count(*) FROM course_document", Integer.class)).isEqualTo(1);
        assertThat(owner.queryForObject("SELECT count(*) FROM course", Integer.class)).isEqualTo(1);

        owner.update("DELETE FROM course WHERE id = ?", c);
        assertThat(owner.queryForObject("SELECT count(*) FROM course_document", Integer.class)).isZero();
        assertThat(owner.queryForObject("SELECT count(*) FROM source_document", Integer.class)).isEqualTo(1);
    }

    @Test
    void aBlockedIntakeKeepsItsTopicAndCategoryAndHasNoQuestions() {
        UUID ana = newUser();
        UUID id = UUID.randomUUID();

        owner.update("""
                INSERT INTO course_intake (id, owner_id, topic, status, blocked_category, expires_at)
                VALUES (?, ?, 'tema bloqueado', 'BLOCKED', 'VIOLENCE', now() + interval '1 hour')""", id, ana);

        assertThat(owner.queryForObject("SELECT blocked_category FROM course_intake WHERE id = ?", String.class, id)).isEqualTo("VIOLENCE");
        assertThat(owner.queryForObject("SELECT questions FROM course_intake WHERE id = ?", Object.class, id)).isNull();
    }

    @Test
    void theCategoryOfABlockedIntakeIsConstrained() {
        UUID ana = newUser();

        assertThatThrownBy(() -> owner.update("""
                INSERT INTO course_intake (id, owner_id, topic, status, blocked_category, expires_at)
                VALUES (gen_random_uuid(), ?, 'x', 'BLOCKED', 'INVENTADA', now() + interval '1 hour')""", ana)).isInstanceOf(Exception.class);
    }

    @Test
    void theAppRoleReadsAndWritesButNeverChangesTheSchema() {
        UUID ana = newUser();
        UUID intakeId = intake(ana, "OPEN");

        app.update("UPDATE course_intake SET status = 'CONSUMED' WHERE id = ?", intakeId);
        assertThat(app.queryForObject("SELECT status FROM course_intake WHERE id = ?", String.class, intakeId)).isEqualTo("CONSUMED");
        app.update("""
                INSERT INTO course (id, owner_id, intake_id, title, status, sheet)
                VALUES (gen_random_uuid(), ?, ?, 'x', 'PENDING_GENERATION', CAST('{}' AS jsonb))""", ana, intakeId);

        assertThatThrownBy(() -> app.execute("DROP TABLE course")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> app.execute("ALTER TABLE course ADD COLUMN extra text")).isInstanceOf(Exception.class);
    }

    @Test
    void theUsageTableNowAcceptsTheIntakeTask() {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, NULL, 'APP', 'INTAKE', 'fake', 'fake-1', 0, 0, false, 0, 'OK', 1)""");

        assertThat(owner.queryForObject("SELECT count(*) FROM ai_usage WHERE task = 'INTAKE'", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, NULL, 'APP', 'OTRA', 'fake', 'fake-1', 0, 0, false, 0, 'OK', 1)""")).isInstanceOf(Exception.class);
    }
}
