package es.vicai.studylab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.StudyLabApplication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * AC-5, AC-7, AC-10 y AC-3 (producción): configuración de IA incorrecta o incompleta impide arrancar y el error
 * nombra la propiedad o variable que falta, nunca su valor. Arrancan la aplicación a mano, sin servidor web.
 */
class AiStartupSettingsTest {

    private static final String[] VALID = {
            "--app.mail.enabled=true", "--app.mail.from=noreply@test.local", "--spring.mail.host=localhost",
            "--spring.mail.port=3025", "--spring.mail.username=test", "--spring.mail.password=test"};

    /** Una tarea con Anthropic (clave y precio correctos) para probar el camino "real" sin llamar a nadie. */
    private static final String[] REAL_OUTLINE = {
            "--app.ai.tasks.outline.provider=anthropic", "--app.ai.tasks.outline.model=modelo-de-prueba",
            "--app.ai.providers.anthropic.api-key=k-test-NO-ES-UN-SECRETO",
            "--app.ai.pricing[anthropic/modelo-de-prueba].input-per-million=3.0",
            "--app.ai.pricing[anthropic/modelo-de-prueba].output-per-million=15.0"};

    private static String[] args(String[]... groups) {
        List<String> all = new ArrayList<>();
        for (String[] group : groups) all.addAll(Arrays.asList(group));
        return all.toArray(new String[0]);
    }

    /** Sustituye los argumentos con la misma clave (Spring concatena los valores de una opción repetida). */
    private static String[] override(String[] base, String... overrides) {
        List<String> all = new ArrayList<>();
        for (String arg : base) {
            String key = arg.substring(0, arg.indexOf('='));
            if (Arrays.stream(overrides).noneMatch(o -> o.startsWith(key + "="))) all.add(arg);
        }
        all.addAll(Arrays.asList(overrides));
        return all.toArray(new String[0]);
    }

    private static ConfigurableApplicationContext start(String profiles, String... args) {
        return new SpringApplicationBuilder(StudyLabApplication.class).web(WebApplicationType.NONE)
                .profiles(profiles.split(",")).run(args);
    }

    private static String fullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) sb.append(t.getMessage()).append('\n');
        return sb.toString();
    }

    @Test
    void controlCase_theFakeConfigurationStarts() {
        try (ConfigurableApplicationContext ctx = start("test", VALID)) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }

    @Test
    void ac4_aRealProviderWithKeyAndPriceStartsWithoutCallingAnyone() {
        try (ConfigurableApplicationContext ctx = start("test", args(VALID, REAL_OUTLINE))) {
            assertThat(ctx.getBean(AiGateway.class).providerFor(AiTask.OUTLINE)).isEqualTo("anthropic");
        }
    }

    @Test
    void ac5_aTaskWithARealProviderButNoKeyFailsNamingTheVariable() {
        String[] noKey = override(args(VALID, REAL_OUTLINE), "--app.ai.providers.anthropic.api-key=");

        assertThatThrownBy(() -> start("test", noKey))
                .satisfies(e -> assertThat(fullMessage(e)).contains("ANTHROPIC_API_KEY"));
    }

    @Test
    void ac5_theErrorNeverRevealsAnyKeyValue() {
        String[] noPrice = args(VALID, new String[] {
                "--app.ai.tasks.outline.provider=openai", "--app.ai.tasks.outline.model=sin-precio",
                "--app.ai.providers.openai.api-key=sk-VALOR-QUE-NO-DEBE-SALIR"});

        assertThatThrownBy(() -> start("test", noPrice))
                .satisfies(e -> assertThat(fullMessage(e)).doesNotContain("sk-VALOR-QUE-NO-DEBE-SALIR"));
    }

    @Test
    void ac5_theKeyOfAProviderNobodyUsesIsNotRequired() {
        // Solo la tarea OUTLINE usa Anthropic: no se exige la clave de OpenAI ni la de Gemini
        try (ConfigurableApplicationContext ctx = start("test", args(VALID, REAL_OUTLINE))) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }

    @Test
    void ac7_aModelWithoutAPriceFailsNamingTheModel() {
        String[] noPrice = args(VALID, new String[] {
                "--app.ai.tasks.outline.provider=anthropic", "--app.ai.tasks.outline.model=modelo-sin-precio",
                "--app.ai.providers.anthropic.api-key=k-test"});

        assertThatThrownBy(() -> start("test", noPrice))
                .satisfies(e -> assertThat(fullMessage(e)).contains("anthropic/modelo-sin-precio"));
    }

    @Test
    void ac10_anOutputLimitAboveTheHardMaximumFailsNamingTheProperty() {
        assertThatThrownBy(() -> start("test", args(VALID, new String[] {"--app.ai.tasks.lesson.max-output-tokens=999999"})))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.ai.tasks.lesson.max-output-tokens"));
    }

    @Test
    void ac8_aRealProviderRequiresAnExplicitMonthlyLimit() {
        String[] noLimit = override(args(VALID, REAL_OUTLINE), "--app.ai.budget.monthly-limit-usd=");

        assertThatThrownBy(() -> start("test", noLimit))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.ai.budget.monthly-limit-usd"));
    }

    @Test
    void edge_anUnconfiguredTaskFailsNamingIt() {
        assertThatThrownBy(() -> start("test", args(VALID, new String[] {"--app.ai.tasks.tutor.provider="})))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.ai.tasks.tutor"));
    }

    @Test
    void edge_anUnknownProviderNameFailsNamingTheTask() {
        assertThatThrownBy(() -> start("test", args(VALID, new String[] {"--app.ai.tasks.tutor.provider=skynet"})))
                .satisfies(e -> assertThat(fullMessage(e)).contains("tutor").contains("skynet"));
    }

    @Test
    void ac14_embeddingsOnlyWorkWithOllamaOrTheFakeProvider() {
        String[] bad = override(args(VALID, REAL_OUTLINE), "--app.ai.tasks.embedding.provider=anthropic",
                "--app.ai.tasks.embedding.model=modelo-de-prueba");

        assertThatThrownBy(() -> start("test", bad))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.ai.tasks.embedding"));
    }

    @Test
    void ac14_ollamaNeedsItsBaseUrl() {
        String[] noUrl = args(VALID, new String[] {
                "--app.ai.tasks.embedding.provider=ollama", "--app.ai.tasks.embedding.model=nomic-embed-text",
                "--app.ai.pricing[ollama/nomic-embed-text].input-per-million=0",
                "--app.ai.pricing[ollama/nomic-embed-text].output-per-million=0",
                "--app.ai.providers.ollama.base-url="});

        assertThatThrownBy(() -> start("test", noUrl))
                .satisfies(e -> assertThat(fullMessage(e)).contains("OLLAMA_BASE_URL"));
    }

    @Test
    void ac5_inProductionAPaidProviderMustUseHttps() {
        String[] clearText = args(VALID, REAL_OUTLINE, new String[] {"--app.ai.providers.anthropic.base-url=http://api.anthropic.example"});

        assertThatThrownBy(() -> start("test,prod", clearText))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.ai.providers.anthropic.base-url").contains("https://")
                        .doesNotContain("k-test-NO-ES-UN-SECRETO"));
    }

    @Test
    void ac3_theFakeProviderIsNotAllowedInProduction() {
        assertThatThrownBy(() -> start("test,prod", VALID))
                .satisfies(e -> assertThat(fullMessage(e)).contains("fake").contains("prod"));
    }
}
