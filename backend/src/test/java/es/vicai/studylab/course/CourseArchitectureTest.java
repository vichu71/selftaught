package es.vicai.studylab.course;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import es.vicai.studylab.material.MaterialFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * AC-11, AC-13, AC-15 (ADR-37 a 41): reglas sobre el código de {@code course/} y {@code safety/} que ningún test funcional ve:
 * el texto de los documentos nunca llega al modelo desde aquí, ningún SQL se construye por concatenación y los logs no llevan temas ni nombres.
 */
class CourseArchitectureTest extends CourseTestBase {

    private static final Path COURSE = Path.of("src/main/java/es/vicai/studylab/course");
    private static final Path SAFETY = Path.of("src/main/java/es/vicai/studylab/safety");

    private static List<Path> sources(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void the_packagesExist() throws IOException {
        assertThat(sources(COURSE)).isNotEmpty();
        assertThat(sources(SAFETY)).isNotEmpty();
    }

    @Test
    void ac11_courseNeverReadsDocumentTextNorSearchesTheMaterial() throws IOException {
        for (Path file : sources(COURSE)) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContain("source_chunk").doesNotContain("MaterialSearch").doesNotContain("Fragment")
                    .doesNotContain("content_sha256").doesNotContain("source_document.content");
        }
    }

    @Test
    void ac11_courseOnlyTalksToTheModelThroughTheGatewayAndNeverBuildsPromptsByHand() throws IOException {
        for (Path file : sources(COURSE)) {
            String code = Files.readString(file);
            assertThat(code).as(file.toString()).doesNotContain("studylab.ai.provider").doesNotContain("PromptAssembler");
        }
    }

    @Test
    void ac13_noSqlIsBuiltByConcatenatingStrings() throws IOException {
        for (Path file : sources(COURSE)) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+")
                    .doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
        }
    }

    @Test
    void ac13_everyQueryOnCoursesOrIntakesFiltersByOwnerExceptTheOneThatCreatesThem() throws IOException {
        for (Path file : sources(COURSE)) {
            String code = Files.readString(file);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(FROM|JOIN)\\s+(course|course_intake|course_document)\\b",
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
    void ac16_theCodeNeverGeneratesHtml() throws IOException {
        for (Path file : sources(COURSE)) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("text/html");
        }
    }

    @Test
    void ac15_logsNeverContainTopicsDocumentNamesOrModelAnswers() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            String ana = logged("ana@example.com");
            createStudent(ana, "Hugo", "hugo");
            String hugo = login("hugo", PASSWORD);
            String doc = uploadedAndProcessed(ana, "diario-unico-xyz.txt", MaterialFixtures.text("contenido-unico-xyz de un documento ".repeat(30)));
            model("{\"appropriate\":true,\"category\":\"NONE\",\"questions\":[{\"field\":\"focus\",\"text\":\"pregunta-unica-xyz\","
                    + "\"options\":[{\"label\":\"a\",\"value\":\"a\",\"default\":true},{\"label\":\"b\",\"value\":\"b\"}]}]}");
            String body = started(ana, "tema-unico-xyz", List.of(doc));
            create(ana, intakeId(body), defaultAnswers(body)).andReturn();
            intake(hugo, "pornografía-unica-xyz").andReturn(); // bloqueado por la lista local
            model("respuesta-rota-unica-xyz que no es json");
            intake(ana, "otro-tema-unico-xyz").andReturn(); // salida inválida: degradación
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(all).doesNotContain("unico-xyz");
    }
}
