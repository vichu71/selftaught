package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** AC-1 a AC-4, AC-14 y AC-15: la subida, sus validaciones y el saneado del nombre. */
class MaterialUploadTest extends MaterialTestBase {

    @Test
    void ac1_uploadingATextFileCreatesAnOwnedDocumentAndRepliesWithoutWaitingForTheIndexing() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "apuntes.txt", text("Las fracciones se suman con común denominador."))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("apuntes.txt"))
                .andExpect(jsonPath("$.mediaType").value("TEXT"))
                .andExpect(jsonPath("$.sizeBytes").value(47)) // la «ú» ocupa dos bytes
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.chunkCount").value(0))
                .andExpect(jsonPath("$.ownedByMe").value(true));

        assertThat(owner.queryForObject("SELECT owner_id FROM source_document", java.util.UUID.class)).isEqualTo(idOf("ana@example.com"));
        assertThat(fake.embedCalls()).isEmpty(); // el indexado no ocurre dentro de la petición
    }

    @Test
    void ac1_theResponseExposesOnlyTheAgreedFieldsNeverTheContentTheHashOrTheOwnerId() throws Exception {
        String ana = logged("ana@example.com");

        String body = upload(ana, "a.txt", text("hola mundo")).andReturn().getResponse().getContentAsString();

        Set<String> keys = JsonPath.<Map<String, Object>>read(body, "$").keySet();
        assertThat(keys).isSubsetOf("id", "name", "mediaType", "sizeBytes", "status", "errorKind", "chunkCount", "pageCount",
                "createdAt", "ownedByMe");
        assertThat(body).doesNotContain("sha").doesNotContain("content").doesNotContain("owner");
    }

    @Test
    void ac15_itRequiresASession() throws Exception {
        mvc.perform(multipart("/api/material").file(new MockMultipartFile("file", "a.txt", "text/plain", text("hola")))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
        assertThat(documentRows()).isZero();
    }

    @Test
    void ac15_itRequiresTheCsrfToken() throws Exception {
        String ana = logged("ana@example.com");

        mvc.perform(multipart("/api/material").file(new MockMultipartFile("file", "a.txt", "text/plain", text("hola")))
                .cookie(new Cookie(SESSION_COOKIE, ana))).andExpect(status().isForbidden());
        assertThat(documentRows()).isZero();
    }

    @Test
    void ac2_pdfDocxAndTextAreAcceptedAndClassifiedByContent() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "a.pdf", pdf("hola")).andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("PDF"));
        upload(ana, "b.docx", docx("hola")).andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("DOCX"));
        upload(ana, "c.txt", text("hola uno")).andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("TEXT"));
        upload(ana, "d.md", text("# Título\n\nhola dos")).andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("TEXT"));
    }

    @Test
    void ac2_aPdfWithAWrongExtensionAndContentTypeIsStillAPdf() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "foto.png", pdf("hola"), "image/png").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mediaType").value("PDF"));
    }

    @Test
    void ac2_anExecutableNamedLikeAPdfIsRejected() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "apuntes.pdf", executable(), "application/pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MATERIAL_TYPE_NOT_ALLOWED"));
        assertThat(documentRows()).isZero();
    }

    @Test
    void ac2_doubleExtensionsAndDisguisedFilesAreJudgedByTheirContent() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "apuntes.pdf.exe", executable()).andExpect(status().isUnsupportedMediaType());
        upload(ana, "informe.docx", plainZip()).andExpect(status().isUnsupportedMediaType());
        upload(ana, "macro.docx", docmWithMacros()).andExpect(status().isUnsupportedMediaType());
        upload(ana, "viejo.doc", legacyDoc()).andExpect(status().isUnsupportedMediaType());
        assertThat(documentRows()).isZero();
    }

    @Test
    void ac2_textThatIsNotUtf8IsRejectedWithAClearReason() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "latin1.txt", new byte[] {'c', 'a', 'n', 'c', (byte) 0xF3, 'n'})
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("UTF-8")));
    }

    @Test
    void ac3_anEmptyFileIsRejected() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "vacio.txt", new byte[0]).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATERIAL_EMPTY"));
    }

    @Test
    void ac3_aMissingFilePartIsABadRequest() throws Exception {
        String ana = logged("ana@example.com");

        mvc.perform(multipart("/api/material").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new Cookie(SESSION_COOKIE, ana))).andExpect(status().isBadRequest());
    }

    @Test
    void ac3_aFileOverTheMaximumSizeIsRejectedAndOneAtTheLimitIsAccepted() throws Exception {
        String ana = logged("ana@example.com");
        int limit = 10 * 1024 * 1024;

        upload(ana, "enorme.txt", "a".repeat(limit + 1).getBytes()).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("MATERIAL_TOO_LARGE"));
        upload(ana, "justo.txt", "a".repeat(limit).getBytes()).andExpect(status().isAccepted());
    }

    @Test
    void ac3_theSameContentTwiceFromTheSameUserIsADuplicate() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");

        upload(ana, "uno.txt", text("mismo contenido")).andExpect(status().isAccepted());
        upload(ana, "otro-nombre.txt", text("mismo contenido")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_DUPLICATE"));
        upload(luis, "uno.txt", text("mismo contenido")).andExpect(status().isAccepted()); // otro usuario sí puede
        assertThat(documentRows()).isEqualTo(2);
    }

    @Test
    void ac4_theNameIsSanitisedAndNeverKeepsAPath() throws Exception {
        String ana = logged("ana@example.com");
        Map<String, String> cases = new java.util.LinkedHashMap<>();
        cases.put("../../etc/passwd.txt", "passwd.txt");
        cases.put("C:\\Users\\x\\apuntes.txt", "apuntes.txt");
        cases.put("con\u0000nul\u0007.txt", "connul.txt");
        cases.put("  espacios  .txt", "espacios  .txt".trim());
        cases.put("   ", "documento");
        int n = 0;
        for (Map.Entry<String, String> entry : cases.entrySet()) {
            String body = upload(ana, entry.getKey(), text("contenido " + n++)).andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            assertThat((String) JsonPath.read(body, "$.name")).as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void ac4_aVeryLongNameIsTruncatedToTheLimit() throws Exception {
        String ana = logged("ana@example.com");

        String body = upload(ana, "n".repeat(300) + ".txt", text("largo")).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.name")).hasSizeLessThanOrEqualTo(120);
        assertThat(owner.queryForObject("SELECT length(display_name) FROM source_document", Integer.class)).isLessThanOrEqualTo(120);
    }

    @Test
    void ac4_markupInTheNameIsStoredAsPlainTextForTheFrontendToEscape() throws Exception {
        String ana = logged("ana@example.com");

        String body = upload(ana, "<img src=x onerror=alert(1)>.txt", text("html")).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.name")).contains("<img");
    }

    @Test
    void ac14_theUploadIsAuditedWithTheIdAndTypeButNeverTheNameOrTheContent() throws Exception {
        String ana = logged("ana@example.com");

        String id = uploaded(ana, "secreto-medico.txt", text("diagnóstico confidencial"));

        assertThat(auditActions()).contains("DOCUMENT_UPLOADED");
        String details = owner.queryForObject("SELECT details::text FROM audit_event WHERE action = 'DOCUMENT_UPLOADED'", String.class);
        assertThat(details).contains(id).contains("TEXT").doesNotContain("secreto-medico").doesNotContain("diagnóstico");
    }

    @Test
    void decision_aMinorCanUploadToo() throws Exception {
        String ana = logged("ana@example.com");
        createStudent(ana, "Hugo", "hugo");
        String hugo = login("hugo", PASSWORD);

        upload(hugo, "mis-apuntes.txt", text("de Hugo")).andExpect(status().isAccepted()).andExpect(jsonPath("$.ownedByMe").value(true));
    }
}
