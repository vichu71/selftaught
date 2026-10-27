package es.vicai.studylab.coursedelete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-1 a AC-9 y AC-12 (ADR-84, ADR-85): qué se lleva el borrado, en qué estados se permite y qué se conserva. */
class CourseDeleteTest extends CourseDeleteTestBase {

    // ---------- AC-1 ----------

    @Test
    void ac1_deleteTakesTheCourseAndEverythingThatDependsOnItAndAnswers204() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = loaded(ana, "ana@example.com");
        UUID lesson = lessonOf(chapterIds(topicIds(course).get(0)).get(0));
        assertThat(rowsOf(course)).isGreaterThan(20);

        deleteOne(ana, course).andExpect(status().isNoContent());

        assertThat(rowsOf(course)).isZero();
        detailOf(ana, course).andExpect(status().isNotFound());
        getAs(ana, "/api/courses/" + course + "/pieces/" + lesson).andExpect(status().isNotFound());
    }

    @Test
    void ac1_itWorksThroughTheBulkEndpointWithASingleId() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        deleteMany(ana, List.of(course)).andExpect(status().isNoContent());

        assertThat(exists(course)).isFalse();
    }

    @Test
    void ac1_theCourseDisappearsFromTheList() throws Exception {
        String ana = logged("ana@example.com");
        UUID gone = ready("ana@example.com");
        UUID kept = ready("ana@example.com");

        deleteOne(ana, gone).andExpect(status().isNoContent());

        getAs(ana, "/api/courses").andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + gone + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == '" + kept + "')]").isNotEmpty());
    }

    @Test
    void ac1_theLibraryDocumentsSurviveTheCourseThatUsedThem() throws Exception {
        String ana = logged("ana@example.com");
        UUID user = idOf("ana@example.com");
        UUID course = fullCourse(ana, "ana@example.com");
        int documents = count("SELECT count(*) FROM source_document WHERE owner_id = ?", user);
        int chunks = count("SELECT count(*) FROM source_chunk WHERE owner_id = ?", user);
        assertThat(count("SELECT count(*) FROM course_document WHERE course_id = ?", course)).isEqualTo(1);

        deleteOne(ana, course).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM source_document WHERE owner_id = ?", user)).isEqualTo(documents);
        assertThat(count("SELECT count(*) FROM source_chunk WHERE owner_id = ?", user)).isEqualTo(chunks);
    }

    // ---------- AC-2 ----------

    @Test
    void ac2_itRequiresASessionAndTheCsrfToken() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        mvc.perform(delete("/api/courses/" + course).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/courses/delete").with(csrf()).contentType("application/json").content("{\"ids\":[\"" + course + "\"]}")).andExpect(status().isUnauthorized());
        deleteAsWithoutCsrf(ana, "/api/courses/" + course).andExpect(status().isForbidden());
        postAsWithoutCsrf(ana, "/api/courses/delete", Map.of("ids", List.of(course))).andExpect(status().isForbidden());

        assertThat(exists(course)).isTrue();
    }

    @Test
    void ac2_anotherUsersCourseAndAMissingOneAnswerTheSame404AndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = loaded(ana, "ana@example.com");
        int rows = rowsOf(course);

        String foreign = deleteOne(luis, course).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = deleteOne(luis, UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        deleteAs(luis, "/api/courses/no-es-un-uuid").andExpect(status().isNotFound());
        deleteMany(luis, List.of(course)).andExpect(status().isNotFound());

        assertThat(foreign).isEqualTo(missing);
        assertThat(rowsOf(course)).isEqualTo(rows);
    }

    @Test
    void ac2_theGuardianCannotDeleteTheCoursesOfTheirMinor() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        UUID course = insertCourse(hugoId, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        int rows = rowsOf(course);

        deleteOne(ana, course).andExpect(status().isNotFound());
        deleteMany(ana, List.of(course)).andExpect(status().isNotFound());

        assertThat(rowsOf(course)).isEqualTo(rows);
    }

    // ---------- AC-3 ----------

    @Test
    void ac3_aMinorGets403OnBothEndpointsAndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(hugoId, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        int rows = rowsOf(course);

        deleteOne(hugo, course).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHILD_CANNOT_DELETE"));
        deleteMany(hugo, List.of(course)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHILD_CANNOT_DELETE"));
        deleteOne(hugo, UUID.randomUUID()).andExpect(status().isForbidden()); // el menor no borra: ni un id ajeno cambia la respuesta

        assertThat(rowsOf(course)).isEqualTo(rows);
        assertThat(deletedAudit()).isEmpty();
    }

    // ---------- AC-4 ----------

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_GENERATION", "GENERATING"})
    void ac4_aCourseInTheQueueOrBeingGeneratedAnswers409AndNothingChanges(String state) throws Exception {
        String ana = logged("ana@example.com");
        UUID course = loaded(ana, "ana@example.com");
        setStatus(course, state);
        int rows = rowsOf(course);

        deleteOne(ana, course).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(BUSY));
        deleteMany(ana, List.of(course)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(BUSY));

        assertThat(rowsOf(course)).isEqualTo(rows);
        assertThat(deletedAudit()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "PAUSED", "FAILED"})
    void ac4_aFinishedPausedOrFailedCourseCanBeDeleted(String state) throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        setStatus(course, state);

        deleteOne(ana, course).andExpect(status().isNoContent());

        assertThat(rowsOf(course)).isZero();
    }

    @Test
    void ac4_aPausedCourseWithPendingPiecesDoesNotComeBackToTheQueueAfterDeleting() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        owner.update("UPDATE course_piece SET status = 'PENDING' WHERE id = ?", lessonOf(chapterIds(topicIds(course).get(0)).get(0)));
        setStatus(course, "PAUSED");
        assertThat(count("SELECT count(*) FROM course_piece WHERE course_id = ? AND status = 'PENDING'", course)).isPositive();

        deleteOne(ana, course).andExpect(status().isNoContent());
        fake.reset();
        modelDoes(happy(2));
        generator.processPending();

        assertThat(fake.calls()).isEmpty();
        assertThat(exists(course)).isFalse();
    }

    @Test
    void edge_deletingTheSameCourseTwiceAnswers404TheSecondTime() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        deleteOne(ana, course).andExpect(status().isNoContent());
        deleteOne(ana, course).andExpect(status().isNotFound());
    }

    // ---------- AC-5 ----------

    @Test
    void ac5_theAiSpendRowsAndTheMonthlyTotalSurviveTheDeletion() throws Exception {
        String ana = logged("ana@example.com");
        UUID user = idOf("ana@example.com");
        UUID course = loaded(ana, "ana@example.com");
        insertUsage(user, course, "0.2500");
        UUID other = ready("ana@example.com");
        insertUsage(user, other, "0.0500");
        int rows = usageRows(course);
        BigDecimal spend = totalSpend(user);
        assertThat(rows).isGreaterThanOrEqualTo(2);

        deleteOne(ana, course).andExpect(status().isNoContent());

        assertThat(usageRows(course)).isEqualTo(rows);
        assertThat(totalSpend(user)).isEqualByComparingTo(spend);
        assertThat(usageRows(other)).isPositive();
    }

    // ---------- AC-6 ----------

    @Test
    void ac6_copiesSurviveUntouchedWithoutAnOriginAndTheCodeStopsWorking() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        UUID copy = redeemed(luis, code);
        int copyRows = rowsOf(copy);
        String copyContent = everythingOf(copy);
        assertThat(owner.queryForObject("SELECT origin_id FROM course WHERE id = ?", UUID.class, copy)).isEqualTo(course);

        deleteOne(ana, course).andExpect(status().isNoContent());

        assertThat(owner.queryForObject("SELECT origin_id FROM course WHERE id = ?", UUID.class, copy)).isNull();
        assertThat(rowsOf(copy)).isEqualTo(copyRows);
        assertThat(everythingOf(copy)).isEqualTo(copyContent);
        detailOf(luis, copy).andExpect(status().isOk());
        redeem(luis, code).andExpect(status().isNotFound());
    }

    @Test
    void ac6_deletingACopyWhoseOriginIsAlreadyGoneWorks() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        UUID copy = redeemed(luis, createCode(ana, course));
        deleteOne(ana, course).andExpect(status().isNoContent());

        deleteOne(luis, copy).andExpect(status().isNoContent());

        assertThat(exists(copy)).isFalse();
    }

    // ---------- AC-7 ----------

    @Test
    void ac7_theIntakeRowStaysForTheRateLimitButWithoutTopicQuestionsOrDocuments() throws Exception {
        String ana = logged("ana@example.com");
        UUID user = idOf("ana@example.com");
        UUID course = fullCourse(ana, "ana@example.com");
        UUID intake = intakeOf(course);
        owner.update("UPDATE course_intake SET topic = 'tema-privado-zz', questions = '[{\"q\":\"pregunta-privada-zz\"}]'::jsonb, document_ids = '[\"abc\"]'::jsonb WHERE id = ?", intake);
        int intakesBefore = count("SELECT count(*) FROM course_intake WHERE owner_id = ?", user);

        deleteOne(ana, course).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM course_intake WHERE owner_id = ?", user)).isEqualTo(intakesBefore);
        Map<String, Object> row = owner.queryForMap("SELECT topic, questions::text AS questions, document_ids::text AS docs, created_at FROM course_intake WHERE id = ?", intake);
        assertThat(row.get("topic")).isEqualTo("");
        assertThat(row.get("questions")).isNull();
        assertThat(row.get("docs")).isEqualTo("[]");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void ac7_aBlockedIntakeWithNoCourseIsNotTouched() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        owner.update("INSERT INTO course_intake (id, owner_id, topic, status, blocked_category, expires_at) VALUES (gen_random_uuid(), ?, 'tema rechazado', 'BLOCKED', 'OTHER', now() + interval '1 day')",
                idOf("ana@example.com"));

        deleteOne(ana, course).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM course_intake WHERE status = 'BLOCKED' AND topic = 'tema rechazado'")).isEqualTo(1);
    }

    // ---------- AC-8 ----------

    @Test
    void ac8_eachDeletedCourseIsAuditedWithIdsAndNeverItsTitle() throws Exception {
        String ana = logged("ana@example.com");
        UUID a = titled("ana@example.com", UNIQUE_TITLE);
        UUID b = ready("ana@example.com");

        deleteMany(ana, List.of(a, b)).andExpect(status().isNoContent());

        List<String> events = deletedAudit();
        assertThat(events).hasSize(2);
        String all = String.join(" ", events);
        assertThat(all).contains(a.toString()).contains(b.toString()).doesNotContain(UNIQUE_TITLE);
        assertThat(count("SELECT count(*) FROM audit_event WHERE action = 'COURSE_DELETED' AND actor_user_id = ?", idOf("ana@example.com"))).isEqualTo(2);
    }

    @Test
    void ac8_rejectionsLeaveNoAuditEvent() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        setStatus(course, "GENERATING");

        deleteOne(ana, course).andExpect(status().isConflict());
        deleteOne(luis, course).andExpect(status().isNotFound());

        assertThat(deletedAudit()).isEmpty();
    }

    // ---------- AC-9 ----------

    @Test
    void ac9_deletingOneCourseTouchesNoOtherCourseOfTheSameUserNorOfAnotherUser() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID gone = loaded(ana, "ana@example.com");
        UUID sameUser = loaded(ana, "ana@example.com");
        UUID otherUser = loaded(luis, "luis@example.com");
        int sameRows = rowsOf(sameUser);
        int otherRows = rowsOf(otherUser);
        int intakes = count("SELECT count(*) FROM course_intake");
        int usage = count("SELECT count(*) FROM ai_usage");

        deleteOne(ana, gone).andExpect(status().isNoContent());

        assertThat(rowsOf(sameUser)).isEqualTo(sameRows);
        assertThat(rowsOf(otherUser)).isEqualTo(otherRows);
        assertThat(count("SELECT count(*) FROM course_intake")).isEqualTo(intakes);
        assertThat(count("SELECT count(*) FROM ai_usage")).isEqualTo(usage);
    }

    // ---------- casos borde: ilustraciones ----------

    @Test
    void edge_imagesOfEveryStatusGoWithTheCourseAndAPendingOneIsNeverGeneratedAfterwards() throws Exception {
        String ana = logged("ana@example.com");
        UUID user = idOf("ana@example.com");
        UUID course = ready("ana@example.com");
        List<UUID> topics = topicIds(course);
        owner.update("UPDATE course SET illustrations = true WHERE id = ?", course);
        insertImage(course, user, topics.get(0), "PENDING");
        insertImage(course, user, topics.get(1), "REMOVED");

        deleteOne(ana, course).andExpect(status().isNoContent());
        fake.reset();
        int processed = illustrations.processPending();

        assertThat(count("SELECT count(*) FROM course_image WHERE course_id = ?", course)).isZero();
        assertThat(processed).isZero();
        assertThat(fake.imageCalls()).isEmpty();
    }

    // ---------- AC-12: borrado múltiple ----------

    @Test
    void ac12_bulkDeletesAllTheCoursesInOneGo() throws Exception {
        String ana = logged("ana@example.com");
        List<UUID> courses = new ArrayList<>();
        for (int i = 0; i < 3; i++) courses.add(loaded(ana, "ana@example.com"));
        UUID kept = ready("ana@example.com");
        int keptRows = rowsOf(kept);

        deleteMany(ana, courses).andExpect(status().isNoContent());

        for (UUID c : courses) assertThat(rowsOf(c)).isZero();
        assertThat(rowsOf(kept)).isEqualTo(keptRows);
    }

    @Test
    void ac12_ifOneIsForeignNothingIsDeletedAndTheAnswerIsTheSame404() throws Exception {
        String ana = logged("ana@example.com");
        logged("luis@example.com");
        UUID mine = loaded(ana, "ana@example.com");
        UUID foreign = ready("luis@example.com");
        int rows = rowsOf(mine);

        String withForeign = deleteMany(ana, List.of(mine, foreign)).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String withMissing = deleteMany(ana, List.of(mine, UUID.randomUUID())).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(withForeign).isEqualTo(withMissing);
        assertThat(rowsOf(mine)).isEqualTo(rows);
        assertThat(exists(foreign)).isTrue();
        assertThat(deletedAudit()).isEmpty();
    }

    @Test
    void ac12_ifOneIsBusyNothingIsDeleted() throws Exception {
        String ana = logged("ana@example.com");
        UUID done = loaded(ana, "ana@example.com");
        UUID busy = ready("ana@example.com");
        setStatus(busy, "GENERATING");
        int rows = rowsOf(done);

        deleteMany(ana, List.of(done, busy)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(BUSY));

        assertThat(rowsOf(done)).isEqualTo(rows);
        assertThat(exists(busy)).isTrue();
        assertThat(deletedAudit()).isEmpty();
    }

    @Test
    void ac12_invalidListsAreRejectedWith400AndNothingChanges() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        List<Object> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) tooMany.add(UUID.randomUUID());

        deleteMany(ana, List.of()).andExpect(status().isBadRequest());
        deleteMany(ana, List.of(course, course)).andExpect(status().isBadRequest());
        deleteMany(ana, tooMany).andExpect(status().isBadRequest());
        deleteMany(ana, List.of("no-es-un-uuid")).andExpect(status().isBadRequest());
        deleteMany(ana, Arrays.asList(course.toString(), null)).andExpect(status().isBadRequest());
        postAs(ana, "/api/courses/delete", Map.of("otro", 1)).andExpect(status().isBadRequest());

        assertThat(exists(course)).isTrue();
    }

    @Test
    void ac12_fiftyIdsIsTheLimitAndIsAccepted() throws Exception {
        String ana = logged("ana@example.com");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) ids.add(insertCourse(idOf("ana@example.com"), "Curso " + i, 1, false, List.of()));
        for (UUID id : ids) setStatus(id, "READY");

        deleteMany(ana, ids).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM course WHERE owner_id = ?", idOf("ana@example.com"))).isZero();
    }
}
