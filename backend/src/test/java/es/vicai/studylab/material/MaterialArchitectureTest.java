package es.vicai.studylab.material;

import static es.vicai.studylab.material.MaterialFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * AC-4, AC-12, AC-13 (ADR-31, 32, 33, 34): reglas sobre el código de {@code material/} que ningún test funcional ve:
 * el aislamiento por propietario no depende de que alguien se acuerde, nada se escribe en disco, el texto nunca va a un prompt
 * desde aquí y nunca se loguea contenido.
 */
class MaterialArchitectureTest extends MaterialTestBase {

    private static final Path MATERIAL = Path.of("src/main/java/es/vicai/studylab/material");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MATERIAL)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void the_materialPackageExists() throws IOException {
        assertThat(sources()).isNotEmpty();
    }

    @Test
    void ac12_everyQueryOnChunksFiltersByOwnerInTheSameStatement() throws IOException {
        Pattern fromChunks = Pattern.compile("(FROM|JOIN)\\s+source_chunk", Pattern.CASE_INSENSITIVE);
        for (Path file : sources()) {
            String code = Files.readString(file);
            Matcher m = fromChunks.matcher(code);
            while (m.find()) {
                String statement = code.substring(m.start(), Math.min(code.length(), m.start() + 700));
                int end = statement.indexOf(".query(");
                if (end < 0) end = statement.indexOf(".update(");
                String scoped = end > 0 ? statement.substring(0, end) : statement;
                assertThat(scoped).as(file + " consulta source_chunk sin filtrar por propietario").containsIgnoringCase("owner_id");
            }
        }
    }

    @Test
    void ac12_noSqlIsBuiltByConcatenatingStrings() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+")
                    .doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
        }
    }

    @Test
    void ac4_nothingIsWrittenToDiskAndNoUploadedNameIsUsedAsAPath() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContain("java.io.File").doesNotContain("java.nio.file")
                    .doesNotContain("FileOutputStream").doesNotContain("transferTo(");
        }
    }

    @Test
    void ac13_materialNeverBuildsPromptsOrCallsTheModel() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContain("PromptAssembler").doesNotContain("AiRequest").doesNotContain("gateway.complete");
        }
    }

    @Test
    void ac13_noHtmlIsGeneratedFromDocumentText() throws IOException {
        for (Path file : sources()) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("text/html");
        }
    }

    @Test
    void ac14_logsNeverContainDocumentContentNamesOrProviderMessages() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.ALL);
        try {
            String ana = logged("ana@example.com");
            uploadedAndProcessed(ana, "nombre-unico-xyz.txt", text("contenido-unico-xyz de un documento normal"));
            uploadedAndProcessed(ana, "roto-unico-xyz.pdf", text("%PDF-1.7 contenido-roto-xyz"));
            fake.failNextWithMessage(ProviderErrorKind.UNAVAILABLE, 99, "mensaje-del-proveedor-xyz");
            uploadedAndProcessed(ana, "caido-unico-xyz.txt", text("contenido-caido-xyz otro documento"));
            search.search(idOf("ana@example.com"), "consulta-unica-xyz", 3, null);
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(all).doesNotContain("unico-xyz").doesNotContain("roto-xyz").doesNotContain("caido-xyz")
                .doesNotContain("mensaje-del-proveedor-xyz").doesNotContain("consulta-unica");
    }
}
