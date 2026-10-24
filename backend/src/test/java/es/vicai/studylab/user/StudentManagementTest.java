package es.vicai.studylab.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.support.IntegrationTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-4, AC-5, AC-7: el adulto da de alta y gestiona a sus menores. */
class StudentManagementTest extends IntegrationTestBase {

    private String ana;

    @BeforeEach
    void anaIsLoggedIn() throws Exception {
        ana = adultSession("ana@example.com");
    }

    // ---------- AC-4 / AC-5: alta y vínculo automático ----------

    @Test
    void ac4_adultCreatesAMinorWithoutEmailAndWithAGeneratedLoginId() throws Exception {
        postAs(ana, "/api/tutor/students", studentBody("Hugo", "hugo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Hugo"))
                .andExpect(jsonPath("$.loginId").value("hugo"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Map<String, Object> row = owner.queryForMap(
                "SELECT kind, status, email, password_hash FROM app_user WHERE kind = 'MINOR'");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("status")).isEqualTo("ACTIVE"); // el menor no verifica correo
        assertThat((String) row.get("password_hash")).startsWith("$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void ac5_theMinorIsAutomaticallyLinkedToTheAdultWhoCreatedIt() throws Exception {
        String studentId = createStudent(ana, "Hugo", "hugo");

        Integer links = owner.queryForObject("""
                SELECT count(*) FROM guardianship g JOIN app_user t ON t.id = g.tutor_id
                WHERE g.student_id = ?::uuid AND t.email = 'ana@example.com'""", Integer.class, studentId);
        assertThat(links).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Hugo|hugo",
            "Ñandú López!|nandu-lopez",
            "  Ana--María |ana-maria",
            "HUGO_2010|hugo-2010"})
    void ac4_loginIdIsDerivedFromTheHintAsAnAsciiSlug(String hint, String expected) throws Exception {
        String id = createStudent(ana, "Alumno", hint);
        assertThat(loginIdOf(id)).isEqualTo(expected);
    }

    @Test
    void ac4_aTakenLoginIdGetsAShortSuffix_evenAcrossFamilies() throws Exception {
        String luis = adultSession("luis@example.com");
        String first = createStudent(luis, "Hugo", "hugo");
        String second = createStudent(ana, "Hugo", "hugo"); // otra familia, mismo nombre: se permite

        assertThat(loginIdOf(first)).isEqualTo("hugo");
        assertThat(loginIdOf(second)).matches("hugo-[a-z0-9]{3}");
        assertThat(loginIdOf(second)).isNotEqualTo(loginIdOf(first));
    }

    @Test
    void ac4_theSameAdultCanUseTheSameHintForDifferentNames() throws Exception {
        String first = createStudent(ana, "Hugo", "hugo");
        String second = createStudent(ana, "Hugo Segundo", "hugo");

        assertThat(loginIdOf(second)).matches("hugo-[a-z0-9]{3}").isNotEqualTo(loginIdOf(first));
    }

    @Test
    void edge_duplicateDisplayNameForTheSameAdultIsRejected_whichCoversDoubleSubmit() throws Exception {
        createStudent(ana, "Hugo", "hugo");

        postAs(ana, "/api/tutor/students", studentBody("hugo", "hugo-b")).andExpect(status().isConflict());

        assertThat(owner.queryForObject("SELECT count(*) FROM app_user WHERE kind = 'MINOR'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void edge_aDeactivatedMinorsNameCanBeReused() throws Exception {
        String old = createStudent(ana, "Hugo", "hugo");
        postAs(ana, "/api/tutor/students/" + old + "/deactivate", null).andExpect(status().isNoContent());

        postAs(ana, "/api/tutor/students", studentBody("Hugo", "hugo")).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "!!!", "x-123456789-123456789-123456789-1"})
    void edge_invalidLoginHintsAreRejected(String hint) throws Exception {
        postAs(ana, "/api/tutor/students", studentBody("Hugo", hint)).andExpect(status().isBadRequest());
    }

    @Test
    void edge_invalidStudentFieldsAreRejectedByTheBackend() throws Exception {
        Map<String, Object> shortPassword = new HashMap<>(studentBody("Hugo", "hugo"));
        shortPassword.put("password", "corta");
        Map<String, Object> hugePassword = new HashMap<>(studentBody("Hugo", "hugo"));
        hugePassword.put("password", "x".repeat(129));
        Map<String, Object> longName = new HashMap<>(studentBody("n".repeat(51), "hugo"));
        Map<String, Object> blankName = new HashMap<>(studentBody("  ", "hugo"));
        Map<String, Object> nullPassword = new HashMap<>(studentBody("Hugo", "hugo"));
        nullPassword.put("password", null);

        for (Map<String, Object> body : new Map[] {shortPassword, hugePassword, longName, blankName, nullPassword}) {
            postAs(ana, "/api/tutor/students", body).andExpect(status().isBadRequest());
        }
        postAs(ana, "/api/tutor/students", Map.of()).andExpect(status().isBadRequest());
        assertThat(owner.queryForObject("SELECT count(*) FROM app_user WHERE kind = 'MINOR'", Integer.class)).isZero();
    }

    @Test
    void edge_createIgnoresPrivilegedFields() throws Exception {
        Map<String, Object> body = new HashMap<>(studentBody("Hugo", "hugo"));
        body.put("kind", "ADULT");
        body.put("email", "hugo@example.com");
        body.put("status", "DEACTIVATED");
        postAs(ana, "/api/tutor/students", body).andExpect(status().isCreated());

        Map<String, Object> row = owner.queryForMap("SELECT kind, status, email FROM app_user WHERE login_id = 'hugo'");
        assertThat(row.get("kind")).isEqualTo("MINOR");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("email")).isNull();
    }

    @Test
    void edge_creatingAStudentRequiresCsrf() throws Exception {
        postAsWithoutCsrf(ana, "/api/tutor/students", studentBody("Hugo", "hugo")).andExpect(status().isForbidden());
    }

    @Test
    void ac5_listShowsOnlyTheCallersOwnMinors() throws Exception {
        String luis = adultSession("luis@example.com");
        createStudent(ana, "Hugo", "hugo");
        createStudent(ana, "Lola", "lola");
        createStudent(luis, "Pablo", "pablo");

        getAs(ana, "/api/tutor/students")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].displayName").value(org.hamcrest.Matchers.containsInAnyOrder("Hugo", "Lola")));
    }

    // ---------- AC-7: restablecer contraseña, dar de baja, reactivar ----------

    @Test
    void ac7_adultResetsAMinorsPasswordAndTheirSessionsAreRevoked() throws Exception {
        String id = createStudent(ana, "Hugo", "hugo");
        String minorSession = login("hugo", PASSWORD);

        postAs(ana, "/api/tutor/students/" + id + "/reset-password", Map.of("newPassword", "Otra-Clave-1234"))
                .andExpect(status().isNoContent());

        getAs(minorSession, "/api/me").andExpect(status().isUnauthorized());
        tryLogin("hugo", PASSWORD).andExpect(status().isUnauthorized());
        tryLogin("hugo", "Otra-Clave-1234").andExpect(status().isOk());
    }

    @Test
    void edge_resetRejectsWeakPasswords() throws Exception {
        String id = createStudent(ana, "Hugo", "hugo");
        postAs(ana, "/api/tutor/students/" + id + "/reset-password", Map.of("newPassword", "corta"))
                .andExpect(status().isBadRequest());
        postAs(ana, "/api/tutor/students/" + id + "/reset-password", Map.of())
                .andExpect(status().isBadRequest());
    }

    @Test
    void ac7_deactivateBlocksLoginAndOpenSessionsButKeepsEverything() throws Exception {
        String id = createStudent(ana, "Hugo", "hugo");
        String minorSession = login("hugo", PASSWORD);

        postAs(ana, "/api/tutor/students/" + id + "/deactivate", null).andExpect(status().isNoContent());

        getAs(minorSession, "/api/me").andExpect(status().isUnauthorized());
        tryLogin("hugo", PASSWORD).andExpect(status().isUnauthorized());
        assertThat(owner.queryForObject("SELECT status FROM app_user WHERE id = ?::uuid", String.class, id))
                .isEqualTo("DEACTIVATED"); // no se borra nada
        assertThat(owner.queryForObject("SELECT count(*) FROM guardianship WHERE student_id = ?::uuid",
                Integer.class, id)).isEqualTo(1);
        getAs(ana, "/api/tutor/students")
                .andExpect(jsonPath("$[0].status").value("DEACTIVATED"));
    }

    @Test
    void ac7_deactivatedMinorsLoginIdStaysReserved() throws Exception {
        String id = createStudent(ana, "Hugo", "hugo");
        postAs(ana, "/api/tutor/students/" + id + "/deactivate", null).andExpect(status().isNoContent());

        String again = createStudent(ana, "Hugo", "hugo");
        assertThat(loginIdOf(again)).matches("hugo-[a-z0-9]{3}");
    }

    @Test
    void ac7_reactivateRestoresAccess_andBothOperationsAreIdempotent() throws Exception {
        String id = createStudent(ana, "Hugo", "hugo");
        postAs(ana, "/api/tutor/students/" + id + "/deactivate", null).andExpect(status().isNoContent());
        postAs(ana, "/api/tutor/students/" + id + "/deactivate", null).andExpect(status().isNoContent());

        postAs(ana, "/api/tutor/students/" + id + "/reactivate", null).andExpect(status().isNoContent());
        postAs(ana, "/api/tutor/students/" + id + "/reactivate", null).andExpect(status().isNoContent());

        tryLogin("hugo", PASSWORD).andExpect(status().isOk());
    }

    // ---------- permisos: solo el tutor de ese menor ----------

    @Test
    void ac7_anotherAdultCannotManageMyMinor_andGetsA404ThatLeaksNothing() throws Exception {
        String id = createStudent(ana, "Hugo", "hugo");
        String luis = adultSession("luis@example.com");
        String missing = UUID.randomUUID().toString();

        for (String action : new String[] {"deactivate", "reactivate"}) {
            String foreign = postAs(luis, "/api/tutor/students/" + id + "/" + action, null)
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            String nonexistent = postAs(luis, "/api/tutor/students/" + missing + "/" + action, null)
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertThat(foreign).isEqualTo(nonexistent);
        }
        postAs(luis, "/api/tutor/students/" + id + "/reset-password", Map.of("newPassword", "Otra-Clave-1234"))
                .andExpect(status().isNotFound());

        tryLogin("hugo", PASSWORD).andExpect(status().isOk()); // nada cambió
    }

    @Test
    void ac7_anAdultIdIsNeverAValidStudentId() throws Exception {
        String anaId = owner.queryForObject("SELECT id::text FROM app_user WHERE email = 'ana@example.com'",
                String.class);
        postAs(ana, "/api/tutor/students/" + anaId + "/deactivate", null).andExpect(status().isNotFound());
        assertThat(owner.queryForObject("SELECT status FROM app_user WHERE email = 'ana@example.com'", String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    void ac10_aMinorCannotUseTutorEndpoints() throws Exception {
        String id = createStudent(ana, "Hugo", "hugo");
        String minor = login("hugo", PASSWORD);

        getAs(minor, "/api/tutor/students").andExpect(status().isForbidden());
        postAs(minor, "/api/tutor/students", studentBody("Otro", "otro")).andExpect(status().isForbidden());
        postAs(minor, "/api/tutor/students/" + id + "/deactivate", null).andExpect(status().isForbidden());
        postAs(minor, "/api/tutor/students/" + id + "/reset-password", Map.of("newPassword", "Otra-Clave-1234"))
                .andExpect(status().isForbidden());
    }
}
