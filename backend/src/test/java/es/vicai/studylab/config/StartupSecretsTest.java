package es.vicai.studylab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.vicai.studylab.StudyLabApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * AC-11 y AC-18: si falta un secreto obligatorio la app no arranca, y el error nombra la propiedad
 * (nunca su valor). Estos tests arrancan la aplicación a mano, sin servidor web.
 */
class StartupSecretsTest {

    private static final String[] VALID_SMTP = {
            "--app.mail.enabled=true", "--app.mail.from=noreply@test.local",
            "--spring.mail.host=localhost", "--spring.mail.port=3025",
            "--spring.mail.username=test", "--spring.mail.password=test"};

    private static ConfigurableApplicationContext start(String profile, String... extraArgs) {
        return new SpringApplicationBuilder(StudyLabApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(profile.split(","))
                .run(extraArgs);
    }

    /**
     * Sustituye los argumentos de {@code base} que tengan la misma clave que alguno de {@code overrides}.
     * Hace falta porque Spring concatena los valores de una opción repetida en la línea de comandos.
     */
    private static String[] concat(String[] base, String... overrides) {
        java.util.List<String> all = new java.util.ArrayList<>();
        for (String arg : base) {
            String key = arg.substring(0, arg.indexOf('='));
            boolean overridden = java.util.Arrays.stream(overrides).anyMatch(o -> o.startsWith(key + "="));
            if (!overridden) all.add(arg);
        }
        all.addAll(java.util.Arrays.asList(overrides));
        return all.toArray(new String[0]);
    }

    private static String fullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) sb.append(t.getMessage()).append('\n');
        return sb.toString();
    }

    @Test
    void ac11_controlCase_startsWhenEverythingIsConfigured() {
        try (ConfigurableApplicationContext ctx = start("test", VALID_SMTP)) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }

    @Test
    void ac11_failsWithoutThePublicUrlUsedInEmailLinks() {
        assertThatThrownBy(() -> start("test", concat(VALID_SMTP, "--app.public-url=")))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.public-url"));
    }

    @Test
    void ac11_failsWithABlankDatabasePassword() {
        assertThatThrownBy(() -> start("test", concat(VALID_SMTP, "--spring.datasource.password=")))
                .satisfies(e -> assertThat(fullMessage(e)).contains("spring.datasource.password"));
    }

    @Test
    void ac18_failsWhenMailIsEnabledWithoutAnSmtpHost() {
        assertThatThrownBy(() -> start("test", "--app.mail.enabled=true", "--spring.mail.host="))
                .satisfies(e -> assertThat(fullMessage(e)).contains("spring.mail.host"));
    }

    @Test
    void ac18_failsWhenMailIsEnabledWithoutCredentialsOrSender() {
        assertThatThrownBy(() -> start("test", concat(VALID_SMTP, "--spring.mail.password=")))
                .satisfies(e -> assertThat(fullMessage(e)).contains("spring.mail.password"));
        assertThatThrownBy(() -> start("test", concat(VALID_SMTP, "--app.mail.from=")))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.mail.from"));
    }

    @Test
    void ac18_theErrorNeverRevealsTheSecretValue() {
        String secret = "super-secreto-que-no-debe-salir";
        assertThatThrownBy(() -> start("test", concat(VALID_SMTP, "--spring.mail.host=", "--spring.mail.password=" + secret)))
                .satisfies(e -> assertThat(fullMessage(e))
                        .contains("spring.mail.host") // falla por la causa correcta...
                        .doesNotContain(secret)); // ...y sin volcar el valor del secreto
    }

    @Test
    void ac18_inProductionMailCannotBeDisabled() {
        // "test" aporta la conexión a la BD de pruebas; "prod" activa las reglas de producción
        assertThatThrownBy(() -> start("test,prod", "--app.mail.enabled=false"))
                .satisfies(e -> assertThat(fullMessage(e)).contains("app.mail.enabled"));
    }

    @Test
    void ac18_inDevelopmentMailCanBeDisabledWithoutSmtp() {
        try (ConfigurableApplicationContext ctx = start("test", "--app.mail.enabled=false",
                "--spring.mail.host=", "--spring.mail.username=", "--spring.mail.password=")) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }
}
