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
 * F12 AC-1: la tarea IMAGE es opcional. Sin proveedor de imagen configurado las ilustraciones quedan desactivadas y la aplicación arranca igual; con un modelo
 * de imagen configurado exige su precio por imagen y su clave. El error nombra la propiedad o variable, nunca su valor.
 */
class ImageStartupSettingsTest {

    private static final String[] VALID = {
            "--app.mail.enabled=true", "--app.mail.from=noreply@test.local", "--spring.mail.host=localhost",
            "--spring.mail.port=3025", "--spring.mail.username=test", "--spring.mail.password=test"};

    private static String[] args(String... extra) {
        List<String> all = new ArrayList<>(Arrays.asList(VALID));
        all.addAll(Arrays.asList(extra));
        return all.toArray(new String[0]);
    }

    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(StudyLabApplication.class).web(WebApplicationType.NONE).profiles("test").run(args);
    }

    private static String fullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) sb.append(t.getMessage()).append('\n');
        return sb.toString();
    }

    @Test
    void ac1_withoutAnImageProviderTheApplicationStartsAndIllustrationsAreOff() {
        try (ConfigurableApplicationContext ctx = start(args("--app.ai.tasks.image.provider=", "--app.ai.tasks.image.model="))) {
            assertThat(ctx.isRunning()).isTrue();
            assertThat(ctx.getBean(AiGateway.class).imageEnabled()).isFalse();
        }
    }

    @Test
    void ac1_withTheFakeImageProviderTheyAreOn() {
        try (ConfigurableApplicationContext ctx = start(args())) {
            assertThat(ctx.getBean(AiGateway.class).imageEnabled()).isTrue();
        }
    }

    @Test
    void ac1_anImageModelWithoutItsPricePerImageFailsNamingTheModel() {
        String[] noPrice = args("--app.ai.tasks.image.provider=fake", "--app.ai.tasks.image.model=fake-sin-precio");

        assertThatThrownBy(() -> start(noPrice)).satisfies(e -> assertThat(fullMessage(e)).contains("fake/fake-sin-precio").contains("per-image"));
    }

    @Test
    void ac1_aRealImageProviderWithoutItsKeyFailsNamingTheVariableAndNeverTheValue() {
        String[] noKey = args("--app.ai.tasks.image.provider=openai", "--app.ai.tasks.image.model=gpt-image-de-prueba",
                "--app.ai.image-pricing[openai/gpt-image-de-prueba].per-image=0.01", "--app.ai.providers.openai.api-key=");

        assertThatThrownBy(() -> start(noKey)).satisfies(e -> assertThat(fullMessage(e)).contains("OPENAI_API_KEY"));
    }

    @Test
    void ac1_aProviderThatCannotDrawFailsAtStartupAndNotWhenSomeoneUsesIt() {
        String[] anthropic = args("--app.ai.tasks.image.provider=anthropic", "--app.ai.tasks.image.model=claude-sin-imagen",
                "--app.ai.image-pricing[anthropic/claude-sin-imagen].per-image=0.01", "--app.ai.providers.anthropic.api-key=k-test");

        assertThatThrownBy(() -> start(anthropic)).satisfies(e -> assertThat(fullMessage(e)).contains("anthropic").contains("imagen"));
    }

    @Test
    void ac1_aRealImageProviderWithKeyAndPriceStartsWithoutCallingAnyone() {
        String[] ok = args("--app.ai.tasks.image.provider=openai", "--app.ai.tasks.image.model=gpt-image-de-prueba",
                "--app.ai.image-pricing[openai/gpt-image-de-prueba].per-image=0.011", "--app.ai.providers.openai.api-key=k-test-NO-ES-UN-SECRETO");

        try (ConfigurableApplicationContext ctx = start(ok)) {
            assertThat(ctx.getBean(AiGateway.class).providerFor(AiTask.IMAGE)).isEqualTo("openai");
        }
    }
}
