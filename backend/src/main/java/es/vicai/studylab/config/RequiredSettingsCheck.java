package es.vicai.studylab.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Falla al arrancar, antes de crear ningún bean (y por tanto antes de tocar BD o red), si falta un secreto o
 * ajuste obligatorio. Se ejecuta con el entorno ya completo, también las propiedades inyectadas por los tests.
 * El mensaje nombra las propiedades que faltan; nunca incluye sus valores.
 */
public class RequiredSettingsCheck implements ApplicationListener<ApplicationPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
        List<String> missing = new ArrayList<>();
        require(env, missing, "app.public-url");
        require(env, missing, "spring.datasource.password");
        require(env, missing, "spring.flyway.password");

        boolean mailEnabled = env.getProperty("app.mail.enabled", Boolean.class, true);
        if (mailEnabled) {
            for (String key : List.of("spring.mail.host", "spring.mail.username", "spring.mail.password", "app.mail.from")) {
                require(env, missing, key);
            }
        } else if (env.acceptsProfiles(Profiles.of("prod"))) {
            missing.add("app.mail.enabled (en producción el correo no puede desactivarse)");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Falta configuración obligatoria: " + String.join(", ", missing)
                    + ". Defínela en las variables de entorno (ver .env.example).");
        }
    }

    private static void require(ConfigurableEnvironment env, List<String> missing, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) missing.add(key);
    }
}
