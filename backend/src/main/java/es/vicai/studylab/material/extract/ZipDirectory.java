package es.vicai.studylab.material.extract;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lee solo los nombres del directorio central de un ZIP, sin descomprimir nada: sirve para clasificar un fichero (¿es un DOCX?)
 * sin exponerse a una bomba de descompresión. No admite ZIP64 (un documento de Word dentro del límite de subida no lo necesita).
 */
final class ZipDirectory {

    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_SIGNATURE = 0x02014b50;
    private static final int MAX_ENTRIES = 10_000;

    private ZipDirectory() {}

    /** Nombres de las entradas, o vacío si el directorio no es legible. */
    static Optional<List<String>> names(byte[] zip) {
        if (zip == null || zip.length < 22) return Optional.empty();
        ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int eocd = -1;
        for (int i = zip.length - 22; i >= Math.max(0, zip.length - 22 - 65_535); i--) {
            if (buffer.getInt(i) == EOCD_SIGNATURE) {
                eocd = i;
                break;
            }
        }
        if (eocd < 0) return Optional.empty();
        int entries = Short.toUnsignedInt(buffer.getShort(eocd + 10));
        long directoryOffset = Integer.toUnsignedLong(buffer.getInt(eocd + 16));
        if (entries == 0xFFFF || entries > MAX_ENTRIES || directoryOffset >= zip.length) return Optional.empty();

        List<String> names = new ArrayList<>(entries);
        int position = (int) directoryOffset;
        for (int i = 0; i < entries; i++) {
            if (position + 46 > zip.length || buffer.getInt(position) != CENTRAL_SIGNATURE) return Optional.empty();
            int nameLength = Short.toUnsignedInt(buffer.getShort(position + 28));
            int extraLength = Short.toUnsignedInt(buffer.getShort(position + 30));
            int commentLength = Short.toUnsignedInt(buffer.getShort(position + 32));
            int nameStart = position + 46;
            if (nameStart + nameLength > zip.length) return Optional.empty();
            names.add(new String(zip, nameStart, nameLength, StandardCharsets.UTF_8));
            position = nameStart + nameLength + extraLength + commentLength;
        }
        return Optional.of(names);
    }
}
