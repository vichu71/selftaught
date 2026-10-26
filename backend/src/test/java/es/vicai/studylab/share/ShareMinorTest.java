package es.vicai.studylab.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-13 (ADR-65): un menor canjea con filtro sobre toda la copia, y su adulto tutor lo ve en el panel. */
class ShareMinorTest extends ShareTestBase {

    private record Setup(String ana, String hugo, UUID hugoId, String luis, UUID course, String code) {}

    private Setup setup() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        String luis = logged("luis@example.com");
        UUID course = ready("luis@example.com");
        return new Setup(ana, hugo, hugoId, luis, course, createCode(luis, course));
    }

    @Test
    void ac13_aMinorRedeemsACleanCourseAndGetsTheirOwnCopy() throws Exception {
        Setup s = setup();

        UUID copy = redeemed(s.hugo(), s.code());

        assertThat(owner.queryForObject("SELECT owner_id FROM course WHERE id = ?", UUID.class, copy)).isEqualTo(s.hugoId());
        assertThat(statusOf(copy)).isEqualTo("READY");
        detailOf(s.hugo(), copy).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lección", "ejercicio", "examen", "título del tema", "objetivo del capítulo"})
    void ac13_ifAnyTextOfTheCopyTripsTheFilterNothingIsCreatedAndTheMinorSeesTheAgreedMessage(String place) throws Exception {
        {
            Setup s = setup();
            UUID topic = topicIds(s.course()).get(0);
            UUID chapter = chapterIds(topic).get(0);
            switch (place) {
                case "lección" -> owner.update("UPDATE course_piece SET content_md = ? WHERE id = ?", "# Lección\n\n" + BLOCKED_TEXT, lessonOf(chapter));
                case "ejercicio" -> owner.update("UPDATE course_piece SET content_json = jsonb_set(content_json, '{exercises,0,statement}', to_jsonb(?::text)) WHERE id = ?", BLOCKED_TEXT, exercisesOf(chapter));
                case "examen" -> owner.update("UPDATE course_piece SET content_json = jsonb_set(content_json, '{questions,0,text}', to_jsonb(?::text)) WHERE id = ?", BLOCKED_TEXT, examOf(topic));
                case "título del tema" -> owner.update("UPDATE course_topic SET title = ? WHERE id = ?", BLOCKED_TEXT, topic);
                default -> owner.update("UPDATE course_chapter SET objective = ? WHERE id = ?", BLOCKED_TEXT, chapter);
            }

            redeem(s.hugo(), s.code()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("Contenido inapropiado. Consulta con tu adulto."))
                    .andExpect(jsonPath("$.code").value("COPY_NOT_ALLOWED"));

            assertThat(count("SELECT count(*) FROM course WHERE owner_id = ?", s.hugoId())).as(place).isZero();
            assertThat(count("SELECT uses FROM share_code WHERE course_id = ?", s.course())).as(place).isZero();
            assertThat(count("SELECT count(*) FROM course_action WHERE user_id = ? AND kind = 'REDEEM_OK'", s.hugoId())).isZero();
        }
    }

    @Test
    void ac13_theSameContentIsCopiedByAnAdultBecauseTheFilterOnlyAppliesToMinors() throws Exception {
        Setup s = setup();
        owner.update("UPDATE course_piece SET content_md = ? WHERE id = ?", "# Lección\n\n" + BLOCKED_TEXT, lessonOf(chapterIds(topicIds(s.course()).get(0)).get(0)));

        redeemed(s.ana(), s.code());
    }

    @Test
    void ac13_theBlockedCopyIsAuditedWithIdsAndNeverTheText() throws Exception {
        Setup s = setup();
        owner.update("UPDATE course_piece SET content_md = ? WHERE id = ?", "# Lección\n\n" + BLOCKED_TEXT, lessonOf(chapterIds(topicIds(s.course()).get(0)).get(0)));

        redeem(s.hugo(), s.code()).andExpect(status().isUnprocessableEntity());

        assertThat(auditActions()).contains("COURSE_COPY_BLOCKED");
        Object details = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action = 'COURSE_COPY_BLOCKED'", String.class);
        assertThat((String) details).contains(s.course().toString()).doesNotContain("bomba").doesNotContain(s.code());
        assertThat(owner.queryForObject("SELECT actor_user_id FROM audit_event WHERE action = 'COURSE_COPY_BLOCKED'", UUID.class)).isEqualTo(s.hugoId());
    }

    @Test
    void ac13_theAdultTutorSeesTheBlockedCopyInThePanelAlertsWithoutTheText() throws Exception {
        Setup s = setup();
        owner.update("UPDATE course_piece SET content_md = ? WHERE id = ?", "# Lección\n\n" + BLOCKED_TEXT, lessonOf(chapterIds(topicIds(s.course()).get(0)).get(0)));
        redeem(s.hugo(), s.code()).andExpect(status().isUnprocessableEntity());

        String body = getAs(s.ana(), "/api/tutor/students/" + s.hugoId() + "/alerts").andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("COURSE_COPY_BLOCKED")).andReturn()
                .getResponse().getContentAsString();

        assertThat(body).doesNotContain("bomba").doesNotContain(s.code());
    }

    @Test
    void ac13_aMinorCanShareTheirOwnCourseAndAnotherAdultRedeemsIt() throws Exception {
        Setup s = setup();
        UUID own = insertCourse(s.hugoId(), "Historia de Hugo", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();

        String code = createCode(s.hugo(), own);
        UUID copy = redeemed(s.luis(), code);

        assertThat(owner.queryForObject("SELECT owner_id FROM course WHERE id = ?", UUID.class, copy)).isEqualTo(idOf("luis@example.com"));
        assertThat(owner.queryForObject("SELECT origin_id FROM course WHERE id = ?", UUID.class, copy)).isEqualTo(own);
    }

    @Test
    void ac13_theAdultTutorCannotRedeemOnBehalfOfTheMinorByAnyOtherPath() throws Exception {
        Setup s = setup();

        postAs(s.ana(), "/api/tutor/students/" + s.hugoId() + "/redeem", java.util.Map.of("code", s.code())).andExpect(status().is4xxClientError());

        assertThat(count("SELECT count(*) FROM course WHERE owner_id = ?", s.hugoId())).isZero();
    }
}
