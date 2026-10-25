package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-10, AC-11 y AC-15: quién ve y quién borra. Un documento ajeno y uno inexistente responden igual. */
class MaterialAccessTest extends MaterialTestBase {

    @Test
    void ac15_everyEndpointRequiresASession() throws Exception {
        String id = UUID.randomUUID().toString();

        mvc.perform(get("/api/material")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/material/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/material/" + id)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ac15_deletingRequiresTheCsrfToken() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "a.txt", text("hola"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/material/" + id)
                .cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, ana))).andExpect(status().isForbidden());
        assertThat(documentRows()).isEqualTo(1);
    }

    @Test
    void ac10_youSeeYourOwnDocumentsWithStateTypeSizeAndDate() throws Exception {
        String ana = logged("ana@example.com");
        uploaded(ana, "a.txt", text("uno"));
        uploadedAndProcessed(ana, "b.txt", text("dos"));

        getAs(ana, "/api/material").andExpect(status().isOk())
                .andExpect(jsonPath("$.own", hasSize(2)))
                .andExpect(jsonPath("$.own[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("a.txt", "b.txt")))
                .andExpect(jsonPath("$.own[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.own[*].ownedByMe").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.minors", hasSize(0)));
    }

    @Test
    void ac10_anotherUsersDocumentsNeverAppearInYourList() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        uploaded(ana, "de-ana.txt", text("de ana"));

        getAs(luis, "/api/material").andExpect(jsonPath("$.own", hasSize(0))).andExpect(jsonPath("$.minors", hasSize(0)));
    }

    @Test
    void ac10_aTutorSeesTheirMinorsDocumentsReadOnlyAndLabelledWithWhoseTheyAre() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        String id = uploaded(hugo, "de-hugo.txt", text("material de hugo"));
        uploaded(ana, "mio.txt", text("material de ana"));

        getAs(ana, "/api/material")
                .andExpect(jsonPath("$.own", hasSize(1)))
                .andExpect(jsonPath("$.minors", hasSize(1)))
                .andExpect(jsonPath("$.minors[0].student.displayName").value("Hugo"))
                .andExpect(jsonPath("$.minors[0].documents[0].name").value("de-hugo.txt"))
                .andExpect(jsonPath("$.minors[0].documents[0].ownedByMe").value(false));
        getAs(ana, "/api/material/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.ownedByMe").value(false));
    }

    @Test
    void ac10_aMinorOnlySeesTheirOwnDocumentsAndNoTutorSection() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        uploaded(ana, "del-tutor.txt", text("del tutor"));
        uploaded(hugo, "suyo.txt", text("suyo"));

        getAs(hugo, "/api/material").andExpect(jsonPath("$.own", hasSize(1))).andExpect(jsonPath("$.own[0].name").value("suyo.txt"))
                .andExpect(jsonPath("$.minors", hasSize(0)));
    }

    @Test
    void ac10_anotherAdultNeverSeesSomeoneElsesMinorsDocuments() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        String id = uploaded(hugo, "de-hugo.txt", text("material de hugo"));

        getAs(luis, "/api/material").andExpect(jsonPath("$.minors", hasSize(0)));
        getAs(luis, "/api/material/" + id).andExpect(status().isNotFound());
    }

    @Test
    void ac10_aMinorCannotReadAnotherMinorsDocument() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        createStudent(ana, "Lola", "lola");
        String hugo = login("hugo", PASSWORD);
        String lola = login("lola", PASSWORD);
        String id = uploaded(hugo, "de-hugo.txt", text("material de hugo"));

        getAs(lola, "/api/material/" + id).andExpect(status().isNotFound());
        deleteAs(lola, id).andExpect(status().isNotFound());
    }

    @Test
    void ac10_aForeignDocumentAndANonexistentOneAnswerTheSame() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String id = uploaded(ana, "a.txt", text("de ana"));

        String foreign = getAs(luis, "/api/material/" + id).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = getAs(luis, "/api/material/" + UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String malformed = getAs(luis, "/api/material/no-es-un-uuid").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing).isEqualTo(malformed);
    }

    @Test
    void ac10_aDeactivatedMinorAndTheirDocumentsAreNotListedForTheTutor() throws Exception {
        String ana = logged("ana@example.com");
        String studentId = createStudent(ana, "Hugo", "hugo");
        createStudent(ana, "Lola", "lola");
        String hugo = login("hugo", PASSWORD);
        uploaded(hugo, "de-hugo.txt", text("material de hugo"));
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?::uuid", studentId);

        getAs(ana, "/api/material")
                .andExpect(jsonPath("$.minors", hasSize(1))) // solo Lola, activa y sin documentos
                .andExpect(jsonPath("$.minors[0].student.displayName").value("Lola"))
                .andExpect(jsonPath("$.minors[0].documents", hasSize(0)));
    }

    @Test
    void ac10_theListNeverExposesContentHashOrOwnerIds() throws Exception {
        String ana = logged("ana@example.com");
        uploadedAndProcessed(ana, "a.txt", text("contenido secreto"));

        String body = getAs(ana, "/api/material").andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("contenido secreto").doesNotContain("sha").doesNotContain("owner_id").doesNotContain("ownerId");
        assertThat((Object) JsonPath.read(body, "$.own[0].id")).isNotNull();
    }

    // ---------- borrado: quién puede ----------

    @Test
    void ac11_theOwnerCanDeleteTheirDocument() throws Exception {
        String ana = logged("ana@example.com");
        String id = uploaded(ana, "a.txt", text("hola"));

        deleteAs(ana, id).andExpect(status().isNoContent());

        getAs(ana, "/api/material/" + id).andExpect(status().isNotFound());
    }

    @Test
    void ac11_aTutorCannotDeleteAMinorsDocumentAndTheAttemptIsAudited() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);
        String id = uploaded(hugo, "de-hugo.txt", text("material de hugo"));

        deleteAs(ana, id).andExpect(status().isForbidden());

        assertThat(statusOf(id)).isEqualTo("UPLOADED");
        assertThat(auditActions()).contains("ACCESS_DENIED");
    }

    @Test
    void ac11_anotherUserCannotDeleteYourDocument() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String id = uploaded(ana, "a.txt", text("hola"));

        deleteAs(luis, id).andExpect(status().isNotFound());

        assertThat(documentRows()).isEqualTo(1);
    }

    @Test
    void ac11_deletingANonexistentDocumentIs404() throws Exception {
        String ana = logged("ana@example.com");

        deleteAs(ana, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        deleteAs(ana, "no-es-un-uuid").andExpect(status().isNotFound());
    }
}
