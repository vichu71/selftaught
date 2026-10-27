package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AC-1, AC-14 (ADR-87): sin {@code OWNER_EMAIL} no hay propietario, el panel no existe y la aplicación arranca igual (no hay valor por defecto). */
class OwnerDisabledTest extends OwnerFamilyBase {

    @Test
    void theApplicationStartsAndNobodyIsTheOwnerWithoutTheVariable() throws Exception {
        Family f = family();

        for (String path : List.of("/api/owner/ai", "/api/owner/prices", "/api/owner/accounts", "/api/owner/usage", "/api/owner/health", "/api/owner/users")) {
            getAs(f.ana(), path).andExpect(status().isNotFound());
        }
        postAs(f.ana(), "/api/owner/users/" + f.luisId() + "/deactivate", null).andExpect(status().isNotFound());
        getAs(f.ana(), "/api/me").andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void ac14_theVariableIsOptionalAndHasNoDefaultInTheApplicationProperties() throws Exception {
        // .env.example y compose.yaml los comprueba el test de entorno (scripts/tests): el contenedor de tests solo ve backend/
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(properties).containsPattern("app\\.owner\\.email\\s*=\\s*\\$\\{OWNER_EMAIL:\\}");
    }
}
