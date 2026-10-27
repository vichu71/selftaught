package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * F12 AC-14 (ADR-82): la copia por código lleva las imágenes ya hechas cuando quien canjea es un adulto, con ids nuevos y sin ningún dato del original, y nace
 * con {@code illustrations = false} (nunca se cobra una generación a quien canjea sin pedirla). Un menor recibe la copia sin imágenes.
 */
class IllustrationCopyTest extends IllustrationTestBase {

    private record Setup(String luis, UUID course, String code) {}

    private Setup shared() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        drain();
        String luis = session("luis@example.com");
        return new Setup(luis, course, createCode(luis, course));
    }

    private UUID topicOf(UUID course, int position) {
        return topicIds(course).get(position);
    }

    @Test
    void ac14_anAdultGetsTheDoneImagesInTheirOwnCopyWithNewIds() throws Exception {
        Setup s = shared();
        String marta = session("marta@example.com");

        UUID copy = redeemed(marta, s.code());

        assertThat(imageCount(copy, "DONE")).isEqualTo(2);
        assertThat(owner.queryForObject("SELECT owner_id FROM course_image WHERE course_id = ? LIMIT 1", UUID.class, copy)).isEqualTo(idOf("marta@example.com"));
        assertThat(count("SELECT count(*) FROM course_image WHERE course_id = ? AND topic_id IN (SELECT id FROM course_topic WHERE course_id = ?)", copy, copy)).isEqualTo(2);
        List<UUID> originalImages = owner.queryForList("SELECT id FROM course_image WHERE course_id = ?", UUID.class, s.course());
        List<UUID> copiedImages = owner.queryForList("SELECT id FROM course_image WHERE course_id = ?", UUID.class, copy);
        assertThat(copiedImages).doesNotContainAnyElementsOf(originalImages);
    }

    @Test
    void ac14_theBytesAndTheAlternativeTextAreTheSameAndTheImageIsServedToTheNewOwner() throws Exception {
        Setup s = shared();
        String marta = session("marta@example.com");

        UUID copy = redeemed(marta, s.code());

        UUID copyTopic = topicOf(copy, 0);
        byte[] original = owner.queryForObject("SELECT content FROM course_image WHERE topic_id = ?", byte[].class, topicOf(s.course(), 0));
        byte[] copied = owner.queryForObject("SELECT content FROM course_image WHERE topic_id = ?", byte[].class, copyTopic);
        assertThat(copied).isEqualTo(original);
        imageOf(marta, copy, copyTopic).andExpect(status().isOk());
        detailOf(marta, copy).andExpect(jsonPath("$.outline[0].image.status").value("DONE"));
    }

    @Test
    void ac14_theCopyIsBornWithTheSwitchOffSoNobodyIsChargedWithoutAskingForIt() throws Exception {
        Setup s = shared();
        String marta = session("marta@example.com");
        UUID copy = redeemed(marta, s.code());

        assertThat(owner.queryForObject("SELECT illustrations FROM course WHERE id = ?", Boolean.class, copy)).isFalse();
        int calls = fake.imageCalls().size();
        drain();
        assertThat(fake.imageCalls()).hasSize(calls);
        detailOf(marta, copy).andExpect(jsonPath("$.illustrations").value(false));
    }

    @Test
    void ac14_aTopicWhoseImageWasNotDoneIsNotCopiedAndIsNotGeneratedForTheRedeemer() throws Exception {
        Setup s = shared();
        owner.update("UPDATE course_image SET status = 'FAILED', content = NULL WHERE topic_id = ?", topicOf(s.course(), 0));
        owner.update("UPDATE course_image SET status = 'REMOVED', content = NULL WHERE topic_id = ?", topicOf(s.course(), 1));
        String marta = session("marta@example.com");

        UUID copy = redeemed(marta, s.code());
        drain();

        assertThat(imageRows(copy)).isEmpty();
        assertThat(imageSpend(idOf("marta@example.com"))).isEqualByComparingTo("0");
    }

    @Test
    void ac14_aMinorGetsTheCopyWithoutImagesAndWithTheSwitchOff() throws Exception {
        Setup s = shared();
        String ana = session("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);

        UUID copy = redeemed(hugo, s.code());

        assertThat(imageRows(copy)).isEmpty();
        assertThat(owner.queryForObject("SELECT illustrations FROM course WHERE id = ?", Boolean.class, copy)).isFalse();
        assertThat(owner.queryForObject("SELECT owner_id FROM course WHERE id = ?", UUID.class, copy)).isEqualTo(hugoId);
        detailOf(hugo, copy).andExpect(status().isOk()).andExpect(jsonPath("$.outline[0].image").doesNotExist());
    }

    @Test
    void ac14_theOriginalDoesNotChangeByWhatACopyDoes() throws Exception {
        Setup s = shared();
        String marta = session("marta@example.com");
        UUID copy = redeemed(marta, s.code());
        int originalRows = imageRows(s.course()).size();

        removeImage(marta, copy, topicOf(copy, 0)).andExpect(status().isOk());

        assertThat(imageRows(s.course())).hasSize(originalRows);
        assertThat(imageCount(s.course(), "DONE")).isEqualTo(2);
    }

    @Test
    void ac14_nothingOfTheOriginalLeaksIntoTheCopyRowsOfImages() throws Exception {
        Setup s = shared();
        String marta = session("marta@example.com");
        UUID copy = redeemed(marta, s.code());

        String everything = owner.queryForObject("SELECT string_agg(row_to_json(i)::text, ' ') FROM (SELECT id, course_id, owner_id, topic_id, status, alt_text, mime, size_bytes FROM course_image WHERE course_id = ?) i", String.class, copy);

        assertThat(everything).doesNotContain(s.course().toString()).doesNotContain(idOf("luis@example.com").toString()).doesNotContain(topicOf(s.course(), 0).toString())
                .doesNotContain(topicOf(s.course(), 1).toString());
    }

    @Test
    void ac14_aCourseWithoutImagesCopiesAsBefore() throws Exception {
        session("luis@example.com");
        UUID course = ready("luis@example.com");
        String code = createCode(session("luis@example.com"), course);

        UUID copy = redeemed(session("marta@example.com"), code);

        assertThat(imageRows(copy)).isEmpty();
        assertThat(statusOf(copy)).isEqualTo("READY");
    }
}
