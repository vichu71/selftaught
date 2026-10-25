package es.vicai.studylab.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Base de los adaptadores HTTP. Reglas comunes (ADR-22, ADR-27): las credenciales viajan siempre en cabeceras y nunca en la
 * URL; el cuerpo de un error del proveedor NO se copia a ninguna excepción (puede repetir el prompt); el estado HTTP, sí.
 */
abstract class HttpProvider implements AiProvider {

    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    /** Respuesta ya leída. */
    protected record Raw(int status, HttpHeaders headers, String body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    protected final ObjectMapper json = new ObjectMapper();
    private final RestClient client;

    protected HttpProvider(RestClient.Builder builder, String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    protected Raw post(String uri, Map<String, String> headers, Object body, Object... uriVariables) {
        try {
            RestClient.RequestBodySpec spec = client.method(HttpMethod.POST).uri(uri, uriVariables)
                    .contentType(MediaType.APPLICATION_JSON);
            headers.forEach(spec::header);
            spec.body(json.writeValueAsString(body));
            return spec.exchange((request, response) -> new Raw(response.getStatusCode().value(), response.getHeaders(), read(response.getBody())), true);
        } catch (ResourceAccessException e) {
            throw network(e);
        } catch (JsonProcessingException e) {
            throw new ProviderException(ProviderErrorKind.BAD_REQUEST, "No se pudo construir la petición");
        }
    }

    protected Raw get(String uri) {
        try {
            return client.get().uri(uri)
                    .exchange((request, response) -> new Raw(response.getStatusCode().value(), response.getHeaders(), read(response.getBody())), true);
        } catch (ResourceAccessException e) {
            throw network(e);
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        byte[] bytes = in.readNBytes(MAX_BODY_BYTES);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Un timeout es transitorio; cualquier otro fallo de red (conexión rechazada, DNS…) también, como UNAVAILABLE. */
    private static ProviderException network(ResourceAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return new ProviderException(ProviderErrorKind.TIMEOUT, "El proveedor no respondió a tiempo");
            }
        }
        return new ProviderException(ProviderErrorKind.UNAVAILABLE, "No se pudo conectar con el proveedor");
    }

    /** Lee un JSON de una respuesta correcta; un cuerpo que no es JSON es una respuesta inválida. */
    protected JsonNode parse(Raw raw) {
        try {
            JsonNode root = json.readTree(raw.body());
            if (root == null || !root.isObject()) throw invalid();
            return root;
        } catch (JsonProcessingException e) {
            throw invalid();
        }
    }

    /** Mensaje de error del proveedor en minúsculas, SOLO para clasificar: nunca sale de esta clase. */
    protected String errorMessage(Raw raw, String... path) {
        try {
            JsonNode node = json.readTree(raw.body());
            for (String key : path) node = node == null ? null : node.get(key);
            return node == null || !node.isTextual() ? "" : node.asText().toLowerCase();
        } catch (IOException e) {
            return "";
        }
    }

    protected static ProviderException invalid() {
        return new ProviderException(ProviderErrorKind.INVALID_RESPONSE, "Respuesta del proveedor no válida");
    }

    protected static ProviderException failure(ProviderErrorKind kind, int status) {
        return new ProviderException(kind, "El proveedor respondió HTTP " + status);
    }

    /** Clasificación por estado HTTP, común a todos; cada adaptador la afina con lo que sabe de su API. */
    protected static ProviderException byStatus(int status) {
        if (status == 401 || status == 403) return failure(ProviderErrorKind.AUTH, status);
        if (status == 404) return failure(ProviderErrorKind.MODEL_NOT_FOUND, status);
        if (status == 408 || status == 504) return failure(ProviderErrorKind.TIMEOUT, status);
        if (status == 429) return failure(ProviderErrorKind.RATE_LIMITED, status);
        if (status >= 500) return failure(ProviderErrorKind.SERVER_ERROR, status);
        return failure(ProviderErrorKind.BAD_REQUEST, status);
    }

    protected static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    @Override
    public EmbedResult embed(EmbedCall call) {
        throw new UnsupportedOperationException(name() + " no genera embeddings en esta versión");
    }

    @Override
    public ChatResult chat(ChatCall call) {
        throw new UnsupportedOperationException(name() + " no se usa para chat en esta versión");
    }

    /** Nunca incluye credenciales. */
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
