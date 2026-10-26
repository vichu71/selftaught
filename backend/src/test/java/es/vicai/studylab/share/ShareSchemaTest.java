package es.vicai.studylab.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ART-002 (V9): lo que garantiza la BD por sí sola, aunque el código fallara. */
class ShareSchemaTest extends ShareTestBase {

    private UUID readyFor(String email) throws Exception {
        logged(email);
        return ready(email);
    }

    @Test
    void theCourseGainsVersionOriginAndOriginVersionWithSafeDefaults() throws Exception {
        UUID course = readyFor("ana@example.com");

        var row = owner.queryForMap("SELECT version, origin_id, origin_version FROM course WHERE id = ?", course);

        assertThat(row.get("version")).isEqualTo(1);
        assertThat(row.get("origin_id")).isNull();
        assertThat(row.get("origin_version")).isNull();
    }

    @Test
    void everyPieceStartsNotOutdated() throws Exception {
        UUID course = readyFor("ana@example.com");

        assertThat(count("SELECT count(*) FROM course_piece WHERE course_id = ? AND outdated", course)).isZero();
    }

    @Test
    void theOriginOfACopyIsClearedWhenTheOriginalIsDeletedButTheCopyIsKept() throws Exception {
        UUID course = readyFor("ana@example.com");
        UUID copy = readyFor("luis@example.com");
        owner.update("UPDATE course SET origin_id = ?, origin_version = 1 WHERE id = ?", course, copy);

        owner.update("DELETE FROM course WHERE id = ?", course);

        assertThat(count("SELECT count(*) FROM course WHERE id = ? AND origin_id IS NULL", copy)).isEqualTo(1);
    }

    @Test
    void aCodeCannotBeStoredTwiceNorForACourseOfAnotherOwner() throws Exception {
        UUID course = readyFor("ana@example.com");
        logged("luis@example.com");
        UUID luisId = idOf("luis@example.com");
        String sql = "INSERT INTO share_code (id, course_id, owner_id, code_hash, expires_at) VALUES (gen_random_uuid(), ?, ?, ?, now() + interval '1 day')";
        String hash = "a".repeat(64);
        owner.update(sql, course, idOf("ana@example.com"), hash);

        assertThatThrownBy(() -> owner.update(sql, course, idOf("ana@example.com"), hash)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> owner.update(sql, course, luisId, "b".repeat(64))).isInstanceOf(Exception.class);
    }

    @Test
    void theHashMustLookLikeASha256() throws Exception {
        UUID course = readyFor("ana@example.com");

        assertThatThrownBy(() -> owner.update("INSERT INTO share_code (id, course_id, owner_id, code_hash, expires_at) VALUES (gen_random_uuid(), ?, ?, 'corto', now() + interval '1 day')",
                course, idOf("ana@example.com"))).isInstanceOf(Exception.class);
    }

    @Test
    void deletingACourseDeletesItsCodes() throws Exception {
        UUID course = readyFor("ana@example.com");
        createCode(login("ana@example.com", PASSWORD), course);

        owner.update("DELETE FROM course WHERE id = ?", course);

        assertThat(count("SELECT count(*) FROM share_code")).isZero();
    }

    @Test
    void theActionLogOnlyAcceptsTheThreeKinds() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        for (String kind : List.of("AI_EDIT", "REDEEM_OK", "REDEEM_FAIL")) owner.update("INSERT INTO course_action (user_id, kind) VALUES (?, ?)", ana, kind);

        assertThatThrownBy(() -> owner.update("INSERT INTO course_action (user_id, kind) VALUES (?, 'OTRA')", ana)).isInstanceOf(Exception.class);
        assertThat(count("SELECT count(*) FROM course_action")).isEqualTo(3);
    }

    @Test
    void theApplicationRoleCanDoWhatTheEditorNeedsAndNothingMoreOnTheCodes() throws Exception {
        UUID course = readyFor("ana@example.com");
        UUID topic = topicIds(course).get(0);

        // el rol de la aplicación (no el propietario) puede borrar y actualizar temas y capítulos y manejar códigos y acciones
        app.update("UPDATE course_topic SET title = 'x' WHERE id = ?", topic);
        app.update("DELETE FROM course_chapter WHERE id = ?", chapterIds(topic).get(0));
        app.update("DELETE FROM course_topic WHERE id = ?", topic);
        app.update("INSERT INTO course_action (user_id, kind) VALUES (?, 'AI_EDIT')", idOf("ana@example.com"));
        assertThat(count("SELECT count(*) FROM course_topic WHERE id = ?", topic)).isZero();
    }
}
