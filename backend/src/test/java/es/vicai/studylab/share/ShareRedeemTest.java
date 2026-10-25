package es.vicai.studylab.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** AC-10, AC-11, AC-12 y AC-15: canjear un código y recibir una copia propia, y lo que esa copia nunca lleva. */
class ShareRedeemTest extends ShareTestBase {

    // ---------- la copia (AC-10) ----------

    @Test
    void ac10_redeemingCreatesAFinishedCourseOwnedByTheRedeemerWithTheSameOutlineAndContent() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);

        redeem(luis, code).andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Curso de ana@example.com")).andExpect(jsonPath("$.id").isNotEmpty());

        UUID copy = courseOwnedBy("luis@example.com");
        assertThat(owner.queryForObject("SELECT owner_id FROM course WHERE id = ?", UUID.class, copy)).isEqualTo(idOf("luis@example.com"));
        assertThat(statusOf(copy)).isEqualTo("READY");
        assertThat(pieceCount(copy)).isEqualTo(pieceCount(course));
        assertThat(owner.queryForList("SELECT title FROM course_topic WHERE course_id = ? ORDER BY position", String.class, copy))
                .isEqualTo(owner.queryForList("SELECT title FROM course_topic WHERE course_id = ? ORDER BY position", String.class, course));
        assertThat(owner.queryForList("SELECT title FROM course_chapter WHERE course_id = ? ORDER BY topic_id, position", String.class, copy)).hasSize(4);
        assertThat(owner.queryForList("SELECT kind || status FROM course_piece WHERE course_id = ? ORDER BY seq", String.class, copy))
                .isEqualTo(owner.queryForList("SELECT kind || status FROM course_piece WHERE course_id = ? ORDER BY seq", String.class, course));
        assertThat(owner.queryForList("SELECT content_json::text FROM course_piece WHERE course_id = ? AND kind <> 'LESSON' ORDER BY seq", String.class, copy))
                .isEqualTo(owner.queryForList("SELECT content_json::text FROM course_piece WHERE course_id = ? AND kind <> 'LESSON' ORDER BY seq", String.class, course));
        detailOf(luis, copy).andExpect(status().isOk());
    }

    @Test
    void ac10_theCopyHasNewIdsAndRecordsItsOriginAndStartsAtVersionOne() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        putAs(ana, coursePath(course, "/topics/" + topicIds(course).get(0)), Map.of("title", "Editado", "summary", "y")).andExpect(status().isOk()); // versión 2
        String code = createCode(ana, course);

        UUID copy = redeemed(luis, code);

        Map<String, Object> row = owner.queryForMap("SELECT * FROM course WHERE id = ?", copy);
        assertThat(row.get("origin_id")).isEqualTo(course);
        assertThat(row.get("origin_version")).isEqualTo(2);
        assertThat(row.get("version")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM course_piece WHERE id IN (SELECT id FROM course_piece WHERE course_id = ?) AND course_id = ?", course, copy)).isZero();
        assertThat(count("SELECT count(*) FROM course_topic a JOIN course_topic b ON a.id = b.id WHERE a.course_id = ? AND b.course_id = ?", course, copy)).isZero();
        assertThat(count("SELECT count(*) FROM course WHERE intake_id = (SELECT intake_id FROM course WHERE id = ?)", course)).isEqualTo(1);
        detailOf(luis, copy).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.copy.originVersion").value(2)).andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void ac10_theDetailOfACopyNeverRevealsTheIdOfTheOriginal() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        UUID copy = redeemed(luis, createCode(ana, course));

        String body = detailOf(luis, copy).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(course.toString()).doesNotContain(idOf("ana@example.com").toString()).doesNotContain("originId").doesNotContain("origin_id");
    }

    @Test
    void ac10_theCopyIsIndependentFromTheOriginalInBothDirections() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        UUID copy = redeemed(luis, createCode(ana, course));
        String originalTitle = owner.queryForObject("SELECT title FROM course_topic WHERE id = ?", String.class, topicIds(course).get(0));

        putAs(luis, coursePath(copy, "/topics/" + topicIds(copy).get(0)), Map.of("title", "Cambio de Luis", "summary", "y")).andExpect(status().isOk());
        deleteAs(luis, coursePath(copy, "/chapters/" + chapterIds(topicIds(copy).get(0)).get(0))).andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT title FROM course_topic WHERE id = ?", String.class, topicIds(course).get(0))).isEqualTo(originalTitle);
        assertThat(pieceCount(course)).isEqualTo(10);
        assertThat(version(course)).isEqualTo(1);

        putAs(ana, coursePath(course, "/topics/" + topicIds(course).get(1)), Map.of("title", "Cambio de Ana", "summary", "y")).andExpect(status().isOk());
        assertThat(owner.queryForObject("SELECT title FROM course_topic WHERE id = ?", String.class, topicIds(copy).get(1))).isNotEqualTo("Cambio de Ana");
        assertThat(version(copy)).isEqualTo(3);
    }

    @Test
    void ac10_theCopyIsFullyEditableAndSharableByItsNewOwner() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String marta = logged("marta@example.com");
        UUID course = ready("ana@example.com");
        UUID copy = redeemed(luis, createCode(ana, course));

        UUID again = redeemed(marta, createCode(luis, copy));

        assertThat(owner.queryForObject("SELECT origin_id FROM course WHERE id = ?", UUID.class, again)).isEqualTo(copy);
        assertThat(owner.queryForObject("SELECT owner_id FROM course WHERE id = ?", UUID.class, copy)).isEqualTo(idOf("luis@example.com"));
    }

    @Test
    void ac10_theOwnerCanRedeemTheirOwnCodeAndTheSamePersonTwiceGetsTwoCopies() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);

        UUID own = redeemed(ana, code);
        UUID first = redeemed(luis, code);
        UUID second = redeemed(luis, code);

        assertThat(List.of(own, first, second)).doesNotHaveDuplicates();
        assertThat(count("SELECT count(*) FROM course WHERE origin_id = ?", course)).isEqualTo(3);
        assertThat(count("SELECT uses FROM share_code WHERE course_id = ?", course)).isEqualTo(3);
    }

    @Test
    void ac10_theCodeAcceptsLowercaseSpacesAndMissingHyphens() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);

        redeemed(luis, code.toLowerCase());
        redeemed(luis, normalized(code));
        redeemed(luis, "  " + code.replace("-", " ") + "  ");

        assertThat(count("SELECT uses FROM share_code WHERE course_id = ?", course)).isEqualTo(3);
    }

    @Test
    void ac10_whenTheOriginalDisappearsTheCopyKeepsWorkingWithoutOrigin() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        UUID copy = redeemed(luis, createCode(ana, course));

        owner.update("DELETE FROM course WHERE id = ?", course);

        assertThat(owner.queryForMap("SELECT origin_id, origin_version FROM course WHERE id = ?", copy).get("origin_id")).isNull();
        detailOf(luis, copy).andExpect(status().isOk());
    }

    @Test
    void ac10_aCodeStopsWorkingWhileTheCourseIsNotFinishedAndWorksAgainWhenItIs() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);

        setStatus(course, "PENDING_GENERATION");
        redeem(luis, code).andExpect(status().isNotFound());
        setStatus(course, "READY");

        redeemed(luis, code);
    }

    @Test
    void ac10_twoPeopleRedeemingAtTheSameTimeBothGetTheirCopy() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        String marta = logged("marta@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> both = new ArrayList<>();
            for (String who : new String[] {luis, marta}) both.add(() -> redeem(who, code).andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(both)) statuses.add(f.get());
            assertThat(statuses).containsOnly(201);
        } finally {
            pool.shutdown();
        }

        assertThat(count("SELECT uses FROM share_code WHERE course_id = ?", course)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM course WHERE origin_id = ?", course)).isEqualTo(2);
    }

    // ---------- lo que no viaja (AC-11) ----------

    @Test
    void ac11_theCopyNeverCarriesTheMaterialTheSourcesTheProgressTheAttemptsOrTheConversations() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = fullCourse(ana, "ana@example.com");
        int originalPieces = pieceCount(course);
        assertThat(count("SELECT count(*) FROM course_piece WHERE course_id = ? AND sources IS NOT NULL", course)).isPositive(); // el original sí cita sus fuentes
        String code = createCode(ana, course);

        UUID copy = redeemed(luis, code);

        UUID luisId = idOf("luis@example.com");
        assertThat(pieceCount(copy)).isEqualTo(originalPieces);
        assertThat(count("SELECT count(*) FROM course_piece WHERE course_id = ? AND sources IS NOT NULL", copy)).isZero();
        assertThat(count("SELECT count(*) FROM course_piece WHERE course_id = ? AND content_md ~ '\\[[0-9]{1,3}\\]'", copy)).isZero();
        assertThat(count("SELECT count(*) FROM course_document WHERE course_id = ?", copy)).isZero();
        assertThat(count("SELECT count(*) FROM source_document WHERE owner_id = ?", luisId)).isZero();
        assertThat(count("SELECT count(*) FROM source_chunk WHERE owner_id = ?", luisId)).isZero();
        assertThat(count("SELECT count(*) FROM lesson_progress WHERE owner_id = ?", luisId)).isZero();
        assertThat(count("SELECT count(*) FROM exercise_attempt WHERE owner_id = ?", luisId)).isZero();
        assertThat(count("SELECT count(*) FROM exam_attempt WHERE owner_id = ?", luisId)).isZero();
        assertThat(count("SELECT count(*) FROM tutor_message WHERE owner_id = ?", luisId)).isZero();
        assertThat(count("SELECT count(*) FROM course_intake WHERE owner_id = ? AND status <> 'CONSUMED'", luisId)).isZero();
        assertThat(count("SELECT count(*) FROM ai_usage WHERE user_id = ?", luisId)).isZero();
        assertThat(owner.queryForObject("SELECT sheet::text FROM course WHERE id = ?", String.class, copy)).contains("\"documentIds\":[]");
    }

    @Test
    void ac11_nothingOfTheOriginalsDataAppearsAnywhereInTheCopy() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = fullCourse(ana, "ana@example.com");
        String documentId = owner.queryForObject("SELECT document_id::text FROM course_document WHERE course_id = ?", String.class, course);
        UUID copy = redeemed(luis, createCode(ana, course));

        String everything = everythingOf(copy);

        assertThat(everything).doesNotContain(documentId).doesNotContain(course.toString()).doesNotContain(idOf("ana@example.com").toString()).doesNotContain("apuntes-privados")
                .doesNotContain("CONCEPTO-PRIVADO");
        owner.queryForList("SELECT id::text FROM course_piece WHERE course_id = ?", String.class, course).forEach(id -> assertThat(everything).doesNotContain(id));
        owner.queryForList("SELECT id::text FROM course_topic WHERE course_id = ?", String.class, course).forEach(id -> assertThat(everything).doesNotContain(id));
    }

    @Test
    void ac11_theOriginalStaysExactlyAsItWasAfterBeingCopied() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = fullCourse(ana, "ana@example.com");
        String before = everythingOf(course);
        int progress = count("SELECT count(*) FROM lesson_progress WHERE course_id = ?", course);
        int attempts = count("SELECT count(*) FROM exercise_attempt WHERE course_id = ?", course) + count("SELECT count(*) FROM exam_attempt WHERE course_id = ?", course);
        int version = version(course);

        redeemed(luis, createCode(ana, course));

        assertThat(everythingOf(course)).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM lesson_progress WHERE course_id = ?", course)).isEqualTo(progress);
        assertThat(count("SELECT count(*) FROM exercise_attempt WHERE course_id = ?", course) + count("SELECT count(*) FROM exam_attempt WHERE course_id = ?", course)).isEqualTo(attempts);
        assertThat(version(course)).isEqualTo(version);
        assertThat(statusOf(course)).isEqualTo("READY");
    }

    // ---------- códigos que no valen (AC-12) ----------

    @Test
    void ac12_everyCodeThatDoesNotWorkAnswersExactlyTheSameWay() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String expired = createCode(ana, course);
        owner.update("UPDATE share_code SET expires_at = now() - interval '1 minute' WHERE course_id = ?", course);
        String revoked = createCode(ana, course);
        deleteAs(ana, coursePath(course, "/share/" + codeId(course))).andExpect(status().isNoContent());

        List<String> bodies = new ArrayList<>();
        for (String bad : new String[] {"AAAA-AAAA-AAAA-AAAA", expired, revoked, "corto", "x".repeat(300), "AAAA-AAAA-AAAA-AAA!", "", "   ", "'; DROP TABLE share_code; --"}) {
            bodies.add(redeem(luis, bad).andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString());
        }

        assertThat(bodies.stream().distinct()).hasSize(1);
        assertThat(bodies.get(0)).doesNotContain("caduc").doesNotContain("revoc").doesNotContain("expired");
        assertThat(count("SELECT count(*) FROM course WHERE owner_id = ?", idOf("luis@example.com"))).isZero();
        assertThat(count("SELECT count(*) FROM share_code")).isEqualTo(2);
    }

    @Test
    void ac12_aMissingOrNonTextCodeIsABadRequestThatStillCountsAsAFailure() throws Exception {
        String luis = logged("luis@example.com");

        postAs(luis, "/api/courses/redeem", Map.of()).andExpect(status().is4xxClientError());
        postAs(luis, "/api/courses/redeem", Map.of("code", 5)).andExpect(status().is4xxClientError());

        assertThat(count("SELECT count(*) FROM course")).isZero();
    }

    @Test
    void ac12_everyFailureIsRecordedForTheLimitAndEverySuccessAlso() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);

        redeem(luis, "AAAA-AAAA-AAAA-AAAA").andExpect(status().isNotFound());
        redeem(luis, "BBBB-BBBB-BBBB-BBBB").andExpect(status().isNotFound());
        redeemed(luis, code);

        assertThat(count("SELECT count(*) FROM course_action WHERE user_id = ? AND kind = 'REDEEM_FAIL'", idOf("luis@example.com"))).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM course_action WHERE user_id = ? AND kind = 'REDEEM_OK'", idOf("luis@example.com"))).isEqualTo(1);
    }

    // ---------- auditoría y logs (AC-15) ----------

    @Test
    void ac15_redeemingAndFailingAreAuditedWithIdsAndNeverTheCode() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        UUID copy = redeemed(luis, code);
        redeem(luis, "AAAA-AAAA-AAAA-AAAA").andExpect(status().isNotFound());

        assertThat(auditActions()).contains("COURSE_SHARE_REDEEMED", "SHARE_CODE_FAILED");
        Map<String, Object> redeemedEvent = owner.queryForMap("SELECT actor_user_id, subject_user_id, details::text AS details FROM audit_event WHERE action = 'COURSE_SHARE_REDEEMED'");
        assertThat(redeemedEvent.get("actor_user_id")).isEqualTo(idOf("luis@example.com"));
        assertThat(redeemedEvent.get("subject_user_id")).isEqualTo(idOf("ana@example.com"));
        assertThat((String) redeemedEvent.get("details")).contains(course.toString()).contains(copy.toString());
        String all = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event", String.class);
        assertThat(all).doesNotContain(code).doesNotContain(normalized(code)).doesNotContain("AAAA-AAAA");
    }

    @Test
    void ac15_theCodeNeverReachesTheLogs() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        String code;
        try {
            String ana = logged("ana@example.com");
            String luis = logged("luis@example.com");
            UUID course = ready("ana@example.com");
            code = createCode(ana, course);
            redeemed(luis, code);
            redeem(luis, "ZZZZ-9999-ZZZZ-9999").andExpect(status().isNotFound());
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain(code).doesNotContain(normalized(code)).doesNotContain("ZZZZ-9999").doesNotContain("ZZZZ9999");
    }
}
