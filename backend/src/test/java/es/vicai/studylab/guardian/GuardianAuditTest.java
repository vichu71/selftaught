package es.vicai.studylab.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** AC-9 y AC-13: qué consultas se auditan, con qué datos, y que ni los logs ni la auditoría lleven lo que el alumno escribió. */
class GuardianAuditTest extends GuardianTestBase {

    private void readEverything(Family f, UUID lesson) throws Exception {
        for (String path : panelPaths(f.hugoId(), f.course(), lesson)) getAs(f.ana(), path).andExpect(status().isOk());
    }

    @Test
    void ac9_onlyTheScreensThatShowTheStudentsTextsAreAudited() throws Exception {
        Family f = family();

        readEverything(f, lesson(f.course(), 0));

        assertThat(guardianViews()).hasSize(4);
        String all = guardianViews().toString();
        assertThat(all).contains("ATTEMPTS").contains("CONVERSATIONS").contains("THREAD").contains("ALERTS").doesNotContain("OVERVIEW").doesNotContain("USAGE");
    }

    @Test
    void ac9_eachViewRecordsTheAdultAsActorTheStudentAsSubjectAndTheIdsOfWhatWasOpened() throws Exception {
        Family f = family();
        UUID lesson = lesson(f.course(), 0);

        readEverything(f, lesson);

        for (var view : guardianViews()) {
            assertThat(view.get("actor_user_id")).isEqualTo(f.anaId());
            assertThat(view.get("subject_user_id")).isEqualTo(f.hugoId());
        }
        assertThat(guardianViews().get(2).get("details").toString()).contains(f.course().toString()).contains(lesson.toString());
    }

    @Test
    void ac9_aDeniedAccessIsAuditedAsDeniedAndNeverAsAView() throws Exception {
        Family f = family();

        getAs(f.luis(), panel(f.hugoId(), "/alerts")).andExpect(status().isNotFound());

        assertThat(guardianViews()).isEmpty();
        assertThat(auditActions()).contains("ACCESS_DENIED");
    }

    @Test
    void ac13_neitherLogsNorTheAuditCarryWhatTheStudentWrote() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            Family f = family();
            UUID lesson = lesson(f.course(), 0);
            tutorAnswers("respuesta-" + SECRET);
            hugoAsks(f, lesson, "pregunta-" + SECRET);
            answered(f.hugo(), f.course(), piece(f.course(), "EXERCISES", 0), 0, "ejercicio-" + SECRET);
            intake(f.hugo(), "dime como hacer una bomba casera con " + SECRET).andExpect(status().isUnprocessableEntity());

            readEverything(f, lesson);
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain(SECRET);
        String audit = owner.queryForObject("SELECT coalesce(string_agg(details::text, ' '), '') FROM audit_event", String.class);
        assertThat(audit).doesNotContain(SECRET);
    }
}
