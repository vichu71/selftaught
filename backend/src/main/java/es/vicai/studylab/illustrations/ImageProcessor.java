package es.vicai.studylab.illustrations;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * La imagen del proveedor es entrada no fiable (ADR-80): se decide su tipo por contenido, se acotan los bytes y los píxeles que declara <b>antes</b> de decodificarla
 * (una bomba de descompresión no llega a la memoria), se descartan las diminutas, se reduce y se re-codifica a JPEG sin metadatos (EXIF, C2PA…). Todo en memoria y
 * sin dependencias: solo {@code javax.imageio}.
 */
@Component
public class ImageProcessor {

    static {
        ImageIO.setUseCache(false); // nada de ficheros temporales
    }

    public record Processed(byte[] bytes, String mime, int width, int height) {}

    private final IllustrationProperties props;

    public ImageProcessor(IllustrationProperties props) {
        this.props = props;
    }

    public Processed process(byte[] raw) {
        if (raw == null || raw.length == 0) throw new ImageRejectedException(ImageRejectedException.Reason.EMPTY);
        if (raw.length > props.maxProviderBytes()) throw new ImageRejectedException(ImageRejectedException.Reason.TOO_BIG);

        int[] size = declaredSize(raw);
        long pixels = (long) size[0] * size[1];
        if (size[0] <= 0 || size[1] <= 0) throw new ImageRejectedException(ImageRejectedException.Reason.DECODE);
        if (pixels > props.maxPixels()) throw new ImageRejectedException(ImageRejectedException.Reason.PIXELS);
        if (Math.min(size[0], size[1]) < props.minSide()) throw new ImageRejectedException(ImageRejectedException.Reason.TOO_SMALL);

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException | RuntimeException e) {
            throw new ImageRejectedException(ImageRejectedException.Reason.DECODE);
        }
        if (decoded == null) throw new ImageRejectedException(ImageRejectedException.Reason.DECODE);

        BufferedImage rgb = toRgb(decoded);
        byte[] jpeg = encode(rgb);
        return new Processed(jpeg, "image/jpeg", rgb.getWidth(), rgb.getHeight());
    }

    // ---------- tipo y tamaño por contenido ----------

    /** Ancho y alto que declara la cabecera de un PNG o de un JPEG; cualquier otra cosa es un tipo no admitido. */
    private static int[] declaredSize(byte[] b) {
        if (b.length >= 24 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return new int[] {int32(b, 16), int32(b, 20)};
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return jpegSize(b);
        throw new ImageRejectedException(ImageRejectedException.Reason.TYPE);
    }

    private static int int32(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    /** Recorre los segmentos hasta el primero de tipo SOF (donde están el alto y el ancho). */
    private static int[] jpegSize(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) throw new ImageRejectedException(ImageRejectedException.Reason.DECODE);
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) { // relleno
                i++;
                continue;
            }
            int length = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) {
                int height = ((b[i + 5] & 0xFF) << 8) | (b[i + 6] & 0xFF);
                int width = ((b[i + 7] & 0xFF) << 8) | (b[i + 8] & 0xFF);
                return new int[] {width, height};
            }
            if (marker == 0xDA || length < 2) break;
            i += 2 + length;
        }
        throw new ImageRejectedException(ImageRejectedException.Reason.DECODE);
    }

    // ---------- reducir y re-codificar ----------

    private BufferedImage toRgb(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) props.maxSide() / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE); // el JPEG no tiene transparencia
            g.fillRect(0, 0, tw, th);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** JPEG sin metadatos; si pesa de más, baja la calidad hasta que quepa. */
    private byte[] encode(BufferedImage image) {
        float quality = props.jpegQuality();
        for (int attempt = 0; attempt < 8; attempt++) {
            byte[] bytes = write(image, quality);
            if (bytes.length <= props.maxBytes()) return bytes;
            quality = Math.max(0.2f, quality - 0.12f);
        }
        throw new ImageRejectedException(ImageRejectedException.Reason.TOO_BIG);
    }

    private static byte[] write(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("No hay codificador JPEG");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param); // sin metadatos
            stream.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageRejectedException(ImageRejectedException.Reason.DECODE);
        } finally {
            writer.dispose();
        }
    }
}
