package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-4 (D3, D4, D12, ADR-91): el panel no enseña nunca contenido de nadie ni, de un menor, nada más que su identificador de acceso, estado, fechas, gasto, tope y tutor.
 * Se siembra cada clase de contenido con una marca reconocible y se recorre el JSON de todos los endpoints buscando la marca y los nombres de campo prohibidos.
 */
class OwnerNoContentTest extends OwnerTestBase {

    private static final String SECRET = "SECRETO-";
    private static final Set<String> FORBIDDEN_FIELDS = Set.of("title", "topic", "summary", "objective", "question", "answer", "answers", "text", "content", "markdown", "body", "prompt",
            "filename", "fileName", "documentName", "passwordHash", "tokenHash", "codeHash", "code", "ip", "message", "errorMessage", "solution", "sheet");

    private record World(String ana, UUID anaId, String hugo, UUID hugoId, UUID course) {}

    /** Hugo, menor con nombre secreto, estudia un curso de tema secreto: lección con texto secreto, respuesta, pregunta al tutor y documento con nombre secreto. */
    private World withSecrets() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, SECRET + "NOMBRE-MENOR", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(hugoId, SECRET + "TEMA-ZZ", 2, true, List.of());
        modelWritesLessons(SECRET + "LECCION-ZZ");
        generator.processPending();
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        answered(hugo, course, exercisesOf(chapter), 0, SECRET + "RESPUESTA-ZZ");
        ask(hugo, course, lessonOf(chapter), SECRET + "PREGUNTA-ZZ").andReturn();
        uploaded(hugo, SECRET + "DOC.txt", longText(3).getBytes());
        createCode(hugo, course);
        aiCall(hugoId, "anthropic", "m1", "TUTOR", "OK", "0.40", 10, 5, 100, "1 hour");
        aiCall(hugoId, "anthropic", "m1", "LESSON", "ERROR", "0.00", 0, 0, 100, "1 hour");
        return new World(ana, idOf("ana@example.com"), hugo, hugoId, course);
    }

    private List<String> allPaths() {
        List<String> paths = new ArrayList<>(REPORTS);
        for (int range : new int[] {7, 30, 90}) {
            paths.add("/api/owner/ai?range=" + range);
            paths.add("/api/owner/accounts?range=" + range);
            paths.add("/api/owner/usage?range=" + range);
        }
        paths.add("/api/owner/users?size=100");
        paths.add("/api/owner/users?kind=MINOR");
        paths.add("/api/owner/users?sort=spend&dir=desc");
        return paths;
    }

    private void walk(JsonNode node, String path, List<String> problems) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                if (FORBIDDEN_FIELDS.contains(e.getKey())) problems.add(path + "." + e.getKey());
                walk(e.getValue(), path + "." + e.getKey(), problems);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) walk(node.get(i), path + "[" + i + "]", problems);
        }
    }

    @Test
    void ac4_noEndpointCarriesAnySeededContentNorAForbiddenField() throws Exception {
        World w = withSecrets();

        for (String path : allPaths()) {
            String body = ownerGet(w.ana(), path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).as(path).doesNotContain(SECRET);
            List<String> problems = new ArrayList<>();
            walk(json.readTree(body), "$", problems);
            assertThat(problems).as(path).isEmpty();
        }
    }

    @Test
    void ac4_noEndpointCarriesSecretsOfTheAccountsEither() throws Exception {
        World w = withSecrets();

        for (String path : allPaths()) {
            String body = ownerGet(w.ana(), path).andReturn().getResponse().getContentAsString();
            assertThat(body).as(path).doesNotContain("password_hash").doesNotContain("hash").doesNotContainPattern("[0-9a-f]{64}").doesNotContain("STUDYLAB_SESSION");
        }
    }

    @Test
    void ac4_aMinorInTheListHasNoNameNoEmailAndNothingBeyondTheAllowedFields() throws Exception {
        World w = withSecrets();

        JsonNode items = json.readTree(ownerGet(w.ana(), "/api/owner/users?kind=MINOR").andReturn().getResponse().getContentAsString()).get("items");

        assertThat(items).hasSize(1);
        JsonNode hugo = items.get(0);
        List<String> names = new ArrayList<>();
        hugo.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("id", "kind", "status", "loginId", "createdAt", "lastActivityAt", "monthSpendUsd", "limitUsd", "limitIsCustom", "courses", "tutorId");
        assertThat(hugo.get("tutorId").asText()).isEqualTo(w.anaId().toString());
    }

    @Test
    void ac4_anAdultInTheListHasOnlyTheAllowedFields() throws Exception {
        World w = withSecrets();

        JsonNode items = json.readTree(ownerGet(w.ana(), "/api/owner/users?kind=ADULT").andReturn().getResponse().getContentAsString()).get("items");

        assertThat(items).hasSize(1);
        List<String> names = new ArrayList<>();
        items.get(0).fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("id", "kind", "status", "displayName", "email", "createdAt", "lastActivityAt", "monthSpendUsd", "limitUsd", "limitIsCustom", "courses");
    }

    @Test
    void ac4_theOwnerIsNotGivenWhatTheTutorSeesEither() throws Exception {
        World w = withSecrets();

        // Ana es tutora de Hugo y ve sus conversaciones en el panel de F08; el panel del propietario, aunque sea la misma persona, no las repite
        panelGet(w.ana(), w.hugoId(), "/conversations").andExpect(status().isOk());
        String body = ownerGet(w.ana(), "/api/owner/users").andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(SECRET);
    }
}
