package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-3 (ART-003): {@code GET /api/owner/accounts?range=…}. Sembrado: Ana (propietaria), Luis y Hugo (menor) con sesión iniciada hoy; Eva con su último inicio de sesión hace 5 días
 * y Óscar hace 20 (creados con esa edad); una adulta sin verificar y Mía (menor desactivada). «Activo» = al menos un {@code LOGIN_OK} en la ventana (D7).
 */
class OwnerAccountsStatsTest extends OwnerTestBase {

    private Family seeded() throws Exception {
        Family f = family();
        UUID eva = adultRow("eva@example.com", "Eva", "5 days");
        auditAt("LOGIN_OK", eva, "5 days");
        UUID oscar = adultRow("oscar@example.com", "Oscar", "20 days");
        auditAt("LOGIN_OK", oscar, "20 days");
        postPublic("/api/auth/register", registrationBody("pend@example.com", "Pendiente")).andExpect(status().isAccepted());
        String miaId = createStudent(f.ana(), "Mia", "mia");
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?::uuid", miaId);
        auditAt("LOGIN_FAILED", null, "2 days");
        auditAt("LOGIN_FAILED", null, "2 days");
        auditAt("LOGIN_LOCKED", null, "2 days");
        // una sesión revocada y otra caducada de Luis no cuentan como abiertas
        owner.update("INSERT INTO auth_session (user_id, token_hash, expires_at, revoked_at) VALUES (?, repeat('a', 64), now() + interval '1 day', now())", f.luisId());
        owner.update("INSERT INTO auth_session (user_id, token_hash, expires_at) VALUES (?, repeat('b', 64), now() - interval '1 day')", f.luisId());
        return f;
    }

    private JsonNode day(JsonNode series, String key, int daysAgo) {
        String wanted = owner.queryForObject("SELECT ((now() AT TIME ZONE 'utc')::date - ?)::text", String.class, daysAgo);
        for (JsonNode entry : series) if (wanted.equals(entry.get(key).asText())) return entry;
        throw new AssertionError("no hay entrada para " + wanted);
    }

    @Test
    void ac3_accountsByKindAndByStatus() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/accounts").andExpect(status().isOk())
                .andExpect(jsonPath("$.adults").value(5))
                .andExpect(jsonPath("$.minors").value(2))
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(5))
                .andExpect(jsonPath("$.byStatus.PENDING_VERIFICATION").value(1))
                .andExpect(jsonPath("$.byStatus.DEACTIVATED").value(1));
    }

    @Test
    void ac3_activeUsersAreThoseWithALoginInTheWindow() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/accounts").andExpect(jsonPath("$.active.d1").value(3)).andExpect(jsonPath("$.active.d7").value(4)).andExpect(jsonPath("$.active.d30").value(5));
    }

    @Test
    void ac3_openSessionsExcludeRevokedAndExpiredOnes() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/accounts").andExpect(jsonPath("$.openSessions").value(3));
    }

    @Test
    void ac3_signupsAreCountedPerDayAndZeroFilled() throws Exception {
        Family f = seeded();

        String body = ownerGet(f.ana(), "/api/owner/accounts?range=30").andExpect(jsonPath("$.signupsDaily", hasSize(30))).andReturn().getResponse().getContentAsString();
        JsonNode series = json.readTree(body).get("signupsDaily");
        assertThat(day(series, "day", 20).get("count").asInt()).isEqualTo(1);
        assertThat(day(series, "day", 5).get("count").asInt()).isEqualTo(1);
        assertThat(day(series, "day", 0).get("count").asInt()).isEqualTo(5);
        assertThat(day(series, "day", 10).get("count").asInt()).isZero();
        int total = 0;
        for (JsonNode entry : series) total += entry.get("count").asInt();
        assertThat(total).isEqualTo(7);
    }

    @Test
    void ac3_loginsOkFailedAndLockedPerDay() throws Exception {
        Family f = seeded();

        String body = ownerGet(f.ana(), "/api/owner/accounts?range=7").andExpect(jsonPath("$.logins", hasSize(7))).andReturn().getResponse().getContentAsString();
        JsonNode logins = json.readTree(body).get("logins");
        assertThat(day(logins, "day", 0).get("ok").asInt()).isEqualTo(3);
        assertThat(day(logins, "day", 2).get("failed").asInt()).isEqualTo(2);
        assertThat(day(logins, "day", 2).get("locked").asInt()).isEqualTo(1);
        assertThat(day(logins, "day", 2).get("ok").asInt()).isZero();
        assertThat(day(logins, "day", 5).get("ok").asInt()).isEqualTo(1);
    }

    @Test
    void ac3_theRangeLimitsTheSeriesButNotTheCurrentTotals() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/accounts?range=7").andExpect(jsonPath("$.signupsDaily", hasSize(7))).andExpect(jsonPath("$.adults").value(5)).andExpect(jsonPath("$.active.d30").value(5));
    }

    @Test
    void ac3_theIndexOnActionAndDateIsUsedToCountEventsInTheRange() throws Exception {
        seeded();
        // la conexión del propietario no es de pool: el SET y el EXPLAIN tienen que ir en la misma conexión
        String plan = owner.execute((java.sql.Connection c) -> {
            StringBuilder sb = new StringBuilder();
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("SET enable_seqscan = off");
                try (java.sql.ResultSet rs = st.executeQuery(
                        "EXPLAIN SELECT count(*) FROM audit_event WHERE action = 'LOGIN_OK' AND occurred_at >= now() - interval '90 days'")) {
                    while (rs.next()) sb.append(rs.getString(1)).append('\n');
                }
            }
            return sb.toString();
        });

        assertThat(plan).contains("audit_event_action_idx");
    }

    @Test
    void ac3_anEmptyInstallationHasZeroSessionsAndAZeroFilledSeries() throws Exception {
        String ana = logged("ana@example.com");
        owner.update("DELETE FROM auth_session WHERE user_id <> (SELECT id FROM app_user WHERE email = CAST('ana@example.com' AS citext))");

        ownerGet(ana, "/api/owner/accounts").andExpect(status().isOk()).andExpect(jsonPath("$.minors").value(0)).andExpect(jsonPath("$.adults").value(1))
                .andExpect(jsonPath("$.byStatus.DEACTIVATED").value(0)).andExpect(jsonPath("$.signupsDaily", hasSize(30)));
    }
}
