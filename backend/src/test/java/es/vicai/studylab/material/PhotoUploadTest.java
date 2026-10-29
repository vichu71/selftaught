package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static es.vicai.studylab.material.PhotoFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import es.vicai.studylab.ai.provider.FakeProvider;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** F19 AC-1, AC-2, AC-10, AC-11, AC-12: la subida de una foto, sus validaciones y el orden en que se comprueban (todas antes de insertar y sin llamar al modelo). */
class PhotoUploadTest extends PhotoTestBase {

    @Test
    void ac1_aJpegAndAPngAreAcceptedAsImagesAndNothingIsReadDuringTheRequest() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "a.jpg", jpeg(80, 60)).andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.status").value("UPLOADED")).andExpect(jsonPath("$.ownedByMe").value(true));
        upload(ana, "b.png", png(80, 60)).andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("IMAGE"));

        assertThat(fake.visionCalls()).isEmpty(); // la lectura es de pago y ocurre en segundo plano
        assertThat(imageActions(idOf("ana@example.com"))).isEqualTo(2);
    }

    @Test
    void ac1_theTypeIsDecidedByContentNotByExtensionNorByContentType() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "foto.pdf", jpeg(40, 40), "application/pdf").andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("IMAGE"));
        upload(ana, "notas.jpg", text("esto es solo texto"), "image/jpeg").andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("TEXT"));
        upload(ana, "doc.png", MaterialFixtures.pdf("hola"), "image/png").andExpect(status().isAccepted()).andExpect(jsonPath("$.mediaType").value("PDF"));
    }

    @Test
    void ac1_webpGifAndSvgAreNotAllowedAndLeaveNoRow() throws Exception {
        String ana = logged("ana@example.com");

        for (byte[] other : new byte[][] {webp(), gif(), svg()}) {
            upload(ana, "x.jpg", other, "image/jpeg").andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("MATERIAL_TYPE_NOT_ALLOWED"));
        }
        assertThat(documentRows()).isZero();
        assertThat(imageActions(idOf("ana@example.com"))).isZero();
    }

    @Test
    void ac2_aPhotoOverTheMaximumSizeIsRejectedWith413() throws Exception {
        String ana = logged("ana@example.com");
        byte[] big = noisyPng(1_700, 1_700); // ~8 MB: por encima de 5 MB y por debajo de los 10 MB del multipart

        assertThat(big.length).isGreaterThan(5 * 1024 * 1024);
        upload(ana, "grande.png", big).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("MATERIAL_TOO_LARGE"));
        assertThat(documentRows()).isZero();
    }

    @Test
    void ac2_aPixelBombIsRejectedFromItsHeaderWith413AndNoRow() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "bomba.png", FakeProvider.pngDeclaring(40_000, 40_000)).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        assertThat(documentRows()).isZero();
        assertThat(imageActions(idOf("ana@example.com"))).isZero();
    }

    @Test
    void ac2_aCorruptImageIsAnErrorOfTheClientNeverA500() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "rota.jpg", corruptJpeg()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_INVALID"));
        upload(ana, "cortada.png", truncatedPng()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_INVALID"));
        assertThat(documentRows()).isZero();
    }

    @Test
    void ac1_theSameImageTwiceIsAConflictAndDoesNotCountTwice() throws Exception {
        String ana = logged("ana@example.com");
        byte[] image = jpeg(50, 50);

        upload(ana, "a.jpg", image).andExpect(status().isAccepted());
        upload(ana, "otra.jpg", image).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATERIAL_DUPLICATE"));

        assertThat(documentRows()).isEqualTo(1);
        assertThat(imageActions(idOf("ana@example.com"))).isEqualTo(1);
    }

    @Test
    void ac10_anotherUserCanUploadTheSameImage() throws Exception {
        byte[] image = jpeg(50, 50);

        upload(logged("ana@example.com"), "a.jpg", image).andExpect(status().isAccepted());
        upload(logged("luis@example.com"), "a.jpg", image).andExpect(status().isAccepted());
    }

    @Test
    void ac12_theResponseKeepsTheAgreedFieldsOfTheDocumentView() throws Exception {
        String ana = logged("ana@example.com");

        String body = upload(ana, "a.jpg", jpeg(40, 40)).andReturn().getResponse().getContentAsString();

        Set<String> keys = JsonPath.<Map<String, Object>>read(body, "$").keySet();
        assertThat(keys).isSubsetOf("id", "name", "mediaType", "sizeBytes", "status", "errorKind", "chunkCount", "pageCount", "createdAt", "ownedByMe");
        assertThat(body).doesNotContain("sha").doesNotContain("content").doesNotContain("owner");
    }

    @Test
    void ac1_theNameIsSanitisedAndNeverUsedAsAPath() throws Exception {
        String ana = logged("ana@example.com");

        upload(ana, "../../etc/passwd\u0000<b>.jpg", jpeg(40, 40)).andExpect(status().isAccepted()).andExpect(jsonPath("$.name").value("passwd<b>.jpg"));
    }

    @Test
    void ac10_theImageDocumentsCountAgainstTheDocumentLimit() throws Exception {
        String ana = logged("ana@example.com");
        UUIDs.fill(owner, idOf("ana@example.com"), 20);

        upload(ana, "a.jpg", jpeg(40, 40)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATERIAL_LIMIT_REACHED"));
        assertThat(imageActions(idOf("ana@example.com"))).isZero(); // rechazada antes de contar: no gasta el límite diario
    }

    @Test
    void ac12_itRequiresASessionAndTheCsrfToken() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg(40, 40));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/material").file(file)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isUnauthorized());
        String ana = logged("ana@example.com");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/material").file(file)
                .cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, ana))).andExpect(status().isForbidden());
        assertThat(documentRows()).isZero();
    }

    /** Rellena la biblioteca de un usuario con documentos de texto para llegar al límite sin subirlos uno a uno. */
    private static final class UUIDs {
        static void fill(org.springframework.jdbc.core.JdbcTemplate owner, java.util.UUID user, int count) {
            for (int i = 0; i < count; i++) {
                owner.update("INSERT INTO source_document (id, owner_id, display_name, media_type, size_bytes, content_sha256, status) VALUES (gen_random_uuid(), ?, ?, 'TEXT', 10, ?, 'READY')",
                        user, "d" + i, String.format("%064d", i));
            }
        }
    }
}
