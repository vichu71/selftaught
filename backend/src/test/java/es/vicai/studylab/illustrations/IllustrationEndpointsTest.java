package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** F12 AC-10, AC-11 y AC-15: la entrega de la imagen y lo que dice el detalle del curso. */
class IllustrationEndpointsTest extends IllustrationTestBase {

    private record Setup(String luis, UUID course, UUID topic) {}

    private Setup done() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        drain();
        return new Setup(session("luis@example.com"), course, topicIds(course).get(0));
    }

    @Test
    void ac10_theOwnerGetsTheImageAsAJpegWithTheSecurityHeaders() throws Exception {
        Setup s = done();

        MockHttpServletResponse response = imageOf(s.luis(), s.course(), s.topic()).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn().getResponse();

        assertThat(response.getHeader("Cache-Control")).contains("private");
        assertThat(response.getHeader("ETag")).isNotBlank();
        byte[] body = response.getContentAsByteArray();
        assertThat(body[0] & 0xFF).isEqualTo(0xFF);
        assertThat(body[1] & 0xFF).isEqualTo(0xD8);
        assertThat(body).isEqualTo((byte[]) owner.queryForObject("SELECT content FROM course_image WHERE topic_id = ?", byte[].class, s.topic()));
    }

    @Test
    void ac10_theImageIsNeverPublicallyCacheable() throws Exception {
        Setup s = done();

        String cache = imageOf(s.luis(), s.course(), s.topic()).andReturn().getResponse().getHeader("Cache-Control");

        assertThat(cache).doesNotContain("public");
    }

    @Test
    void ac10_theSameEtagAnswersNotModified() throws Exception {
        Setup s = done();
        String etag = imageOf(s.luis(), s.course(), s.topic()).andReturn().getResponse().getHeader("ETag");

        imageOf(s.luis(), s.course(), s.topic(), etag).andExpect(status().isNotModified());
    }

    @Test
    void ac10_aNewImageHasANewEtag() throws Exception {
        Setup s = done();
        String before = imageOf(s.luis(), s.course(), s.topic()).andReturn().getResponse().getHeader("ETag");
        regenerate(s.luis(), s.course(), s.topic()).andExpect(status().isAccepted());
        drain();

        String after = imageOf(s.luis(), s.course(), s.topic()).andReturn().getResponse().getHeader("ETag");

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void ac10_theImageNeedsASession() throws Exception {
        Setup s = done();

        mvc.perform(get(imagePath(s.course(), s.topic()))).andExpect(status().isUnauthorized());
    }

    @Test
    void ac10_anotherUsersCourseAnUnknownTopicAndATopicWithoutImageAnswerTheSame404() throws Exception {
        Setup s = done();
        String marta = session("marta@example.com");
        UUID noImage = insertTopic(s.course(), idOf("luis@example.com"), 9, "Sin imagen");

        String other = imageOf(marta, s.course(), s.topic()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = imageOf(s.luis(), s.course(), UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = imageOf(s.luis(), s.course(), noImage).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String course = imageOf(s.luis(), UUID.randomUUID(), s.topic()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(other).isEqualTo(unknown).isEqualTo(missing).isEqualTo(course);
    }

    @Test
    void ac10_aTopicOfOneCourseCannotBeReadThroughAnotherCourseOfTheSameOwner() throws Exception {
        Setup s = done();
        UUID second = ready("luis@example.com");

        imageOf(s.luis(), second, s.topic()).andExpect(status().isNotFound());
    }

    @Test
    void ac10_theAdultTutorOfAMinorCannotReadImagesByThisRoute() throws Exception {
        Setup s = done();
        String ana = session("ana@example.com");

        imageOf(ana, s.course(), s.topic()).andExpect(status().isNotFound());
    }

    @Test
    void ac10_anImageThatIsPendingFailedBlockedOrRemovedIsNotServed() throws Exception {
        Setup s = done();
        for (String state : new String[] {"PENDING", "FAILED", "BLOCKED", "REMOVED"}) {
            owner.update("UPDATE course_image SET status = ?, content = CASE WHEN ? = 'PENDING' THEN content ELSE NULL END WHERE topic_id = ?", state, state, s.topic());
            imageOf(s.luis(), s.course(), s.topic()).andExpect(status().isNotFound());
        }
    }

    // ---------- el detalle del curso ----------

    @Test
    void ac11_theDetailSaysIfTheCourseHasIllustrationsAndTheStateAndAlternativeTextOfEveryTopic() throws Exception {
        Setup s = done();

        detailOf(s.luis(), s.course()).andExpect(status().isOk()).andExpect(jsonPath("$.illustrations").value(true))
                .andExpect(jsonPath("$.outline[0].image.status").value("DONE"))
                .andExpect(jsonPath("$.outline[0].image.alt").value(org.hamcrest.Matchers.startsWith("Ilustración generada por IA sobre «")))
                .andExpect(jsonPath("$.outline[1].image.status").value("DONE"));
    }

    @Test
    void ac11_beforeTheQueueWorksTheTopicsHaveNoImageAndWhileItWaitsTheyArePending() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        String luis = session("luis@example.com");

        detailOf(luis, course).andExpect(jsonPath("$.illustrations").value(true)).andExpect(jsonPath("$.outline[0].image").doesNotExist());

        spendFor(idOf("luis@example.com"), "4.99");
        drain();
        detailOf(luis, course).andExpect(jsonPath("$.outline[0].image.status").value("PENDING"));
    }

    @Test
    void ac11_aCourseWithoutIllustrationsSaysSoAndShowsNoImages() throws Exception {
        session("luis@example.com");
        UUID course = ready("luis@example.com");

        detailOf(session("luis@example.com"), course).andExpect(jsonPath("$.illustrations").value(false)).andExpect(jsonPath("$.outline[0].image").doesNotExist());
    }

    @Test
    void ac11_failedAndBlockedAreShownButRemovedIsNot() throws Exception {
        Setup s = done();
        UUID second = topicIds(s.course()).get(1);
        owner.update("UPDATE course_image SET status = 'FAILED', content = NULL WHERE topic_id = ?", s.topic());
        owner.update("UPDATE course_image SET status = 'REMOVED', content = NULL WHERE topic_id = ?", second);

        detailOf(s.luis(), s.course()).andExpect(jsonPath("$.outline[0].image.status").value("FAILED")).andExpect(jsonPath("$.outline[1].image").doesNotExist());
    }

    @Test
    void ac11_theDetailNeverCarriesBytesPromptOrErrorMessages() throws Exception {
        Setup s = done();
        owner.update("UPDATE course_image SET error_kind = 'INVALID_RESPONSE' WHERE topic_id = ?", s.topic());

        String body = detailOf(s.luis(), s.course()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("content").doesNotContain("prompt").doesNotContain("INVALID_RESPONSE").doesNotContain("base64").doesNotContain("size_bytes");
    }

    @Test
    void ac11_theLessonSaysWhichTopicItBelongsToAndTheImageOfThatTopic() throws Exception {
        Setup s = done();
        UUID lesson = lessonOf(chapterIds(s.topic()).get(0));

        getAs(s.luis(), "/api/courses/" + s.course() + "/pieces/" + lesson).andExpect(status().isOk()).andExpect(jsonPath("$.topicId").value(s.topic().toString()))
                .andExpect(jsonPath("$.image.status").value("DONE")).andExpect(jsonPath("$.image.alt").exists());
    }

    @Test
    void ac11_aLessonOfATopicWithoutImageHasNoImageField() throws Exception {
        session("luis@example.com");
        UUID course = ready("luis@example.com");
        UUID topic = topicIds(course).get(0);

        getAs(session("luis@example.com"), "/api/courses/" + course + "/pieces/" + lessonOf(chapterIds(topic).get(0))).andExpect(jsonPath("$.image").doesNotExist());
    }

    @Test
    void ac9_deletingATopicOrTheCourseDeletesItsImage() throws Exception {
        Setup s = done();
        assertThat(imageRows(s.course())).hasSize(2);

        owner.update("DELETE FROM course_topic WHERE id = ?", s.topic());
        assertThat(imageRows(s.course())).hasSize(1);

        owner.update("DELETE FROM course WHERE id = ?", s.course());
        assertThat(count("SELECT count(*) FROM course_image WHERE course_id = ?", s.course())).isZero();
    }

    @Test
    void ac10_theExportOfTheCourseDoesNotIncludeTheImages() throws Exception {
        Setup s = done();

        String body = export(s.luis(), s.course()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("image").doesNotContain("Ilustración generada").doesNotContain("/9j/");
    }
}
