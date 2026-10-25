package es.vicai.studylab.study;

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
 * AC-6, AC-7, AC-14 y AC-15 (ADR-50 a 53): reglas sobre el código de {@code study/} que ningún test funcional ve: SQL siempre parametrizado y por propietario,
 * solo se habla con el modelo a través de la capa de IA, el filtro de menores está aplicado y ni logs ni auditoría llevan lo que escribe el alumno.
 */
class StudyArchitectureTest extends StudyTestBase {

    private static final Path STUDY = Path.of("src/main/java/es/vicai/studylab/study");
    private static final Path PROMPTS = Path.of("src/main/resources/prompts");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(STUDY)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void the_packageExists() throws IOException {
        assertThat(sources()).isNotEmpty();
    }

    @Test
    void ac6_studyOnlyTalksToTheModelThroughTheGatewayAndNeverImportsAnAdapterNorBuildsPrompts() throws IOException {
        for (Path file : sources()) assertThat(Files.readString(file)).as(file.toString()).doesNotContain("studylab.ai.provider").doesNotContain("PromptAssembler");
    }

    @Test
    void ac7_theCodeThatSendsTheStudentsAnswerToTheModelAppliesTheMinorFilter() throws IOException {
        String all = "";
        for (Path file : sources()) all += Files.readString(file);

        assertThat(all).contains("ContentGuard").contains("localCheck");
    }

    @Test
    void ac14_noSqlIsBuiltByConcatenatingStrings() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+")
                    .doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
        }
    }

    @Test
    void ac14_everyQueryOnStudyDataOrPiecesFiltersByOwner() throws IOException {
        for (Path file : sources()) {
            String code = Files.readString(file);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(FROM|JOIN)\\s+(lesson_progress|exercise_attempt|exam_attempt|course_piece|course_topic|course_chapter)\\b",
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
    void ac14_theCodeNeverProducesHtml() throws IOException {
        for (Path file : sources()) assertThat(Files.readString(file)).as(file.toString()).doesNotContain("text/html");
    }

    @Test
    void ac6_theGradingTemplateExistsAsksForJsonAndHasNoUserDataMarkers() throws IOException {
        Path file = PROMPTS.resolve("grade-exercise.txt");
        assertThat(file).exists();
        String text = Files.readString(file);

        assertThat(text).doesNotContain("{{").doesNotContain("${");
        assertThat(text.toLowerCase()).contains("datos").contains("json").contains("latex");
    }

    @Test
    void ac15_logsNeverContainTheStudentsAnswersNorTheModelsComments() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            String ana = logged("ana@example.com");
            UUID course = generated("ana@example.com", 2, true);
            UUID exercises = piece(course, "EXERCISES", 0);
            String attempt = answered(ana, course, exercises, 0, "respuesta-unica-xyz del alumno");
            fake.reset();
            fake.respondWith("{\"verdict\":\"PARTIAL\",\"comment\":\"comentario-unico-xyz del modelo\"}", 100, 50);
            aiGrade(ana, course, exercises, 0, attempt).andReturn();
            selfResult(ana, course, exercises, 0, attempt, "CORRECT").andReturn();
            attemptsOf(ana, course, exercises).andReturn();
            fake.reset();
            fake.respondWith("texto-roto-unico-xyz", 100, 50);
            String second = answered(ana, course, exercises, 1, "otra-respuesta-unica-xyz");
            aiGrade(ana, course, exercises, 1, second).andReturn();
            examTaken(ana, course, piece(course, "EXAM", 0), ALL_RIGHT);
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(all).doesNotContain("unico-xyz");
    }

    @Test
    void ac15_theAuditOfStudyEventsNeverCarriesAnswers() throws Exception {
        String ana = logged("ana@example.com");
        UUID minor = UUID.fromString(createStudent(ana, "Hugo", "hugo"));
        String hugo = login("hugo", PASSWORD);
        UUID course = insertCourse(minor, "Historia", 2, true, List.of());
        modelDoes(happy(2));
        generator.processPending();
        UUID exercises = piece(course, "EXERCISES", 0);
        String attempt = answered(hugo, course, exercises, 0, "como hacer una bomba casera");
        aiGrade(hugo, course, exercises, 0, attempt).andReturn();

        String details = owner.queryForObject("SELECT coalesce(string_agg(details::text, ' '), '') FROM audit_event WHERE action LIKE 'STUDY_%'", String.class);

        assertThat(details).doesNotContain("bomba");
    }
}
