package es.vicai.studylab.ai;

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
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** AC-1 (nada llama a un proveedor fuera de la capa), AC-2 (modelos solo en configuración) y AC-6 (sin contenido en logs). */
class AiArchitectureTest extends AiTestBase {

    private static final Path MAIN = Path.of("src/main/java");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean inside(Path file, String folder) {
        return file.toString().replace('\\', '/').contains("/studylab/" + folder + "/");
    }

    @Test
    void ac1_noCodeOutsideTheAiLayerTouchesAProviderAdapter() throws IOException {
        for (Path file : javaFiles()) {
            if (inside(file, "ai")) continue;
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("studylab.ai.provider");
        }
    }

    @Test
    void ac1_providerHostsAppearOnlyInsideTheAdapters() throws IOException {
        for (Path file : javaFiles()) {
            if (inside(file, "ai/provider")) continue;
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContain("api.anthropic.com").doesNotContain("api.openai.com").doesNotContain("generativelanguage.googleapis.com");
        }
    }

    @Test
    void ac2_noModelNameIsWrittenInTheCode() throws IOException {
        for (Path file : javaFiles()) {
            assertThat(Files.readString(file)).as(file.toString())
                    // Un modelo lleva versión tras el nombre ("claude-…", "gpt-…", "gemini-…", "o3-…"); el nombre del proveedor solo no cuenta
                    .doesNotContainPattern("\"((claude|gpt|gemini|nomic)-[-a-z0-9.]*|o[1-9](-[-a-z0-9.]*)?)\"");
        }
    }

    @Test
    void ac6_neitherPromptsNorAnswersNorProviderMessagesReachTheLogs() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level before = root.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        try {
            UUID user = newUser();
            fake.failNextWithMessage(ProviderErrorKind.SERVER_ERROR, 1, "MENSAJE-DEL-PROVEEDOR-XYZ");
            fake.respondWith("RESPUESTA-ULTRA-SECRETA", 10, 10);
            gateway.complete(AiRequest.of(AiTask.OUTLINE, user, "test").data("tema", "TEXTO-ULTRA-SECRETO"));
        } finally {
            root.detachAppender(appender);
            root.setLevel(before);
        }

        String everything = String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertThat(everything).doesNotContain("TEXTO-ULTRA-SECRETO").doesNotContain("RESPUESTA-ULTRA-SECRETA")
                .doesNotContain("MENSAJE-DEL-PROVEEDOR-XYZ");
    }
}
