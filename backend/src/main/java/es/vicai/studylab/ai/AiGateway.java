package es.vicai.studylab.ai;

import es.vicai.studylab.ai.benchmark.ModelRef;
import es.vicai.studylab.ai.budget.BudgetService;
import es.vicai.studylab.ai.budget.UsageRepository;
import es.vicai.studylab.ai.prompt.AssembledPrompt;
import es.vicai.studylab.ai.prompt.PromptAssembler;
import es.vicai.studylab.ai.prompt.TemplateNotFoundException;
import es.vicai.studylab.ai.provider.AiProvider;
import es.vicai.studylab.ai.provider.ChatCall;
import es.vicai.studylab.ai.provider.ChatResult;
import es.vicai.studylab.ai.provider.EmbedCall;
import es.vicai.studylab.ai.provider.EmbedResult;
import es.vicai.studylab.ai.provider.ImageCall;
import es.vicai.studylab.ai.provider.ImageResult;
import es.vicai.studylab.ai.provider.ProviderErrorKind;
import es.vicai.studylab.ai.provider.ProviderException;
import es.vicai.studylab.ai.provider.ProviderRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Único punto de entrada a los modelos de IA. Las features piden una tarea y no saben qué proveedor hay detrás. Cada intento
 * queda registrado (sin contenido), los reintentos son acotados y solo ante errores transitorios, y el tope de gasto se vigila.
 * Los errores que salen son genéricos: nunca el mensaje del proveedor ni datos del usuario (tampoco en los logs).
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /** Una llamada al proveedor que puede lanzar ProviderException. */
    @FunctionalInterface
    private interface ProviderCall<R> {
        R run();
    }

    private record Ctx(UUID requestId, UUID userId, UUID courseId, String source, AiTask task, String provider, String model) {}

    private record Attempt<R>(R result, int number, long durationMs) {}

    private final AiProperties props;
    private final AiImageProperties imageProps;
    private final ProviderRegistry providers;
    private final PromptAssembler assembler;
    private final UsageRepository usage;
    private final BudgetService budget;
    private final JdbcClient jdbc;

    public AiGateway(AiProperties props, AiImageProperties imageProps, ProviderRegistry providers, PromptAssembler assembler, UsageRepository usage,
                     BudgetService budget, JdbcClient jdbc) {
        this.props = props;
        this.imageProps = imageProps;
        this.providers = providers;
        this.assembler = assembler;
        this.usage = usage;
        this.budget = budget;
        this.jdbc = jdbc;
    }

    /** Proveedor configurado para una tarea (para diagnóstico y tests). */
    public String providerFor(AiTask task) {
        return props.task(task).providerName();
    }

    public AiResponse complete(AiRequest request) {
        AiProperties.TaskConfig task = props.task(request.task());
        return doComplete(request, task.providerName(), task.model().trim());
    }

    /** Para el benchmark: fuerza proveedor y modelo, sin usuario ni vigilancia del tope (el run tiene el suyo). */
    public AiResponse completeWith(ModelRef model, AiRequest request) {
        return doComplete(request, model.provider(), model.model());
    }

    // ---------- chat ----------

    private AiResponse doComplete(AiRequest request, String provider, String model) {
        UUID requestId = UUID.randomUUID();
        boolean bench = request.isBenchmark();
        Ctx ctx = new Ctx(requestId, request.userId(), request.courseId(), bench ? "BENCHMARK" : "APP", request.task(), provider, model);

        if (!bench) requireActiveUser(ctx);
        AssembledPrompt prompt = assemble(ctx, request);
        if (prompt.instructions().length() + prompt.dataMessage().length() > props.maxInputChars()) {
            throw blocked(ctx, AiErrorKind.INPUT_TOO_LARGE);
        }

        AiProperties.Price price = priceOf(ctx);
        int maxOutput = maxOutput(request);
        if (!bench) {
            BigDecimal worstCase = cost(price, estimateTokens(prompt.instructions().length() + prompt.dataMessage().length()), maxOutput);
            try {
                budget.checkBeforeCall(ctx.userId(), worstCase);
            } catch (AiException e) {
                usage.insert(row(ctx, 1, 0, 0, false, BigDecimal.ZERO, "BLOCKED", e.kind().name(), null, 0));
                throw e;
            }
        }

        AiProvider adapter = providers.get(provider);
        Duration timeout = props.tasks().get(request.task()) == null ? Duration.ofMinutes(3) : props.tasks().get(request.task()).timeout();
        ChatCall call = new ChatCall(model, prompt.instructions(), prompt.dataMessage(), maxOutput, timeout);

        Attempt<ChatResult> attempt = withRetries(ctx, () -> adapter.chat(call));
        ChatResult result = attempt.result();
        int in = result.tokensEstimated() ? estimateTokens(prompt.instructions().length() + prompt.dataMessage().length()) : result.inputTokens();
        int out = result.tokensEstimated() ? estimateTokens(result.text().length()) : result.outputTokens();
        BigDecimal cost = cost(price, in, out);

        if (result.text().isBlank() && result.finish() != FinishReason.LENGTH) {
            // Respuesta vacía sin haber agotado el límite: no sirve a la feature, pero los tokens ya están cobrados y se contabilizan
            usage.insert(row(ctx, attempt.number(), in, out, result.tokensEstimated(), cost, "ERROR", ProviderErrorKind.INVALID_RESPONSE.name(),
                    result.finish().name(), attempt.durationMs()));
            if (!bench) budget.afterCall(ctx.userId());
            else budget.afterCall(null);
            throw AiException.billed(AiErrorKind.INVALID_RESPONSE, cost);
        }
        usage.insert(row(ctx, attempt.number(), in, out, result.tokensEstimated(), cost, "OK", null, result.finish().name(), attempt.durationMs()));
        log.debug("IA {} {}/{} OK (intento {}, {} ms)", ctx.task(), provider, model, attempt.number(), attempt.durationMs());
        if (!bench) budget.afterCall(ctx.userId());
        else budget.afterCall(null);
        return new AiResponse(result.text(), in, out, result.tokensEstimated(), cost, result.finish(), provider, model, requestId, attempt.durationMs());
    }

    // ---------- embeddings ----------

    public EmbeddingResult embed(UUID userId, List<String> texts) {
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        AiProperties.TaskConfig task = props.task(AiTask.EMBEDDING);
        Ctx ctx = new Ctx(UUID.randomUUID(), userId, null, "APP", AiTask.EMBEDDING, task.providerName(), task.model().trim());

        requireActiveUser(ctx);
        long chars = texts.stream().mapToLong(String::length).sum();
        if (chars > props.maxInputChars()) throw blocked(ctx, AiErrorKind.INPUT_TOO_LARGE);

        AiProperties.Price price = priceOf(ctx);
        AiProvider adapter = providers.get(ctx.provider());
        EmbedCall call = new EmbedCall(ctx.model(), List.copyOf(texts), task.timeout());

        Attempt<EmbedResult> attempt = withRetries(ctx, () -> adapter.embed(call));
        EmbedResult result = attempt.result();

        int expected = props.embedding().dimensions();
        if (result.vectors().size() != texts.size() || result.vectors().stream().anyMatch(v -> v.length != expected)) {
            usage.insert(row(ctx, attempt.number(), 0, 0, false, BigDecimal.ZERO, "ERROR", ProviderErrorKind.INVALID_RESPONSE.name(), null, attempt.durationMs()));
            throw AiException.of(AiErrorKind.INVALID_RESPONSE);
        }
        boolean estimated = result.inputTokens() <= 0;
        int in = estimated ? estimateTokens((int) chars) : result.inputTokens();
        usage.insert(row(ctx, attempt.number(), in, 0, estimated, cost(price, in, 0), "OK", null, null, attempt.durationMs()));
        return new EmbeddingResult(result.vectors(), in);
    }

    // ---------- imágenes (F12, ADR-78) ----------

    /** Hay proveedor y modelo de imagen configurados; sin ellos las ilustraciones quedan desactivadas. */
    public boolean imageEnabled() {
        AiProperties.TaskConfig task = props.tasks().get(AiTask.IMAGE);
        return task != null && !task.providerName().isEmpty() && task.model() != null && !task.model().isBlank();
    }

    /** Precio por imagen del modelo configurado (para ofrecerlo al usuario y para calcular el peor caso). */
    public BigDecimal imagePriceUsd() {
        if (!imageEnabled()) throw AiException.of(AiErrorKind.CONFIG);
        AiProperties.TaskConfig task = props.task(AiTask.IMAGE);
        return imageProps.priceOf(task.providerName(), task.model().trim());
    }

    /** Una imagen para una feature, a nombre de un usuario, con su tope y sus avisos de gasto. */
    public AiImageResponse generateImage(AiImageRequest request) {
        if (!imageEnabled()) throw AiException.of(AiErrorKind.CONFIG);
        AiProperties.TaskConfig task = props.task(AiTask.IMAGE);
        return doImage(request, task.providerName(), task.model().trim(), task.timeout());
    }

    /** Para el benchmark de imágenes: fuerza proveedor y modelo, sin usuario ni vigilancia del tope (el run tiene el suyo). */
    public AiImageResponse generateImageWith(ModelRef model, AiImageRequest request) {
        AiProperties.TaskConfig task = props.tasks().get(AiTask.IMAGE);
        return doImage(request, model.provider(), model.model(), task == null || task.timeout() == null ? Duration.ofMinutes(2) : task.timeout());
    }

    private AiImageResponse doImage(AiImageRequest request, String provider, String model, Duration timeout) {
        UUID requestId = UUID.randomUUID();
        boolean bench = request.isBenchmark();
        Ctx ctx = new Ctx(requestId, request.userId(), request.courseId(), bench ? "BENCHMARK" : "APP", AiTask.IMAGE, provider, model);

        if (!bench) requireActiveUser(ctx);
        BigDecimal price;
        try {
            price = imageProps.priceOf(provider, model);
        } catch (AiException e) {
            throw blocked(ctx, AiErrorKind.CONFIG);
        }
        if (!bench) {
            try {
                budget.checkBeforeCall(ctx.userId(), price);
            } catch (AiException e) {
                usage.insert(row(ctx, 1, 0, 0, false, BigDecimal.ZERO, "BLOCKED", e.kind().name(), null, 0));
                throw e;
            }
        }

        AiProvider adapter = providers.get(provider);
        ImageCall call = new ImageCall(model, request.prompt(), imageProps.imageSize(), imageProps.imageQuality(), timeout);
        Attempt<ImageResult> attempt = withRetries(ctx, () -> adapter.image(call));
        ImageResult result = attempt.result();

        if (result == null || result.bytes() == null || result.bytes().length == 0) {
            // Respuesta vacía de una llamada que el proveedor ya cobró: no sirve a la feature, pero el coste se contabiliza
            usage.insert(row(ctx, attempt.number(), 0, 0, false, price, "ERROR", ProviderErrorKind.INVALID_RESPONSE.name(), null, attempt.durationMs()));
            budget.afterCall(bench ? null : ctx.userId());
            throw AiException.billed(AiErrorKind.INVALID_RESPONSE, price);
        }
        usage.insert(row(ctx, attempt.number(), 0, 0, false, price, "OK", null, null, attempt.durationMs()));
        log.debug("IA {} {}/{} OK (intento {}, {} ms)", ctx.task(), provider, model, attempt.number(), attempt.durationMs());
        budget.afterCall(bench ? null : ctx.userId());
        return new AiImageResponse(result.bytes(), result.mime() == null ? "image/png" : result.mime(), price, provider, model, requestId, attempt.durationMs());
    }

    // ---------- reintentos acotados (ADR-23) ----------

    private <R> Attempt<R> withRetries(Ctx ctx, ProviderCall<R> call) {
        int maxAttempts = 1 + props.retry().maxRetries();
        for (int n = 1; ; n++) {
            long start = System.nanoTime();
            try {
                R result = call.run();
                return new Attempt<>(result, n, elapsedMs(start));
            } catch (ProviderException e) {
                usage.insert(row(ctx, n, 0, 0, false, BigDecimal.ZERO, "ERROR", e.kind().name(), null, elapsedMs(start)));
                // Se registra solo el tipo de error: el mensaje del proveedor puede repetir el prompt
                if (e.retryable() && n < maxAttempts) {
                    log.warn("IA {} {}/{}: reintento {} de {} ({})", ctx.task(), ctx.provider(), ctx.model(), n, maxAttempts - 1, e.kind());
                    sleep(props.retry().backoff().multipliedBy(1L << (n - 1)));
                    continue;
                }
                log.warn("IA {} {}/{} falla: {}", ctx.task(), ctx.provider(), ctx.model(), e.kind());
                throw AiException.of(toAiKind(e.kind()));
            } catch (RuntimeException e) {
                usage.insert(row(ctx, n, 0, 0, false, BigDecimal.ZERO, "ERROR", ProviderErrorKind.SERVER_ERROR.name(), null, elapsedMs(start)));
                log.error("IA {} {}/{}: fallo inesperado ({})", ctx.task(), ctx.provider(), ctx.model(), e.getClass().getSimpleName());
                throw AiException.of(AiErrorKind.PROVIDER_UNAVAILABLE);
            }
        }
    }

    private static AiErrorKind toAiKind(ProviderErrorKind kind) {
        return switch (kind) {
            case QUOTA_EXCEEDED -> AiErrorKind.PROVIDER_QUOTA;
            case AUTH -> AiErrorKind.PROVIDER_AUTH;
            case BAD_REQUEST, MODEL_NOT_FOUND -> AiErrorKind.PROVIDER_REJECTED;
            case INVALID_RESPONSE -> AiErrorKind.INVALID_RESPONSE;
            case MODERATION -> AiErrorKind.MODERATED;
            case TIMEOUT, RATE_LIMITED, SERVER_ERROR, UNAVAILABLE -> AiErrorKind.PROVIDER_UNAVAILABLE;
        };
    }

    // ---------- piezas ----------

    private void requireActiveUser(Ctx ctx) {
        String status = jdbc.sql("SELECT status FROM app_user WHERE id = :id").param("id", ctx.userId())
                .query(String.class).optional().orElse(null);
        if (!"ACTIVE".equals(status)) throw blocked(ctx, AiErrorKind.USER_NOT_ALLOWED);
    }

    private AssembledPrompt assemble(Ctx ctx, AiRequest request) {
        try {
            return assembler.assemble(request.template(), request.data());
        } catch (TemplateNotFoundException | IllegalArgumentException e) {
            throw blocked(ctx, AiErrorKind.CONFIG);
        }
    }

    private AiProperties.Price priceOf(Ctx ctx) {
        try {
            return props.price(ctx.provider(), ctx.model());
        } catch (AiException e) {
            throw blocked(ctx, AiErrorKind.CONFIG);
        }
    }

    /** El límite de la tarea, que una petición puede bajar pero no subir, y nunca por encima del máximo duro. */
    private int maxOutput(AiRequest request) {
        AiProperties.TaskConfig task = props.tasks().get(request.task());
        int configured = task == null || task.maxOutputTokens() == null ? props.hardMaxOutputTokens() : task.maxOutputTokens();
        int wanted = request.maxOutputTokens() == null ? configured : Math.min(request.maxOutputTokens(), configured);
        return Math.max(1, Math.min(wanted, props.hardMaxOutputTokens()));
    }

    private AiException blocked(Ctx ctx, AiErrorKind kind) {
        usage.insert(row(ctx, 1, 0, 0, false, BigDecimal.ZERO, "BLOCKED", kind.name(), null, 0));
        return AiException.of(kind);
    }

    private static UsageRepository.Row row(Ctx ctx, int attempt, int in, int out, boolean estimated, BigDecimal cost, String status,
                                           String errorKind, String finish, long durationMs) {
        return new UsageRepository.Row(ctx.requestId(), attempt, ctx.userId(), ctx.courseId(), ctx.source(), ctx.task(), ctx.provider(),
                ctx.model(), in, out, estimated, cost, status, errorKind, finish, durationMs);
    }

    /** USD = (entrada · precio_entrada + salida · precio_salida) / 1.000.000, a 6 decimales. */
    static BigDecimal cost(AiProperties.Price price, int inputTokens, int outputTokens) {
        return price.inputPerMillion().multiply(BigDecimal.valueOf(inputTokens))
                .add(price.outputPerMillion().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    /** Estimación gruesa (4 caracteres por token) para cuando el proveedor no informa del uso. */
    static int estimateTokens(int chars) {
        return Math.max(1, (chars + 3) / 4);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
