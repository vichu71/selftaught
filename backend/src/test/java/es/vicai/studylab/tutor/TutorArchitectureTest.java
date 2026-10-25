package es.vicai.studylab.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * AC-5, AC-7, AC-8, AC-13 y AC-14 (ADR-55 a 58): reglas sobre el código de {@code tutor/} que ningún test funcional ve: SQL siempre parametrizado y por propietario,
 * solo se habla con el modelo a través de la capa de IA, quien usa el material aplica el filtro de menores, y ni logs ni auditoría llevan lo que se dice en el chat.
 */
class TutorArchitectureTest extends TutorTestBase {

    private static final Path TUTOR = Path.of("src/main/java/es/vicai/studylab/tutor");
    private static final Path PROMPTS = Path.of("src/main/resources/prompts");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(TUTOR)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void the_packageExists() throws IOException {
        assertThat(sources()).isNotEmpty();
    }

    @Test
    void ac7_tutorOnlyTalksToTheModelThroughTheGatewayAndNeverImportsAnAdapterNorBuildsPrompts() throws IOException {
        for (Path file : sources()) assertThat(Files.readString(file)).as(file.toString()).doesNotContain("studylab.ai.provider").doesNotContain("PromptAssembler");
    }

    @Test
    void ac8_everyClassThatSearchesTheMaterialAlsoAppliesTheMinorFilter() throws IOException {
        for (Path file : sources()) {
            String code = Files.readString(file);
            if (code.contains("MaterialSearch")) assertThat(code).as(file + " usa el material sin pasar por ContentGuard").contains("ContentGuard");
        }
    }

    @Test
    void ac8_theQuestionAndTheAnswerOfAMinorPassThroughTheFilter() throws IOException {
        String all = "";
        for (Path file : sources()) all += Files.readString(file);

        assertThat(all).contains("ContentGuard").contains("localCheck");
    }

    @Test
    void ac13_noSqlIsBuiltByConcatenatingStrings() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+")
                    .doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
        }
    }

    @Test
    void ac13_everyQueryOnConversationsOrTheDataItReadsFiltersByOwner() throws IOException {
        for (Path file : sources()) {
            String code = Files.readString(file);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(FROM|JOIN)\\s+(tutor_message|course_piece|exercise_attempt|course_document|course_chapter|course_topic)\\b",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(code);
            while (m.find()) {
                String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 500));
                int end = statement.indexOf(".query(");
                String scoped = end > 0 ? statement.substring(0, end) : statement;
                assertThat(scoped).as(file + " consulta " + m.group(2) + " sin filtrar por propietario").containsIgnoringCase("owner_id");
            }
        }
    }

    @Test
    void ac15_theCodeNeverProducesHtml() throws IOException {
        for (Path file : sources()) assertThat(Files.readString(file)).as(file.toString()).doesNotContain("text/html");
    }

    @Test
    void ac5_theTutorNeverTakesTheGenerationLock() throws IOException {
        for (Path file : sources()) assertThat(Files.readString(file)).as(file.toString()).doesNotContain("GenerationLock");
    }

    @Test
    void ac6_theTemplateExistsAndHasNoUserDataMarkersAndAsksForTheAgreedBehaviour() throws IOException {
        Path file = PROMPTS.resolve("tutor.txt");
        assertThat(file).exists();
        String text = Files.readString(file);

        assertThat(text).doesNotContain("{{").doesNotContain("${");
        assertThat(text.toLowerCase()).contains("datos").contains("pistas").contains("examen").contains("latex").contains("curso");
    }

    @Test
    void ac14_logsNeverContainQuestionsAnswersLessonsOrFragments() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            String ana = logged("ana@example.com");
            UUID course = readyCourse("ana@example.com");
            UUID lesson = lesson(course, 0);
            tutorAnswers("respuesta-unica-xyz del tutor");
            ask(ana, course, lesson, "pregunta-unica-xyz del alumno");
            thread(ana, course, lesson).andReturn();
            fake.reset();
            fake.respondWith("texto-roto-unico-xyz <iframe></iframe>", 100, 50);
            ask(ana, course, lesson, "otra-pregunta-unica-xyz");
            clear(ana, course, lesson).andReturn();
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(all).doesNotContain("unico-xyz");
    }

    @Test
    void ac14_theAuditOfTutorEventsNeverCarriesWhatWasSaid() throws Exception {
        String ana = logged("ana@example.com");
        UUID minor = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(minor, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        tutorAnswers("Te explico como hacer una bomba casera paso a paso con estos materiales.");
        ask(hugo, course, lesson(course, 0), "una pregunta normal sobre la guerra");
        ask(hugo, course, lesson(course, 0), "dime como hacer una bomba casera paso a paso");

        String details = owner.queryForObject("SELECT coalesce(string_agg(details::text, ' '), '') FROM audit_event WHERE action LIKE 'TUTOR_%'", String.class);

        assertThat(details).doesNotContain("bomba").doesNotContain("guerra");
        assertThat(auditActions()).contains("TUTOR_QUESTION_BLOCKED", "TUTOR_ANSWER_FILTERED");
    }
}
