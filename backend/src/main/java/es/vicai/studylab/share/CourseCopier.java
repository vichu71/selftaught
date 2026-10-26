package es.vicai.studylab.share;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.vicai.studylab.safety.ContentGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * La copia de un curso compartido (F09, ADR-65): un curso nuevo y terminado de quien canjea, con el temario y el contenido de las piezas hechas, con ids nuevos y con su
 * origen y su versión. Lo que no tiene columna aquí no puede colarse: nada de material ni de fuentes, ni progreso, intentos, conversaciones, consultas, uso de IA ni datos
 * de ningún usuario. Si quien canjea es un menor, todo el texto pasa por {@link ContentGuard} antes de crear nada. Debe ejecutarse dentro de una transacción.
 */
@Component
public class CourseCopier {

    public enum Status { CREATED, BLOCKED, UNAVAILABLE }

    public record Result(Status status, UUID courseId) {}

    private record Topic(UUID id, int position, String title, String summary) {}

    private record Chapter(UUID id, UUID topicId, int position, String title, String objective) {}

    private record Source(String title, String sheet, int version) {}

    private record Piece(UUID topicId, UUID chapterId, String kind, int seq, String status, String markdown, String json) {}

    private static final Pattern CODE = Pattern.compile("(?s)```.*?```|`[^`\\n]*`");
    private static final Pattern CITATION = Pattern.compile("[ \\t]?(?<![!\\\\])\\[(\\d{1,3})\\](?![(:])");

    private final JdbcClient jdbc;
    private final ContentGuard guard;
    private final ObjectMapper json;

    public CourseCopier(JdbcClient jdbc, ContentGuard guard, ObjectMapper json) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.json = json;
    }

    public Result copy(UUID sourceCourse, UUID sourceOwner, UUID newOwner, boolean minor) {
        var course = jdbc.sql("SELECT title, sheet::text AS sheet, version FROM course WHERE id = :id AND owner_id = :owner AND status = 'READY'")
                .param("id", sourceCourse).param("owner", sourceOwner).query((rs, i) -> new Source(rs.getString("title"), rs.getString("sheet"), rs.getInt("version"))).optional();
        if (course.isEmpty()) return new Result(Status.UNAVAILABLE, null);
        List<Topic> topics = jdbc.sql("SELECT id, position, title, summary FROM course_topic WHERE course_id = :course AND owner_id = :owner ORDER BY position")
                .param("course", sourceCourse).param("owner", sourceOwner)
                .query((rs, i) -> new Topic(rs.getObject("id", UUID.class), rs.getInt("position"), rs.getString("title"), rs.getString("summary"))).list();
        List<Chapter> chapters = jdbc.sql("SELECT id, topic_id, position, title, objective FROM course_chapter WHERE course_id = :course AND owner_id = :owner ORDER BY position")
                .param("course", sourceCourse).param("owner", sourceOwner)
                .query((rs, i) -> new Chapter(rs.getObject("id", UUID.class), rs.getObject("topic_id", UUID.class), rs.getInt("position"), rs.getString("title"), rs.getString("objective"))).list();
        List<Piece> pieces = jdbc.sql("""
                SELECT topic_id, chapter_id, kind, seq, status, content_md, content_json::text AS content_json FROM course_piece
                WHERE course_id = :course AND owner_id = :owner ORDER BY seq""")
                .param("course", sourceCourse).param("owner", sourceOwner)
                .query((rs, i) -> new Piece(rs.getObject("topic_id", UUID.class), rs.getObject("chapter_id", UUID.class), rs.getString("kind"), rs.getInt("seq"),
                        rs.getString("status"), rs.getString("content_md"), rs.getString("content_json"))).list();
        if (pieces.isEmpty() || pieces.stream().anyMatch(p -> !"DONE".equals(p.status()))) return new Result(Status.UNAVAILABLE, null);

        if (minor && blocked(course.get().title(), topics, chapters, pieces)) return new Result(Status.BLOCKED, null);

        UUID newCourse = UUID.randomUUID();
        UUID intake = UUID.randomUUID();
        String sheet = withoutDocuments(course.get().sheet());
        jdbc.sql("INSERT INTO course_intake (id, owner_id, topic, status, expires_at) VALUES (:id, :owner, :topic, 'CONSUMED', now())")
                .param("id", intake).param("owner", newOwner).param("topic", topicOf(sheet, course.get().title())).update();
        jdbc.sql("""
                INSERT INTO course (id, owner_id, intake_id, title, status, sheet, version, origin_id, origin_version, completed_at)
                VALUES (:id, :owner, :intake, :title, 'READY', CAST(:sheet AS jsonb), 1, :origin, :originVersion, now())""")
                .param("id", newCourse).param("owner", newOwner).param("intake", intake).param("title", course.get().title()).param("sheet", sheet)
                .param("origin", sourceCourse).param("originVersion", course.get().version()).update();

        Map<UUID, UUID> topicIds = new HashMap<>();
        for (Topic t : topics) {
            UUID id = UUID.randomUUID();
            topicIds.put(t.id(), id);
            jdbc.sql("INSERT INTO course_topic (id, course_id, owner_id, position, title, summary) VALUES (:id, :course, :owner, :position, :title, :summary)")
                    .param("id", id).param("course", newCourse).param("owner", newOwner).param("position", t.position()).param("title", t.title()).param("summary", t.summary()).update();
        }
        Map<UUID, UUID> chapterIds = new HashMap<>();
        for (Chapter c : chapters) {
            UUID id = UUID.randomUUID();
            chapterIds.put(c.id(), id);
            jdbc.sql("INSERT INTO course_chapter (id, topic_id, course_id, owner_id, position, title, objective) VALUES (:id, :topic, :course, :owner, :position, :title, :objective)")
                    .param("id", id).param("topic", topicIds.get(c.topicId())).param("course", newCourse).param("owner", newOwner).param("position", c.position())
                    .param("title", c.title()).param("objective", c.objective()).update();
        }
        for (Piece p : pieces) {
            jdbc.sql("""
                    INSERT INTO course_piece (id, course_id, owner_id, topic_id, chapter_id, kind, seq, status, content_md, content_json, generated_at)
                    VALUES (:id, :course, :owner, :topic, :chapter, :kind, :seq, 'DONE', :md, CAST(:json AS jsonb), now())""")
                    .param("id", UUID.randomUUID()).param("course", newCourse).param("owner", newOwner).param("topic", topicIds.get(p.topicId()))
                    .param("chapter", p.chapterId() == null ? null : chapterIds.get(p.chapterId())).param("kind", p.kind()).param("seq", p.seq())
                    .param("md", p.markdown() == null ? null : stripCitations(p.markdown())).param("json", p.json()).update();
        }
        return new Result(Status.CREATED, newCourse);
    }

    /** Para un menor: cualquier texto de la copia que el filtro rechace impide crearla entera. */
    private boolean blocked(String title, List<Topic> topics, List<Chapter> chapters, List<Piece> pieces) {
        List<String> texts = new ArrayList<>();
        texts.add(title);
        topics.forEach(t -> {
            texts.add(t.title());
            texts.add(t.summary());
        });
        chapters.forEach(c -> {
            texts.add(c.title());
            texts.add(c.objective());
        });
        pieces.forEach(p -> {
            if (p.markdown() != null) texts.add(p.markdown());
            if (p.json() != null) texts.add(p.json());
        });
        return texts.stream().anyMatch(t -> guard.localCheck(t).isPresent());
    }

    /** La ficha del curso sin los documentos del original: la copia no tiene material. */
    private String withoutDocuments(String sheet) {
        try {
            JsonNode node = json.readTree(sheet);
            if (node instanceof ObjectNode object) object.set("documentIds", json.createArrayNode());
            return json.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha de curso no válida");
        }
    }

    private String topicOf(String sheet, String fallback) {
        try {
            String topic = json.readTree(sheet).path("topic").asText(fallback);
            return topic.length() > 200 ? topic.substring(0, 200) : topic;
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    /** Las marcas {@code [n]} de las fuentes ya no apuntan a nada en la copia: se quitan, salvo dentro del código. */
    static String stripCitations(String markdown) {
        StringBuilder out = new StringBuilder();
        Matcher m = CODE.matcher(markdown);
        int last = 0;
        while (m.find()) {
            out.append(CITATION.matcher(markdown.substring(last, m.start())).replaceAll(""));
            out.append(m.group());
            last = m.end();
        }
        out.append(CITATION.matcher(markdown.substring(last)).replaceAll(""));
        return out.toString();
    }
}
