package es.vicai.studylab.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-3 (ART-003): {@code GET /api/owner/usage?range=…}. Sembrado: dos cursos {@code READY} (el de Hugo y uno de Ana con progreso, intentos y una duda), uno por cada otro estado,
 * tres documentos, tres ilustraciones, dos códigos creados y uno canjeado. Las cuentas se cuentan sin leer ningún texto.
 */
class OwnerUsageStatsTest extends OwnerTestBase {

    private record Setup(Family f, UUID anaCourse, UUID paused, UUID failed, UUID queued, UUID generating) {}

    private Setup seeded() throws Exception {
        Family f = family();
        UUID anaCourse = ready("ana@example.com");
        giveDependents(f.ana(), f.anaId(), anaCourse, chapterIds(topicIds(anaCourse).get(0)).get(0), topicIds(anaCourse).get(0));
        UUID paused = insertCourse(f.anaId(), "En pausa", 1, false, List.of());
        UUID failed = insertCourse(f.anaId(), "Fallado", 1, false, List.of());
        UUID queued = insertCourse(f.anaId(), "En cola", 1, false, List.of());
        UUID generating = insertCourse(f.anaId(), "Generando", 1, false, List.of());
        setStatus(paused, "PAUSED");
        setStatus(failed, "FAILED");
        setStatus(queued, "PENDING_GENERATION");
        setStatus(generating, "GENERATING");

        UUID lesson = firstPiece(anaCourse, "LESSON");
        owner.update("UPDATE course_piece SET status = 'FAILED' WHERE id = ?", lesson);
        hugoAsks(f, firstPiece(f.course(), "LESSON"), BLOCKED_QUESTION);

        for (String name : List.of("a.txt", "b.txt", "c.txt")) uploaded(f.ana(), name, longText(3).getBytes());
        List<UUID> docs = owner.queryForList("SELECT id FROM source_document ORDER BY id", UUID.class);
        owner.update("UPDATE source_document SET status = 'READY' WHERE id = ?", docs.get(0));
        owner.update("UPDATE source_document SET status = 'ERROR' WHERE id = ?", docs.get(1));

        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status) VALUES (gen_random_uuid(), ?, ?, ?, 'DONE')", anaCourse, f.anaId(), topicIds(anaCourse).get(0));
        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status) VALUES (gen_random_uuid(), ?, ?, ?, 'FAILED')", anaCourse, f.anaId(), topicIds(anaCourse).get(1));
        owner.update("INSERT INTO course_image (id, course_id, owner_id, topic_id, status) VALUES (gen_random_uuid(), ?, ?, ?, 'PENDING')", f.course(), f.hugoId(), topicIds(f.course()).get(0));

        redeemed(f.luis(), createCode(f.ana(), anaCourse));
        createCode(f.ana(), anaCourse);
        auditAt("COURSE_SHARE_REVOKED", f.anaId(), "1 hour");
        auditAt("COURSE_SHARE_REVOKED", f.anaId(), "40 days");
        auditAt("COURSE_DELETED", f.anaId(), "1 day");
        auditAt("COURSE_DELETED", f.anaId(), "2 days");
        auditAt("COURSE_DELETED", f.anaId(), "40 days");
        return new Setup(f, anaCourse, paused, failed, queued, generating);
    }

    @Test
    void ac3_coursesByStatusAndCreatedPerDay() throws Exception {
        Setup s = seeded();

        String body = ownerGet(s.f().ana(), "/api/owner/usage?range=30").andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.byStatus.READY").value(3)) // el de Hugo, el de Ana y la copia que canjea Luis
                .andExpect(jsonPath("$.courses.byStatus.PAUSED").value(1))
                .andExpect(jsonPath("$.courses.byStatus.FAILED").value(1))
                .andExpect(jsonPath("$.courses.byStatus.PENDING_GENERATION").value(1))
                .andExpect(jsonPath("$.courses.byStatus.GENERATING").value(1))
                .andExpect(jsonPath("$.courses.createdDaily", hasSize(30))).andReturn().getResponse().getContentAsString();
        int total = 0;
        for (JsonNode entry : json.readTree(body).get("courses").get("createdDaily")) total += entry.get("count").asInt();
        assertThat(total).isEqualTo(7);
    }

    @Test
    void ac3_piecesByStatusMatchTheDatabase() throws Exception {
        Setup s = seeded();
        int done = count("SELECT count(*) FROM course_piece WHERE status = 'DONE'");
        int failed = count("SELECT count(*) FROM course_piece WHERE status = 'FAILED'");
        int pending = count("SELECT count(*) FROM course_piece WHERE status = 'PENDING'");

        ownerGet(s.f().ana(), "/api/owner/usage").andExpect(jsonPath("$.pieces.done").value(done)).andExpect(jsonPath("$.pieces.failed").value(failed)).andExpect(jsonPath("$.pieces.pending").value(pending));
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void ac3_studyAndTutorActivity() throws Exception {
        Setup s = seeded();
        int attempts = count("SELECT count(*) FROM exercise_attempt");
        int questions = count("SELECT count(*) FROM tutor_message WHERE role = 'USER'");
        int blocked = count("SELECT count(*) FROM tutor_message WHERE status = 'BLOCKED'");

        ownerGet(s.f().ana(), "/api/owner/usage").andExpect(jsonPath("$.study.lessonsDone").value(1)).andExpect(jsonPath("$.study.exerciseAttempts").value(attempts))
                .andExpect(jsonPath("$.study.examAttempts").value(1)).andExpect(jsonPath("$.study.examsPassed").value(1))
                .andExpect(jsonPath("$.tutor.questions").value(questions)).andExpect(jsonPath("$.tutor.blocked").value(blocked)).andExpect(jsonPath("$.tutor.filtered").value(0));
        assertThat(blocked).isPositive();
    }

    @Test
    void ac3_documentsAndIllustrationsByStatus() throws Exception {
        Setup s = seeded();

        ownerGet(s.f().ana(), "/api/owner/usage").andExpect(jsonPath("$.documents.byStatus.READY").value(1)).andExpect(jsonPath("$.documents.byStatus.ERROR").value(1))
                .andExpect(jsonPath("$.documents.byStatus.UPLOADED").value(1)).andExpect(jsonPath("$.documents.byStatus.PROCESSING").value(0))
                .andExpect(jsonPath("$.illustrations.byStatus.DONE").value(1)).andExpect(jsonPath("$.illustrations.byStatus.FAILED").value(1))
                .andExpect(jsonPath("$.illustrations.byStatus.PENDING").value(1)).andExpect(jsonPath("$.illustrations.byStatus.BLOCKED").value(0))
                .andExpect(jsonPath("$.illustrations.byStatus.REMOVED").value(0));
    }

    @Test
    void ac3_sharingAndDeletedCoursesCountEventsInTheRange() throws Exception {
        Setup s = seeded();

        ownerGet(s.f().ana(), "/api/owner/usage?range=30").andExpect(jsonPath("$.sharing.created").value(2)).andExpect(jsonPath("$.sharing.redeemed").value(1))
                .andExpect(jsonPath("$.sharing.revoked").value(1)).andExpect(jsonPath("$.coursesDeleted").value(2));
        ownerGet(s.f().ana(), "/api/owner/usage?range=90").andExpect(jsonPath("$.sharing.revoked").value(2)).andExpect(jsonPath("$.coursesDeleted").value(3));
    }

    @Test
    void ac3_anEmptyInstallationShowsZeros() throws Exception {
        String ana = logged("ana@example.com");

        ownerGet(ana, "/api/owner/usage").andExpect(status().isOk()).andExpect(jsonPath("$.courses.byStatus.READY").value(0)).andExpect(jsonPath("$.pieces.done").value(0))
                .andExpect(jsonPath("$.study.lessonsDone").value(0)).andExpect(jsonPath("$.tutor.questions").value(0)).andExpect(jsonPath("$.sharing.created").value(0))
                .andExpect(jsonPath("$.coursesDeleted").value(0)).andExpect(jsonPath("$.courses.createdDaily", hasSize(30)));
    }

    @Test
    void ac15_readingTheUsageReportChangesNoRowOfTheUsedTables() throws Exception {
        Setup s = seeded();
        int before = writableRows() + rows("source_document") + rows("course_image") + rows("share_code") + rows("audit_event") - count("SELECT count(*) FROM audit_event WHERE action = 'ACCESS_DENIED'");

        ownerGet(s.f().ana(), "/api/owner/usage").andExpect(status().isOk());
        ownerGet(s.f().ana(), "/api/owner/health").andExpect(status().isOk());

        int after = writableRows() + rows("source_document") + rows("course_image") + rows("share_code") + rows("audit_event") - count("SELECT count(*) FROM audit_event WHERE action = 'ACCESS_DENIED'");
        assertThat(after).isEqualTo(before);
    }
}
