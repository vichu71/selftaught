package es.vicai.studylab.generation;

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
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * AC-5, AC-6, AC-7, AC-13, AC-15 y AC-16 (ADR-42 a 47): reglas sobre el código de {@code generation/} que ningún test funcional ve:
 * quien usa el material usa el filtro de menores, nada se construye por concatenación, y ni logs ni plantillas dejan escapar contenido.
 */
class GenerationArchitectureTest extends GenerationTestBase {

    private static final Path GENERATION = Path.of("src/main/java/es/vicai/studylab/generation");
    private static final Path PROMPTS = Path.of("src/main/resources/prompts");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(GENERATION)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void the_packageExists() throws IOException {
        assertThat(sources()).isNotEmpty();
    }

    @Test
    void ac7_everyClassThatSearchesTheMaterialAlsoAppliesTheMinorFilter() throws IOException {
        for (Path file : sources()) {
            String code = Files.readString(file);
            if (code.contains("MaterialSearch")) {
                assertThat(code).as(file + " usa el material sin pasar por ContentGuard").contains("ContentGuard");
            }
        }
    }

    @Test
    void ac7_theCodeThatSavesGeneratedContentAppliesTheMinorFilterToo() throws IOException {
        String all = "";
        for (Path file : sources()) all += Files.readString(file);

        assertThat(all).contains("localCheck");
    }

    @Test
    void ac6_generationOnlyTalksToTheModelThroughTheGatewayAndNeverImportsAnAdapter() throws IOException {
        for (Path file : sources()) assertThat(Files.readString(file)).as(file.toString()).doesNotContain("studylab.ai.provider").doesNotContain("PromptAssembler");
    }

    @Test
    void ac15_noSqlIsBuiltByConcatenatingStrings() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+")
                    .doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
        }
    }

    @Test
    void ac15_everyQueryOnTheCourseContentFiltersByOwner() throws IOException {
        for (Path file : sources()) {
            String code = Files.readString(file);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(FROM|JOIN)\\s+(course_topic|course_chapter|course_piece)\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(code);
            while (m.find()) {
                String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 500));
                int end = statement.indexOf(".query(");
                String scoped = end > 0 ? statement.substring(0, end) : statement;
                assertThat(scoped).as(file + " consulta " + m.group(2) + " sin filtrar por propietario").containsIgnoringCase("owner_id");
            }
        }
    }

    @Test
    void ac13_theCodeNeverProducesHtml() throws IOException {
        for (Path file : sources()) assertThat(Files.readString(file)).as(file.toString()).doesNotContain("text/html");
    }

    @Test
    void ac16_theTemplatesExistAskForNoLatexAndHaveNoUserDataMarkers() throws IOException {
        for (String id : new String[] {"generation-outline", "generation-lesson", "generation-exercises", "generation-exam"}) {
            Path file = PROMPTS.resolve(id + ".txt");
            assertThat(file).as(id).exists();
            String text = Files.readString(file);
            assertThat(text).as(id).doesNotContain("{{").doesNotContain("${");
            assertThat(text.toLowerCase()).as(id).contains("datos");
        }
        assertThat(Files.readString(PROMPTS.resolve("generation-lesson.txt")).toLowerCase()).contains("latex").contains("complemento");
        assertThat(Files.readString(PROMPTS.resolve("generation-exam.txt")).toLowerCase()).contains("latex");
    }

    @Test
    void ac15_logsNeverContainTitlesLessonsDocumentNamesOrModelAnswers() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            String ana = logged("ana@example.com");
            String doc = uploadedAndProcessed(ana, "diario-unico-xyz.txt", MaterialFixtures.text("contenido-unico-xyz del documento. ".repeat(40)));
            UUID course = insertCourse(idOf("ana@example.com"), "tema-unico-xyz", 2, true, List.of(doc));
            modelDoes(call -> switch (piece(call)) {
                case "temario" -> outlineJson(2, 2, true).replace("Tema 1 de prueba", "titulo-unico-xyz");
                case "leccion" -> lessonMd("leccion-unica-xyz", 1);
                case "ejercicios" -> "respuesta-rota-unica-xyz";
                default -> examJson();
            });
            generator.processPending();
            detail(ana, course).andReturn();
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(all).doesNotContain("unico-xyz");
    }
}
