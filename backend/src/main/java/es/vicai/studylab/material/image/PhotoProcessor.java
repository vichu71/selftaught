package es.vicai.studylab.material.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * La foto es entrada no fiable (ADR-101). {@link #inspect} lee solo la cabecera (formato y tamaño declarado) sin decodificar nada, así que una bomba de píxeles se
 * rechaza sin reservar memoria. {@code prepare} decodifica una imagen ya inspeccionada, aplica la orientación EXIF, la reduce al lado máximo y la re-codifica a JPEG
 * sin metadatos: lo que sigue a la imagen en un fichero políglota y el EXIF (GPS) no llegan al resultado. Todo en memoria: nada toca el disco.
 */
public class PhotoProcessor {

    /** Calidad del JPEG enviado al modelo: el texto manuscrito sigue siendo legible y el peso se queda en unos cientos de kB. */
    private static final float JPEG_QUALITY = 0.88f;

    public record Inspected(String format, int width, int height) {}

    /** El resultado ya re-codificado. Nunca imprime sus bytes. */
    public record Prepared(byte[] bytes, int width, int height) {
        @Override
        public String toString() {
            return "Prepared[" + bytes.length + " bytes, " + width + "x" + height + "]";
        }
    }

    private final long maxPixels;
    private final int maxSide;

    public PhotoProcessor(long maxPixels, int maxSide) {
        this.maxPixels = maxPixels;
        this.maxSide = maxSide;
        ImageIO.setUseCache(false); // nada de ficheros temporales: el contenedor es de solo lectura y la foto no debe tocar el disco
    }

    /** Formato y tamaño declarados en la cabecera, sin decodificar los píxeles. Rechaza lo que no sea JPEG o PNG, lo ilegible, lo animado y lo que supere los píxeles. */
    public Inspected inspect(byte[] content) {
        if (content == null || content.length < 8) throw invalid();
        String format = signature(content);
        if (format == null) throw invalid();
        if ("PNG".equals(format) && isAnimatedPng(content)) throw invalid();
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            ImageReader reader = readerFor(in, format);
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) throw invalid();
                if ((long) width * height > maxPixels) throw new PhotoRejectedException(PhotoRejectedException.TOO_LARGE);
                if (!isComplete(format, content)) throw invalid(); // cabecera buena pero fichero cortado o relleno de basura: se dice ya al subir
                return new Inspected(format, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof PhotoRejectedException rejected) throw rejected;
            throw invalid();
        }
    }

    /** Decodifica, endereza, reduce y re-codifica a JPEG. Cualquier fallo de decodificación es {@code IMAGE_INVALID}, nunca un error técnico. */
    public Prepared prepare(byte[] content) {
        Inspected header = inspect(content);
        BufferedImage image;
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            ImageReader reader = readerFor(in, header.format());
            try {
                reader.setInput(in, true, true);
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw invalid();
        }
        if (image == null) throw invalid();
        try {
            BufferedImage upright = applyOrientation(image, "JPEG".equals(header.format()) ? exifOrientation(content) : 1);
            BufferedImage fitted = fit(upright);
            return new Prepared(encode(fitted), fitted.getWidth(), fitted.getHeight());
        } catch (IOException | RuntimeException e) {
            throw invalid();
        }
    }

    // ---------- formato ----------

    private static String signature(byte[] c) {
        if ((c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) return "JPEG";
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < png.length; i++) {
            if (c[i] != png[i]) return null;
        }
        return "PNG";
    }

    private static ImageReader readerFor(ImageInputStream in, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.toLowerCase());
        if (!readers.hasNext()) throw invalid();
        return readers.next();
    }

    /** Comprobación barata de que el fichero no está cortado: un PNG recorre sus fragmentos hasta {@code IEND}; un JPEG tiene que contener su marcador final {@code FF D9}. */
    private static boolean isComplete(String format, byte[] c) {
        if ("JPEG".equals(format)) {
            for (int i = c.length - 2; i > 2; i--) {
                if ((c[i] & 0xFF) == 0xFF && (c[i + 1] & 0xFF) == 0xD9) return true;
            }
            return false;
        }
        long pos = 8;
        while (pos + 12 <= c.length) {
            int at = (int) pos;
            long length = ((c[at] & 0xFFL) << 24) | ((c[at + 1] & 0xFFL) << 16) | ((c[at + 2] & 0xFFL) << 8) | (c[at + 3] & 0xFFL);
            String type = new String(c, at + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (pos + 12 + length > c.length) return false;
            if (type.equals("IEND")) return true;
            pos += 12 + length;
        }
        return false;
    }

    /** Un PNG animado (APNG) lleva un fragmento {@code acTL} antes de los datos de imagen. */
    private static boolean isAnimatedPng(byte[] c) {
        int pos = 8;
        while (pos + 12 <= c.length) {
            long length = ((c[pos] & 0xFFL) << 24) | ((c[pos + 1] & 0xFFL) << 16) | ((c[pos + 2] & 0xFFL) << 8) | (c[pos + 3] & 0xFFL);
            String type = new String(c, pos + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (type.equals("acTL")) return true;
            if (type.equals("IDAT") || type.equals("IEND")) return false;
            pos += 12 + length;
            if (length < 0 || pos < 0) return false;
        }
        return false;
    }

    // ---------- orientación EXIF ----------

    /** La orientación (1 a 8) del primer APP1 EXIF de un JPEG, o 1 si no hay o no se entiende. */
    static int exifOrientation(byte[] c) {
        int pos = 2;
        while (pos + 4 <= c.length && (c[pos] & 0xFF) == 0xFF) {
            int marker = c[pos + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) return 1; // empiezan los datos
            int length = ((c[pos + 2] & 0xFF) << 8) | (c[pos + 3] & 0xFF);
            if (length < 2) return 1;
            if (marker == 0xE1 && length >= 16 && pos + 2 + length <= c.length && c[pos + 4] == 'E' && c[pos + 5] == 'x' && c[pos + 6] == 'i' && c[pos + 7] == 'f') {
                return tiffOrientation(c, pos + 10, pos + 2 + length);
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] c, int tiff, int end) {
        if (tiff + 8 > end) return 1;
        boolean little = c[tiff] == 'I' && c[tiff + 1] == 'I';
        if (!little && !(c[tiff] == 'M' && c[tiff + 1] == 'M')) return 1;
        int ifd = tiff + (int) u32(c, tiff + 4, little);
        if (ifd < tiff || ifd + 2 > end) return 1;
        int entries = u16(c, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) return 1;
            if (u16(c, entry, little) == 0x0112) {
                int value = u16(c, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] c, int at, boolean little) {
        return little ? ((c[at + 1] & 0xFF) << 8) | (c[at] & 0xFF) : ((c[at] & 0xFF) << 8) | (c[at + 1] & 0xFF);
    }

    private static long u32(byte[] c, int at, boolean little) {
        return little ? ((long) u16(c, at + 2, true) << 16) | u16(c, at, true) : ((long) u16(c, at, false) << 16) | u16(c, at + 2, false);
    }

    private static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation == 1) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(source, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    // ---------- reducción y codificación ----------

    /** Reduce (nunca amplía) para que el lado mayor no pase de {@code maxSide}, en pasos de la mitad para no perder el texto fino. Siempre devuelve RGB opaco. */
    private BufferedImage fit(BufferedImage source) {
        BufferedImage current = source;
        int w = current.getWidth();
        int h = current.getHeight();
        int longest = Math.max(w, h);
        int targetW = w;
        int targetH = h;
        if (longest > maxSide) {
            double scale = (double) maxSide / longest;
            targetW = Math.max(1, (int) Math.round(w * scale));
            targetH = Math.max(1, (int) Math.round(h * scale));
        }
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH && targetW < current.getWidth()) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetW || current.getHeight() != targetH || current.getType() != BufferedImage.TYPE_INT_RGB) {
            current = resize(current, targetW, targetH);
        }
        return current;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // un PNG con transparencia se aplana sobre blanco
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("sin escritor JPEG");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); javax.imageio.stream.MemoryCacheImageOutputStream stream = new javax.imageio.stream.MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param); // sin metadatos: ni EXIF ni GPS
            stream.flush();
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static PhotoRejectedException invalid() {
        return new PhotoRejectedException(PhotoRejectedException.INVALID);
    }
}
