package es.vicai.studylab.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprueba la configuración de IA antes de crear ningún bean. Devuelve los problemas encontrados; los mensajes nombran la
 * propiedad o variable que falta y nunca incluyen sus valores (una clave jamás se imprime).
 */
public final class AiSettingsValidator {

    public static final Set<String> KNOWN_PROVIDERS = Set.of("fake", "anthropic", "openai", "gemini", "ollama");
    private static final Set<String> CHAT_PROVIDERS = Set.of("fake", "anthropic", "openai", "gemini");
    private static final Set<String> EMBEDDING_PROVIDERS = Set.of("fake", "ollama");
    public static final Set<String> IMAGE_PROVIDERS = Set.of("fake", "openai", "gemini");

    private AiSettingsValidator() {}

    /** Variable de entorno que da la clave (o la URL, en Ollama) de cada proveedor. */
    public static String envVarOf(String provider) {
        return switch (provider) {
            case "anthropic" -> "ANTHROPIC_API_KEY";
            case "openai" -> "OPENAI_API_KEY";
            case "gemini" -> "GEMINI_API_KEY";
            case "ollama" -> "OLLAMA_BASE_URL";
            default -> provider;
        };
    }

    public static List<String> validate(AiProperties props, boolean production, boolean benchmarkProfile) {
        return validate(props, AiImageProperties.defaults(), production, benchmarkProfile);
    }

    public static List<String> validate(AiProperties props, AiImageProperties imageProps, boolean production, boolean benchmarkProfile) {
        List<String> problems = new ArrayList<>();
        Set<String> realProvidersInUse = new LinkedHashSet<>();

        if (props.hardMaxOutputTokens() <= 0) problems.add("app.ai.hard-max-output-tokens (debe ser mayor que 0)");
        if (props.maxInputChars() <= 0) problems.add("app.ai.max-input-chars (debe ser mayor que 0)");
        if (props.retry().maxRetries() < 0 || props.retry().maxRetries() > 5) problems.add("app.ai.retry.max-retries (entre 0 y 5)");
        if (props.embedding().dimensions() <= 0) problems.add("app.ai.embedding.dimensions (debe ser mayor que 0)");
        if (props.budget().warnAtPercent() < 1 || props.budget().warnAtPercent() > 100) {
            problems.add("app.ai.budget.warn-at-percent (entre 1 y 100)");
        }
        if (props.budget().monthlyLimitUsd() != null && props.budget().monthlyLimitUsd().signum() <= 0) {
            problems.add("app.ai.budget.monthly-limit-usd (debe ser mayor que 0)");
        }

        if (props.budget().userLimitMinorUsd() == null || props.budget().userLimitMinorUsd().signum() <= 0) {
            problems.add("app.ai.budget.user-limit-minor-usd (AI_USER_LIMIT_MINOR_USD, debe ser mayor que 0)");
        }
        if (props.budget().userLimitAdultUsd() == null || props.budget().userLimitAdultUsd().signum() <= 0) {
            problems.add("app.ai.budget.user-limit-adult-usd (AI_USER_LIMIT_ADULT_USD, debe ser mayor que 0)");
        }

        // En el run del benchmark las tareas no se usan: el propio runner valida su plan
        if (!benchmarkProfile) {
            for (AiTask task : AiTask.values()) {
                if (task == AiTask.IMAGE) continue; // opcional: sin proveedor, las ilustraciones quedan desactivadas (F12)
                if (task == AiTask.OCR && isUnset(props.tasks().get(task))) continue; // opcional: sin proveedor, las fotos quedan desactivadas (F19)
                validateTask(props, task, production, problems, realProvidersInUse);
            }
            validateImage(props, imageProps, production, problems, realProvidersInUse);
        }

        for (String provider : realProvidersInUse) {
            AiProperties.ProviderConfig config = props.providers().get(provider);
            if (provider.equals("ollama")) {
                if (config == null || config.baseUrl() == null || config.baseUrl().isBlank()) {
                    problems.add("OLLAMA_BASE_URL (app.ai.providers.ollama.base-url)");
                }
            } else if (config == null || !config.hasKey()) {
                problems.add(envVarOf(provider) + " (app.ai.providers." + provider + ".api-key)");
            }
        }

        // En producción la clave de un proveedor de pago nunca puede viajar en claro: la URL base debe ser https
        if (production) {
            for (String provider : realProvidersInUse) {
                AiProperties.ProviderConfig config = props.providers().get(provider);
                boolean paid = !provider.equals("ollama");
                if (paid && config != null && config.baseUrl() != null && !config.baseUrl().startsWith("https://")) {
                    problems.add("app.ai.providers." + provider + ".base-url debe empezar por https:// en producción");
                }
            }
        }

        // Un proveedor de pago exige un tope explícito: nunca se gasta sin límite conocido
        boolean pays = realProvidersInUse.stream().anyMatch(p -> !p.equals("ollama"));
        if (pays && props.budget().monthlyLimitUsd() == null) {
            problems.add("AI_MONTHLY_LIMIT_USD (app.ai.budget.monthly-limit-usd): obligatorio con un proveedor real");
        }
        return problems;
    }

    private static boolean isUnset(AiProperties.TaskConfig config) {
        return config == null || (config.providerName().isEmpty() && (config.model() == null || config.model().isBlank()));
    }

    /** La tarea de imagen (F12) es opcional; si se configura, exige proveedor que dibuje, clave, tiempo y precio por imagen. */
    private static void validateImage(AiProperties props, AiImageProperties imageProps, boolean production, List<String> problems, Set<String> realProvidersInUse) {
        String key = "app.ai.tasks.image";
        AiProperties.TaskConfig config = props.tasks().get(AiTask.IMAGE);
        String provider = config == null ? "" : config.providerName();
        String model = config == null || config.model() == null ? "" : config.model().trim();
        if (provider.isEmpty() && model.isEmpty()) return;
        if (provider.isEmpty() || model.isEmpty()) {
            problems.add(key + ".provider y " + key + ".model (APP_AI_TASKS_IMAGE_PROVIDER / _MODEL: o las dos o ninguna)");
            return;
        }
        if (!KNOWN_PROVIDERS.contains(provider)) {
            problems.add(key + ".provider: proveedor desconocido '" + provider + "'");
            return;
        }
        if (!IMAGE_PROVIDERS.contains(provider)) {
            problems.add(key + ".provider: '" + provider + "' no sirve para la tarea de imagen (válidos: " + String.join(", ", new java.util.TreeSet<>(IMAGE_PROVIDERS)) + ")");
            return;
        }
        if (provider.equals("fake")) {
            if (production) problems.add(key + ".provider=fake no está permitido en producción (perfil prod)");
        } else {
            realProvidersInUse.add(provider);
        }
        if (config.timeout() == null || config.timeout().isZero() || config.timeout().isNegative()) problems.add(key + ".timeout (debe ser positivo)");
        AiImageProperties.ImagePrice price = imageProps.imagePricing().get(provider + "/" + model);
        if (price == null || price.perImage() == null || price.perImage().signum() < 0) {
            problems.add("app.ai.image-pricing[" + provider + "/" + model + "].per-image (precio por imagen del modelo)");
        }
    }

    private static void validateTask(AiProperties props, AiTask task, boolean production, List<String> problems,
                                     Set<String> realProvidersInUse) {
        String key = "app.ai.tasks." + task.name().toLowerCase();
        AiProperties.TaskConfig config = props.tasks().get(task);
        String provider = config == null ? "" : config.providerName();
        String model = config == null || config.model() == null ? "" : config.model().trim();

        if (provider.isEmpty() || model.isEmpty()) {
            problems.add(key + ".provider y " + key + ".model (APP_AI_TASKS_" + task.name() + "_PROVIDER / _MODEL)");
            return;
        }
        if (!KNOWN_PROVIDERS.contains(provider)) {
            problems.add(key + ".provider: proveedor desconocido '" + provider + "' (válidos: " + String.join(", ", new java.util.TreeSet<>(KNOWN_PROVIDERS)) + ")");
            return;
        }
        Set<String> allowed = task == AiTask.EMBEDDING ? EMBEDDING_PROVIDERS : task == AiTask.OCR ? IMAGE_PROVIDERS : CHAT_PROVIDERS; // leer una foto: los que ven imágenes
        if (!allowed.contains(provider)) {
            problems.add(key + ".provider: '" + provider + "' no sirve para esta tarea (válidos: " + String.join(", ", new java.util.TreeSet<>(allowed)) + ")");
            return;
        }
        if (provider.equals("fake")) {
            if (production) problems.add(key + ".provider=fake no está permitido en producción (perfil prod)");
        } else {
            realProvidersInUse.add(provider);
        }

        if (task != AiTask.EMBEDDING) {
            Integer max = config.maxOutputTokens();
            if (max == null || max <= 0 || max > props.hardMaxOutputTokens()) {
                problems.add(key + ".max-output-tokens (debe estar entre 1 y el máximo duro de " + props.hardMaxOutputTokens() + ")");
            }
        }
        if (config.timeout() == null || config.timeout().isZero() || config.timeout().isNegative()) {
            problems.add(key + ".timeout (debe ser positivo)");
        }

        // Todo modelo usado necesita precio: nunca se asume coste 0 por omisión
        AiProperties.Price price = props.pricing().get(provider + "/" + model);
        BigDecimal zero = BigDecimal.ZERO;
        if (price == null || price.inputPerMillion() == null || price.outputPerMillion() == null
                || price.inputPerMillion().compareTo(zero) < 0 || price.outputPerMillion().compareTo(zero) < 0) {
            problems.add("app.ai.pricing[" + provider + "/" + model + "].input-per-million y .output-per-million (precio del modelo)");
        }
    }
}
