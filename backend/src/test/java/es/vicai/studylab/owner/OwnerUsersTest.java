package es.vicai.studylab.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-4, AC-5 (D3, D4, ART-003): {@code GET /api/owner/users?kind=&status=&sort=spend|activity|created&dir=&page=&size=}. Cada elemento lleva solo
 * {@code id, kind, status, displayName?, email?, loginId?, createdAt, lastActivityAt, monthSpendUsd, limitUsd, limitIsCustom, courses, tutorId?}.
 * Familia: Ana (propietaria, 0,30 USD este mes), Hugo (menor de Ana, 0,80 y un curso) y Luis (adulto, 0). Topes de test: menor 1,00, adulto 5,00.
 */
class OwnerUsersTest extends OwnerTestBase {

    private Family seeded() throws Exception {
        Family f = quietFamily();
        aiCall(f.anaId(), "anthropic", "m1", "LESSON", "OK", "0.30", 1, 1, 1, "1 hour");
        aiCall(f.hugoId(), "anthropic", "m1", "TUTOR", "OK", "0.80", 1, 1, 1, "1 hour");
        aiCall(f.hugoId(), "anthropic", "m1", "TUTOR", "OK", "9.00", 1, 1, 1, "2 months"); // otro mes: no cuenta
        return f;
    }

    private String items(String session, String query) throws Exception {
        return ownerGet(session, "/api/owner/users" + query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void ac5_anAdultShowsNameAndEmailAndNeverAnAccessIdOrATutor() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/users?kind=ADULT&sort=created&dir=asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + f.luisId() + "')].displayName").value(org.hamcrest.Matchers.hasItem("Adulto luis")))
                .andExpect(jsonPath("$.items[?(@.id=='" + f.luisId() + "')].email").value(org.hamcrest.Matchers.hasItem("luis@example.com")))
                .andExpect(jsonPath("$.items[?(@.id=='" + f.luisId() + "')].loginId").isEmpty())
                .andExpect(jsonPath("$.items[?(@.id=='" + f.luisId() + "')].tutorId").isEmpty())
                .andExpect(jsonPath("$.items[?(@.id=='" + f.luisId() + "')].limitUsd").value(org.hamcrest.Matchers.hasItem(5.0)));
    }

    @Test
    void ac4_aMinorShowsTheirAccessIdAndTutorButNeitherNameNorEmail() throws Exception {
        Family f = seeded();
        String hugoLogin = loginIdOf(f.hugoId().toString());
        String hugo = "$.items[?(@.id=='" + f.hugoId() + "')]";

        ownerGet(f.ana(), "/api/owner/users?kind=MINOR").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath(hugo + ".kind").value(org.hamcrest.Matchers.hasItem("MINOR")))
                .andExpect(jsonPath(hugo + ".loginId").value(org.hamcrest.Matchers.hasItem(hugoLogin)))
                .andExpect(jsonPath(hugo + ".tutorId").value(org.hamcrest.Matchers.hasItem(f.anaId().toString())))
                .andExpect(jsonPath(hugo + ".displayName").isEmpty())
                .andExpect(jsonPath(hugo + ".email").isEmpty())
                .andExpect(jsonPath(hugo + ".limitUsd").value(org.hamcrest.Matchers.hasItem(1.0)))
                .andExpect(jsonPath(hugo + ".courses").value(org.hamcrest.Matchers.hasItem(1)));
    }

    @Test
    void ac5_theMonthSpendMatchesAiUsageAndIgnoresOtherMonths() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/users").andExpect(jsonPath("$.items[?(@.id=='" + f.hugoId() + "')].monthSpendUsd").value(org.hamcrest.Matchers.hasItem(0.80)))
                .andExpect(jsonPath("$.items[?(@.id=='" + f.anaId() + "')].monthSpendUsd").value(org.hamcrest.Matchers.hasItem(0.30)))
                .andExpect(jsonPath("$.items[?(@.id=='" + f.luisId() + "')].monthSpendUsd").value(org.hamcrest.Matchers.hasItem(0.0)));
    }

    @Test
    void ac5_aCustomLimitShowsInsteadOfTheDefaultAndIsMarked() throws Exception {
        Family f = seeded();
        owner.update("INSERT INTO user_limit_override (user_id, limit_usd, set_by) VALUES (?, 3.00, ?)", f.luisId(), f.anaId());

        String luis = "$.items[?(@.id=='" + f.luisId() + "')]";
        ownerGet(f.ana(), "/api/owner/users").andExpect(jsonPath(luis + ".limitUsd").value(org.hamcrest.Matchers.hasItem(3.0)))
                .andExpect(jsonPath(luis + ".limitIsCustom").value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.items[?(@.id=='" + f.anaId() + "')].limitIsCustom").value(org.hamcrest.Matchers.hasItem(false)));
    }

    @Test
    void ac5_sortBySpendAndByCreationInBothDirections() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/users?sort=spend&dir=desc").andExpect(jsonPath("$.items[0].id").value(f.hugoId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(f.anaId().toString())).andExpect(jsonPath("$.items[2].id").value(f.luisId().toString()));
        ownerGet(f.ana(), "/api/owner/users?sort=spend&dir=asc").andExpect(jsonPath("$.items[0].id").value(f.luisId().toString()))
                .andExpect(jsonPath("$.items[2].id").value(f.hugoId().toString()));
        owner.update("UPDATE app_user SET created_at = now() - interval '9 days' WHERE id = ?", f.luisId());
        ownerGet(f.ana(), "/api/owner/users?sort=created&dir=asc").andExpect(jsonPath("$.items[0].id").value(f.luisId().toString()));
    }

    @Test
    void ac5_sortByActivityPutsTheMostRecentFirstAndUsersWithoutActivityLast() throws Exception {
        Family f = seeded();
        UUID ghost = adultRow("ghost@example.com", "Ghost", "3 days");
        aiCall(ghost, "anthropic", "m1", "LESSON", "OK", "0.01", 1, 1, 1, "2 days");
        UUID nobody = adultRow("nobody@example.com", "Nobody", "3 days");

        ownerGet(f.ana(), "/api/owner/users?sort=activity&dir=desc&size=100")
                .andExpect(jsonPath("$.items[?(@.id=='" + ghost + "')].lastActivityAt").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$.items[?(@.id=='" + nobody + "')].lastActivityAt").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.items[4].id").value(nobody.toString()));
    }

    @Test
    void ac5_filterByKindAndStatus() throws Exception {
        Family f = seeded();
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?", f.hugoId());

        ownerGet(f.ana(), "/api/owner/users?status=DEACTIVATED").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(f.hugoId().toString()));
        ownerGet(f.ana(), "/api/owner/users?kind=ADULT&status=ACTIVE").andExpect(jsonPath("$.total").value(2));
        ownerGet(f.ana(), "/api/owner/users?kind=MINOR&status=ACTIVE").andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void ac5_paginationReturnsTheTotalAndHonoursTheSizeWithZeroBasedPages() throws Exception {
        Family f = seeded();

        ownerGet(f.ana(), "/api/owner/users?size=2&page=0&sort=created&dir=asc").andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2));
        ownerGet(f.ana(), "/api/owner/users?size=2&page=1&sort=created&dir=asc").andExpect(jsonPath("$.items", hasSize(1)));
        ownerGet(f.ana(), "/api/owner/users?size=2&page=5").andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.total").value(3));
        ownerGet(f.ana(), "/api/owner/users").andExpect(jsonPath("$.size").value(25)).andExpect(jsonPath("$.page").value(0));
        ownerGet(f.ana(), "/api/owner/users?size=100").andExpect(status().isOk());
    }

    @Test
    void ac5_badParametersAreRejectedWith400() throws Exception {
        Family f = seeded();

        for (String query : new String[] {"?size=101", "?size=0", "?size=-5", "?page=-1", "?size=abc", "?kind=ROBOT", "?status=BORRADO", "?sort=password", "?sort=email", "?dir=sideways"}) {
            ownerGet(f.ana(), "/api/owner/users" + query).andExpect(status().isBadRequest());
        }
    }

    @Test
    void ac5_listingNeverChangesAnything() throws Exception {
        Family f = seeded();
        int users = rows("app_user");

        items(f.ana(), "?size=100");

        org.assertj.core.api.Assertions.assertThat(rows("app_user")).isEqualTo(users);
        org.assertj.core.api.Assertions.assertThat(statusOfUser(f.hugoId())).isEqualTo("ACTIVE");
    }
}
