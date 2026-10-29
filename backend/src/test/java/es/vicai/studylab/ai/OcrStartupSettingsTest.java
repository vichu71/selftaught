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
 * F19 AC-5: la tarea OCR es opcional como IMAGE. Sin proveedor de lectura las fotos quedan desactivadas y la aplicación arranca igual; con un modelo configurado
 * exige su precio por tokens y su clave. El error nombra la propiedad o variable, nunca su valor.
 */
class OcrStartupSettingsTest {

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
    void ac5_withoutAnOcrProviderTheApplicationStartsAndPhotosAreOff() {
        try (ConfigurableApplicationContext ctx = start(args("--app.ai.tasks.ocr.provider=", "--app.ai.tasks.ocr.model="))) {
            assertThat(ctx.isRunning()).isTrue();
            assertThat(ctx.getBean(AiGateway.class).readEnabled()).isFalse();
        }
    }

    @Test
    void ac5_withTheFakeVisionProviderTheyAreOn() {
        try (ConfigurableApplicationContext ctx = start(args())) {
            assertThat(ctx.getBean(AiGateway.class).readEnabled()).isTrue();
        }
    }

    @Test
    void ac5_aReadingModelWithoutItsPriceFailsNamingTheModel() {
        String[] noPrice = args("--app.ai.tasks.ocr.provider=fake", "--app.ai.tasks.ocr.model=fake-sin-precio");

        assertThatThrownBy(() -> start(noPrice)).satisfies(e -> assertThat(fullMessage(e)).contains("fake/fake-sin-precio"));
    }

    @Test
    void ac5_aProviderThatCannotReadImagesIsAConfigurationErrorAtStartup() {
        String[] ollama = args("--app.ai.tasks.ocr.provider=ollama", "--app.ai.tasks.ocr.model=llava");

        assertThatThrownBy(() -> start(ollama)).satisfies(e -> assertThat(fullMessage(e)).containsIgnoringCase("ocr"));
    }
}
