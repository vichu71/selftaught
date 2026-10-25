package es.vicai.studylab.material.extract;

import es.vicai.studylab.material.MaterialProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Texto de un PDF con PDFBox (ADR-31): solo lee el texto de las páginas; no ejecuta JavaScript ni abre enlaces ni adjuntos. Acotado en
 * páginas y en texto, en memoria (sin ficheros temporales) y sin dejar salir el mensaje de la librería.
 */
@Component
public class PdfTextExtractor implements TextExtractor {

    private final MaterialProperties props;

    public PdfTextExtractor(MaterialProperties props) {
        this.props = props;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(content), "", null, null,
                IOUtils.createMemoryOnlyStreamCache())) {
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                throw new ExtractionException(ExtractionError.ENCRYPTED);
            }
            int pages = document.getNumberOfPages();
            if (pages > props.maxPages()) throw new ExtractionException(ExtractionError.TOO_MANY_PAGES);

            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            List<Integer> starts = new ArrayList<>(pages);
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).replace("\r\n", "\n").replace('\r', '\n').strip();
                if (page > 1) text.append('\n');
                starts.add(text.length());
                text.append(pageText);
                if (text.length() > props.maxTextChars()) throw new ExtractionException(ExtractionError.TOO_MUCH_TEXT);
            }
            if (text.toString().isBlank()) throw new ExtractionException(ExtractionError.NO_TEXT);
            return new ExtractedText(text.toString(), pages, starts);
        } catch (InvalidPasswordException e) {
            throw new ExtractionException(ExtractionError.ENCRYPTED);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ExtractionException extraction) throw extraction;
            throw new ExtractionException(ExtractionError.CORRUPT);
        }
    }
}
