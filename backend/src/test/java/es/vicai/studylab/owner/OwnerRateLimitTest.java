package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-10 (ADR-89): 120 consultas y 60 acciones por hora y propietario, contadas en la BD (nunca por IP). La que pasa del límite responde 429 y no ejecuta nada. */
class OwnerRateLimitTest extends OwnerTestBase {

    private void fill(UUID ownerId, String kind, int rows, String ago) {
        owner.update("INSERT INTO owner_action (owner_id, kind, created_at) SELECT ?, ?, now() - CAST(? AS interval) FROM generate_series(1, ?)", ownerId, kind, ago, rows);
    }

    private int rowsOf(String kind) {
        return count("SELECT count(*) FROM owner_action WHERE kind = ?", kind);
    }

    @Test
    void ac10_everyQueryIsCountedAndThe121stInAnHourIsRejectedWithoutRunning() throws Exception {
        Family f = family();
        fill(f.anaId(), "QUERY", 119, "1 minute");
        int usage = rows("ai_usage");

        ownerGet(f.ana(), "/api/owner/ai").andExpect(status().isOk()); // la 120.ª
        assertThat(rowsOf("QUERY")).isEqualTo(120);

        ownerGet(f.ana(), "/api/owner/ai").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OWNER_RATE_LIMIT"));
        ownerGet(f.ana(), "/api/owner/users").andExpect(status().isTooManyRequests());
        assertThat(rowsOf("QUERY")).isEqualTo(120); // la rechazada no cuenta ni deja fila
        assertThat(rows("ai_usage")).isEqualTo(usage);
    }

    @Test
    void ac10_everyReadEndpointCountsAsAQuery() throws Exception {
        Family f = family();
        int before = rowsOf("QUERY");

        for (String path : REPORTS) ownerGet(f.ana(), path).andExpect(status().isOk());
        ownerGet(f.ana(), "/api/owner/users").andExpect(status().isOk());

        assertThat(rowsOf("QUERY")).isEqualTo(before + REPORTS.size() + 1);
    }

    @Test
    void ac10_callsOlderThanAnHourDoNotCount() throws Exception {
        Family f = family();
        fill(f.anaId(), "QUERY", 300, "61 minutes");

        ownerGet(f.ana(), "/api/owner/ai").andExpect(status().isOk());
    }

    @Test
    void ac10_actionsHaveTheirOwnLimitOf60AnHourAndTheRejectedOneChangesNothing() throws Exception {
        Family f = family();
        fill(f.anaId(), "ACTION", 59, "1 minute");

        postAs(f.ana(), userPath(f.luisId(), "/revoke-sessions"), null).andExpect(status().isNoContent()); // la 60.ª
        postAs(f.ana(), userPath(f.hugoId(), "/deactivate"), null).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OWNER_RATE_LIMIT"));

        assertThat(statusOfUser(f.hugoId())).isEqualTo("ACTIVE");
        assertThat(rowsOf("ACTION")).isEqualTo(60);
        assertThat(auditCount("OWNER_USER_DEACTIVATED")).isZero();
    }

    @Test
    void ac10_theTwoLimitsAreIndependent() throws Exception {
        Family f = family();
        fill(f.anaId(), "QUERY", 120, "1 minute");
        postAs(f.ana(), userPath(f.luisId(), "/revoke-sessions"), null).andExpect(status().isNoContent());

        fill(f.anaId(), "ACTION", 59, "1 minute");
        ownerGet(f.ana(), "/api/owner/users").andExpect(status().isTooManyRequests());
        postAs(f.ana(), userPath(f.luisId(), "/revoke-sessions"), null).andExpect(status().isTooManyRequests());
    }

    @Test
    void ac10_theLimitIsPerOwnerAccountNotPerIp() throws Exception {
        Family f = family();
        fill(f.anaId(), "QUERY", 120, "1 minute");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/owner/ai")
                .cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, f.ana())).with(req -> { req.setRemoteAddr("203.0.113.9"); return req; }))
                .andExpect(status().isTooManyRequests());
        // y quien no es propietario nunca cuenta ni deja fila, aunque insista
        for (int i = 0; i < 5; i++) ownerGet(f.luis(), "/api/owner/ai").andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM owner_action WHERE owner_id = ?", f.luisId())).isZero();
    }

    @Test
    void ac10_oldRowsOfMoreThanTwoHoursArePrunedWhenANewOneIsWritten() throws Exception {
        Family f = family();
        fill(f.anaId(), "QUERY", 5, "3 hours");
        fill(f.anaId(), "ACTION", 5, "3 hours");

        ownerGet(f.ana(), "/api/owner/prices").andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM owner_action WHERE created_at < now() - interval '2 hours'")).isZero();
    }

    @Test
    void ac10_theTooManyRequestsAnswerCarriesAClearMessageWithoutInternals() throws Exception {
        Family f = family();
        fill(f.anaId(), "QUERY", 120, "1 minute");

        String body = ownerGet(f.ana(), "/api/owner/ai").andExpect(status().isTooManyRequests()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("OWNER_RATE_LIMIT").doesNotContainIgnoringCase("exception").doesNotContainIgnoringCase("owner_action").doesNotContainIgnoringCase("select");
    }
}
