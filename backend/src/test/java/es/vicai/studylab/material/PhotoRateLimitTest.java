package es.vicai.studylab.material;

import static es.vicai.studylab.material.PhotoFixtures.jpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** F19 AC-10 (ADR-105): el límite diario de fotos se cuenta al subir, por usuario y en la BD (contexto propio porque cambia la configuración). */
@TestPropertySource(properties = "app.material.photos.max-reads-per-day=2")
class PhotoRateLimitTest extends PhotoTestBase {

    @Test
    void ac10_theThirdPhotoOfTheDayIsRejectedWith429AndLeavesNoRowNorCall() throws Exception {
        String ana = logged("ana@example.com");
        upload(ana, "1.jpg", jpeg(41, 41)).andExpect(status().isAccepted());
        upload(ana, "2.jpg", jpeg(42, 42)).andExpect(status().isAccepted());

        upload(ana, "3.jpg", jpeg(43, 43)).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("IMAGE_READ_RATE_LIMIT"));

        assertThat(documentRows()).isEqualTo(2);
        assertThat(imageActions(idOf("ana@example.com"))).isEqualTo(2);
        assertThat(fake.visionCalls()).isEmpty();
    }

    @Test
    void ac10_deletingADocumentDoesNotGiveTheLimitBack() throws Exception {
        String ana = logged("ana@example.com");
        String first = uploaded(ana, "1.jpg", jpeg(41, 41));
        uploaded(ana, "2.jpg", jpeg(42, 42));

        deleteAs(ana, first).andExpect(status().isNoContent());

        upload(ana, "3.jpg", jpeg(43, 43)).andExpect(status().isTooManyRequests());
    }

    @Test
    void ac10_theLimitIsPerUserNotGlobalNorPerIp() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        uploaded(ana, "1.jpg", jpeg(41, 41));
        uploaded(ana, "2.jpg", jpeg(42, 42));

        upload(luis, "1.jpg", jpeg(44, 44)).andExpect(status().isAccepted());
    }

    @Test
    void ac10_actionsOlderThanADayDoNotCount() throws Exception {
        String ana = logged("ana@example.com");
        uploaded(ana, "1.jpg", jpeg(41, 41));
        uploaded(ana, "2.jpg", jpeg(42, 42));
        owner.update("UPDATE material_action SET created_at = now() - interval '25 hours'");

        upload(ana, "3.jpg", jpeg(43, 43)).andExpect(status().isAccepted());
    }

    @Test
    void ac10_aDocumentOfAnotherTypeDoesNotSpendThePhotoLimit() throws Exception {
        String ana = logged("ana@example.com");
        for (int i = 0; i < 3; i++) uploaded(ana, "t" + i + ".txt", MaterialFixtures.text("texto número " + i));

        upload(ana, "1.jpg", jpeg(41, 41)).andExpect(status().isAccepted());
    }
}
