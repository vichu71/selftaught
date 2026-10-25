package es.vicai.studylab.material.extract;

import es.vicai.studylab.material.MaterialProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Texto de un DOCX (ADR-31) leyendo solo {@code word/document.xml} con ZIP + StAX del JDK, sin Apache POI. Acotado contra bombas de
 * descompresión (número de entradas y bytes descomprimidos en total) y sin resolver DTD ni entidades externas (XXE).
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    private static final String DOCUMENT = "word/document.xml";

    private final MaterialProperties props;

    public DocxTextExtractor(MaterialProperties props) {
        this.props = props;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        byte[] documentXml = readDocumentXml(content);
        String text = parse(documentXml);
        if (text.isBlank()) throw new ExtractionException(ExtractionError.NO_TEXT);
        return new ExtractedText(text, null, List.of());
    }

    /** Recorre el ZIP descomprimiendo como mucho el límite configurado entre todas las entradas. */
    private byte[] readDocumentXml(byte[] content) {
        long budget = props.maxDocxUncompressedBytes();
        int entries = 0;
        byte[] found = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > props.maxDocxEntries()) throw new ExtractionException(ExtractionError.TOO_LARGE);
                boolean wanted = DOCUMENT.equals(entry.getName());
                ByteArrayOutputStream out = wanted ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    budget -= read;
                    if (budget < 0) throw new ExtractionException(ExtractionError.TOO_LARGE);
                    if (out != null) out.write(buffer, 0, read);
                }
                if (out != null) found = out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ExtractionException extraction) throw extraction;
            throw new ExtractionException(ExtractionError.CORRUPT);
        }
        if (found == null) throw new ExtractionException(ExtractionError.CORRUPT);
        return found;
    }

    private String parse(byte[] xml) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA);

        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int total = 0;
        try (InputStream in = new ByteArrayInputStream(xml)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        switch (reader.getLocalName()) {
                            case "t" -> paragraph.append(reader.getElementText());
                            case "tab" -> paragraph.append('\t');
                            case "br", "cr" -> paragraph.append('\n');
                            default -> { }
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT && "p".equals(reader.getLocalName())) {
                        String done = paragraph.toString().stripTrailing();
                        paragraph.setLength(0);
                        total += done.length() + 1;
                        if (total > props.maxTextChars()) throw new ExtractionException(ExtractionError.TOO_MUCH_TEXT);
                        paragraphs.add(done);
                    }
                    if (paragraph.length() > props.maxTextChars()) throw new ExtractionException(ExtractionError.TOO_MUCH_TEXT);
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException | IOException | RuntimeException e) {
            if (e instanceof ExtractionException extraction) throw extraction;
            throw new ExtractionException(ExtractionError.CORRUPT);
        }
        return String.join("\n", paragraphs).strip();
    }

    private static void trySet(XMLInputFactory factory, String property) {
        try {
            factory.setProperty(property, "");
        } catch (IllegalArgumentException ignored) {
            // La implementación no reconoce la propiedad: ya tiene desactivados el DTD y las entidades externas
        }
    }
}
