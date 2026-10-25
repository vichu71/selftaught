package es.vicai.studylab.course;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-5, AC-6, AC-7, AC-13, AC-14 y AC-15: «Aceptar» crea el curso pendiente con su ficha, solo a partir de lo que guardó el servidor. */
class CourseCreationTest extends CourseTestBase {

    private static Map<String, String> answer(String questionId, String optionId) {
        return Map.of("questionId", questionId, "optionId", optionId);
    }

    private String optionIdByLabel(String intakeBody, int question, String label) {
        List<String> ids = JsonPath.read(intakeBody, "$.questions[" + question + "].options[?(@.label == '" + label + "')].id");
        return ids.get(0);
    }

    @Test
    void ac5_acceptingCreatesAPendingCourseWithTheSheetBuiltFromTheAnswers() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés de 2º de la ESO", List.of());

        create(ana, intakeId(body), List.of(answer("q1", optionIdByLabel(body, 0, "Conversación")), answer("q2", optionIdByLabel(body, 1, "Pocos (4)"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Inglés de 2º de la ESO"))
                .andExpect(jsonPath("$.status").value("PENDING_GENERATION"))
                .andExpect(jsonPath("$.sheet.schemaVersion").value(1))
                .andExpect(jsonPath("$.sheet.topic").value("Inglés de 2º de la ESO"))
                .andExpect(jsonPath("$.sheet.focus").value("Conversación"))
                .andExpect(jsonPath("$.sheet.topicsCount").value(4))
                .andExpect(jsonPath("$.sheet.examPerTopic").value(true))
                .andExpect(jsonPath("$.sheet.startLevel").value("Desde cero"))
                .andExpect(jsonPath("$.sheet.goal").value("Aprender el tema"));

        assertThat(courses()).hasSize(1);
        assertThat(courses().get(0).get("owner_id")).isEqualTo(idOf("ana@example.com"));
    }

    @Test
    void ac5_asYouSeeFitAndUnansweredQuestionsUseTheDefaults() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());

        create(ana, intakeId(body), List.of(answer("q1", "auto")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sheet.focus").value("Gramática y vocabulario"))
                .andExpect(jsonPath("$.sheet.topicsCount").value(6)); // q2 sin contestar: su opción por defecto
    }

    @Test
    void ac5_withNoQuestionsAndNoAnswersTheCourseIsStillCreatedWithTheDefaults() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        String body = started(ana, "Cocina básica", List.of());

        create(ana, intakeId(body), List.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sheet.topicsCount").value(6)).andExpect(jsonPath("$.sheet.examPerTopic").value(true));
    }

    @Test
    void ac5_theChosenDocumentsAreLinkedToTheCourse() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text("contenido de apuntes ".repeat(30)));
        model(NO_QUESTIONS);
        String body = started(ana, "Inglés", List.of(doc));

        create(ana, intakeId(body), List.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sheet.documentIds[0]").value(doc))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].name").value("apuntes.txt"));

        assertThat(owner.queryForObject("SELECT count(*) FROM course_document", Integer.class)).isEqualTo(1);
    }

    @Test
    void ac6_theSavedSheetIsTheDocumentedContractWithNoPersonalDataOrDocumentText() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "diario.txt", text("texto-privado-xyz del documento ".repeat(30)));
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of(doc));
        create(ana, intakeId(body), defaultAnswers(body)).andExpect(status().isCreated());

        String sheet = courses().get(0).get("sheet").toString();

        assertThat(sheet).contains("\"schemaVersion\"").doesNotContain("texto-privado-xyz").doesNotContain("diario.txt")
                .doesNotContain("ana@example.com");
    }

    @Test
    void ac6_acceptingTwiceReturnsTheSameCourseAndCreatesOnlyOne() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());
        List<Map<String, String>> answers = defaultAnswers(body);

        String first = create(ana, intakeId(body), answers).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = create(ana, intakeId(body), answers).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(courses()).hasSize(1);
        assertThat(intakes().get(0).get("status")).isEqualTo("CONSUMED");
    }

    @Test
    void ac6_aRepeatedAcceptWithDifferentAnswersStillReturnsTheOriginalCourse() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());
        String first = create(ana, intakeId(body), List.of(answer("q1", "auto"))).andReturn().getResponse().getContentAsString();

        create(ana, intakeId(body), List.of(answer("q1", optionIdByLabel(body, 0, "Conversación")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sheet.focus").value((String) JsonPath.read(first, "$.sheet.focus")));
    }

    @Test
    void ac7_theCourseHasAStateReadableFromItsOwnEndpoint() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        String course = courseCreated(ana, "Cocina básica");
        String id = JsonPath.read(course, "$.id");

        courseOf(ana, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_GENERATION"))
                .andExpect(jsonPath("$.title").value("Cocina básica"));
    }

    @Test
    void ac7_creatingTheCourseDoesNotCallTheModelAgain() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());
        int before = chatCalls();

        create(ana, intakeId(body), defaultAnswers(body)).andExpect(status().isCreated());

        assertThat(chatCalls()).isEqualTo(before);
    }

    // ---------- validación: el servidor manda ----------

    @Test
    void ac13_anAnswerToAQuestionTheServerNeverAskedIsRejected() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());

        create(ana, intakeId(body), List.of(answer("q9", "o1"))).andExpect(status().isBadRequest());
        create(ana, intakeId(body), List.of(answer("q1", "o99"))).andExpect(status().isBadRequest());
        create(ana, intakeId(body), List.of(answer("q1", "<script>"))).andExpect(status().isBadRequest());
        assertThat(courses()).isEmpty();
        assertThat(intakes().get(0).get("status")).isEqualTo("OPEN"); // una petición inválida no consume la consulta
    }

    @Test
    void ac13_aClientCannotInjectItsOwnValuesInAnAnswer() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());

        postAs(ana, "/api/courses", Map.of("intakeId", intakeId(body), "answers", List.of(Map.of("questionId", "q1",
                "optionId", optionIdByLabel(body, 0, "Conversación"), "value", "VALOR-INYECTADO", "field", "goal"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sheet.focus").value("Conversación"))
                .andExpect(jsonPath("$.sheet.goal").value("Aprender el tema"));
    }

    @Test
    void ac13_answeringTheSameQuestionTwiceIsRejected() throws Exception {
        String ana = logged("ana@example.com");
        model(TWO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());

        create(ana, intakeId(body), List.of(answer("q1", "auto"), answer("q1", optionIdByLabel(body, 0, "Conversación"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ac13_anIntakeOlderThanAnHourHasExpired() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        String body = started(ana, "Inglés", List.of());
        owner.update("UPDATE course_intake SET expires_at = now() - interval '1 minute'");

        create(ana, intakeId(body), List.of()).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("INTAKE_EXPIRED"));

        assertThat(courses()).isEmpty();
    }

    @Test
    void ac13_aDocumentDeletedOrNotReadyBetweenStepsIsRevalidatedAtAcceptance() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text("contenido de apuntes ".repeat(30)));
        model(NO_QUESTIONS);
        String body = started(ana, "Inglés", List.of(doc));
        deleteAs(ana, doc).andExpect(status().isNoContent());

        create(ana, intakeId(body), List.of()).andExpect(status().isNotFound());

        assertThat(courses()).isEmpty();
        assertThat(intakes().get(0).get("status")).isEqualTo("OPEN"); // puede volver atrás y elegir otros
    }

    @Test
    void ac13_aForeignOrNonexistentIntakeAnswersTheSame404() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        model(NO_QUESTIONS);
        String anaIntake = intakeId(started(ana, "Inglés", List.of()));

        String foreign = create(luis, anaIntake, List.of()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = create(luis, UUID.randomUUID().toString(), List.of()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String malformed = create(luis, "no-es-un-uuid", List.of()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing).isEqualTo(malformed);
        assertThat(courses()).isEmpty();
    }

    @Test
    void ac14_creatingRequiresASessionAndTheCsrfToken() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        String id = intakeId(started(ana, "Inglés", List.of()));

        postAsWithoutCsrf(ana, "/api/courses", Map.of("intakeId", id, "answers", List.of())).andExpect(status().isForbidden());
        assertThat(courses()).isEmpty();
    }

    // ---------- auditoría y textos ----------

    @Test
    void ac15_theCreationIsAuditedWithIdsOnlyNeverTheTopicOrTheDocumentNames() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "diario-secreto.txt", text("contenido de apuntes ".repeat(30)));
        model(NO_QUESTIONS);
        String body = started(ana, "tema-secreto-xyz", List.of(doc));
        create(ana, intakeId(body), List.of()).andExpect(status().isCreated());

        assertThat(auditActions()).contains("COURSE_CREATED");
        String details = owner.queryForObject("SELECT details::text FROM audit_event WHERE action = 'COURSE_CREATED'", String.class);
        assertThat(details).doesNotContain("tema-secreto-xyz").doesNotContain("diario-secreto");
    }

    @Test
    void ac16_markupInTheTopicIsStoredAsPlainTextForTheFrontendToEscape() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);

        String course = courseCreated(ana, "<img src=x onerror=alert(1)> física");

        assertThat((String) JsonPath.read(course, "$.title")).contains("<img");
    }

    @Test
    void edge_aCourseWhoseDocumentIsLaterDeletedKeepsExistingWithoutTheLink() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "apuntes.txt", text("contenido de apuntes ".repeat(30)));
        model(NO_QUESTIONS);
        String body = started(ana, "Inglés", List.of(doc));
        String id = JsonPath.read(create(ana, intakeId(body), List.of()).andReturn().getResponse().getContentAsString(), "$.id");

        deleteAs(ana, doc).andExpect(status().isNoContent());

        courseOf(ana, id).andExpect(status().isOk()).andExpect(jsonPath("$.documents", hasSize(0)));
        assertThat(courses()).hasSize(1);
    }
}
