package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** F12 AC-16: ni los logs ni la auditoría ni el registro de uso llevan el prompt, el título del tema, la imagen ni mensajes del proveedor; la auditoría lleva ids. */
class IllustrationLogsTest extends IllustrationTestBase {

    private static final String SECRET_TITLE = "TITULO-SECRETO-DEL-USUARIO-ZZ";
    private static final String PROVIDER_MESSAGE = "MENSAJE-DEL-PROVEEDOR-QUE-REPITE-EL-PROMPT sk-no-debe-salir";

    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void capture() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        owner.update("DELETE FROM course_image");
        fake.reset();
    }

    @AfterEach
    void release() {
        root.detachAppender(appender);
    }

    private String logs() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : List.copyOf(appender.list)) {
            sb.append(e.getFormattedMessage()).append('\n');
            if (e.getThrowableProxy() != null) sb.append(e.getThrowableProxy().getMessage()).append('\n');
        }
        return sb.toString();
    }

    private String audit() {
        return String.valueOf(owner.queryForObject("SELECT coalesce(string_agg(action || ' ' || coalesce(details::text, ''), ' | '), '') FROM audit_event", String.class));
    }

    private UUID courseWithSecretTitle() throws Exception {
        UUID course = courseWithIllustrations("luis@example.com");
        owner.update("UPDATE course_topic SET title = ?, summary = ? WHERE course_id = ?", SECRET_TITLE, SECRET_TITLE + " resumen", course);
        return course;
    }

    @Test
    void ac16_aSuccessfulRunLeavesNoPromptTitleOrAltTextInLogsAuditOrUsage() throws Exception {
        UUID course = courseWithSecretTitle();
        drain();

        assertThat(logs()).doesNotContain(SECRET_TITLE).doesNotContain("sin texto");
        assertThat(audit()).doesNotContain(SECRET_TITLE);
        assertThat(usage().toString()).doesNotContain(SECRET_TITLE);
        assertThat(audit()).contains("COURSE_IMAGE_GENERATED").contains(course.toString());
    }

    @Test
    void ac16_aFailureNeverCopiesTheMessageOfTheProviderToLogsAuditOrTheRow() throws Exception {
        UUID course = courseWithSecretTitle();
        fake.imageFailMessage(PROVIDER_MESSAGE);
        fake.imageFailsTimes(100, ProviderErrorKind.SERVER_ERROR);

        drain();

        assertThat(logs()).doesNotContain("MENSAJE-DEL-PROVEEDOR").doesNotContain("sk-no-debe-salir").doesNotContain(SECRET_TITLE);
        assertThat(audit()).doesNotContain("MENSAJE-DEL-PROVEEDOR").doesNotContain(SECRET_TITLE).contains("COURSE_IMAGE_FAILED");
        assertThat(owner.queryForList("SELECT error_kind FROM course_image WHERE course_id = ?", String.class, course).toString()).doesNotContain("MENSAJE-DEL-PROVEEDOR");
    }

    @Test
    void ac16_aModerationRefusalIsAuditedWithIdsAndNeverTheSubject() throws Exception {
        courseWithSecretTitle();
        fake.imageFailMessage(PROVIDER_MESSAGE);
        fake.imageIsModerated();

        drain();

        assertThat(audit()).contains("COURSE_IMAGE_BLOCKED").doesNotContain(SECRET_TITLE).doesNotContain("MENSAJE-DEL-PROVEEDOR");
        assertThat(logs()).doesNotContain(SECRET_TITLE).doesNotContain("MENSAJE-DEL-PROVEEDOR");
    }

    @Test
    void ac16_removingIsAuditedWithIdsAndTheAuditNeverCarriesTheSecret() throws Exception {
        UUID course = courseWithSecretTitle();
        drain();
        String luis = session("luis@example.com");

        removeImage(luis, course, topicIds(course).get(0)).andExpect(status().isOk());

        assertThat(audit()).contains("COURSE_IMAGE_REMOVED").doesNotContain(SECRET_TITLE);
        Object details = owner.queryForObject("SELECT details::text FROM audit_event WHERE action = 'COURSE_IMAGE_REMOVED'", String.class);
        assertThat((String) details).contains(course.toString()).contains(topicIds(course).get(0).toString());
        assertThat(owner.queryForObject("SELECT actor_user_id FROM audit_event WHERE action = 'COURSE_IMAGE_REMOVED'", UUID.class)).isEqualTo(idOf("luis@example.com"));
    }

    @Test
    void ac16_theHttpErrorsOfTheEditNeverShowInternalDetails() throws Exception {
        UUID course = courseWithSecretTitle();
        drain();
        String luis = session("luis@example.com");
        setStatus(course, "GENERATING");

        String body = regenerate(luis, course, topicIds(course).get(0)).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("course_image").doesNotContain("SQL").doesNotContain("Exception").doesNotContain(SECRET_TITLE);
    }

    @Test
    void ac16_theKeyOfTheProviderTravelsInHeadersAndNeverInTheUrlOrInTheLogs() throws Exception {
        courseWithSecretTitle();
        drain();

        assertThat(logs()).doesNotContainIgnoringCase("api-key").doesNotContainIgnoringCase("?key=").doesNotContain("Bearer ");
    }
}
