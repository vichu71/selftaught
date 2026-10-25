package es.vicai.studylab.web;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Cuando una feature deja salir una AiException por un endpoint, cada tipo tiene su estado HTTP y un mensaje genérico. */
class AiExceptionHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ResponseEntity<Map<String, String>> handle(AiErrorKind kind) {
        return handler.handleAi(AiException.of(kind));
    }

    @Test
    void everyKindMapsToTheAgreedStatus() {
        assertThat(handle(AiErrorKind.BUDGET_BLOCKED).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(handle(AiErrorKind.USER_LIMIT).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(handle(AiErrorKind.GENERATION_BUSY).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handle(AiErrorKind.INPUT_TOO_LARGE).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(handle(AiErrorKind.USER_NOT_ALLOWED).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handle(AiErrorKind.PROVIDER_QUOTA).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handle(AiErrorKind.PROVIDER_UNAVAILABLE).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handle(AiErrorKind.PROVIDER_AUTH).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(handle(AiErrorKind.PROVIDER_REJECTED).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(handle(AiErrorKind.INVALID_RESPONSE).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(handle(AiErrorKind.CONFIG).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void theBodyHasACodeAndAGenericMessageAndNothingElse() {
        for (AiErrorKind kind : AiErrorKind.values()) {
            Map<String, String> body = handle(kind).getBody();
            assertThat(body).containsOnlyKeys("code", "message");
            assertThat(body.get("code")).isEqualTo("AI_" + kind.name());
            assertThat(body.get("message")).isNotBlank();
        }
    }

    @Test
    void aMessageThatCarriedProviderTextWouldNeverBeBuiltBecauseTheMessagesAreFixed() {
        for (AiErrorKind kind : AiErrorKind.values()) {
            assertThat(AiException.of(kind).getCause()).isNull();
            assertThat(AiException.of(kind).getMessage()).doesNotContain("sk-").doesNotContain("api.");
        }
    }
}
