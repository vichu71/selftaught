package es.vicai.studylab.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a usuarios y tutela. Todo el SQL es parametrizado. */
@Repository
public class UserRepository {

    public record UserRow(UUID id, UserKind kind, String displayName, String email, String loginId,
                          String passwordHash, UserStatus status) {}

    public record StudentRow(UUID id, String displayName, String loginId, UserStatus status) {}

    private static final String COLUMNS = "id, kind, display_name, email::text AS email, login_id::text AS login_id, "
            + "password_hash, status";

    private static final RowMapper<UserRow> USER = (rs, i) -> new UserRow(
            rs.getObject("id", UUID.class), UserKind.valueOf(rs.getString("kind")), rs.getString("display_name"),
            rs.getString("email"), rs.getString("login_id"), rs.getString("password_hash"),
            UserStatus.valueOf(rs.getString("status")));

    private static final RowMapper<StudentRow> STUDENT = (rs, i) -> new StudentRow(
            rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("login_id"),
            UserStatus.valueOf(rs.getString("status")));

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // `CAST(:x AS citext)`: sin el cast, comparar citext con un parámetro varchar sería sensible a mayúsculas
    public Optional<UserRow> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE email = CAST(:email AS citext)")
                .param("email", email).query(USER).optional();
    }

    public Optional<UserRow> findByLoginId(String loginId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE login_id = CAST(:loginId AS citext)")
                .param("loginId", loginId).query(USER).optional();
    }

    public void insertAdult(UUID id, String email, String displayName, String passwordHash) {
        jdbc.sql("""
                INSERT INTO app_user (id, kind, display_name, email, password_hash, status)
                VALUES (:id, 'ADULT', :name, CAST(:email AS citext), :hash, 'PENDING_VERIFICATION')""")
                .param("id", id).param("name", displayName).param("email", email).param("hash", passwordHash)
                .update();
    }

    public void insertMinor(UUID id, String displayName, String loginId, String passwordHash) {
        jdbc.sql("""
                INSERT INTO app_user (id, kind, display_name, login_id, password_hash, status)
                VALUES (:id, 'MINOR', :name, CAST(:loginId AS citext), :hash, 'ACTIVE')""")
                .param("id", id).param("name", displayName).param("loginId", loginId).param("hash", passwordHash)
                .update();
    }

    public void link(UUID tutorId, UUID studentId) {
        jdbc.sql("INSERT INTO guardianship (tutor_id, student_id) VALUES (:tutor, :student)")
                .param("tutor", tutorId).param("student", studentId).update();
    }

    public void activateIfPending(UUID id) {
        jdbc.sql("UPDATE app_user SET status = 'ACTIVE', updated_at = now() "
                        + "WHERE id = :id AND status = 'PENDING_VERIFICATION'")
                .param("id", id).update();
    }

    public void setPassword(UUID id, String passwordHash) {
        jdbc.sql("UPDATE app_user SET password_hash = :hash, updated_at = now() WHERE id = :id")
                .param("hash", passwordHash).param("id", id).update();
    }

    /** Cambia el estado solo si el actual es {@code from}. Devuelve si hubo cambio (para ser idempotente). */
    public boolean changeStatus(UUID id, UserStatus from, UserStatus to) {
        return jdbc.sql("UPDATE app_user SET status = :to, updated_at = now() WHERE id = :id AND status = :from")
                .param("to", to.name()).param("from", from.name()).param("id", id).update() > 0;
    }

    // ---------- tutela ----------

    public List<StudentRow> studentsOf(UUID tutorId) {
        return jdbc.sql("""
                SELECT u.id, u.display_name, u.login_id::text AS login_id, u.status
                FROM app_user u JOIN guardianship g ON g.student_id = u.id
                WHERE g.tutor_id = :tutor ORDER BY g.created_at, u.id""")
                .param("tutor", tutorId).query(STUDENT).list();
    }

    public Optional<StudentRow> findStudentOf(UUID tutorId, UUID studentId) {
        return jdbc.sql("""
                SELECT u.id, u.display_name, u.login_id::text AS login_id, u.status
                FROM app_user u JOIN guardianship g ON g.student_id = u.id
                WHERE g.tutor_id = :tutor AND u.id = :student""")
                .param("tutor", tutorId).param("student", studentId).query(STUDENT).optional();
    }

    public boolean isTutorOf(UUID tutorId, UUID studentId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM guardianship WHERE tutor_id = :tutor AND student_id = :student)")
                .param("tutor", tutorId).param("student", studentId).query(Boolean.class).single();
    }

    /** Bloquea la fila del tutor para serializar altas simultáneas (nombre único entre sus menores activos). */
    public void lockUser(UUID id) {
        jdbc.sql("SELECT id FROM app_user WHERE id = :id FOR UPDATE").param("id", id).query(UUID.class).optional();
    }

    public boolean activeStudentNameExists(UUID tutorId, String displayName) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM app_user u JOIN guardianship g ON g.student_id = u.id
                    WHERE g.tutor_id = :tutor AND u.status = 'ACTIVE' AND lower(u.display_name) = lower(:name))""")
                .param("tutor", tutorId).param("name", displayName).query(Boolean.class).single();
    }
}
