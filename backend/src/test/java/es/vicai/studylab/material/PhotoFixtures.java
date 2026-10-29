package es.vicai.studylab.material;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Imágenes de prueba generadas en memoria (F19; nunca hay ficheros binarios en el repositorio), incluidas las adversarias. */
public final class PhotoFixtures {

    private PhotoFixtures() {}

    private static BufferedImage canvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawString("apuntes", 4, Math.max(12, height / 2));
        g.dispose();
        return image;
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] jpeg(int width, int height) {
        return write(canvas(width, height), "jpg");
    }

    public static byte[] png(int width, int height) {
        return write(canvas(width, height), "png");
    }

    /** Un JPEG con un segmento APP1 (EXIF) que lleva {@code marker}: tras el re-codificado el marcador no debe aparecer. */
    public static byte[] jpegWithExif(int width, int height, String marker) {
        return insertApp1(jpeg(width, height), ("Exif\0\0" + marker).getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Un JPEG cuya orientación EXIF es {@code orientation} (6 = girado 90° a la derecha): el resultado debe salir ya enderezado. */
    public static byte[] jpegWithOrientation(int width, int height, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4); // big endian por defecto
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1).putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        byte[] payload = new byte[header.length + tiff.capacity()];
        System.arraycopy(header, 0, payload, 0, header.length);
        System.arraycopy(tiff.array(), 0, payload, header.length, tiff.capacity());
        return insertApp1(jpeg(width, height), payload);
    }

    private static byte[] insertApp1(byte[] jpeg, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]);
        out.write(jpeg[1]); // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write((payload.length + 2) >> 8);
        out.write((payload.length + 2) & 0xFF);
        out.writeBytes(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Firma de JPEG y basura detrás: no se puede decodificar. */
    public static byte[] corruptJpeg() {
        byte[] junk = new byte[200];
        for (int i = 0; i < junk.length; i++) junk[i] = (byte) (i * 31 + 7);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        out.writeBytes(junk);
        return out.toByteArray();
    }

    /** Un PNG cortado por la mitad: cabecera válida, datos incompletos. */
    public static byte[] truncatedPng() {
        byte[] full = png(64, 64);
        byte[] cut = new byte[full.length / 2];
        System.arraycopy(full, 0, cut, 0, cut.length);
        return cut;
    }

    /** Un GIF (también animado): la firma no es la de un tipo admitido. */
    public static byte[] gif() {
        return "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;".getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Un WebP: la firma RIFF…WEBP no es la de un tipo admitido. */
    public static byte[] webp() {
        byte[] body = "RIFF\u0010\u0000\u0000\u0000WEBPVP8 \u0004\u0000\u0000\u0000\u0000\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
        return body;
    }

    public static byte[] svg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
    }

    /** Un PNG válido al que se le añade un ZIP detrás: un políglota. Se admite como imagen y el re-codificado descarta lo que sobra. */
    public static byte[] pngWithTrailingZip() {
        byte[] png = png(32, 32);
        byte[] zip = MaterialFixtures.docx("secreto");
        byte[] both = new byte[png.length + zip.length];
        System.arraycopy(png, 0, both, 0, png.length);
        System.arraycopy(zip, 0, both, png.length, zip.length);
        return both;
    }

    /** Un PNG de ruido (no se comprime): sirve para superar el tamaño máximo sin superar los píxeles. */
    public static byte[] noisyPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(19);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, random.nextInt(0x1000000));
        }
        return write(image, "png");
    }
}
