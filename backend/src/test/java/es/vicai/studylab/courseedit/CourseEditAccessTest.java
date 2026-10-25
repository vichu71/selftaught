package es.vicai.studylab.courseedit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** AC-13, AC-14 y AC-15 para la edición: quién puede editar, lo que escribe un menor y que ni logs ni auditoría lleven los textos. */
class CourseEditAccessTest extends CourseEditTestBase {

    /** Un camino de cada tipo de edición, con un cuerpo válido. */
    private record Call(String method, String path, Object body) {}

    private List<Call> calls(UUID course, UUID topic, UUID chapter, UUID lesson) {
        return List.of(new Call("PUT", coursePath(course, "/topics/order"), Map.of("ids", topicIds(course))), new Call("PUT", coursePath(course, "/topics/" + topic), Map.of("title", "x", "summary", "y")),
                new Call("DELETE", coursePath(course, "/topics/" + topic), null), new Call("POST", coursePath(course, "/topics"), Map.of("title", "x", "summary", "y", "chapter", Map.of("title", "c", "objective", "o"))),
                new Call("PUT", coursePath(course, "/topics/" + topic + "/chapters/order"), Map.of("ids", chapterIds(topic))), new Call("POST", coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "x", "objective", "y")),
                new Call("PUT", coursePath(course, "/chapters/" + chapter), Map.of("title", "x", "objective", "y")), new Call("DELETE", coursePath(course, "/chapters/" + chapter), null),
                new Call("PUT", coursePath(course, "/pieces/" + lesson + "/content"), Map.of("markdown", "texto")), new Call("POST", coursePath(course, "/pieces/" + lesson + "/regenerate"), null));
    }

    private org.springframework.test.web.servlet.ResultActions perform(String session, Call call, boolean csrf) throws Exception {
        MockHttpServletRequestBuilder req = switch (call.method()) {
            case "PUT" -> put(call.path());
            case "POST" -> post(call.path());
            default -> delete(call.path());
        };
        if (csrf) req.with(csrf());
        if (session != null) req.cookie(new jakarta.servlet.http.Cookie(SESSION_COOKIE, session));
        if (call.body() != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(call.body()));
        return mvc.perform(req);
    }

    // ---------- AC-14 ----------

    @Test
    void ac14_everyEditRequiresASessionAndTheCsrfToken() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);

        for (Call call : calls(course, topic, chapter, lessonOf(chapter))) {
            perform(null, call, true).andExpect(status().isUnauthorized());
            perform(ana, call, false).andExpect(status().isForbidden());
        }

        assertThat(version(course)).isEqualTo(1);
        assertThat(pieceCount(course)).isEqualTo(10);
    }

    @Test
    void ac14_anotherUserGetsA404OnEveryEditAndNothingChanges() throws Exception {
        logged("ana@example.com");
        String luis = logged("luis@example.com");
        UUID course = ready("ana@example.com");
        UUID topic = topicIds(course).get(0);
        UUID chapter = chapterIds(topic).get(0);
        int rows = pieceCount(course) + count("SELECT count(*) FROM course_topic WHERE course_id = ?", course);

        for (Call call : calls(course, topic, chapter, lessonOf(chapter))) perform(luis, call, true).andExpect(status().isNotFound());
        String foreign = perform(luis, new Call("PUT", coursePath(course, "/topics/" + topic), Map.of("title", "x", "summary", "y")), true).andReturn().getResponse().getContentAsString();
        String missing = perform(luis, new Call("PUT", coursePath(UUID.randomUUID(), "/topics/" + UUID.randomUUID()), Map.of("title", "x", "summary", "y")), true).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
        assertThat(version(course)).isEqualTo(1);
        assertThat(pieceCount(course) + count("SELECT count(*) FROM course_topic WHERE course_id = ?", course)).isEqualTo(rows);
    }

    @Test
    void ac14_aTopicOrChapterOfAnotherCourseOfTheSameUserIsNotFound() throws Exception {
        String ana = logged("ana@example.com");
        UUID first = ready("ana@example.com");
        UUID second = ready("ana@example.com");
        UUID topicOfSecond = topicIds(second).get(0);
        UUID chapterOfSecond = chapterIds(topicOfSecond).get(0);

        putAs(ana, coursePath(first, "/topics/" + topicOfSecond), Map.of("title", "x", "summary", "y")).andExpect(status().isNotFound());
        putAs(ana, coursePath(first, "/chapters/" + chapterOfSecond), Map.of("title", "x", "objective", "y")).andExpect(status().isNotFound());
        postAs(ana, coursePath(first, "/topics/" + topicOfSecond + "/chapters"), Map.of("title", "x", "objective", "y")).andExpect(status().isNotFound());
        putAs(ana, coursePath(first, "/pieces/" + lessonOf(chapterOfSecond) + "/content"), Map.of("markdown", "texto")).andExpect(status().isNotFound());
        putAs(ana, coursePath(first, "/topics/" + topicIds(first).get(0) + "/chapters/order"), Map.of("ids", chapterIds(topicOfSecond))).andExpect(status().isBadRequest());
    }

    @Test
    void ac14_invalidIdentifiersAreNotFoundNeverAnError() throws Exception {
        String ana = logged("ana@example.com");
        UUID course = ready("ana@example.com");

        putAs(ana, "/api/courses/no-es-un-uuid/topics/otro", Map.of("title", "x", "summary", "y")).andExpect(status().isNotFound());
        putAs(ana, coursePath(course, "/topics/no-es-un-uuid"), Map.of("title", "x", "summary", "y")).andExpect(status().isNotFound());
        deleteAs(ana, coursePath(course, "/chapters/no-es-un-uuid")).andExpect(status().isNotFound());
    }

    @Test
    void ac14_theAdultTutorCannotEditTheCoursesOfTheirMinor() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        UUID course = insertCourse(hugoId, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        UUID topic = topicIds(course).get(0);

        for (Call call : calls(course, topic, chapterIds(topic).get(0), lessonOf(chapterIds(topic).get(0)))) perform(ana, call, true).andExpect(status().isNotFound());

        assertThat(version(course)).isEqualTo(1);
    }

    // ---------- AC-13: lo que escribe un menor ----------

    private record Family(String ana, String hugo, UUID hugoId, UUID course) {}

    private Family family() throws Exception {
        String ana = logged("ana@example.com");
        UUID hugoId = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(hugoId, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        return new Family(ana, hugo, hugoId, course);
    }

    @Test
    void ac13_whatAMinorWritesWhileEditingPassesThroughTheFilterBeforeItIsSaved() throws Exception {
        Family f = family();
        UUID topic = topicIds(f.course()).get(0);
        UUID chapter = chapterIds(topic).get(0);

        putAs(f.hugo(), coursePath(f.course(), "/topics/" + topic), Map.of("title", BLOCKED_TEXT, "summary", "ok")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Contenido inapropiado. Consulta con tu adulto.")).andExpect(jsonPath("$.code").value("EDIT_NOT_ALLOWED"));
        putAs(f.hugo(), coursePath(f.course(), "/topics/" + topic), Map.of("title", "ok", "summary", BLOCKED_TEXT)).andExpect(status().isUnprocessableEntity());
        putAs(f.hugo(), coursePath(f.course(), "/chapters/" + chapter), Map.of("title", "ok", "objective", BLOCKED_TEXT)).andExpect(status().isUnprocessableEntity());
        putAs(f.hugo(), coursePath(f.course(), "/pieces/" + lessonOf(chapter) + "/content"), Map.of("markdown", "# Título\n\n" + BLOCKED_TEXT)).andExpect(status().isUnprocessableEntity());
        postAs(f.hugo(), coursePath(f.course(), "/topics/" + topic + "/chapters"), Map.of("title", BLOCKED_TEXT, "objective", "ok")).andExpect(status().isUnprocessableEntity());
        postAs(f.hugo(), coursePath(f.course(), "/topics"), Map.of("title", "ok", "summary", "ok", "chapter", Map.of("title", "ok", "objective", BLOCKED_TEXT))).andExpect(status().isUnprocessableEntity());

        assertThat(version(f.course())).isEqualTo(1);
        assertThat(pieceCount(f.course())).isEqualTo(10);
        assertThat(owner.queryForObject("SELECT title FROM course_topic WHERE id = ?", String.class, topic)).isNotEqualTo(BLOCKED_TEXT);
        assertThat(statusOf(f.course())).isEqualTo("READY");
        assertThat(count("SELECT count(*) FROM course_action WHERE kind = 'AI_EDIT'")).isZero();
        assertThat(fake.calls().stream().map(c -> c.dataMessage()).filter(m -> m.contains("bomba")).count()).isZero();
    }

    @Test
    void ac13_aMinorCanEditWithNormalTextAndTheSameTextIsAcceptedFromAnAdult() throws Exception {
        Family f = family();
        UUID topic = topicIds(f.course()).get(0);

        putAs(f.hugo(), coursePath(f.course(), "/topics/" + topic), Map.of("title", "La Segunda Guerra Mundial", "summary", "Causas y consecuencias")).andExpect(status().isOk());

        String ana = logged("luis@example.com");
        UUID adultCourse = ready("luis@example.com");
        putAs(ana, coursePath(adultCourse, "/topics/" + topicIds(adultCourse).get(0)), Map.of("title", BLOCKED_TEXT, "summary", "ok")).andExpect(status().isOk());
    }

    @Test
    void ac13_theBlockedAttemptIsAuditedWithIdsAndNeverTheText() throws Exception {
        Family f = family();
        UUID topic = topicIds(f.course()).get(0);

        putAs(f.hugo(), coursePath(f.course(), "/topics/" + topic), Map.of("title", BLOCKED_TEXT, "summary", "ok")).andExpect(status().isUnprocessableEntity());

        assertThat(auditActions()).contains("COURSE_EDIT_BLOCKED");
        String details = owner.queryForObject("SELECT string_agg(details::text, ' ') FROM audit_event WHERE action = 'COURSE_EDIT_BLOCKED'", String.class);
        assertThat(details).doesNotContain("bomba").contains(f.course().toString());
    }

    @Test
    void ac13_theAdultTutorSeesTheBlockedEditsInThePanelAlerts() throws Exception {
        Family f = family();
        UUID topic = topicIds(f.course()).get(0);
        putAs(f.hugo(), coursePath(f.course(), "/topics/" + topic), Map.of("title", BLOCKED_TEXT, "summary", "ok")).andExpect(status().isUnprocessableEntity());

        getAs(f.ana(), "/api/tutor/students/" + f.hugoId() + "/alerts").andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("COURSE_EDIT_BLOCKED"))
                .andExpect(jsonPath("$[0].courseId").value(f.course().toString())).andExpect(jsonPath("$[0].courseTitle").value("Historia"));
    }

    // ---------- AC-15 ----------

    @Test
    void ac15_neitherLogsNorTheAuditCarryTheTextsTheUserEdits() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            String ana = logged("ana@example.com");
            UUID course = ready("ana@example.com");
            UUID topic = topicIds(course).get(0);
            UUID chapter = chapterIds(topic).get(0);
            putAs(ana, coursePath(course, "/topics/" + topic), Map.of("title", "título-" + SECRET, "summary", "resumen-" + SECRET)).andExpect(status().isOk());
            putAs(ana, coursePath(course, "/pieces/" + lessonOf(chapter) + "/content"), Map.of("markdown", "lección-" + SECRET)).andExpect(status().isOk());
            postAs(ana, coursePath(course, "/topics/" + topic + "/chapters"), Map.of("title", "nuevo-" + SECRET, "objective", "objetivo-" + SECRET)).andExpect(status().isCreated());
            modelDoes(happy(2));
            generator.processPending();
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain(SECRET);
        assertThat(owner.queryForObject("SELECT coalesce(string_agg(details::text, ' '), '') FROM audit_event", String.class)).doesNotContain(SECRET);
    }
}
