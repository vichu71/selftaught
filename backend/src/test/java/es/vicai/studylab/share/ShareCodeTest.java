package es.vicai.studylab.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-8, AC-9, AC-14 y AC-15: crear, listar y revocar códigos de compartir. */
class ShareCodeTest extends ShareTestBase {

    // ---------- crear (AC-8) ----------

    @Test
    void ac8_theOwnerCreatesACodeThatExpiresInSevenDaysAndIsShownOnlyOnce() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        String body = createCodeRequest(ana, course, Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty()).andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(body, "$.code");
        assertThat(CODE.matcher(code).matches()).as(code).isTrue();
        Instant expires = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(Instant.now(), expires).toHours()).isBetween(6 * 24L, 7 * 24L);
        getAs(ana, coursePath(course, "/share")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void ac8_theCodeIsNeverStoredOnlyItsHash() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        String code = createCode(ana, course);

        Map<String, Object> row = owner.queryForMap("SELECT * FROM share_code WHERE course_id = ?", course);
        assertThat(row.toString()).doesNotContain(code).doesNotContain(normalized(code));
        assertThat((String) row.get("code_hash")).matches("[0-9a-f]{64}");
        assertThat(owner.queryForObject("SELECT code_hash FROM share_code WHERE course_id = ?", String.class, course)).isEqualTo(sha256(normalized(code)));
    }

    @Test
    void ac8_codesAreDifferentEveryTimeAndLookRandom() throws Exception {
        String ana = logged("ana@example.com");
        Set<String> codes = new HashSet<>();
        UUID course = ready("ana@example.com");
        for (int i = 0; i < 5; i++) codes.add(createCode(ana, course));

        assertThat(codes).hasSize(5);
        assertThat(codes.stream().flatMapToInt(String::chars).distinct().count()).isGreaterThan(15);
    }

    @Test
    void ac8_theExpiryCanBeChosenBetweenOneAndNinetyDays() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        for (int days : new int[] {1, 30, 90}) {
            String body = createCodeRequest(ana, course, Map.of("days", days)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long hours = Duration.between(Instant.now(), Instant.parse(JsonPath.read(body, "$.expiresAt"))).toHours();
            assertThat(hours).isBetween(days * 24L - 2, days * 24L);
        }
        for (Object bad : new Object[] {0, 91, -3, "siete", 1.5}) createCodeRequest(ana, course, Map.of("days", bad)).andExpect(status().isBadRequest());
    }

    @Test
    void ac8_onlyAFinishedCourseCanBeShared() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        for (String busy : new String[] {"PENDING_GENERATION", "GENERATING", "PAUSED", "FAILED"}) {
            setStatus(course, busy);
            createCodeRequest(ana, course, Map.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_NOT_READY"));
        }

        assertThat(count("SELECT count(*) FROM share_code")).isZero();
    }

    @Test
    void ac8_aCourseHasAtMostFiveActiveCodesAndRevokedOrExpiredOnesDoNotCount() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        for (int i = 0; i < 5; i++) createCode(ana, course);

        createCodeRequest(ana, course, Map.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHARE_LIMIT"));

        deleteAs(ana, coursePath(course, "/share/" + codeId(course))).andExpect(status().isNoContent());
        createCode(ana, course); // uno revocado libera sitio
        owner.update("UPDATE share_code SET expires_at = now() - interval '1 hour' WHERE id = (SELECT id FROM share_code WHERE revoked_at IS NULL ORDER BY created_at LIMIT 1)");
        createCode(ana, course); // uno caducado también
        createCodeRequest(ana, course, Map.of()).andExpect(status().isConflict());
    }

    // ---------- listar (AC-8) ----------

    @Test
    void ac8_theListShowsTheStateOfEachCodeAndNeverTheCodeItself() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String active = createCode(ana, course);
        createCode(ana, course);
        redeemed(luis, active);
        redeemed(luis, active);
        UUID revoked = codeId(course);
        deleteAs(ana, coursePath(course, "/share/" + revoked)).andExpect(status().isNoContent());

        String body = getAs(ana, coursePath(course, "/share")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2))).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(active).doesNotContain(normalized(active)).doesNotContain("code");
        assertThat(JsonPath.<List<Integer>>read(body, "$[*].uses")).containsExactlyInAnyOrder(2, 0);
        assertThat(JsonPath.<List<Boolean>>read(body, "$[*].active")).containsExactlyInAnyOrder(true, false);
        assertThat(JsonPath.<List<String>>read(body, "$[?(@.active == false)].revokedAt")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(body, "$[*].createdAt")).doesNotContainNull();
        assertThat(JsonPath.<List<String>>read(body, "$[*].expiresAt")).doesNotContainNull();
    }

    @Test
    void ac8_anExpiredCodeShowsAsNotActive() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        createCode(ana, course);
        owner.update("UPDATE share_code SET expires_at = now() - interval '1 minute'");

        getAs(ana, coursePath(course, "/share")).andExpect(jsonPath("$[0].active").value(false));
    }

    // ---------- revocar (AC-9) ----------

    @Test
    void ac9_revokingStopsTheCodeAtOnceAndIsIdempotent() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        UUID id = codeId(course);

        deleteAs(ana, coursePath(course, "/share/" + id)).andExpect(status().isNoContent());
        deleteAs(ana, coursePath(course, "/share/" + id)).andExpect(status().isNoContent());

        redeem(luis, code).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM course WHERE owner_id = ?", idOf("luis@example.com"))).isZero();
    }

    @Test
    void ac9_revokingDoesNotTouchTheCopiesAlreadyMade() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        UUID copy = redeemed(luis, code);

        deleteAs(ana, coursePath(course, "/share/" + codeId(course))).andExpect(status().isNoContent());

        detailOf(luis, copy).andExpect(status().isOk());
        assertThat(pieceCount(copy)).isEqualTo(pieceCount(course));
    }

    @Test
    void ac9_aRevokedCodeAnAnotherCodeOfTheSameCourseKeepsWorking() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        createCode(ana, course);
        UUID first = codeId(course);
        String second = createCode(ana, course);

        deleteAs(ana, coursePath(course, "/share/" + first)).andExpect(status().isNoContent());

        redeemed(luis, second);
    }

    // ---------- permisos (AC-14) ----------

    @Test
    void ac14_onlyTheOwnerManagesTheCodesOfACourse() throws Exception {
        String ana = logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        createCode(ana, course);
        UUID id = codeId(course);

        createCodeRequest(luis, course, Map.of()).andExpect(status().isNotFound());
        getAs(luis, coursePath(course, "/share")).andExpect(status().isNotFound());
        deleteAs(luis, coursePath(course, "/share/" + id)).andExpect(status().isNotFound());

        assertThat(count("SELECT count(*) FROM share_code WHERE revoked_at IS NOT NULL")).isZero();
        assertThat(count("SELECT count(*) FROM share_code")).isEqualTo(1);
    }

    @Test
    void ac14_aCodeOfAnotherCourseOfTheSameOwnerIsNotFoundThroughThisOne() throws Exception {
        String ana = logged("ana@example.com");
        UUID first = ready("ana@example.com");
        UUID second = ready("ana@example.com");
        createCode(ana, second);

        deleteAs(ana, coursePath(first, "/share/" + codeId(second))).andExpect(status().isNotFound());
        getAs(ana, coursePath(first, "/share")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ac14_everythingThatChangesCodesRequiresSessionAndCsrf() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        createCode(ana, course);

        postAsWithoutCsrf(ana, coursePath(course, "/share"), Map.of()).andExpect(status().isForbidden());
        deleteAsWithoutCsrf(ana, coursePath(course, "/share/" + codeId(course))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(coursePath(course, "/share"))).andExpect(status().isUnauthorized());
        postAsWithoutCsrf(ana, "/api/courses/redeem", Map.of("code", "AAAA-AAAA-AAAA-AAAA")).andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM share_code WHERE revoked_at IS NOT NULL")).isZero();
    }

    @Test
    void ac14_theAdultTutorCannotShareTheCoursesOfTheirMinor() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        UUID course = insertCourse(hugoId, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();

        createCodeRequest(ana, course, Map.of()).andExpect(status().isNotFound());
        getAs(ana, coursePath(course, "/share")).andExpect(status().isNotFound());
    }

    // ---------- auditoría (AC-15) ----------

    @Test
    void ac15_creatingAndRevokingAreAuditedWithIdsAndNeverTheCode() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        String code = createCode(ana, course);
        deleteAs(ana, coursePath(course, "/share/" + codeId(course))).andExpect(status().isNoContent());

        assertThat(auditActions()).contains("COURSE_SHARE_CREATED", "COURSE_SHARE_REVOKED");
        String details = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action LIKE 'COURSE_SHARE_%'", String.class);
        assertThat(details).contains(course.toString()).doesNotContain(code).doesNotContain(normalized(code));
    }

    private static String sha256(String text) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
