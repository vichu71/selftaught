package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.provider.ChatCall;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** AC-1 a AC-5 y AC-16 (ADR-42, 43): el curso se genera en orden, por piezas, sin regenerar lo hecho y sin que una pieza mala tumbe el curso. */
class GenerationFlowTest extends GenerationTestBase {

    @Test
    void ac4_aCourseIsGeneratedCompletelyInOrderAndEndsReady() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 3, true);
        modelDoes(happy(3));

        int processed = generator.processPending();

        assertThat(processed).isEqualTo(1);
        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(pieces(course, "DONE")).isEqualTo(15); // 3 temas × (2 capítulos × (lección + ejercicios) + examen)
        assertThat(allPieces(course)).isEqualTo(15);
        assertThat(owner.queryForObject("SELECT count(*) FROM course_topic WHERE course_id = ?", Integer.class, course)).isEqualTo(3);
        assertThat(owner.queryForObject("SELECT count(*) FROM course_chapter WHERE course_id = ?", Integer.class, course)).isEqualTo(6);
        assertThat(owner.queryForObject("SELECT completed_at IS NOT NULL FROM course WHERE id = ?", Boolean.class, course)).isTrue();
    }

    @Test
    void ac3_theOutlineIsAskedFirstAndOnlyOnce() throws Exception {
        logged("ana@example.com");
        insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));

        generator.processPending();

        List<String> kinds = pieceKindsCalled();
        assertThat(kinds.get(0)).isEqualTo("temario");
        assertThat(kinds.stream().filter("temario"::equals)).hasSize(1);
        assertThat(kinds).hasSize(1 + 10);
    }

    @Test
    void ac4_theOrderIsLessonThenExercisesByChapterAndTheExamAtTheEndOfEachTopic() throws Exception {
        logged("ana@example.com");
        insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));

        generator.processPending();

        assertThat(pieceKindsCalled()).containsExactly("temario",
                "leccion", "ejercicios", "leccion", "ejercicios", "examen",
                "leccion", "ejercicios", "leccion", "ejercicios", "examen");
    }

    @Test
    void ac3_theOutlineIsVisibleBeforeTheFirstLessonIsGenerated() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 3, true);
        List<String> seenAtFirstLesson = new ArrayList<>();
        modelDoes(call -> {
            if ("leccion".equals(piece(call)) && seenAtFirstLesson.isEmpty()) {
                seenAtFirstLesson.add(statusOf(course));
                seenAtFirstLesson.add(String.valueOf(owner.queryForObject("SELECT count(*) FROM course_topic WHERE course_id = ?", Integer.class, course)));
                seenAtFirstLesson.add(String.valueOf(allPieces(course)));
            }
            return happy(3).apply(call);
        });

        generator.processPending();

        assertThat(seenAtFirstLesson).containsExactly("GENERATING", "3", "15"); // el temario y todas las piezas ya estaban guardados
    }

    @Test
    void ac4_withoutExamPerTopicNoExamIsGenerated() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 3, false);
        modelDoes(happy(3));

        generator.processPending();

        assertThat(pieceKindsCalled()).doesNotContain("examen");
        assertThat(allPieces(course)).isEqualTo(12);
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac4_everyDonePieceKeepsItsContent() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));

        generator.processPending();

        for (Map<String, Object> row : pieceRows(course)) {
            assertThat(row.get("status")).isEqualTo("DONE");
            if ("LESSON".equals(row.get("kind"))) assertThat((String) row.get("content_md")).startsWith("# ");
            else assertThat(row.get("content_json")).isNotNull();
            assertThat(row.get("generated_at")).isNotNull();
        }
    }

    @Test
    void ac4_aDonePieceIsNeverRegeneratedEvenIfTheCourseIsQueuedAgain() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));
        generator.processPending();
        int calls = chatCalls();
        owner.update("UPDATE course SET status = 'PENDING_GENERATION' WHERE id = ?", course);

        generator.processPending();

        assertThat(chatCalls()).isEqualTo(calls); // ni el temario ni ninguna pieza
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    // ---------- validación y reintentos (AC-5) ----------

    @Test
    void ac5_anInvalidPieceIsRetriedOnceAndTheSecondAnswerIsUsed() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        AtomicInteger lessonCalls = new AtomicInteger();
        modelDoes(call -> {
            if ("leccion".equals(piece(call)) && lessonCalls.incrementAndGet() == 1) return "demasiado corto";
            return happy(2).apply(call);
        });

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(chatCalls()).isEqualTo(1 + 10 + 1); // una llamada de más por el reintento
        assertThat(owner.queryForObject("SELECT attempts FROM course_piece WHERE course_id = ? AND kind = 'LESSON' ORDER BY seq LIMIT 1", Integer.class, course)).isEqualTo(2);
    }

    @Test
    void ac5_aPieceThatFailsTwiceIsFailedAndTheCourseKeepsGoing() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(call -> "ejercicios".equals(piece(call)) ? "no es json" : happy(2).apply(call));

        generator.processPending();

        assertThat(pieces(course, "FAILED")).isEqualTo(4); // los ejercicios de los cuatro capítulos
        assertThat(pieces(course, "DONE")).isEqualTo(6); // 4 lecciones + 2 exámenes: el resto siguió
        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("CONTENT");
        assertThat(owner.queryForObject("SELECT error_kind FROM course_piece WHERE status = 'FAILED' LIMIT 1", String.class)).isEqualTo("INVALID_OUTPUT");
    }

    @Test
    void ac5_theUserNeverSeesTheModelsTextWhenAPieceFails() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(call -> "leccion".equals(piece(call)) ? "TEXTO-ROTO-DEL-MODELO-XYZ" : happy(2).apply(call));
        generator.processPending();

        String body = detail(ana, course).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("TEXTO-ROTO-DEL-MODELO-XYZ");
        assertThat(owner.queryForList("SELECT * FROM course_piece").toString()).doesNotContain("TEXTO-ROTO-DEL-MODELO-XYZ");
    }

    @Test
    void ac5_anOutlineWithTheWrongNumberOfTopicsIsRetriedAndThenPausesTheCourseWithoutPieces() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 4, true);
        modelDoes(call -> outlineJson(3, 2, true)); // la ficha pide 4 temas

        generator.processPending();

        assertThat(chatCalls()).isEqualTo(2); // intento + un reintento
        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("CONTENT");
        assertThat(allPieces(course)).isZero();
    }

    @Test
    void ac5_exercisesInsideAFenceAreAccepted() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, false);
        modelDoes(call -> "ejercicios".equals(piece(call)) ? "```json\n" + exercisesJson() + "\n```" : happy(2).apply(call));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac5_exercisesAreWrittenFromTheLessonThatWasJustGenerated() throws Exception {
        logged("ana@example.com");
        insertCourse(idOf("ana@example.com"), 2, false);
        modelDoes(call -> "leccion".equals(piece(call)) ? lessonMd("Lección-unica-xyz") : happy(2).apply(call));

        generator.processPending();

        ChatCall exercises = fake.calls().stream().filter(c -> "ejercicios".equals(piece(c))).findFirst().orElseThrow();
        assertThat(exercises.dataMessage()).contains("Lección-unica-xyz"); // el contenido de la lección va como dato, para que sean coherentes
    }

    @Test
    void ac15_theStartAndTheEndOfAGenerationAreAuditedWithTheCourseIdAndNothingElse() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), "tema-secreto-xyz", 2, false, List.of());
        modelDoes(happy(2));

        generator.processPending();

        assertThat(auditActions()).contains("COURSE_GENERATION_STARTED", "COURSE_GENERATION_COMPLETED");
        String details = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action LIKE 'COURSE_GENERATION_%'", String.class);
        assertThat(details).contains(course.toString()).doesNotContain("tema-secreto-xyz").doesNotContain("Tema 1 de prueba");
    }

    // ---------- cerrojo, colas y reinicios (AC-1, AC-2) ----------

    @Test
    void ac2_theGenerationHoldsTheUsersLockAndReleasesItAtTheEnd() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        insertCourse(ana, 2, false);
        List<Integer> locksDuring = new ArrayList<>();
        modelDoes(call -> {
            locksDuring.add(owner.queryForObject("SELECT count(*) FROM ai_generation_lock WHERE user_id = ?", Integer.class, ana));
            return happy(2).apply(call);
        });

        generator.processPending();

        assertThat(locksDuring).isNotEmpty().allSatisfy(n -> assertThat(n).isEqualTo(1));
        assertThat(owner.queryForObject("SELECT count(*) FROM ai_generation_lock WHERE user_id = ?", Integer.class, ana)).isZero();
    }

    @Test
    void ac2_ifTheUserAlreadyHasAGenerationRunningTheCourseWaitsInTheQueue() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        UUID course = insertCourse(ana, 2, false);
        modelDoes(happy(2));

        try (var lease = locks.acquire(ana)) {
            assertThat(lease).isNotNull();
            assertThat(generator.processPending()).isZero();
            assertThat(statusOf(course)).isEqualTo("PENDING_GENERATION");
            assertThat(chatCalls()).isZero();
        }
        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac2_twoCoursesOfTheSameUserNeverRunAtTheSameTimeAndBothFinish() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        UUID first = insertCourse(ana, "Primer curso", 2, false, List.of());
        UUID second = insertCourse(ana, "Segundo curso", 2, false, List.of());
        List<Integer> running = new ArrayList<>();
        modelDoes(call -> {
            running.add(owner.queryForObject("SELECT count(*) FROM course WHERE owner_id = ? AND status = 'GENERATING'", Integer.class, ana));
            return happy(2).apply(call);
        });

        generator.processPending();

        assertThat(running).isNotEmpty().allSatisfy(n -> assertThat(n).isEqualTo(1));
        assertThat(statusOf(first)).isEqualTo("READY");
        assertThat(statusOf(second)).isEqualTo("READY");
    }

    @Test
    void ac2_otherUsersAreNotBlockedByAnotherUsersLock() throws Exception {
        logged("ana@example.com");
        logged("luis@example.com");
        UUID ana = idOf("ana@example.com");
        UUID luisCourse = insertCourse(idOf("luis@example.com"), 2, false);
        modelDoes(happy(2));

        try (var lease = locks.acquire(ana)) {
            assertThat(lease).isNotNull();
            generator.processPending();
        }

        assertThat(statusOf(luisCourse)).isEqualTo("READY");
    }

    @Test
    void ac2_ifTheLockIsLostInTheMiddleTheGeneratorStopsWithoutWritingMore() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        UUID course = insertCourse(ana, 3, true);
        AtomicInteger calls = new AtomicInteger();
        modelDoes(call -> {
            if (calls.incrementAndGet() == 4) owner.update("UPDATE ai_generation_lock SET owner = gen_random_uuid() WHERE user_id = ?", ana); // otro la toma
            return happy(3).apply(call);
        });

        generator.processPending();

        assertThat(chatCalls()).isLessThanOrEqualTo(5); // se detiene en la siguiente renovación
        assertThat(pieces(course, "DONE")).isLessThan(15);
        assertThat(statusOf(course)).isEqualTo("PENDING_GENERATION"); // vuelve a la cola, no se marca como acabado ni como fallido
    }

    @Test
    void ac1_aDeactivatedOwnersCoursesAreNotPickedUp() throws Exception {
        String ana = logged("ana@example.com");
        String studentId = createStudent(ana, "Hugo", "hugo");
        UUID course = insertCourse(UUID.fromString(studentId), 2, false);
        owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?::uuid", studentId);
        modelDoes(happy(2));

        assertThat(generator.processPending()).isZero();

        assertThat(statusOf(course)).isEqualTo("PENDING_GENERATION");
        assertThat(chatCalls()).isZero();
    }

    @Test
    void ac1_ifTheOwnerIsDeactivatedInTheMiddleTheCourseGoesBackToTheQueueUnchanged() throws Exception {
        String ana = logged("ana@example.com");
        String studentId = createStudent(ana, "Hugo", "hugo");
        UUID course = insertCourse(UUID.fromString(studentId), 2, true);
        AtomicInteger calls = new AtomicInteger();
        modelDoes(call -> {
            if (calls.incrementAndGet() == 3) owner.update("UPDATE app_user SET status = 'DEACTIVATED' WHERE id = ?::uuid", studentId);
            return happy(2).apply(call);
        });

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("PENDING_GENERATION");
        assertThat(pieces(course, "DONE")).isLessThan(10);
    }

    @Test
    void ac1_noHttpRequestWaitsForTheModel() throws Exception {
        String ana = logged("ana@example.com");
        model(NO_QUESTIONS);
        String started = started(ana, "Cocina básica", List.of());

        long before = System.nanoTime();
        create(ana, intakeId(started), List.of()).andReturn();
        long ms = (System.nanoTime() - before) / 1_000_000;

        assertThat(ms).isLessThan(2000);
        assertThat(courses().get(0).get("status")).isEqualTo("PENDING_GENERATION"); // aceptar solo encola; el trabajo lo hace el proceso de fondo
    }

    // ---------- gasto y registro (AC-8) ----------

    @Test
    void ac8_everyCallIsRecordedWithTheUserAndTheCourseAndTheCourseCostIsTheirSum() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));
        generator.processPending();

        List<Map<String, Object>> rows = owner.queryForList("SELECT * FROM ai_usage WHERE course_id = ?", course);

        assertThat(rows).hasSize(11);
        assertThat(rows).allSatisfy(r -> assertThat(r.get("user_id")).isEqualTo(idOf("ana@example.com")));
        BigDecimal sum = owner.queryForObject("SELECT sum(cost_usd) FROM ai_usage WHERE course_id = ?", BigDecimal.class, course);
        detail(ana, course).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.costUsd").value(sum.doubleValue()));
    }

    @Test
    void ac16_aCourseNeverMakesMoreCallsThanItsPiecesPlusTheOutlineAndOneRetryEach() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 3, true);
        modelDoes(call -> "no sirve para nada");

        generator.processPending();
        generator.processPending();

        assertThat(chatCalls()).isLessThanOrEqualTo(2); // el temario nunca se valida: no se pasa a las piezas y el curso espera a que alguien lo reanude
        assertThat(statusOf(course)).isEqualTo("PAUSED");
    }

    @Test
    void ac1_aCourseLeftGeneratingByARestartGoesBackToTheQueueAndContinuesWhereItWas() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));
        generator.processPending();
        // Como si el backend se hubiera caído tras las 6 primeras piezas: el resto sin hacer y el curso a medias
        owner.update("UPDATE course_piece SET status = 'PENDING', content_md = NULL, content_json = NULL, generated_at = NULL WHERE course_id = ? AND seq > 6", course);
        owner.update("UPDATE course SET status = 'GENERATING', completed_at = NULL WHERE id = ?", course);
        int pending = pieces(course, "PENDING");
        fake.reset();
        modelDoes(happy(2));

        generator.recoverInterrupted();
        assertThat(statusOf(course)).isEqualTo("PENDING_GENERATION");
        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(pending).isEqualTo(4);
        assertThat(chatCalls()).isEqualTo(pending); // solo lo que faltaba: ni el temario ni lo ya hecho
        assertThat(pieceKindsCalled()).doesNotContain("temario");
    }
}
