package es.vicai.studylab.study;

import com.fasterxml.jackson.databind.JsonNode;
import es.vicai.studylab.generation.ModelJson;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valida el veredicto del modelo al corregir un ejercicio (ADR-52): uno de tres valores y un comentario corto y sin marcado. Devuelve vacío ante
 * cualquier incumplimiento; nunca lanza.
 */
@Component
public class GradeParser {

    public static final int MAX_COMMENT = 500;
    private static final Set<String> VERDICTS = Set.of("CORRECT", "PARTIAL", "INCORRECT");

    public record Grade(String verdict, String comment) {}

    public Optional<Grade> parse(String raw) {
        try {
            JsonNode root = ModelJson.object(raw);
            JsonNode verdict = root.get("verdict");
            if (verdict == null || !verdict.isTextual() || !VERDICTS.contains(verdict.asText())) throw new IllegalArgumentException();
            return Optional.of(new Grade(verdict.asText(), ModelJson.plain(root, "comment", MAX_COMMENT)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
