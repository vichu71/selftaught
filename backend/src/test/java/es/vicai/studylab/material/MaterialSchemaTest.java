package es.vicai.studylab.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.AiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ART-002: V3__material.sql impone en la base de datos lo que el código promete. */
class MaterialSchemaTest extends AiTestBase {

    private UUID document(UUID ownerId, String sha) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status)
                VALUES (?, ?, 'a.txt', 'TEXT', 10, ?, 'READY')""", id, ownerId, sha);
        return id;
    }

    private void chunk(UUID documentId, UUID ownerId, int ordinal, String vector) {
        owner.update("""
                INSERT INTO source_chunk (document_id, owner_id, ordinal, start_offset, end_offset, text, embedding, embedding_model)
                VALUES (?, ?, ?, 0, 5, 'hola', CAST(? AS vector), 'm')""", documentId, ownerId, ordinal, vector);
    }

    private static String sha(char c) {
        return String.valueOf(c).repeat(64);
    }

    @Test
    void theVectorExtensionIsAvailableAndComparesByCosine() {
        Double score = owner.queryForObject("SELECT 1 - (CAST('[1,0]' AS vector) <=> CAST('[1,0]' AS vector))", Double.class);

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void aChunkCannotBelongToADifferentOwnerThanItsDocument() {
        UUID ana = newUser();
        UUID luis = newUser();
        UUID doc = document(ana, sha('a'));

        assertThatThrownBy(() -> chunk(doc, luis, 0, "[1,2,3]")).isInstanceOf(Exception.class);
    }

    @Test
    void deletingTheDocumentDeletesItsChunks() {
        UUID ana = newUser();
        UUID doc = document(ana, sha('a'));
        chunk(doc, ana, 0, "[1,2,3]");
        chunk(doc, ana, 1, "[3,2,1]");

        owner.update("DELETE FROM source_document WHERE id = ?", doc);

        assertThat(owner.queryForObject("SELECT count(*) FROM source_chunk", Integer.class)).isZero();
    }

    @Test
    void theEmbeddingColumnIsNotTiedToAFixedDimension() {
        UUID ana = newUser();
        UUID doc = document(ana, sha('a'));

        chunk(doc, ana, 0, "[1,2,3]");
        chunk(doc, ana, 1, "[1,2,3,4,5,6,7,8]");

        assertThat(owner.queryForObject("SELECT count(*) FROM source_chunk", Integer.class)).isEqualTo(2);
    }

    @Test
    void theSameContentCannotBeStoredTwiceForTheSameOwnerButCanForAnother() {
        UUID ana = newUser();
        UUID luis = newUser();
        document(ana, sha('a'));

        document(luis, sha('a'));
        assertThatThrownBy(() -> document(ana, sha('a'))).isInstanceOf(Exception.class);
    }

    @Test
    void ordinalsAreUniquePerDocument() {
        UUID ana = newUser();
        UUID doc = document(ana, sha('a'));
        chunk(doc, ana, 0, "[1,2,3]");

        assertThatThrownBy(() -> chunk(doc, ana, 0, "[1,2,3]")).isInstanceOf(Exception.class);
    }

    @Test
    void statusAndMediaTypeAreConstrained() {
        UUID ana = newUser();

        assertThatThrownBy(() -> owner.update("""
                INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status)
                VALUES (gen_random_uuid(), ?, 'a', 'TEXT', 10, ?, 'INVENTADO')""", ana, sha('b'))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> owner.update("""
                INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status)
                VALUES (gen_random_uuid(), ?, 'a', 'EXE', 10, ?, 'READY')""", ana, sha('c'))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> owner.update("""
                INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status)
                VALUES (gen_random_uuid(), ?, 'a', 'TEXT', 0, ?, 'READY')""", ana, sha('d'))).isInstanceOf(Exception.class);
    }

    @Test
    void theAppRoleCanReadWriteAndDeleteButNeverChangeTheSchema() {
        UUID ana = newUser();
        UUID doc = document(ana, sha('a'));

        app.update("UPDATE source_document SET status = 'ERROR', error_kind = 'NO_TEXT' WHERE id = ?", doc);
        assertThat(app.queryForObject("SELECT count(*) FROM source_document", Integer.class)).isEqualTo(1);
        app.update("DELETE FROM source_document WHERE id = ?", doc);

        assertThatThrownBy(() -> app.execute("DROP TABLE source_chunk")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> app.execute("ALTER TABLE source_document ADD COLUMN extra text")).isInstanceOf(Exception.class);
    }

    @Test
    void deletingAUserThroughTheirAccountIsNotPossibleBecauseAccountsAreNeverDeletedButTheForeignKeyHolds() {
        UUID ana = newUser();
        document(ana, sha('a'));

        assertThatThrownBy(() -> owner.update("INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes,"
                + " content_sha256, status) VALUES (gen_random_uuid(), gen_random_uuid(), 'a', 'TEXT', 10, ?, 'READY')", sha('z')))
                .isInstanceOf(Exception.class); // propietario inexistente
    }
}
