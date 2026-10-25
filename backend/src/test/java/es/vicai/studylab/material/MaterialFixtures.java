package es.vicai.studylab.material;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;

/** Ficheros de prueba generados en memoria (nunca hay ficheros binarios en el repositorio), incluidos los adversarios. */
public final class MaterialFixtures {

    private MaterialFixtures() {}

    public static byte[] text(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    /** Un PDF con una línea de texto por página. */
    public static byte[] pdf(String... pages) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill(doc, pages);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] blankPdf() {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encryptedPdf(String page) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill(doc, page);
            StandardProtectionPolicy policy = new StandardProtectionPolicy("dueno-secreto", "usuario-secreto", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF con un JavaScript que se ejecutaría al abrirlo: la extracción debe ignorarlo. */
    public static byte[] pdfWithJavaScript(String page) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill(doc, page);
            PDActionJavaScript js = new PDActionJavaScript("app.alert('ejecutado'); this.getURL('http://example.invalid');");
            doc.getDocumentCatalog().setOpenAction(js);
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void fill(PDDocument doc, String... pages) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (String text : pages) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
        }
    }

    // ---------- DOCX ----------

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>""";

    private static String documentXml(String... paragraphs) {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(escape(p)).append("</w:t></w:r></w:p>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + body + "</w:body></w:document>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static byte[] docx(String... paragraphs) {
        return zip(new String[] {"[Content_Types].xml", "word/document.xml"}, new byte[][] {
                text(CONTENT_TYPES), text(documentXml(paragraphs))});
    }

    /** Un DOCX con macros (.docm): debe rechazarse. */
    public static byte[] docmWithMacros() {
        return zip(new String[] {"[Content_Types].xml", "word/document.xml", "word/vbaProject.bin"}, new byte[][] {
                text(CONTENT_TYPES), text(documentXml("hola")), new byte[] {1, 2, 3}});
    }

    /** Un ZIP corriente (no es un documento de Word). */
    public static byte[] plainZip() {
        return zip(new String[] {"datos.txt"}, new byte[][] {text("hola")});
    }

    /** Pequeño comprimido, enorme al descomprimir: ≈ 60 MB de ceros dentro del documento. */
    public static byte[] zipBombDocx() {
        byte[] huge = new byte[60 * 1024 * 1024];
        java.util.Arrays.fill(huge, (byte) 'a');
        String xml = "<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"
                + new String(huge, StandardCharsets.US_ASCII) + "</w:t></w:r></w:p></w:body></w:document>";
        return zip(new String[] {"[Content_Types].xml", "word/document.xml"}, new byte[][] {text(CONTENT_TYPES), text(xml)});
    }

    public static byte[] docxWithManyEntries(int extraEntries) {
        String[] names = new String[2 + extraEntries];
        byte[][] data = new byte[2 + extraEntries][];
        names[0] = "[Content_Types].xml";
        data[0] = text(CONTENT_TYPES);
        names[1] = "word/document.xml";
        data[1] = text(documentXml("hola"));
        for (int i = 0; i < extraEntries; i++) {
            names[2 + i] = "word/media/f" + i + ".txt";
            data[2 + i] = new byte[] {1};
        }
        return zip(names, data);
    }

    /** DOCX con una entidad externa que apunta a un fichero local: no debe leerse nunca. */
    public static byte[] docxWithExternalEntity() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE w:document [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>antes &xxe; despues</w:t></w:r></w:p></w:body></w:document>";
        return zip(new String[] {"[Content_Types].xml", "word/document.xml"}, new byte[][] {text(CONTENT_TYPES), text(xml)});
    }

    public static byte[] zip(String[] names, byte[][] contents) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < names.length; i++) {
                zip.putNextEntry(new ZipEntry(names[i]));
                zip.write(contents[i]);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Cabecera de un ejecutable de Windows ("MZ"): lo que sea con extensión .pdf pero con esto dentro no es un PDF. */
    public static byte[] executable() {
        byte[] bytes = new byte[256];
        bytes[0] = 'M';
        bytes[1] = 'Z';
        bytes[2] = (byte) 0x90;
        return bytes;
    }

    /** Cabecera de un .doc antiguo (OLE2): fuera de alcance. */
    public static byte[] legacyDoc() {
        byte[] bytes = new byte[512];
        byte[] magic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(magic, 0, bytes, 0, magic.length);
        return bytes;
    }
}
