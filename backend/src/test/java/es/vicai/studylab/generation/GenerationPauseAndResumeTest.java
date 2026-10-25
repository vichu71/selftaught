package es.vicai.studylab.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** AC-8 y AC-9 (ADR-44): pausa por tope o por proveedor, sin gasto mientras está en pausa, y «Reanudar» que sigue por lo que falta. */
class GenerationPauseAndResumeTest extends GenerationTestBase {

    private void spendPersonalLimit(UUID user) {
        owner.update("""
                INSERT INTO ai_usage (request_id, attempt, user_id, source, task, provider, model, input_tokens, output_tokens,
                                      tokens_estimated, cost_usd, status, duration_ms)
                VALUES (gen_random_uuid(), 1, ?, 'APP', 'OUTLINE', 'fake', 'fake-1', 0, 0, false, 5.00, 'OK', 1)""", user);
    }

    // ---------- tope de gasto ----------

    @Test
    void ac8_ifThePersonalLimitIsAlreadyReachedTheCoursePausesWithoutCallingTheModel() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        UUID course = insertCourse(ana, 2, true);
        spendPersonalLimit(ana);
        modelDoes(happy(2));

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("BUDGET");
        assertThat(chatCalls()).isZero();
        assertThat(allPieces(course)).isZero();
    }

    @Test
    void ac8_reachingTheLimitInTheMiddlePausesAndKeepsWhatWasDone() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        UUID course = insertCourse(ana, 3, true);
        AtomicInteger calls = new AtomicInteger();
        modelDoes(call -> {
            if (calls.incrementAndGet() == 4) spendPersonalLimit(ana); // el gasto llega al tope tras la tercera pieza
            return happy(3).apply(call);
        });

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("BUDGET");
        assertThat(pieces(course, "DONE")).isEqualTo(3);
        assertThat(pieces(course, "PENDING")).isEqualTo(12);
        assertThat(owner.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'COURSE_GENERATION_PAUSED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void ac8_whileThePausedCourseWaitsNoCallsAreMadeEvenIfTheGeneratorRunsAgain() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        UUID course = insertCourse(ana, 2, true);
        spendPersonalLimit(ana);
        modelDoes(happy(2));
        generator.processPending();

        generator.processPending();
        generator.processPending();

        assertThat(chatCalls()).isZero();
        assertThat(statusOf(course)).isEqualTo("PAUSED");
    }

    @Test
    void ac8_resumingWhileTheLimitStillAppliesPausesAgainWithoutSpendingAnything() throws Exception {
        String ana = logged("ana@example.com");
        UUID anaId = idOf("ana@example.com");
        UUID course = insertCourse(anaId, 2, true);
        spendPersonalLimit(anaId);
        modelDoes(happy(2));
        generator.processPending();
        owner.update("UPDATE course SET resumed_at = now() - interval '5 minutes' WHERE id = ?", course);

        resume(ana, course).andExpect(status().isAccepted());
        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("BUDGET");
        assertThat(chatCalls()).isZero();
    }

    // ---------- proveedor ----------

    @Test
    void ac8_ifTheProviderFailsAfterTheGatewayRetriesTheCoursePausesForTheProvider() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));
        fake.failNext(ProviderErrorKind.UNAVAILABLE, 99);

        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("PAUSED");
        assertThat(stopReasonOf(course)).isEqualTo("PROVIDER");
        assertThat(chatCalls()).isLessThanOrEqualTo(3); // la llamada y los reintentos del gateway, y ni una más
    }

    @Test
    void ac8_aMissingOrRejectedKeyAlsoPausesForTheProviderNotForTheContent() throws Exception {
        logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(happy(2));
        fake.failNext(ProviderErrorKind.AUTH, 99);

        generator.processPending();

        assertThat(stopReasonOf(course)).isEqualTo("PROVIDER");
    }

    @Test
    void ac9_resumingAfterAProviderFailureContinuesFromWhereItStoppedWithoutRegeneratingAnything() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        AtomicInteger calls = new AtomicInteger();
        modelDoes(call -> {
            if (calls.incrementAndGet() == 5) fake.failNext(ProviderErrorKind.UNAVAILABLE, 99); // tras 4 piezas hechas, el proveedor cae
            return happy(2).apply(call);
        });
        generator.processPending();
        int done = pieces(course, "DONE");
        assertThat(statusOf(course)).isEqualTo("PAUSED");
        owner.update("UPDATE course SET resumed_at = NULL WHERE id = ?", course);
        fake.reset();
        modelDoes(happy(2));

        resume(ana, course).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING_GENERATION"));
        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(done).isGreaterThan(0);
        assertThat(chatCalls()).isEqualTo(10 - done); // solo lo que faltaba
        assertThat(pieceKindsCalled()).doesNotContain("temario");
    }

    @Test
    void ac9_resumingAfterContentFailuresRetriesOnlyTheFailedPieces() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(call -> "examen".equals(piece(call)) ? "no es json" : happy(2).apply(call));
        generator.processPending();
        assertThat(stopReasonOf(course)).isEqualTo("CONTENT");
        int failed = pieces(course, "FAILED");
        owner.update("UPDATE course SET resumed_at = NULL WHERE id = ?", course);
        fake.reset();
        modelDoes(happy(2));

        resume(ana, course).andExpect(status().isAccepted());
        generator.processPending();

        assertThat(failed).isEqualTo(2);
        assertThat(chatCalls()).isEqualTo(failed);
        assertThat(pieceKindsCalled()).containsOnly("examen");
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    @Test
    void ac9_resumingAnOutlineThatNeverValidatedAsksForTheOutlineAgain() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        modelDoes(call -> "no es un temario");
        generator.processPending();
        owner.update("UPDATE course SET resumed_at = NULL WHERE id = ?", course);
        fake.reset();
        modelDoes(happy(2));

        resume(ana, course).andExpect(status().isAccepted());
        generator.processPending();

        assertThat(statusOf(course)).isEqualTo("READY");
        assertThat(pieceKindsCalled().get(0)).isEqualTo("temario");
    }

    // ---------- el endpoint ----------

    @Test
    void ac9_onlyAPausedCourseCanBeResumed() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);

        for (String state : new String[] {"PENDING_GENERATION", "GENERATING", "READY", "FAILED"}) {
            owner.update("UPDATE course SET status = ?, resumed_at = NULL WHERE id = ?", state, course);
            resume(ana, course).andExpect(status().isConflict());
        }
        assertThat(statusOf(course)).isEqualTo("FAILED");
    }

    @Test
    void ac9_aSecondClickIsRejectedBecauseTheCourseIsAlreadyQueued() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        owner.update("UPDATE course SET status = 'PAUSED', stop_reason = 'PROVIDER' WHERE id = ?", course);

        resume(ana, course).andExpect(status().isAccepted());
        resume(ana, course).andExpect(status().isConflict());

        assertThat(statusOf(course)).isEqualTo("PENDING_GENERATION");
    }

    @Test
    void ac9_aCourseThatPausesAgainCannotBeResumedBeforeSixtySeconds() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        owner.update("UPDATE course SET status = 'PAUSED', stop_reason = 'PROVIDER' WHERE id = ?", course);
        resume(ana, course).andExpect(status().isAccepted());
        owner.update("UPDATE course SET status = 'PAUSED', stop_reason = 'PROVIDER' WHERE id = ?", course); // vuelve a caer enseguida

        resume(ana, course).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RESUME_TOO_SOON"));

        owner.update("UPDATE course SET resumed_at = now() - interval '61 seconds' WHERE id = ?", course);
        resume(ana, course).andExpect(status().isAccepted());
    }

    @Test
    void ac15_resumingIsAuditedAndRequiresSessionAndCsrfAndOwnership() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = insertCourse(idOf("ana@example.com"), 2, true);
        owner.update("UPDATE course SET status = 'PAUSED', stop_reason = 'PROVIDER' WHERE id = ?", course);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/courses/" + course + "/resume")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isUnauthorized());
        postAsWithoutCsrf(ana, "/api/courses/" + course + "/resume", null).andExpect(status().isForbidden());
        resume(luis, course).andExpect(status().isNotFound());
        resume(luis, UUID.randomUUID()).andExpect(status().isNotFound());
        resume(ana, "no-es-un-uuid").andExpect(status().isNotFound());
        assertThat(statusOf(course)).isEqualTo("PAUSED");

        resume(ana, course).andExpect(status().isAccepted());
        assertThat(auditActions()).contains("COURSE_GENERATION_RESUMED");
    }

    @Test
    void ac15_theAuditOfAPauseCarriesTheReasonAndNeverTheTopic() throws Exception {
        logged("ana@example.com");
        UUID ana = idOf("ana@example.com");
        insertCourse(ana, "tema-secreto-xyz", 2, true, java.util.List.of());
        spendPersonalLimit(ana);
        modelDoes(happy(2));

        generator.processPending();

        String details = owner.queryForObject("SELECT details::text FROM audit_event WHERE action = 'COURSE_GENERATION_PAUSED'", String.class);
        assertThat(details).contains("BUDGET").doesNotContain("tema-secreto-xyz");
    }
}
