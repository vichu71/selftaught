package es.vicai.studylab.material.extract;

import es.vicai.studylab.material.MaterialType;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Decide el tipo de un fichero por su contenido (ADR-31): la extensión y el Content-Type los pone el cliente y no se miran.
 * PDF por su firma; DOCX si es un ZIP con {@code word/document.xml} y sin macros; texto si es UTF-8 válido sin bytes nulos.
 * Cualquier otra cosa (ejecutables, ZIP corrientes, .doc antiguos, binarios) no se admite.
 */
@Component
public class FileTypeDetector {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public Optional<MaterialType> detect(byte[] content) {
        if (content == null || content.length == 0) return Optional.empty();
        if (startsWith(content, PDF)) return Optional.of(MaterialType.PDF);
        // Solo la firma: que de verdad se pueda decodificar lo comprueba PhotoProcessor.inspect al subir
        if (startsWith(content, JPEG) || startsWith(content, PNG)) return Optional.of(MaterialType.IMAGE);
        if (startsWith(content, ZIP)) return isDocx(content) ? Optional.of(MaterialType.DOCX) : Optional.empty();
        return isUtf8Text(content) && !looksLikeMarkupImage(content) ? Optional.of(MaterialType.TEXT) : Optional.empty();
    }

    private static boolean isDocx(byte[] content) {
        Optional<List<String>> names = ZipDirectory.names(content);
        if (names.isEmpty()) return false;
        List<String> entries = names.get();
        boolean hasMacros = entries.stream().anyMatch(n -> n.toLowerCase().contains("vbaproject"));
        return !hasMacros && entries.contains("[Content_Types].xml") && entries.contains("word/document.xml");
    }

    /** Un SVG (o un XML que lo disfraza) es UTF-8 válido pero no es texto de apuntes: puede llevar scripts y no se admite como foto (F19). */
    private static boolean looksLikeMarkupImage(byte[] content) {
        String head = new String(content, 0, Math.min(content.length, 256), StandardCharsets.UTF_8).replace("﻿", "").stripLeading().toLowerCase();
        return head.startsWith("<svg") || head.startsWith("<?xml") || head.startsWith("<!doctype svg");
    }

    private static boolean isUtf8Text(byte[] content) {
        for (byte b : content) {
            if (b == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) return false;
        }
        return true;
    }
}
