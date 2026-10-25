package es.vicai.studylab.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** AC-3: un cuerpo que supera el límite del servidor (antes de llegar al servicio) también responde con el código acordado. */
class MaterialExceptionHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void ac3_aMultipartOverTheServerLimitAnswers413WithTheMaterialCodeAndNothingElse() {
        ResponseEntity<Map<String, String>> response = handler.handleMaxUpload(new MaxUploadSizeExceededException(11L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsOnlyKeys("code", "message").containsEntry("code", "MATERIAL_TOO_LARGE");
        assertThat(response.getBody().get("message")).doesNotContain("11534336").doesNotContain("MaxUploadSize");
    }
}
