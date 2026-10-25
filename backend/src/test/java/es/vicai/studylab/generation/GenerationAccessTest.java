package es.vicai.studylab.generation;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-10, AC-11, AC-12 y AC-15: lo que se puede leer de un curso, de quién y con qué forma. */
class GenerationAccessTest extends GenerationTestBase {

    private UUID generated(String email, int topics, boolean exam) throws Exception {
        UUID course = insertCourse(idOf(email), "Curso de " + email, topics, exam, List.of());
        modelDoes(happy(topics));
        generator.processPending();
        return course;
    }

    // ---------- detalle y progreso (AC-10) ----------

    @Test
    void ac10_theDetailShowsTheOutlineWithTheStateOfEveryPiece() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        detail(ana, course).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.outline", hasSize(2)))
                .andExpect(jsonPath("$.outline[0].title").value("Tema 1 de prueba"))
                .andExpect(jsonPath("$.outline[0].chapters", hasSize(2)))
                .andExpect(jsonPath("$.outline[0].chapters[0].title").value("Capítulo 1.1"))
                .andExpect(jsonPath("$.outline[0].chapters[0].lesson.status").value("DONE"))
                .andExpect(jsonPath("$.outline[0].chapters[0].lesson.id").isNotEmpty())
                .andExpect(jsonPath("$.outline[0].chapters[0].exercises.status").value("DONE"))
                .andExpect(jsonPath("$.outline[0].exam.status").value("DONE"))
                .andExpect(jsonPath("$.progress.done").value(10))
                .andExpect(jsonPath("$.progress.total").value(10));
    }

    @Test
    void ac10_withoutExamPerTopicThereIsNoExamEntry() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, false);

        detail(ana, course).andExpect(jsonPath("$.outline[0].exam").doesNotExist()).andExpect(jsonPath("$.progress.total").value(8));
    }

    @Test
    void ac10_beforeTheOutlineExistsTheDetailHasNoTopicsAndZeroProgress() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);

        detail(ana, course).andExpect(jsonPath("$.status").value("PENDING_GENERATION")).andExpect(jsonPath("$.outline", hasSize(0)))
                .andExpect(jsonPath("$.progress.done").value(0)).andExpect(jsonPath("$.progress.total").value(0));
    }

    @Test
    void ac10_aPausedCourseShowsItsReason() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        owner.update("UPDATE course SET status = 'PAUSED', stop_reason = 'BUDGET' WHERE id = ?", course);

        detail(ana, course).andExpect(jsonPath("$.status").value("PAUSED")).andExpect(jsonPath("$.stopReason").value("BUDGET"));
    }

    @Test
    void ac10_aQueuedCourseSaysItIsWaitingForAnotherCourseOfTheSameUserThatIsBeingGenerated() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID running = insertCourse(anaId, "Primer curso", 2, true, List.of());
        UUID queued = insertCourse(anaId, "Segundo curso", 2, true, List.of());
        owner.update("UPDATE course SET status = 'GENERATING' WHERE id = ?", running);

        detail(ana, queued).andExpect(jsonPath("$.waitingForOther").value(true));
        detail(ana, running).andExpect(jsonPath("$.waitingForOther").doesNotExist());

        owner.update("UPDATE course SET status = 'READY' WHERE id = ?", running);
        detail(ana, queued).andExpect(jsonPath("$.waitingForOther").doesNotExist());
    }

    @Test
    void ac10_aGenerationOfAnotherUserNeverMakesMyQueuedCourseWait() throws Exception {
        String ana = logged("ana@example.com");
        logged("luis@example.com");
        UUID queued = insertCourse(idOf("ana@example.com"), "Mi curso", 2, true, List.of());
        UUID other = insertCourse(idOf("luis@example.com"), "Curso de Luis", 2, true, List.of());
        owner.update("UPDATE course SET status = 'GENERATING' WHERE id = ?", other);

        detail(ana, queued).andExpect(jsonPath("$.waitingForOther").doesNotExist());
    }

    @Test
    void ac10_aForeignCourseAndANonexistentOneAnswerTheSame404() throws Exception {
        logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = generated("ana@example.com", 2, true);

        String foreign = detail(luis, course).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = detail(luis, UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
    }

    @Test
    void ac10_theListAndTheCreationOfF04KeepTheirShape() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);

        String created = courseCreated(ana, "Cocina básica");

        assertThat(JsonPath.<Map<String, Object>>read(created, "$").keySet()).containsExactlyInAnyOrder("id", "title", "status", "sheet", "documents", "createdAt");
    }

    // ---------- piezas legibles (AC-11) ----------

    @Test
    void ac11_aLessonIsReadWithItsMarkdownOnceDone() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        UUID lesson = firstPiece(course, "LESSON");

        pieceOf(ana, course, lesson).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("LESSON")).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.title").value("Capítulo 1.1"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.startsWith("# Lección sin fuentes")))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    void ac11_exercisesAndExamAreReadAsStructuredContent() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        pieceOf(ana, course, firstPiece(course, "EXERCISES")).andExpect(jsonPath("$.exercises", hasSize(3)))
                .andExpect(jsonPath("$.exercises[0].statement").value("Primer ejercicio")).andExpect(jsonPath("$.exercises[0].solution").value("Solución uno"));
        pieceOf(ana, course, firstPiece(course, "EXAM")).andExpect(jsonPath("$.questions", hasSize(5)))
                .andExpect(jsonPath("$.questions[0].options", hasSize(4))).andExpect(jsonPath("$.questions[0].correct").isNumber());
    }

    @Test
    void ac11_aPieceThatIsNotDoneYetShowsItsStateAndNoContent() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        owner.update("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (gen_random_uuid(), ?, ?, 1, 'T', 's')", course, idOf("ana@example.com"));
        UUID topic = owner.queryForObject("SELECT id FROM course_topic WHERE course_id = ?", UUID.class, course);
        owner.update("INSERT INTO course_chapter (id, topic_id, course_id, owner_id, position, title, objective) VALUES (gen_random_uuid(), ?, ?, ?, 1, 'C', 'o')",
                topic, course, idOf("ana@example.com"));
        UUID chapter = owner.queryForObject("SELECT id FROM course_chapter WHERE course_id = ?", UUID.class, course);
        UUID piece = UUID.randomUUID();
        owner.update("INSERT INTO course_piece (id, course_id, owner_id, topic_id, chapter_id, kind, seq, status) VALUES (?, ?, ?, ?, ?, 'LESSON', 1, 'PENDING')",
                piece, course, idOf("ana@example.com"), topic, chapter);

        String body = pieceOf(ana, course, piece).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).doesNotContain("markdown", "exercises", "questions");
    }

    @Test
    void ac11_aPieceOfAnotherUserOrAnotherCourseOrNonexistentAnswersTheSame404() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID anaCourse = generated("ana@example.com", 2, true);
        UUID otherCourse = insertCourse(idOf("ana@example.com"), "Otro curso", 2, true, List.of());
        UUID lesson = firstPiece(anaCourse, "LESSON");

        String foreign = pieceOf(luis, anaCourse, lesson).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String wrongCourse = pieceOf(ana, otherCourse, lesson).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = pieceOf(ana, anaCourse, UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        pieceOf(ana, anaCourse, "no-es-un-uuid").andExpect(status().isNotFound());

        assertThat(foreign).isEqualTo(wrongCourse).isEqualTo(missing);
    }

    @Test
    void ac11_aTutorCannotReadAMinorsContentThroughThisApiThatPanelIsF08() throws Exception {
        String ana = logged("ana@example.com");
        UUID minor = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        UUID course = insertCourse(minor, 2, true);
        modelDoes(happy(2));
        generator.processPending();

        detail(ana, course).andExpect(status().isNotFound());
        pieceOf(ana, course, firstPiece(course, "LESSON")).andExpect(status().isNotFound());
    }

    @Test
    void ac11_everyPieceEndpointRequiresASession() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/api/courses/" + id + "/pieces/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses/" + id + "/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses/" + id)).andExpect(status().isUnauthorized());
    }

    // ---------- exportación (AC-12) ----------

    @Test
    void ac12_theExportIsAVersionedJsonWithTheWholeCourse() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        export(ana, course).andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.topics", hasSize(2)))
                .andExpect(jsonPath("$.topics[0].title").value("Tema 1 de prueba"))
                .andExpect(jsonPath("$.topics[0].chapters[0].lesson.markdown").value(org.hamcrest.Matchers.startsWith("# Lección")))
                .andExpect(jsonPath("$.topics[0].chapters[0].exercises", hasSize(3)))
                .andExpect(jsonPath("$.topics[0].exam.questions", hasSize(5)));
    }

    @Test
    void ac12_theExportHasNoMaterialDocumentNamesIdsOrPersonalData() throws Exception {
        String ana = logged("ana@example.com");
        String doc = uploadedAndProcessed(ana, "diario-secreto-xyz.txt", text(("CONTENIDO-PRIVADO-XYZ explica el tema con ejemplos y definiciones. ").repeat(30)));
        UUID course = insertCourse(idOf("ana@example.com"), "Curso con apuntes", 2, true, List.of(doc));
        modelDoes(happy(2));
        generator.processPending();

        String body = export(ana, course).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("diario-secreto-xyz").doesNotContain("CONTENIDO-PRIVADO-XYZ").doesNotContain("documentIds").doesNotContain(doc)
                .doesNotContain(idOf("ana@example.com").toString()).doesNotContain("ana@example.com").doesNotContain(course.toString());
        assertThat(body).contains("Fuente 1"); // las fuentes se citan genéricamente
    }

    @Test
    void ac12_onlyTheOwnerCanExportAndANonexistentCourseIs404() throws Exception {
        logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = generated("ana@example.com", 2, true);

        export(luis, course).andExpect(status().isNotFound());
        export(luis, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void ac12_aCourseInTheMiddleExportsOnlyWhatIsDone() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);
        owner.update("UPDATE course_piece SET status = 'PENDING', content_md = NULL, content_json = NULL WHERE course_id = ? AND kind = 'EXAM'", course);

        export(ana, course).andExpect(status().isOk()).andExpect(jsonPath("$.topics[0].exam").doesNotExist());
    }

    @Test
    void ac11_everyPieceCarriesTheGeneratedByAiNoticeInTheView() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 2, true);

        pieceOf(ana, course, firstPiece(course, "LESSON")).andExpect(jsonPath("$.generatedByAi").value(true));
        detail(ana, course).andExpect(jsonPath("$.generatedByAi").value(true));
    }
}
