package es.vicai.studylab.material.extract;

import es.vicai.studylab.material.MaterialProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/** Texto plano (.txt, .md): UTF-8 sin marca de orden de bytes, saltos de línea normalizados y sin caracteres de control. */
@Component
public class PlainTextExtractor implements TextExtractor {

    private final MaterialProperties props;

    public PlainTextExtractor(MaterialProperties props) {
        this.props = props;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        String raw = new String(content, StandardCharsets.UTF_8);
        if (raw.startsWith("﻿")) raw = raw.substring(1);
        String normalised = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder clean = new StringBuilder(normalised.length());
        for (int i = 0; i < normalised.length(); i++) {
            char c = normalised.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) clean.append(c);
        }
        String text = clean.toString();
        if (text.isBlank()) throw new ExtractionException(ExtractionError.NO_TEXT);
        if (text.length() > props.maxTextChars()) throw new ExtractionException(ExtractionError.TOO_MUCH_TEXT);
        return new ExtractedText(text, null, List.of());
    }
}
