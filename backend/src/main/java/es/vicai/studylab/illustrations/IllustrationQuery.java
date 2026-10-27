package es.vicai.studylab.illustrations;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * La lectura pública de las ilustraciones para el detalle del curso y de la lección (F12): estado y texto alternativo por tema. Es lo único de este paquete que
 * conoce {@code generation/}. Nunca devuelve bytes, prompt ni errores. Una imagen quitada ({@code REMOVED}) no aparece.
 */
@Component
public class IllustrationQuery {

    /** Lo que se enseña de la imagen de un tema; el texto alternativo solo cuando está hecha. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopicImage(String status, String alt) {}

    private final JdbcClient jdbc;

    public IllustrationQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, TopicImage> byTopic(UUID courseId, UUID ownerId) {
        Map<UUID, TopicImage> images = new HashMap<>();
        jdbc.sql("SELECT topic_id, status, alt_text FROM course_image WHERE course_id = :course AND owner_id = :owner AND status <> 'REMOVED'")
                .param("course", courseId).param("owner", ownerId)
                .query(rs -> {
                    String status = rs.getString(2);
                    images.put(rs.getObject(1, UUID.class), new TopicImage(status, "DONE".equals(status) ? rs.getString(3) : null));
                });
        return images;
    }

    /** Si el curso se creó con las ilustraciones activadas. */
    public boolean enabled(UUID courseId, UUID ownerId) {
        return jdbc.sql("SELECT illustrations FROM course WHERE id = :id AND owner_id = :owner").param("id", courseId).param("owner", ownerId)
                .query(Boolean.class).optional().orElse(false);
    }
}
