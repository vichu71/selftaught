package es.vicai.studylab.material.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import es.vicai.studylab.material.MaterialProperties;
import es.vicai.studylab.material.extract.ExtractedText;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AC-7: fragmentos de tamaño acotado, con solapamiento y con posición para poder citar. */
class ChunkerTest {

    private final MaterialProperties props = MaterialProperties.defaults().withChunking(200, 40);
    private final Chunker chunker = new Chunker(props);

    private static ExtractedText plain(String text) {
        return new ExtractedText(text, null, List.of());
    }

    private static String sentences(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append("Esta es la frase número ").append(i).append(" del documento. ");
        return sb.toString().trim();
    }

    @Test
    void ac7_aShortTextIsASingleChunk() {
        List<Chunk> chunks = chunker.split(plain("Un texto corto."));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("Un texto corto.");
        assertThat(chunks.get(0).ordinal()).isZero();
    }

    @Test
    void ac7_noChunkExceedsTheConfiguredSizeAndOrdinalsAreSequential() {
        List<Chunk> chunks = chunker.split(plain(sentences(60)));

        assertThat(chunks.size()).isGreaterThan(5);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).text().length()).isLessThanOrEqualTo(200);
            assertThat(chunks.get(i).ordinal()).isEqualTo(i);
        }
    }

    @Test
    void ac7_offsetsPointBackToTheExactTextForCitation() {
        String text = sentences(40);
        for (Chunk chunk : chunker.split(plain(text))) {
            assertThat(text.substring(chunk.startOffset(), chunk.endOffset())).isEqualTo(chunk.text());
        }
    }

    @Test
    void ac7_consecutiveChunksOverlapSoNoIdeaIsCutInTwo() {
        List<Chunk> chunks = chunker.split(plain(sentences(40)));

        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).startOffset()).isLessThan(chunks.get(i - 1).endOffset());
            assertThat(chunks.get(i).startOffset()).isGreaterThan(chunks.get(i - 1).startOffset()); // siempre avanza
        }
    }

    @Test
    void ac7_everyNonBlankCharacterIsCoveredByAtLeastOneChunk() {
        String text = sentences(40);
        boolean[] covered = new boolean[text.length()];
        for (Chunk chunk : chunker.split(plain(text))) {
            for (int i = chunk.startOffset(); i < chunk.endOffset(); i++) covered[i] = true;
        }
        for (int i = 0; i < covered.length; i++) {
            if (!Character.isWhitespace(text.charAt(i))) assertThat(covered[i]).as("carácter " + i).isTrue();
        }
    }

    @Test
    void ac7_prefersToCutAtParagraphOrSentenceBoundaries() {
        String text = ("Primer párrafo con algo de texto que ocupa bastante espacio. ".repeat(2) + "\n\n"
                + "Segundo párrafo con otro texto distinto que también ocupa espacio. ".repeat(2)).trim();

        List<Chunk> chunks = chunker.split(plain(text));

        assertThat(chunks.get(0).text()).endsWith(".");
    }

    @Test
    void ac7_aTextWithoutAnySeparatorIsStillSplitBySize() {
        List<Chunk> chunks = chunker.split(plain("x".repeat(1000)));

        assertThat(chunks).hasSizeGreaterThan(4);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(200));
    }

    @Test
    void ac7_neverSplitsASurrogatePairInHalf() {
        String text = "😀".repeat(300); // cada emoji ocupa dos unidades de código

        for (Chunk chunk : chunker.split(plain(text))) {
            assertThat(Character.isLowSurrogate(chunk.text().charAt(0))).isFalse();
            assertThat(Character.isHighSurrogate(chunk.text().charAt(chunk.text().length() - 1))).isFalse();
        }
    }

    @Test
    void ac7_assignsThePageOfEachChunkForPdfs() {
        String page1 = sentences(8);
        String page2 = "Segunda página. " + sentences(8);
        ExtractedText extracted = new ExtractedText(page1 + "\n" + page2, 2, List.of(0, page1.length() + 1));

        List<Chunk> chunks = chunker.split(extracted);

        assertThat(chunks.get(0).page()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).page()).isEqualTo(2);
        assertThat(chunks).allSatisfy(c -> assertThat(c.page()).isNotNull());
    }

    @Test
    void ac7_noPageForTextThatHasNone() {
        assertThat(chunker.split(plain(sentences(20)))).allSatisfy(c -> assertThat(c.page()).isNull());
    }

    @Test
    void edge_blankChunksAreNeverProduced() {
        List<Chunk> chunks = chunker.split(plain("Hola.\n\n\n\n\n\n" + " ".repeat(500) + "\n\nAdiós."));

        assertThat(chunks).allSatisfy(c -> assertThat(c.text()).isNotBlank());
    }
}
