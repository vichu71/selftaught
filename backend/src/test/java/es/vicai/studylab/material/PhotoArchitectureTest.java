package es.vicai.studylab.material;

import static es.vicai.studylab.material.PhotoFixtures.jpegWithExif;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * F19 AC-5, AC-9, AC-12 y AC-13 (ADR-100 a 105): reglas sobre el código que ningún test funcional ve. Solo el procesado llama a la lectura de fotos, nunca con un
 * adaptador, el cerrojo se renueva antes de la llamada, la lectura de pago no se reintenta desde la cola, nada de la imagen toca el disco y nunca se loguea contenido.
 */
class PhotoArchitectureTest extends PhotoTestBase {

    private static final Path MAIN = Path.of("src/main/java/es/vicai/studylab");

    private static List<Path> sources(String folder) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN.resolve(folder))) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static List<Path> allSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String normalised(Path file) {
        return file.toString().replace('\\', '/');
    }

    @Test
    void ac5_onlyTheAiLayerAndTheMaterialWorkerTouchTheReadingOfPhotos() throws IOException {
        for (Path file : allSources()) {
            String path = normalised(file);
            if (path.contains("/studylab/ai/") || path.endsWith("/material/MaterialWorker.java")) continue;
            assertThat(java.nio.file.Files.readString(file)).as(path + " lee fotos sin pasar por el procesado de material")
                    .doesNotContain("readImage(").doesNotContain("AiReadRequest");
        }
    }

    @Test
    void ac5_noPackageOutsideTheAiLayerNamesAVisionAdapterOrCallsItsOperation() throws IOException {
        for (Path file : allSources()) {
            if (normalised(file).contains("/studylab/ai/")) continue;
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain(".vision(").doesNotContain("VisionCall").doesNotContain("ai.provider");
        }
    }

    @Test
    void ac9_theLockIsAcquiredAndRenewedBeforeTheCallAndTheLeaseIsAlwaysClosed() throws IOException {
        String worker = Files.readString(MAIN.resolve("material/MaterialWorker.java"));

        assertThat(worker).contains("GenerationLock");
        int acquire = worker.indexOf(".acquire(");
        int renew = worker.indexOf(".renew()");
        int read = worker.indexOf("readImage(");
        assertThat(acquire).isPositive();
        assertThat(renew).isGreaterThan(acquire);
        assertThat(read).isGreaterThan(renew);
        assertThat(worker).containsPattern("try\\s*\\(\\s*GenerationLock\\.Lease");
    }

    @Test
    void ac9_aPaidReadingIsNeverRequeuedByTheQueueOfF03() throws IOException {
        String repository = Files.readString(MAIN.resolve("material/MaterialRepository.java"));

        assertThat(repository).contains("releaseBusy(");
        int requeue = repository.indexOf("public boolean requeueOrFail");
        int recover = repository.indexOf("public List<UUID> recoverInterrupted");
        assertThat(repository.substring(requeue, requeue + 600)).contains("IMAGE");
        assertThat(repository.substring(recover, recover + 900)).contains("IMAGE");
    }

    @Test
    void ac3_nothingOfTheImageIsWrittenToDiskAndTheImageIoCacheIsOff() throws IOException {
        for (Path file : sources("material/image")) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("java.io.File").doesNotContain("java.nio.file")
                    .doesNotContain("FileOutputStream").doesNotContain("createTempFile").doesNotContain("transferTo(");
        }
        assertThat(Files.readString(MAIN.resolve("material/image/PhotoProcessor.java"))).contains("ImageIO.setUseCache(false)");
    }

    @Test
    void ac3_theHeaderIsInspectedBeforeAnythingIsDecoded() throws IOException {
        String processor = Files.readString(MAIN.resolve("material/image/PhotoProcessor.java"));

        assertThat(processor).contains("getWidth(").contains("getHeight(");
        assertThat(processor.indexOf("inspect(")).isLessThan(processor.indexOf("prepare("));
        assertThat(processor).doesNotContain("ImageIO.read(new ByteArrayInputStream(content))"); // ImageIO.read decodifica sin mirar la cabecera antes
    }

    @Test
    void ac11b_theSettingOfAChildLivesOutsideTheGuardianPackageWhichStaysReadOnly() throws IOException {
        for (Path file : sources("guardian")) {
            assertThat(Files.readString(file)).as(file.toString()).doesNotContain("child_setting").doesNotContain("photo_material");
        }
        assertThat(sources("childsettings")).isNotEmpty();
    }

    @Test
    void ac11b_childSettingsNeverTalksToTheModelTheMaterialSearchOrTheLockAndChecksTheTutorFirst() throws IOException {
        for (Path file : sources("childsettings")) {
            String code = Files.readString(file);
            assertThat(code).as(file.toString()).doesNotContain("AiGateway").doesNotContain("MaterialSearch").doesNotContain("GenerationLock")
                    .doesNotContain("source_chunk").doesNotContain("source_document");
        }
        String controller = Files.readString(MAIN.resolve("childsettings/ChildSettingsController.java"));
        assertThat(controller.split("requireStudentOf").length - 1).isGreaterThanOrEqualTo(2); // GET y PUT
    }

    @Test
    void ac13_noSqlOfThePhotoCodeIsBuiltByConcatenatingStrings() throws IOException {
        for (String folder : List.of("material", "childsettings")) {
            for (Path file : sources(folder)) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContainPattern("sql\\(\\s*\"[^\"]*\"\\s*\\+").doesNotContainPattern("sql\\(\\s*[a-zA-Z_]+\\s*\\+");
            }
        }
    }

    @Test
    void ac13_logsNeverContainTheImageTheReadTheNameOrTheMetadata() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            String ana = logged("ana@example.com");
            modelReads("texto-leido-unico-xyz de la foto");
            uploaded(ana, "nombre-foto-unico-xyz.jpg", jpegWithExif(90, 60, "gps-unico-xyz"));
            worker.processPending();
            fake.visionFailsTimes(1, ProviderErrorKind.BAD_REQUEST);
            fake.visionFailMessage("mensaje-del-proveedor-xyz");
            uploaded(ana, "otra-foto-unica-xyz.jpg", jpegWithExif(91, 61, "gps2-unico-xyz"));
            worker.processPending();
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(all).doesNotContain("unico-xyz").doesNotContain("unica-xyz").doesNotContain("mensaje-del-proveedor-xyz").doesNotContain("[B@");
    }
}
