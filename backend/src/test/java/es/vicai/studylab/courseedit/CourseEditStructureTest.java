package es.vicai.studylab.courseedit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** AC-1, AC-2, AC-6 y AC-7: renombrar, reordenar, quitar y añadir temas y capítulos de un curso propio. */
class CourseEditStructureTest extends CourseEditTestBase {

    // ---------- renombrar (AC-1, AC-7) ----------

    @Test
    void ac1_renamingATopicChangesItsTitleAndSummaryAndRaisesTheVersion() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);

        putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "La Antigüedad", "summary", "Egipto y Grecia")).andExpect(status().isOk())
                .andExpect(jsonPath("$.outline[0].title").value("La Antigüedad")).andExpect(jsonPath("$.outline[0].summary").value("Egipto y Grecia")).andExpect(jsonPath("$.version").value(2));

        assertThat(version(course)).isEqualTo(2);
        assertThat(owner.queryForObject("SELECT title FROM course_topic WHERE id = ?", String.class, topic)).isEqualTo("La Antigüedad");
    }

    @Test
    void ac1_renamingAChapterChangesItsTitleAndObjective() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID chapter = chapterIds(topicIds(course).get(0)).get(1);

        putAs(ana, coursePath(course, "/chapters/" + chapter), Map.of("title", "Grecia clásica", "objective", "Entender la polis")).andExpect(status().isOk())
                .andExpect(jsonPath("$.outline[0].chapters[1].title").value("Grecia clásica")).andExpect(jsonPath("$.outline[0].chapters[1].objective").value("Entender la polis"));
    }

    @Test
    void ac1_renamingNeverTouchesTheGeneratedContentNorTheProgress() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapter, topic);
        String lessonBefore = owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, lessonOf(chapter));
        int dependentsBefore = dependents(lessonOf(chapter)) + dependents(exercisesOf(chapter)) + dependents(examOf(topic));

        putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "Otro título", "summary", "Otro resumen")).andExpect(status().isOk());

        assertThat(owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, lessonOf(chapter))).isEqualTo(lessonBefore);
        assertThat(dependents(lessonOf(chapter)) + dependents(exercisesOf(chapter)) + dependents(examOf(topic))).isEqualTo(dependentsBefore);
    }

    @Test
    void ac1_invalidNamesAreRefusedAndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);

        for (String bad : new String[] {"", "   ", "x".repeat(121), "con\u0000control"}) {
            putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", bad, "summary", "ok")).andExpect(status().isBadRequest());
            putAs(ana, coursePath(course, "/chapters/" + chapter), Map.of("title", bad, "objective", "ok")).andExpect(status().isBadRequest());
        }
        putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "ok", "summary", "x".repeat(301))).andExpect(status().isBadRequest());
        putAs(ana, coursePath(course, "/chapters/" + chapter), Map.of("title", "ok", "objective", "x".repeat(201))).andExpect(status().isBadRequest());
        putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "solo título")).andExpect(status().isBadRequest());
        putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", 5, "summary", "ok")).andExpect(status().isBadRequest());

        assertThat(version(course)).isEqualTo(1);
    }

    // ---------- reordenar (AC-1) ----------

    @Test
    void ac1_topicsCanBeReorderedAndTheirContentStaysWhereItWas() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        List<UUID> topics = topicIds(course);
        UUID lessonBefore = lessonOf(chapterIds(topics.get(0)).get(0));

        putAs(ana, coursePath(course, "/topics/order"), Map.of("ids", List.of(topics.get(1), topics.get(0)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.outline[0].id").value(topics.get(1).toString())).andExpect(jsonPath("$.outline[1].id").value(topics.get(0).toString())).andExpect(jsonPath("$.version").value(2));

        assertThat(topicIds(course)).containsExactly(topics.get(1), topics.get(0));
        assertThat(lessonOf(chapterIds(topics.get(0)).get(0))).isEqualTo(lessonBefore);
        assertThat(pieceRow(lessonBefore).get("status")).isEqualTo("DONE");
    }

    @Test
    void ac1_chaptersCanBeReorderedInsideTheirTopicOnly() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        List<UUID> topics = topicIds(course);
        List<UUID> first = chapterIds(topics.get(0));
        List<UUID> second = chapterIds(topics.get(1));

        putAs(ana, coursePath(course, "/topics/" + topics.get(0) + "/chapters/order"), Map.of("ids", List.of(first.get(1), first.get(0)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.outline[0].chapters[0].id").value(first.get(1).toString()));

        assertThat(chapterIds(topics.get(0))).containsExactly(first.get(1), first.get(0));
        assertThat(chapterIds(topics.get(1))).containsExactlyElementsOf(second);
    }

    @Test
    void ac1_aReorderThatIsNotExactlyTheSameSetIsRefusedAndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID other = ready("ana@example.com"); // otro curso de Ana: sus temas no son de este
        List<UUID> topics = topicIds(course);

        String path = coursePath(course, "/topics/order");
        putAs(ana, path, Map.of("ids", List.of(topics.get(0)))).andExpect(status().isBadRequest());
        putAs(ana, path, Map.of("ids", List.of(topics.get(0), topics.get(0)))).andExpect(status().isBadRequest());
        putAs(ana, path, Map.of("ids", List.of(topics.get(0), topics.get(1), topics.get(1)))).andExpect(status().isBadRequest());
        putAs(ana, path, Map.of("ids", List.of(topics.get(0), UUID.randomUUID()))).andExpect(status().isBadRequest());
        putAs(ana, path, Map.of("ids", List.of(topics.get(0), topicIds(other).get(0)))).andExpect(status().isBadRequest());
        putAs(ana, path, Map.of("ids", List.of("no-es-un-uuid", topics.get(1).toString()))).andExpect(status().isBadRequest());
        putAs(ana, path, Map.of()).andExpect(status().isBadRequest());

        assertThat(topicIds(course)).containsExactlyElementsOf(topics);
        assertThat(version(course)).isEqualTo(1);
    }

    // ---------- quitar (AC-1) ----------

    @Test
    void ac1_removingATopicDeletesItsPiecesAndEverythingThatDependedOnThem() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        List<UUID> topics = topicIds(course);
        UUID chapter = chapterIds(topics.get(0)).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapter, topics.get(0));
        UUID keptChapter = chapterIds(topics.get(1)).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, keptChapter, topics.get(1));
        int keptDependents = dependents(lessonOf(keptChapter)) + dependents(exercisesOf(keptChapter)) + dependents(examOf(topics.get(1)));

        deleteAs(ana, coursePath(course, "/topics/" + topics.get(0))).andExpect(status().isOk()).andExpect(jsonPath("$.outline", hasSize(1)))
                .andExpect(jsonPath("$.version").value(2));

        assertThat(topicIds(course)).containsExactly(topics.get(1));
        assertThat(count("SELECT count(*) FROM course_piece WHERE topic_id = ?", topics.get(0))).isZero();
        assertThat(count("SELECT count(*) FROM exercise_attempt WHERE course_id = ? AND piece_id NOT IN (SELECT id FROM course_piece)", course)).isZero();
        assertThat(dependents(lessonOf(keptChapter)) + dependents(exercisesOf(keptChapter)) + dependents(examOf(topics.get(1)))).isEqualTo(keptDependents);
    }

    @Test
    void ac1_theLastTopicCannotBeRemoved() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 1, true);

        deleteAs(ana, coursePath(course, "/topics/" + topicIds(course).get(0))).andExpect(status().isBadRequest());

        assertThat(topicIds(course)).hasSize(1);
        assertThat(version(course)).isEqualTo(1);
    }

    @Test
    void ac1_removingAChapterDeletesItsTwoPiecesAndMarksTheTopicExamAsOutdated() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        giveDependents(ana, idOf("ana@example.com"), course, chapter, topic);
        UUID lesson = lessonOf(chapter);
        UUID exercises = exercisesOf(chapter);

        deleteAs(ana, coursePath(course, "/chapters/" + chapter)).andExpect(status().isOk()).andExpect(jsonPath("$.outline[0].chapters", hasSize(1)))
                .andExpect(jsonPath("$.outline[0].exam.outdated").value(true));

        assertThat(count("SELECT count(*) FROM course_piece WHERE id IN (?, ?)", lesson, exercises)).isZero();
        assertThat(count("SELECT count(*) FROM tutor_message WHERE piece_id = ?", lesson)).isZero();
        assertThat(outdated(examOf(topic))).isTrue();
        assertThat(pieceCount(course)).isEqualTo(8);
    }

    @Test
    void ac1_theLastChapterOfATopicCannotBeRemoved() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        List<UUID> chapters = chapterIds(topic);
        deleteAs(ana, coursePath(course, "/chapters/" + chapters.get(0))).andExpect(status().isOk());

        deleteAs(ana, coursePath(course, "/chapters/" + chapters.get(1))).andExpect(status().isBadRequest());

        assertThat(chapterIds(topic)).containsExactly(chapters.get(1));
    }

    // ---------- añadir con IA (AC-2) ----------

    @Test
    void ac2_addingAChapterCreatesItsTwoPendingPiecesAndQueuesTheCourse() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);

        postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "Roma", "objective", "Entender la república")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_GENERATION")).andExpect(jsonPath("$.outline[0].chapters", hasSize(3))).andExpect(jsonPath("$.outline[0].chapters[2].title").value("Roma"))
                .andExpect(jsonPath("$.outline[0].chapters[2].lesson.status").value("PENDING")).andExpect(jsonPath("$.outline[0].chapters[2].exercises.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(2));

        UUID added = chapterIds(topic).get(2);
        assertThat(pieceRow(lessonOf(added)).get("status")).isEqualTo("PENDING");
        assertThat(pieceRow(exercisesOf(added)).get("status")).isEqualTo("PENDING");
        assertThat((Integer) pieceRow(lessonOf(added)).get("seq")).isGreaterThan(10);
        assertThat(outdated(examOf(topic))).isTrue();
        assertThat(statusOf(course)).isEqualTo("PENDING_GENERATION");
    }

    @Test
    void ac2_theQueueGeneratesOnlyWhatIsNewAndNeverTouchesWhatWasDone() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        List<Object> doneContent = owner.queryForList("SELECT content_md FROM course_piece WHERE course_id = ? AND kind = 'LESSON' ORDER BY seq", Object.class, course);
        postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "Roma", "objective", "Entender la república")).andExpect(status().isCreated());
        fake.reset();
        modelWritesLessons("Lección de Roma");

        generator.processPending();

        assertThat(requested("temario")).isZero();
        assertThat(requested("leccion")).isEqualTo(1);
        assertThat(requested("ejercicios")).isEqualTo(1);
        assertThat(requested("examen")).isZero(); // el examen ya hecho no se regenera solo: queda marcado como desactualizado
        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(owner.queryForList("SELECT content_md FROM course_piece WHERE course_id = ? AND kind = 'LESSON' ORDER BY seq LIMIT 4", Object.class, course)).isEqualTo(doneContent);
        assertThat(owner.queryForObject("SELECT content_md FROM course_piece WHERE id = ?", String.class, lessonOf(chapterIds(topic).get(2)))).contains("Lección de Roma");
    }

    @Test
    void ac2_addingATopicCreatesItsChapterItsPiecesAndItsExamWhenTheCourseHasExams() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        postAs(ana, coursePath(course, "/topics"), Map.of("title", "La Edad Moderna", "summary", "Del Renacimiento a las revoluciones",
                "chapter", Map.of("title", "El Renacimiento", "objective", "Entender el humanismo"))).andExpect(status().isCreated()).andExpect(jsonPath("$.outline", hasSize(3)))
                .andExpect(jsonPath("$.outline[2].title").value("La Edad Moderna")).andExpect(jsonPath("$.outline[2].chapters", hasSize(1))).andExpect(jsonPath("$.outline[2].exam.status").value("PENDING"));

        assertThat(pieceCount(course)).isEqualTo(13);
        fake.reset();
        modelWritesLessons("Lección nueva");
        generator.processPending();
        assertThat(requested("leccion")).isEqualTo(1);
        assertThat(requested("ejercicios")).isEqualTo(1);
        assertThat(requested("examen")).isEqualTo(1);
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac2_aCourseWithoutExamsDoesNotGetAnExamForANewTopic() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = generated("ana@example.com", 1, false);

        postAs(ana, coursePath(course, "/topics"), Map.of("title", "Otro tema", "summary", "Resumen", "chapter", Map.of("title", "Capítulo", "objective", "Objetivo")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.outline[1].exam").doesNotExist());

        assertThat(count("SELECT count(*) FROM course_piece WHERE course_id = ? AND kind = 'EXAM'", course)).isZero();
    }

    @Test
    void ac2_newThingsAreValidatedLikeRenames() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);

        postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "", "objective", "ok")).andExpect(status().isBadRequest());
        postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "ok", "objective", "x".repeat(201))).andExpect(status().isBadRequest());
        postAs(ana, coursePath(course, "/topics"), Map.of("title", "ok", "summary", "ok")).andExpect(status().isBadRequest()); // sin su primer capítulo
        postAs(ana, coursePath(course, "/topics"), Map.of("title", "ok", "summary", "ok", "chapter", Map.of("title", "", "objective", "ok"))).andExpect(status().isBadRequest());

        assertThat(pieceCount(course)).isEqualTo(10);
        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(version(course)).isEqualTo(1);
    }

    // ---------- estado del curso y concurrencia (AC-6) ----------

    @Test
    void ac6_everyEditIsRefusedWhileTheCourseIsQueuedOrBeingGenerated() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);

        for (String busy : new String[] {"PENDING_GENERATION", "GENERATING"}) {
            setStatus(course, busy);
            putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "x", "summary", "y")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_BUSY"));
            putAs(ana, coursePath(course, "/topics/order"), Map.of("ids", topicIds(course))).andExpect(status().isConflict());
            deleteAs(ana, coursePath(course, "/chapters/" + chapter)).andExpect(status().isConflict());
            postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "x", "objective", "y")).andExpect(status().isConflict());
            putAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/content"), Map.of("markdown", "texto")).andExpect(status().isConflict());
            postAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/regenerate"), null).andExpect(status().isConflict());
        }

        assertThat(version(course)).isEqualTo(1);
        assertThat(pieceCount(course)).isEqualTo(10);
    }

    @Test
    void ac6_aPausedOrFailedCourseCanBeEdited() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);

        for (String status : new String[] {"PAUSED", "FAILED"}) {
            setStatus(course, status);
            putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "Con " + status, "summary", "y")).andExpect(status().isOk());
        }
    }

    @Test
    void ac6_fourSimultaneousEditsAreAppliedOneAfterAnotherWithoutLeavingTheOutlineHalfDone() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        List<UUID> topics = topicIds(course);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> edits = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int n = i;
                edits.add(() -> putAs(ana, coursePath(course, "/topics/" + topics.get(n % 2)), Map.of("title", "Título " + n, "summary", "Resumen " + n)).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(edits)) statuses.add(f.get());

            assertThat(statuses).containsOnly(200);
        } finally {
            pool.shutdown();
        }
        assertThat(version(course)).isEqualTo(5);
        List<Integer> positions = owner.queryForList("SELECT position FROM course_topic WHERE course_id = ? ORDER BY position", Integer.class, course);
        assertThat(positions).containsExactly(1, 2);
        List<String> titles = owner.queryForList("SELECT title FROM course_topic WHERE course_id = ?", String.class, course);
        Collections.sort(titles);
        assertThat(titles).allMatch(t -> t.startsWith("Título "));
    }

    @Test
    void ac7_everyCourseStartsAtVersionOneAndAFailedEditDoesNotRaiseIt() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        detailOf(ana, course).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.copy").doesNotExist());
        putAs(ana, coursePath(course, "/topics/" + topicIds(course).get(0)), Map.of("title", "", "summary", "x")).andExpect(status().isBadRequest());

        assertThat(version(course)).isEqualTo(1);
    }

    @Test
    void ac7_everyPieceOfTheDetailSaysWhetherItIsOutdated() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        detailOf(ana, course).andExpect(jsonPath("$.outline[0].chapters[0].lesson.outdated").value(false)).andExpect(jsonPath("$.outline[0].chapters[0].exercises.outdated").value(false))
                .andExpect(jsonPath("$.outline[0].exam.outdated").value(false));
    }
}
