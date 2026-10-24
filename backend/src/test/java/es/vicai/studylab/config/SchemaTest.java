package es.vicai.studylab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** AC-12 (base técnica), AC-5 y AC-8 a nivel de BD: las restricciones no dependen solo del código. */
class SchemaTest extends IntegrationTestBase {

    private void insertUser(String kind, String email, String loginId, String status) {
        owner.update("""
                INSERT INTO app_user (id, kind, display_name, email, login_id, password_hash, status)
                VALUES (?::uuid, ?, 'X', ?, ?, 'hash', ?)""",
                UUID.randomUUID().toString(), kind, email, loginId, status);
    }

    @Test
    void ac12_pgvectorExtensionIsInstalled() {
        assertThat(owner.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ac12_migrationsRunWithFlyway() {
        assertThat(owner.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(owner.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class))
                .isZero();
    }

    @Test
    void ac12_theApplicationRoleCannotChangeTheSchema() {
        assertThatThrownBy(() -> app.execute("CREATE TABLE intruso (a int)")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> app.execute("DROP TABLE app_user")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void ac8_anAdultMustHaveAnEmail() {
        assertThatThrownBy(() -> insertUser("ADULT", null, null, "ACTIVE")).isInstanceOf(DataAccessException.class);
        insertUser("ADULT", "ok@example.com", null, "ACTIVE");
    }

    @Test
    void ac6_aMinorMustHaveALoginIdAndNeverAnEmail() {
        assertThatThrownBy(() -> insertUser("MINOR", null, null, "ACTIVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertUser("MINOR", "m@example.com", "hugo", "ACTIVE"))
                .isInstanceOf(DataAccessException.class);
        insertUser("MINOR", null, "hugo", "ACTIVE");
    }

    @Test
    void ac1_emailAndLoginIdAreUniqueRegardlessOfCase() {
        insertUser("ADULT", "ana@example.com", null, "ACTIVE");
        insertUser("MINOR", null, "hugo", "ACTIVE");

        assertThatThrownBy(() -> insertUser("ADULT", "ANA@example.com", null, "ACTIVE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertUser("MINOR", null, "hugo", "ACTIVE")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void edge_onlyKnownKindsAndStatusesAreAccepted() {
        assertThatThrownBy(() -> insertUser("ADMIN", "a@example.com", null, "ACTIVE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertUser("ADULT", "b@example.com", null, "BORRADO"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void ac5_aMinorHasExactlyOneTutorAndAdultsCannotBeStudents() {
        insertUser("ADULT", "ana@example.com", null, "ACTIVE");
        insertUser("ADULT", "luis@example.com", null, "ACTIVE");
        insertUser("MINOR", null, "hugo", "ACTIVE");
        String ana = owner.queryForObject("SELECT id::text FROM app_user WHERE email = 'ana@example.com'", String.class);
        String luis = owner.queryForObject("SELECT id::text FROM app_user WHERE email = 'luis@example.com'", String.class);
        String hugo = owner.queryForObject("SELECT id::text FROM app_user WHERE login_id = 'hugo'", String.class);

        owner.update("INSERT INTO guardianship (id, tutor_id, student_id) VALUES (gen_random_uuid(), ?::uuid, ?::uuid)",
                ana, hugo);
        assertThatThrownBy(() -> owner.update(
                "INSERT INTO guardianship (id, tutor_id, student_id) VALUES (gen_random_uuid(), ?::uuid, ?::uuid)",
                luis, hugo)).isInstanceOf(DataAccessException.class); // un solo tutor por menor en v1
        assertThatThrownBy(() -> owner.update(
                "INSERT INTO guardianship (id, tutor_id, student_id) VALUES (gen_random_uuid(), ?::uuid, ?::uuid)",
                hugo, luis)).isInstanceOf(DataAccessException.class); // tutor debe ser adulto y alumno menor
    }
}
