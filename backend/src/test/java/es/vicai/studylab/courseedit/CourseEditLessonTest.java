package es.vicai.studylab.courseedit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-3, AC-4, AC-5 y AC-7: editar el texto de una lección, regenerar piezas con IA y qué queda desactualizado. */
class CourseEditLessonTest extends CourseEditTestBase {

    private static final String NEW_TEXT = "# Mi versión\n\nTexto escrito por el propietario, con **negrita** y una lista:\n\n- uno\n- dos\n";

    // ---------- editar una lección (AC-3, AC-4) ----------

    @Test
    void ac3_theOwnerReplacesTheTextOfADoneLessonAndTheViewerShowsIt() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID lesson = lessonOf(chapterIds(topicIds(course).get(0)).get(0));

        putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        pieceOf(ana, course, lesson).andExpect(jsonPath("$.markdown").value(NEW_TEXT));
        assertThat(owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, lesson)).isEqualTo(NEW_TEXT);
    }

    @Test
    void ac3_theSourcesOfAnEditedLessonAreDropped() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID lesson = lessonOf(chapterIds(topicIds(course).get(0)).get(0));
        owner.update("UPDATE course_piece SET sources = CAST('[{\"n\":1,\"documentId\":\"" + UUID.randomUUID() + "\",\"page\":2}]' AS jsonb) WHERE id = ?", lesson);

        putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isOk());

        assertThat(pieceRow(lesson).get("sources")).isNull();
        pieceOf(ana, course, lesson).andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void ac4_editingALessonKeepsItsExercisesAndTheTopicExamButMarksThemAsOutdated() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapter, topic);
        UUID exercises = exercisesOf(chapter);
        Object exercisesContent = pieceRow(exercises).get("content_json");
        int attempts = count("SELECT count(*) FROM exercise_attempt WHERE piece_id = ?", exercises);

        putAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.outline[0].chapters[0].exercises.outdated").value(true)).andExpect(jsonPath("$.outline[0].exam.outdated").value(true))
                .andExpect(jsonPath("$.outline[0].chapters[0].lesson.outdated").value(false)).andExpect(jsonPath("$.outline[0].chapters[1].exercises.outdated").value(false));

        assertThat(pieceRow(exercises).get("content_json")).isEqualTo(exercisesContent);
        assertThat(pieceRow(exercises).get("status")).isEqualTo("DONE");
        assertThat(count("SELECT count(*) FROM exercise_attempt WHERE piece_id = ?", exercises)).isEqualTo(attempts);
        assertThat(outdated(exercisesOf(chapterIds(topicIds(course).get(1)).get(0)))).isFalse();
    }

    @Test
    void ac4_editingALessonKeepsTheProgressAndTheTutorThread() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapter, topic);
        UUID lesson = lessonOf(chapter);

        putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isOk());

        assertThat(dependents(lesson)).isEqualTo(2); // lección hecha y una pregunta al tutor
    }

    @Test
    void ac3_invalidTextsAreRefusedAndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID lesson = lessonOf(chapterIds(topicIds(course).get(0)).get(0));
        String before = owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, lesson);

        for (String bad : new String[] {"", "   \n  ", "x".repeat(20_001), "con\u0000control"}) {
            putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", bad)).andExpect(status().isBadRequest());
        }
        putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of()).andExpect(status().isBadRequest());
        putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", 5)).andExpect(status().isBadRequest());
        putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", "x".repeat(20_000))).andExpect(status().isOk());

        assertThat(before).isNotEqualTo("x".repeat(20_000));
        assertThat(version(course)).isEqualTo(2); // solo la última, válida
    }

    @Test
    void ac3_onlyDoneLessonsCanBeEdited() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID chapter = chapterIds(topicIds(course).get(0)).get(0);

        putAs(ana, coursePath(course, "/pieces/" + exercisesOf(chapter) + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isBadRequest());
        putAs(ana, coursePath(course, "/pieces/" + examOf(topicIds(course).get(0)) + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isBadRequest());
        owner.update("UPDATE course_piece SET status = 'PENDING', content_md = NULL WHERE id = ?", lessonOf(chapter));
        putAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isConflict());
        putAs(ana, coursePath(course, "/pieces/" + UUID.randomUUID() + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isNotFound());
    }

    @Test
    void ac3_hostileMarkdownIsStoredAsTextAndNeverBecomesHtml() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID lesson = lessonOf(chapterIds(topicIds(course).get(0)).get(0));
        String hostile = "# Título\n\n<script>alert(1)</script> [clic](javascript:alert(1)) <img src=x onerror=alert(1)>";

        putAs(ana, coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", hostile)).andExpect(status().isOk());

        pieceOf(ana, course, lesson).andExpect(jsonPath("$.markdown").value(hostile)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/json"));
    }

    // ---------- regenerar (AC-4) ----------

    @Test
    void ac4_regeneratingALessonEmptiesItQueuesTheCourseAndDeletesWhatDependedOnIt() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapter, topic);
        UUID lesson = lessonOf(chapter);
        UUID exercises = exercisesOf(chapter);

        postAs(ana, coursePath(course, "/pieces/" + lesson + "/regenerate"), null).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING_GENERATION"))
                .andExpect(jsonPath("$.outline[0].chapters[0].lesson.status").value("PENDING")).andExpect(jsonPath("$.version").value(2));

        assertThat(pieceRow(lesson).get("content_md")).isNull();
        assertThat(dependents(lesson)).isZero();
        assertThat(pieceRow(exercises).get("status")).isEqualTo("DONE");
        assertThat(outdated(exercises)).isTrue();
        assertThat(dependents(exercises)).isPositive(); // los intentos de los ejercicios se conservan: no se regeneraron
    }

    @Test
    void ac4_theQueueRewritesTheRegeneratedPieceAndOnlyThatOneAndClearsItsMark() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID chapter = chapterIds(topicIds(course).get(0)).get(0);
        UUID lesson = lessonOf(chapter);
        UUID other = lessonOf(chapterIds(topicIds(course).get(0)).get(1));
        String otherBefore = owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, other);
        postAs(ana, coursePath(course, "/pieces/" + lesson + "/regenerate"), null).andExpect(status().isAccepted());
        fake.reset();
        modelWritesLessons("Lección regenerada");

        generator.processPending();

        assertThat(requested("leccion")).isEqualTo(1);
        assertThat(requested("ejercicios") + requested("examen") + requested("temario")).isZero();
        assertThat(owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, lesson)).contains("Lección regenerada");
        assertThat(owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, other)).isEqualTo(otherBefore);
        assertThat(outdated(lesson)).isFalse();
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac4_regeneratingExercisesDeletesTheirAttemptsAndClearsTheOutdatedMark() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapter, topic);
        putAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isOk());
        UUID exercises = exercisesOf(chapter);
        assertThat(outdated(exercises)).isTrue();

        postAs(ana, coursePath(course, "/pieces/" + exercises + "/regenerate"), null).andExpect(status().isAccepted());
        modelDoes(happy(2));
        generator.processPending();

        assertThat(count("SELECT count(*) FROM exercise_attempt WHERE piece_id = ?", exercises)).isZero();
        assertThat(pieceRow(exercises).get("status")).isEqualTo("DONE");
        assertThat(outdated(exercises)).isFalse();
        assertThat(dependents(lessonOf(chapter))).isEqualTo(2); // la lección conserva su progreso y su hilo
    }

    @Test
    void ac4_regeneratingAnExamDeletesItsAttemptsAndClearsItsMark() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapterIds(topic).get(0), topic);
        UUID exam = examOf(topic);
        owner.update("UPDATE course_piece SET outdated = true WHERE id = ?", exam);

        postAs(ana, coursePath(course, "/pieces/" + exam + "/regenerate"), null).andExpect(status().isAccepted());
        modelDoes(happy(2));
        generator.processPending();

        assertThat(count("SELECT count(*) FROM exam_attempt WHERE piece_id = ?", exam)).isZero();
        assertThat(outdated(exam)).isFalse();
        assertThat(pieceRow(exam).get("status")).isEqualTo("DONE");
    }

    @Test
    void ac4_onlyDonePiecesCanBeRegeneratedAndAnUnknownOneIsNotFound() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID lesson = lessonOf(chapterIds(topicIds(course).get(0)).get(0));
        owner.update("UPDATE course_piece SET status = 'PENDING', content_md = NULL WHERE id = ?", lesson);

        postAs(ana, coursePath(course, "/pieces/" + lesson + "/regenerate"), null).andExpect(status().isConflict());
        postAs(ana, coursePath(course, "/pieces/" + UUID.randomUUID() + "/regenerate"), null).andExpect(status().isNotFound());
    }

    // ---------- gasto (AC-5) ----------

    @Test
    void ac5_ifTheUserLimitIsReachedTheNewPiecesStayPendingAndTheCourseIsPausedWithoutError() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "Roma", "objective", "Entender la república")).andExpect(status().isCreated());
        spendPersonalLimit(idOf("ana@example.com"));
        fake.reset();
        modelDoes(happy(2));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("BUDGET");
        assertThat(requested("leccion") + requested("ejercicios")).isZero();
        assertThat(pieceRow(lessonOf(chapterIds(topic).get(2))).get("status")).isEqualTo("PENDING");
    }

    @Test
    void ac5_everyAdditionAndRegenerationIsCountedForTheHourlyLimit() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);

        postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "Roma", "objective", "Entender la república")).andExpect(status().isCreated());
        modelDoes(happy(2));
        generator.processPending();
        postAs(ana, coursePath(course, "/pieces/" + lessonOf(chapterIds(topic).get(0)) + "/regenerate"), null).andExpect(status().isAccepted());

        assertThat(count("SELECT count(*) FROM course_action WHERE user_id = ? AND kind = 'AI_EDIT'", idOf("ana@example.com"))).isEqualTo(2);
    }

    @Test
    void ac5_editsThatDoNotAskTheAiAreNotCounted() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);

        putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "Otro", "summary", "Otro resumen")).andExpect(status().isOk());
        putAs(ana, coursePath(course, "/pieces/" + lessonOf(chapterIds(topic).get(0)) + "/content"), Map.of("markdown", NEW_TEXT)).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM course_action WHERE kind = 'AI_EDIT'")).isZero();
    }
}
