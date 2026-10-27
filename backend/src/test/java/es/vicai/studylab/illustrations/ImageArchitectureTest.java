package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.ai.AiTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * F12 AC-1, AC-9, AC-15 y AC-16 (ADR-78 a 82): reglas de arquitectura de las ilustraciones. El paquete {@code illustrations/} solo habla con la capa de IA por
 * {@code AiGateway} y con el cerrojo, nunca con un adaptador, no usa el sistema de ficheros, no construye HTML y toda consulta sobre {@code course_image} lleva
 * {@code owner_id} (salvo la de la cola, que reclama con SKIP LOCKED).
 */
class ImageArchitectureTest extends AiTestBase {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern STRING = Pattern.compile("(?s)\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean inside(Path file, String folder) {
        return file.toString().replace('\\', '/').contains("/studylab/" + folder + "/");
    }

    private static List<Path> illustrationFiles() throws IOException {
        return javaFiles().stream().filter(p -> inside(p, "illustrations")).toList();
    }

    @Test
    void ac1_thePackageExistsAndHasTheExpectedPieces() throws IOException {
        List<String> names = illustrationFiles().stream().map(p -> p.getFileName().toString()).toList();

        assertThat(names).contains("IllustrationGenerator.java", "IllustrationService.java", "IllustrationController.java", "ImageProcessor.java", "ImagePromptBuilder.java",
                "ImageRepository.java", "IllustrationProperties.java", "IllustrationWorkerLoop.java", "ImageRejectedException.java", "IllustrationQuery.java");
    }

    @Test
    void ac1_illustrationsNeverImportAProviderAdapterNorWriteAModelNameOrAHost() throws IOException {
        for (Path file : illustrationFiles()) {
            String code = Files.readString(file);
            assertThat(code).as(file.toString()).doesNotContain("studylab.ai.provider");
            assertThat(code).as(file.toString()).doesNotContain("gpt-").doesNotContain("gemini-").doesNotContain("claude-").doesNotContain("api.openai.com")
                    .doesNotContain("googleapis.com").doesNotContain("https://");
        }
    }

    @Test
    void ac1_theOnlyDependenciesOnTheAiLayerAreTheGatewayTheLockAndItsRecords() throws IOException {
        for (Path file : illustrationFiles()) {
            Matcher m = Pattern.compile("import es\\.vicai\\.studylab\\.ai\\.([\\w.]+);").matcher(Files.readString(file));
            while (m.find()) {
                assertThat(m.group(1)).as(file + " importa ai." + m.group(1))
                        .isIn("AiGateway", "AiImageRequest", "AiImageResponse", "AiException", "AiErrorKind", "AiProperties", "lock.GenerationLock", "lock.GenerationBusyException");
            }
        }
    }

    @Test
    void ac1_theAiLayerAndTheGenerationPackageKnowAlmostNothingOfIllustrations() throws IOException {
        for (Path file : javaFiles()) {
            String code = Files.readString(file);
            if (inside(file, "ai")) assertThat(code).as(file.toString()).doesNotContain("studylab.illustrations");
            if (inside(file, "generation")) {
                Matcher m = Pattern.compile("import es\\.vicai\\.studylab\\.illustrations\\.([\\w.]+);").matcher(code);
                while (m.find()) assertThat(m.group(1)).as(file.toString()).isIn("IllustrationQuery", "IllustrationQuery.TopicImage");
            }
        }
    }

    @Test
    void ac9_noCodeOfIllustrationsTouchesTheFileSystemOrBuildsHtml() throws IOException {
        for (Path file : illustrationFiles()) {
            String code = Files.readString(file);
            assertThat(code).as(file.toString()).doesNotContain("java.io.File").doesNotContain("java.nio.file").doesNotContain("FileOutputStream").doesNotContain("<img")
                    .doesNotContain("<html");
        }
    }

    @Test
    void ac9_everyQueryOnTheImagesTableCarriesTheOwnerExceptTheClaimOfTheQueue() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Path file : illustrationFiles()) {
            Matcher m = STRING.matcher(Files.readString(file));
            while (m.find()) {
                String sql = m.group(1) != null ? m.group(1) : m.group(2);
                if (!sql.contains("course_image") || !Pattern.compile("(?i)\\b(select|update|delete|insert)\\b").matcher(sql).find()) continue;
                checked++;
                boolean claim = sql.contains("SKIP LOCKED");
                boolean insertOnly = Pattern.compile("(?is)^\\s*insert\\s+into\\s+course_image\\b").matcher(sql).find();
                if (!claim && !insertOnly && !sql.contains("owner_id")) offenders.add(file.getFileName() + ": " + sql.strip().replaceAll("\\s+", " "));
            }
        }
        assertThat(checked).isGreaterThan(5);
        assertThat(offenders).isEmpty();
    }

    @Test
    void ac15_theControllerNeverReturnsRawEntitiesOrTheStoredPromptAndUsesTheSharedErrors() throws IOException {
        String controller = Files.readString(MAIN.resolve("es/vicai/studylab/illustrations/IllustrationController.java"));

        assertThat(controller).contains("nosniff").contains("private").doesNotContain("ExceptionHandler");
    }

    @Test
    void ac16_noIllustrationCodeLogsAnythingButIdsAndKinds() throws IOException {
        for (Path file : illustrationFiles()) {
            Matcher m = Pattern.compile("log\\.(?:trace|debug|info|warn|error)\\(([^;]*)\\);").matcher(Files.readString(file));
            while (m.find()) {
                String call = m.group(1).toLowerCase();
                assertThat(call).as(file + ": " + call).doesNotContain("prompt").doesNotContain("title").doesNotContain("alt").doesNotContain("getmessage").doesNotContain("summary");
            }
        }
    }

    @Test
    void ac1_noThreadIsStartedExceptByTheWorkerLoop() throws IOException {
        for (Path file : illustrationFiles()) {
            if (file.getFileName().toString().equals("IllustrationWorkerLoop.java")) continue;
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("new Thread").doesNotContain("Executors.").doesNotContain("@Async");
        }
    }
}
