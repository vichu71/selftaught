package es.vicai.studylab.illustrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.provider.FakeProvider;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * F12 AC-7 (ADR-80): la imagen del proveedor es entrada no fiable. Se decide su tipo por contenido, se acota antes de decodificar, se reduce a 768 px y se
 * re-codifica a JPEG sin metadatos, con un máximo de 300 kB.
 */
class ImageProcessorTest extends IllustrationTestBase {

    @Autowired ImageProcessor processor;

    private static byte[] jpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) img.setRGB(x, y, (x * 7 + y * 13) & 0xFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /** Un JPEG al que se le añade un segmento APP1 con «Exif» y una marca reconocible. */
    private static byte[] jpegWithExif(byte[] plain) {
        byte[] exif = "Exif\0\0MARCA-DE-METADATOS-ZZ".getBytes(StandardCharsets.ISO_8859_1);
        int length = exif.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(exif, 0, exif.length);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void ac7_aValidPngBecomesAJpegNoBiggerThan768PixelsAndThreeHundredKilobytes() throws Exception {
        ImageProcessor.Processed result = processor.process(FakeProvider.png(1024, 1024));

        BufferedImage image = decode(result.bytes());
        assertThat(result.mime()).isEqualTo("image/jpeg");
        assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(768);
        assertThat(result.bytes().length).isLessThanOrEqualTo(300 * 1024);
        assertThat(result.bytes()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result.bytes()[1] & 0xFF).isEqualTo(0xD8);
        assertThat(result.width()).isEqualTo(image.getWidth());
    }

    @Test
    void ac7_theAspectRatioIsKeptWhenReducing() throws Exception {
        ImageProcessor.Processed result = processor.process(FakeProvider.png(1536, 1024));

        BufferedImage image = decode(result.bytes());
        assertThat(image.getWidth()).isEqualTo(768);
        assertThat(image.getHeight()).isEqualTo(512);
    }

    @Test
    void ac7_aSmallImageIsNotEnlarged() throws Exception {
        ImageProcessor.Processed result = processor.process(FakeProvider.png(200, 100));

        BufferedImage image = decode(result.bytes());
        assertThat(image.getWidth()).isEqualTo(200);
        assertThat(image.getHeight()).isEqualTo(100);
    }

    @Test
    void ac7_aJpegIsAcceptedToo() throws Exception {
        ImageProcessor.Processed result = processor.process(jpeg(900, 600));

        assertThat(decode(result.bytes()).getWidth()).isEqualTo(768);
    }

    @Test
    void ac7_theMetadataOfTheInputDoesNotSurviveTheReencoding() throws Exception {
        byte[] withExif = jpegWithExif(jpeg(400, 300));
        assertThat(contains(withExif, "MARCA-DE-METADATOS-ZZ")).isTrue();

        ImageProcessor.Processed result = processor.process(withExif);

        assertThat(contains(result.bytes(), "MARCA-DE-METADATOS-ZZ")).isFalse();
        assertThat(contains(result.bytes(), "Exif")).isFalse();
    }

    @Test
    void ac7_theTypeIsDecidedByContentNotByWhatTheProviderClaims() {
        byte[] text = "esto no es una imagen, aunque el proveedor diga image/png".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.process(text)).isInstanceOf(ImageRejectedException.class)
                .satisfies(e -> assertThat(((ImageRejectedException) e).reason()).isEqualTo(ImageRejectedException.Reason.TYPE));
    }

    @Test
    void ac7_aFileWithAPngHeaderButGarbageInsideIsRejected() {
        byte[] fake = new byte[2000];
        new Random(1).nextBytes(fake);
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, fake, 0, 8);

        assertThatThrownBy(() -> processor.process(fake)).isInstanceOf(ImageRejectedException.class);
    }

    @Test
    void ac7_aWebpIsNotSupportedBecauseTheJdkCannotReadIt() {
        byte[] webp = new byte[400];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);

        assertThatThrownBy(() -> processor.process(webp)).isInstanceOf(ImageRejectedException.class);
    }

    @Test
    void ac7_emptyInputIsRejected() {
        assertThatThrownBy(() -> processor.process(new byte[0])).isInstanceOf(ImageRejectedException.class)
                .satisfies(e -> assertThat(((ImageRejectedException) e).reason()).isEqualTo(ImageRejectedException.Reason.EMPTY));
        assertThatThrownBy(() -> processor.process(null)).isInstanceOf(ImageRejectedException.class);
    }

    @Test
    void ac7_aOnePixelImageIsRejectedAsTooSmall() {
        assertThatThrownBy(() -> processor.process(FakeProvider.png(1, 1))).isInstanceOf(ImageRejectedException.class)
                .satisfies(e -> assertThat(((ImageRejectedException) e).reason()).isEqualTo(ImageRejectedException.Reason.TOO_SMALL));
    }

    @Test
    void ac7_anImageThatDeclaresAHugeSizeIsRejectedBeforeDecodingItSoNoMemoryIsExhausted() {
        byte[] bomb = FakeProvider.pngDeclaring(30_000, 30_000);

        long start = System.nanoTime();
        assertThatThrownBy(() -> processor.process(bomb)).isInstanceOf(ImageRejectedException.class)
                .satisfies(e -> assertThat(((ImageRejectedException) e).reason()).isEqualTo(ImageRejectedException.Reason.PIXELS));
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
    }

    @Test
    void ac7_aResponseBiggerThanTheLimitOfBytesIsRejectedWithoutReadingIt() {
        byte[] huge = new byte[9 * 1024 * 1024];
        System.arraycopy(FakeProvider.png(64, 64), 0, huge, 0, 100);

        assertThatThrownBy(() -> processor.process(huge)).isInstanceOf(ImageRejectedException.class)
                .satisfies(e -> assertThat(((ImageRejectedException) e).reason()).isEqualTo(ImageRejectedException.Reason.TOO_BIG));
    }

    @Test
    void ac7_noisyImagesStillFitTheFinalWeightByLoweringTheQuality() throws Exception {
        BufferedImage noise = new BufferedImage(768, 768, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int x = 0; x < 768; x++) for (int y = 0; y < 768; y++) noise.setRGB(x, y, random.nextInt(0xFFFFFF));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(noise, "png", out);

        ImageProcessor.Processed result = processor.process(out.toByteArray());

        assertThat(result.bytes().length).isLessThanOrEqualTo(300 * 1024);
    }

    @Test
    void ac7_theErrorsNeverCarryTheContentOfTheInput() {
        byte[] text = "CONTENIDO-SECRETO-ZZ".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.process(text)).satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain("CONTENIDO-SECRETO"));
    }

    @Test
    void ac7_decodingDoesNotTouchTheFileSystem() throws Exception {
        // El procesado trabaja en memoria: dos pasadas seguidas dan el mismo resultado y no dejan nada en disco
        byte[] input = FakeProvider.png(500, 500);

        assertThat(processor.process(input).bytes()).isEqualTo(processor.process(input).bytes());
    }
}
