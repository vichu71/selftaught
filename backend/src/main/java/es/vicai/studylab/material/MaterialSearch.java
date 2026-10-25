package es.vicai.studylab.material;

import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Recuperación de fragmentos del material de UN usuario (AC-12, ADR-33). Búsqueda exacta por coseno, siempre con {@code owner_id} en
 * el SQL: un documento ajeno que se cuele en el filtro no devuelve nada. El texto devuelto es dato, nunca instrucción (ADR-34).
 */
@Service
public class MaterialSearch {

    public static final int MAX_K = 20;
    public static final int MAX_QUERY_CHARS = 2000;

    private final JdbcClient jdbc;
    private final AiGateway gateway;
    private final AiProperties ai;

    public MaterialSearch(JdbcClient jdbc, AiGateway gateway, AiProperties ai) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.ai = ai;
    }

    /**
     * @param documentIds si no es {@code null}, solo estos documentos (los que no sean del usuario se ignoran); vacío = ninguno
     * @return fragmentos por similitud descendente; vacío si el usuario no tiene material listo o ya no está activo
     */
    public List<Fragment> search(UUID userId, String query, int k, Collection<UUID> documentIds) {
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("La consulta no puede estar vacía");
        if (query.length() > MAX_QUERY_CHARS) throw new IllegalArgumentException("La consulta es demasiado larga");
        if (k < 1 || k > MAX_K) throw new IllegalArgumentException("k debe estar entre 1 y " + MAX_K);
        Set<UUID> filter = documentIds == null ? null : new LinkedHashSet<>(documentIds);
        if (filter != null && filter.isEmpty()) return List.of();

        String model = ai.task(AiTask.EMBEDDING).model().trim();
        if (!isActive(userId) || !hasIndexedMaterial(userId, model)) return List.of();

        float[] vector = gateway.embed(userId, List.of(query)).vectors().get(0);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("owner", userId).addValue("model", model)
                .addValue("query", MaterialRepository.toVectorLiteral(vector)).addValue("k", k);
        if (filter != null) params.addValue("ids", new ArrayList<>(filter));

        String sql = filter == null ? SEARCH : SEARCH_IN_DOCUMENTS;
        return jdbc.sql(sql).params(params.getValues())
                .query((rs, row) -> new Fragment(rs.getObject("document_id", UUID.class), rs.getString("display_name"),
                        rs.getObject("page", Integer.class), rs.getInt("start_offset"), rs.getString("text"), rs.getDouble("score")))
                .list();
    }

    private static final String SEARCH = """
            SELECT c.text, c.page, c.start_offset, d.id AS document_id, d.display_name,
                   1 - (c.embedding <=> CAST(:query AS vector)) AS score
            FROM source_chunk c
            JOIN source_document d ON d.id = c.document_id AND d.owner_id = c.owner_id
            WHERE c.owner_id = :owner AND d.status = 'READY' AND c.embedding_model = :model
            ORDER BY c.embedding <=> CAST(:query AS vector) LIMIT :k""";

    private static final String SEARCH_IN_DOCUMENTS = """
            SELECT c.text, c.page, c.start_offset, d.id AS document_id, d.display_name,
                   1 - (c.embedding <=> CAST(:query AS vector)) AS score
            FROM source_chunk c
            JOIN source_document d ON d.id = c.document_id AND d.owner_id = c.owner_id
            WHERE c.owner_id = :owner AND d.status = 'READY' AND c.embedding_model = :model AND d.id IN (:ids)
            ORDER BY c.embedding <=> CAST(:query AS vector) LIMIT :k""";

    private boolean isActive(UUID userId) {
        return jdbc.sql("SELECT status FROM app_user WHERE id = :id").param("id", userId).query(String.class).optional()
                .map("ACTIVE"::equals).orElse(false);
    }

    private boolean hasIndexedMaterial(UUID userId, String model) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM source_chunk WHERE owner_id = :owner AND embedding_model = :model)")
                .param("owner", userId).param("model", model).query(Boolean.class).single();
    }
}
