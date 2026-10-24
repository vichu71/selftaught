package es.vicai.studylab.ai;

import es.vicai.studylab.ai.prompt.PromptAssembler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que una feature pide a la IA. Inmutable. Por diseño NO hay forma de poner texto del usuario en las instrucciones:
 * la plantilla se elige por id (fichero fijo) y todo lo demás entra solo como dato etiquetado (ADR-26).
 */
public final class AiRequest {

    private final AiTask task;
    private final UUID userId;
    private final String template;
    private final Map<String, String> data;
    private final UUID courseId;
    private final Integer maxOutputTokens;
    private final boolean benchmark;

    private AiRequest(AiTask task, UUID userId, String template, Map<String, String> data, UUID courseId,
                      Integer maxOutputTokens, boolean benchmark) {
        this.task = task;
        this.userId = userId;
        this.template = template;
        this.data = data;
        this.courseId = courseId;
        this.maxOutputTokens = maxOutputTokens;
        this.benchmark = benchmark;
    }

    /** Petición de un usuario. El usuario es obligatorio: toda llamada se registra a su nombre. */
    public static AiRequest of(AiTask task, UUID userId, String template) {
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        return create(task, userId, template, false);
    }

    /** Petición del benchmark: sin usuario, se registra como BENCHMARK. */
    public static AiRequest benchmark(AiTask task, String template) {
        return create(task, null, template, true);
    }

    private static AiRequest create(AiTask task, UUID userId, String template, boolean benchmark) {
        if (task == null) throw new IllegalArgumentException("La tarea es obligatoria");
        if (template == null || template.isBlank()) throw new IllegalArgumentException("La plantilla es obligatoria");
        return new AiRequest(task, userId, template, Map.of(), null, null, benchmark);
    }

    /** Añade un dato etiquetado (la etiqueta solo admite a-z, 0-9 y _). */
    public AiRequest data(String label, String text) {
        if (label == null || !PromptAssembler.LABEL.matcher(label).matches()) {
            throw new IllegalArgumentException("Etiqueta de datos no válida");
        }
        Map<String, String> copy = new LinkedHashMap<>(data);
        copy.put(label, text == null ? "" : text);
        return new AiRequest(task, userId, template, Collections.unmodifiableMap(copy), courseId, maxOutputTokens, benchmark);
    }

    public AiRequest course(UUID courseId) {
        return new AiRequest(task, userId, template, data, courseId, maxOutputTokens, benchmark);
    }

    /** Puede bajar el límite de salida de la tarea, nunca subirlo. */
    public AiRequest maxOutputTokens(int tokens) {
        return new AiRequest(task, userId, template, data, courseId, tokens, benchmark);
    }

    public AiTask task() {
        return task;
    }

    public UUID userId() {
        return userId;
    }

    public String template() {
        return template;
    }

    public Map<String, String> data() {
        return data;
    }

    public UUID courseId() {
        return courseId;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    public boolean isBenchmark() {
        return benchmark;
    }

    @Override
    public String toString() {
        return "AiRequest[task=" + task + ", template=" + template + "]"; // nunca el contenido de los datos
    }
}
