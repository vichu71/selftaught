package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * AC-6, AC-7, AC-8, AC-9 (D2, ART-003): desactivar, reactivar, revocar sesiones y fijar o quitar el tope de un usuario. El propietario es Ana; Luis y Hugo son sus
 * destinatarios. Cada acción es una transacción, se audita con ids y nunca lleva correos ni nombres.
 */
class OwnerActionsTest extends OwnerTestBase {

    private List<Map<String, Object>> events(String action) {
        return owner.queryForList("SELECT actor_user_id, subject_user_id, outcome, details::text AS details FROM audit_event WHERE action = ? ORDER BY id", action);
    }

    // ---------- desactivar y reactivar (AC-6) ----------

    @Test
    void ac6_deactivatingAnAdultKeepsThemOutAndRevokesTheirSessions() throws Exception {
        Family f = family();

        postAs(f.ana(), userPath(f.luisId(), "/deactivate"), null).andExpect(status().isNoContent());

        assertThat(statusOfUser(f.luisId())).isEqualTo("DEACTIVATED");
        getAs(f.luis(), "/api/me").andExpect(status().isUnauthorized());
        tryLogin("luis@example.com", PASSWORD).andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM auth_session WHERE user_id = ? AND revoked_at IS NULL", f.luisId())).isZero();
    }

    @Test
    void ac6_aMinorCanBeDeactivatedAndReactivatedAndAMinorsTutorsMinorsStayActive() throws Exception {
        Family f = family();

        postAs(f.ana(), userPath(f.hugoId(), "/deactivate"), null).andExpect(status().isNoContent());
        assertThat(statusOfUser(f.hugoId())).isEqualTo("DEACTIVATED");
        tryLogin(loginIdOf(f.hugoId().toString()), PASSWORD).andExpect(status().isUnauthorized());

        postAs(f.ana(), userPath(f.hugoId(), "/reactivate"), null).andExpect(status().isNoContent());
        assertThat(statusOfUser(f.hugoId())).isEqualTo("ACTIVE");
        tryLogin(loginIdOf(f.hugoId().toString()), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void ac6_deactivatingATutorDoesNotDeactivateTheirMinors() throws Exception {
        Family f = family();
        String marta = logged("marta@example.com");
        UUID nico = UUID.fromString(createStudent(marta, "Nico", "nico"));

        postAs(f.ana(), userPath(idOf("marta@example.com"), "/deactivate"), null).andExpect(status().isNoContent());

        assertThat(statusOfUser(nico)).isEqualTo("ACTIVE");
        tryLogin(loginIdOf(nico.toString()), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void ac6_repeatingTheSameChangeIsIdempotentAndAuditedOnce() throws Exception {
        Family f = family();

        postAs(f.ana(), userPath(f.luisId(), "/deactivate"), null).andExpect(status().isNoContent());
        postAs(f.ana(), userPath(f.luisId(), "/deactivate"), null).andExpect(status().isNoContent());
        assertThat(events("OWNER_USER_DEACTIVATED")).hasSize(1);

        postAs(f.ana(), userPath(f.luisId(), "/reactivate"), null).andExpect(status().isNoContent());
        postAs(f.ana(), userPath(f.luisId(), "/reactivate"), null).andExpect(status().isNoContent());
        assertThat(events("OWNER_USER_REACTIVATED")).hasSize(1);
    }

    @Test
    void ac6_theOwnerCannotDeactivateOrRevokeThemselves() throws Exception {
        Family f = family();

        for (String suffix : List.of("/deactivate", "/revoke-sessions")) {
            postAs(f.ana(), userPath(f.anaId(), suffix), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_TARGET_SELF"));
        }
        assertThat(statusOfUser(f.anaId())).isEqualTo("ACTIVE");
        getAs(f.ana(), "/api/me").andExpect(status().isOk());
        assertThat(events("OWNER_USER_DEACTIVATED")).isEmpty();
        assertThat(events("OWNER_SESSIONS_REVOKED")).isEmpty();
    }

    @Test
    void ac6_anUnknownOrMalformedIdIsA404ForEveryAction() throws Exception {
        Family f = family();

        for (Action action : actions()) {
            perform(f.ana(), UUID.randomUUID(), action).andExpect(status().isNotFound());
            String path = "/api/owner/users/no-es-un-uuid" + action.suffix();
            switch (action.method()) {
                case "POST" -> postAs(f.ana(), path, action.body()).andExpect(status().isNotFound());
                case "PUT" -> putJson(f.ana(), path, action.body()).andExpect(status().isNotFound());
                default -> deleteWithCsrf(f.ana(), path).andExpect(status().isNotFound());
            }
        }
        assertThat(events("OWNER_USER_DEACTIVATED")).isEmpty();
    }

    @Test
    void ac6_anAccountPendingVerificationCannotBeActivatedFromHere() throws Exception {
        Family f = family();
        postPublic("/api/auth/register", registrationBody("pend@example.com", "Pendiente")).andExpect(status().isAccepted());
        UUID pending = idOf("pend@example.com");

        postAs(f.ana(), userPath(pending, "/reactivate"), null).andExpect(status().isBadRequest());
        assertThat(statusOfUser(pending)).isEqualTo("PENDING_VERIFICATION");
        assertThat(events("OWNER_USER_REACTIVATED")).isEmpty();
    }

    @Test
    void ac6_aNonOwnerWhoTriesChangesNothing() throws Exception {
        Family f = family();

        for (Action action : actions()) perform(f.luis(), f.anaId(), action).andExpect(status().isNotFound());
        for (Action action : actions()) perform(f.hugo(), f.luisId(), action).andExpect(status().isNotFound());

        assertThat(statusOfUser(f.anaId())).isEqualTo("ACTIVE");
        assertThat(overrideOf(f.anaId())).isNull();
        assertThat(events("OWNER_USER_DEACTIVATED")).isEmpty();
        getAs(f.ana(), "/api/me").andExpect(status().isOk());
    }

    // ---------- revocar sesiones (AC-8) ----------

    @Test
    void ac8_revokingSessionsLogsTheUserOutButKeepsTheAccountActive() throws Exception {
        Family f = family();
        String second = login("luis@example.com", PASSWORD); // dos sesiones abiertas

        postAs(f.ana(), userPath(f.luisId(), "/revoke-sessions"), null).andExpect(status().isNoContent());

        getAs(f.luis(), "/api/me").andExpect(status().isUnauthorized());
        getAs(second, "/api/me").andExpect(status().isUnauthorized());
        assertThat(statusOfUser(f.luisId())).isEqualTo("ACTIVE");
        login("luis@example.com", PASSWORD); // puede volver a entrar
        getAs(f.ana(), "/api/me").andExpect(status().isOk());
    }

    // ---------- tope por usuario (AC-7) ----------

    @Test
    void ac7_settingTheLimitStoresItWithWhoSetItAndAcceptsTheBounds() throws Exception {
        Family f = family();

        putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("limitUsd", 7.5)).andExpect(status().isNoContent());
        assertThat(overrideOf(f.luisId())).isEqualByComparingTo("7.50");
        assertThat(owner.queryForObject("SELECT set_by FROM user_limit_override WHERE user_id = ?", UUID.class, f.luisId())).isEqualTo(f.anaId());

        putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("limitUsd", 0)).andExpect(status().isNoContent());
        assertThat(overrideOf(f.luisId())).isEqualByComparingTo("0.00");
        putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("limitUsd", 500)).andExpect(status().isNoContent());
        assertThat(overrideOf(f.luisId())).isEqualByComparingTo("500.00");
        assertThat(count("SELECT count(*) FROM user_limit_override")).isEqualTo(1); // reemplaza, no duplica
    }

    @Test
    void ac7_invalidLimitsAreRejectedWith400AndStoreNothing() throws Exception {
        Family f = family();

        for (Object bad : new Object[] {-1, -0.01, 500.01, 1000, 1.234, "abc", "", true}) {
            putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("limitUsd", bad)).andExpect(status().isBadRequest());
        }
        putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of()).andExpect(status().isBadRequest());
        putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("otro", 3)).andExpect(status().isBadRequest());
        assertThat(overrideOf(f.luisId())).isNull();
        assertThat(events("OWNER_LIMIT_SET")).isEmpty();
    }

    @Test
    void ac7_clearingTheLimitGoesBackToTheDefaultAndIsIdempotent() throws Exception {
        Family f = family();
        putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("limitUsd", 7)).andExpect(status().isNoContent());

        deleteWithCsrf(f.ana(), userPath(f.luisId(), "/limit")).andExpect(status().isNoContent());
        deleteWithCsrf(f.ana(), userPath(f.luisId(), "/limit")).andExpect(status().isNoContent());

        assertThat(overrideOf(f.luisId())).isNull();
        assertThat(events("OWNER_LIMIT_CLEARED")).hasSize(1);
        ownerGet(f.ana(), "/api/owner/users?kind=ADULT").andExpect(jsonPath("$.items[?(@.id=='" + f.luisId() + "')].limitIsCustom").value(org.hamcrest.Matchers.hasItem(false)));
    }

    // ---------- auditoría y logs (AC-9) ----------

    @Test
    void ac9_everyActionIsAuditedWithActorSubjectAndNoPersonalData() throws Exception {
        Family f = family();

        postAs(f.ana(), userPath(f.luisId(), "/deactivate"), null).andExpect(status().isNoContent());
        postAs(f.ana(), userPath(f.luisId(), "/reactivate"), null).andExpect(status().isNoContent());
        postAs(f.ana(), userPath(f.luisId(), "/revoke-sessions"), null).andExpect(status().isNoContent());
        putJson(f.ana(), userPath(f.hugoId(), "/limit"), Map.of("limitUsd", 3.25)).andExpect(status().isNoContent());
        deleteWithCsrf(f.ana(), userPath(f.hugoId(), "/limit")).andExpect(status().isNoContent());

        for (String action : List.of("OWNER_USER_DEACTIVATED", "OWNER_USER_REACTIVATED", "OWNER_SESSIONS_REVOKED", "OWNER_LIMIT_SET", "OWNER_LIMIT_CLEARED")) {
            List<Map<String, Object>> found = events(action);
            assertThat(found).as(action).hasSize(1);
            assertThat(found.get(0).get("actor_user_id")).isEqualTo(f.anaId());
            assertThat(found.get(0).get("outcome")).isEqualTo("OK");
        }
        assertThat(events("OWNER_LIMIT_SET").get(0).get("subject_user_id")).isEqualTo(f.hugoId());
        assertThat(String.valueOf(events("OWNER_LIMIT_SET").get(0).get("details"))).contains("3.25");
        String everything = String.join(" ", owner.queryForList("SELECT coalesce(details::text, '') FROM audit_event WHERE action LIKE 'OWNER_%'", String.class));
        assertThat(everything).doesNotContain("@example.com").doesNotContain("Hugo").doesNotContain("Adulto");
    }

    @Test
    void ac9_aRejectedActionLeavesNoActionEvent() throws Exception {
        Family f = family();

        postAs(f.ana(), userPath(f.anaId(), "/deactivate"), null).andExpect(status().isBadRequest());
        putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("limitUsd", -3)).andExpect(status().isBadRequest());
        postAs(f.ana(), userPath(UUID.randomUUID(), "/deactivate"), null).andExpect(status().isNotFound());

        assertThat(owner.queryForList("SELECT action FROM audit_event WHERE action LIKE 'OWNER_%'", String.class)).isEmpty();
    }

    @Test
    void ac9_aFailedActionLeavesNothingBehindBecauseTheChangeAndTheAuditShareATransaction() throws Exception {
        Family f = family();
        // una restricción del esquema hace fallar el alta de la fila: el estado y la auditoría no pueden quedar a medias
        owner.execute("ALTER TABLE audit_event ADD CONSTRAINT zz_no_owner_events CHECK (action NOT LIKE 'OWNER_%') NOT VALID");
        try {
            postAs(f.ana(), userPath(f.luisId(), "/deactivate"), null).andExpect(status().is5xxServerError());
        } finally {
            owner.execute("ALTER TABLE audit_event DROP CONSTRAINT zz_no_owner_events");
        }

        assertThat(statusOfUser(f.luisId())).isEqualTo("ACTIVE");
        getAs(f.luis(), "/api/me").andExpect(status().isOk());
    }

    @Test
    void ac9_logsNeverContainEmailsNamesOrLimitsOfThePeopleTheOwnerTouches() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            Family f = family();
            postAs(f.ana(), userPath(f.luisId(), "/deactivate"), null).andReturn();
            putJson(f.ana(), userPath(f.luisId(), "/limit"), Map.of("limitUsd", 4.5)).andReturn();
            ownerGet(f.ana(), "/api/owner/users").andReturn();
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain("luis@example.com").doesNotContain("Adulto luis");
    }

    @Test
    void ac15_theActionsOnlyTouchTheirOwnTables() throws Exception {
        Family f = family();
        int courses = rows("course");
        int pieces = rows("course_piece");
        BigDecimal spent = owner.queryForObject("SELECT coalesce(sum(cost_usd), 0) FROM ai_usage", BigDecimal.class);
        int usageRows = rows("ai_usage");

        postAs(f.ana(), userPath(f.hugoId(), "/deactivate"), null).andExpect(status().isNoContent());
        putJson(f.ana(), userPath(f.hugoId(), "/limit"), Map.of("limitUsd", 3)).andExpect(status().isNoContent());
        postAs(f.ana(), userPath(f.hugoId(), "/revoke-sessions"), null).andExpect(status().isNoContent());

        assertThat(rows("course")).isEqualTo(courses);
        assertThat(rows("course_piece")).isEqualTo(pieces);
        assertThat(rows("ai_usage")).isEqualTo(usageRows);
        assertThat(owner.queryForObject("SELECT coalesce(sum(cost_usd), 0) FROM ai_usage", BigDecimal.class)).isEqualByComparingTo(spent);
    }
}
