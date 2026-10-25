package es.vicai.studylab.material.extract;

import static es.vicai.studylab.material.MaterialFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.material.MaterialType;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** AC-2: el tipo se decide por el contenido (firma), nunca por la extensión ni el Content-Type. */
class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    void ac2_aRealPdfIsRecognised() {
        assertThat(detector.detect(pdf("hola"))).contains(MaterialType.PDF);
    }

    @Test
    void ac2_aRealDocxIsRecognised() {
        assertThat(detector.detect(docx("hola"))).contains(MaterialType.DOCX);
    }

    @Test
    void ac2_plainUtf8TextIsRecognisedWithAccentsAndEmoji() {
        assertThat(detector.detect(text("Canción de la ñ y el emoji 😀"))).contains(MaterialType.TEXT);
    }

    @Test
    void ac2_utf8WithByteOrderMarkIsStillText() {
        byte[] withBom = ("﻿hola").getBytes(StandardCharsets.UTF_8);
        assertThat(detector.detect(withBom)).contains(MaterialType.TEXT);
    }

    @Test
    void ac2_anExecutableIsNotAllowedWhateverItsNameSaysEvenWithAPdfExtension() {
        assertThat(detector.detect(executable())).isEmpty();
    }

    @Test
    void ac2_anOrdinaryZipIsNotADocx() {
        assertThat(detector.detect(plainZip())).isEmpty();
    }

    @Test
    void ac2_aDocxWithMacrosIsRejected() {
        assertThat(detector.detect(docmWithMacros())).isEmpty();
    }

    @Test
    void ac2_theLegacyDocFormatIsRejected() {
        assertThat(detector.detect(legacyDoc())).isEmpty();
    }

    @Test
    void ac2_binaryDataWithNulBytesIsNotText() {
        assertThat(detector.detect(new byte[] {'h', 'o', 0, 'l', 'a'})).isEmpty();
    }

    @Test
    void ac2_bytesThatAreNotValidUtf8AreNotText() {
        assertThat(detector.detect(new byte[] {'c', 'a', 'n', 'c', (byte) 0xF3, 'n'})).isEmpty(); // "canción" en Latin-1
    }

    @Test
    void edge_emptyOrTinyInputIsNeverAllowed() {
        assertThat(detector.detect(new byte[0])).isEmpty();
    }

    @Test
    void edge_aFileThatOnlyStartsLikeAPdfIsStillClassifiedByItsSignature() {
        assertThat(detector.detect(text("%PDF-1.7 esto es basura"))).contains(MaterialType.PDF); // el extractor dirá que está corrupto
    }

    @Test
    void ac2_htmlOrScriptDisguisedAsTextIsJustText() {
        // Es texto válido: se indexa como datos, jamás se interpreta (AC-5/AC-13)
        assertThat(detector.detect(text("<script>alert(1)</script>"))).contains(MaterialType.TEXT);
    }

    @Test
    void edge_nullInputIsRejectedWithoutBlowingUp() {
        assertThat(detector.detect(null)).isEqualTo(Optional.empty());
    }
}
