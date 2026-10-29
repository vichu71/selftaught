package es.vicai.studylab.material.image;

import static es.vicai.studylab.material.PhotoFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.ai.provider.FakeProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * F19 AC-2 y AC-3 (ADR-101): la foto es entrada no fiable. Se mira la cabecera SIN decodificar, se decodifica, se reduce y se re-codifica a JPEG sin metadatos.
 * Prueba unitaria pura (sin Spring ni BD). Contrato: {@code new PhotoProcessor(maxPixels, maxSide)}, {@code inspect(byte[])} y {@code prepare(byte[])};
 * un rechazo lanza {@code PhotoRejectedException} con {@code kind()} = IMAGE_INVALID o IMAGE_TOO_LARGE.
 */
class PhotoProcessorTest {

    private static final int MAX_PIXELS = 25_000_000;
    private static final int MAX_SIDE = 1568;
    private final PhotoProcessor processor = new PhotoProcessor(MAX_PIXELS, MAX_SIDE);

    private static String kindOf(Throwable t) {
        return ((PhotoRejectedException) t).kind();
    }

    @Test
    void ac2_inspectReadsFormatAndSizeFromTheHeaderOfAJpegAndOfAPng() {
        PhotoProcessor.Inspected jpg = processor.inspect(jpeg(120, 80));
        PhotoProcessor.Inspected png = processor.inspect(png(64, 32));

        assertThat(jpg.format()).isEqualTo("JPEG");
        assertThat(jpg.width()).isEqualTo(120);
        assertThat(jpg.height()).isEqualTo(80);
        assertThat(png.format()).isEqualTo("PNG");
        assertThat(png.width()).isEqualTo(64);
    }

    @Test
    void ac2_aPixelBombIsRejectedFromItsHeaderWithoutDecodingIt() {
        // 40.000 x 40.000 = 1.600 Mpx: decodificarla reservaría ~6 GB. Solo lleva la cabecera, así que si se intentara decodificar fallaría de otra forma
        byte[] bomb = FakeProvider.pngDeclaring(40_000, 40_000);

        assertThatThrownBy(() -> processor.inspect(bomb)).isInstanceOf(PhotoRejectedException.class)
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo("IMAGE_TOO_LARGE"));
        assertThatThrownBy(() -> processor.prepare(bomb)).isInstanceOf(PhotoRejectedException.class)
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo("IMAGE_TOO_LARGE"));
    }

    @Test
    void ac2_theLimitIsInPixelsNotInSide() {
        PhotoProcessor tight = new PhotoProcessor(100 * 100, MAX_SIDE);

        assertThat(tight.inspect(jpeg(100, 100)).width()).isEqualTo(100);
        assertThatThrownBy(() -> tight.inspect(jpeg(101, 100))).isInstanceOf(PhotoRejectedException.class);
    }

    @Test
    void ac2_corruptTruncatedAndEmptyImagesAreInvalidNeverAnUnexpectedError() {
        for (byte[] bad : new byte[][] {corruptJpeg(), truncatedPng(), new byte[0], new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}}) {
            assertThatThrownBy(() -> processor.prepare(bad)).isInstanceOf(PhotoRejectedException.class)
                    .satisfies(e -> assertThat(kindOf(e)).isEqualTo("IMAGE_INVALID"));
        }
    }

    @Test
    void ac2_aGifAnSvgAndAWebpAreNotImages() {
        for (byte[] other : new byte[][] {gif(), svg(), webp()}) {
            assertThatThrownBy(() -> processor.inspect(other)).isInstanceOf(PhotoRejectedException.class);
        }
    }

    @Test
    void ac3_theResultIsAJpegAndNeverCarriesTheMetadataOfTheOriginal() {
        byte[] original = jpegWithExif(200, 100, "GPS-41.38N-2.17E-SECRETO");
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("SECRETO"); // el original sí lo lleva

        PhotoProcessor.Prepared prepared = processor.prepare(original);

        assertThat(prepared.bytes()[0]).isEqualTo((byte) 0xFF);
        assertThat(prepared.bytes()[1]).isEqualTo((byte) 0xD8);
        assertThat(new String(prepared.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("SECRETO").doesNotContain("Exif");
    }

    @Test
    void ac3_aPngBecomesAJpeg() {
        PhotoProcessor.Prepared prepared = processor.prepare(png(80, 60));

        assertThat(prepared.bytes()[0]).isEqualTo((byte) 0xFF);
        assertThat(prepared.bytes()[1]).isEqualTo((byte) 0xD8);
        assertThat(prepared.width()).isEqualTo(80);
        assertThat(prepared.height()).isEqualTo(60);
    }

    @Test
    void ac3_aBigPhotoIsShrunkToTheMaximumSideKeepingTheProportion() throws Exception {
        PhotoProcessor.Prepared prepared = processor.prepare(jpeg(3136, 1568)); // el doble del lado máximo

        assertThat(prepared.width()).isEqualTo(1568);
        assertThat(prepared.height()).isEqualTo(784);
        var decoded = ImageIO.read(new ByteArrayInputStream(prepared.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(1568);
    }

    @Test
    void ac3_aSmallPhotoIsNeverEnlarged() {
        PhotoProcessor.Prepared prepared = processor.prepare(jpeg(300, 200));

        assertThat(prepared.width()).isEqualTo(300);
        assertThat(prepared.height()).isEqualTo(200);
    }

    @Test
    void ac3_theExifOrientationIsAppliedBeforeDroppingIt() {
        PhotoProcessor.Prepared prepared = processor.prepare(jpegWithOrientation(200, 100, 6)); // girada 90°: debe salir de 100 de ancho y 200 de alto

        assertThat(prepared.width()).isEqualTo(100);
        assertThat(prepared.height()).isEqualTo(200);
    }

    @Test
    void ac3_whateverFollowsTheImageInAPolyglotIsDiscarded() {
        byte[] polyglot = pngWithTrailingZip();

        PhotoProcessor.Prepared prepared = processor.prepare(polyglot);

        String text = new String(prepared.bytes(), StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("word/document.xml").doesNotContain("PK");
    }

    @Test
    void theObjectNeverPrintsTheBytes() {
        PhotoProcessor.Prepared prepared = processor.prepare(jpeg(40, 40));

        assertThat(prepared.toString()).doesNotContain("[B@").hasSizeLessThan(200);
    }
}
