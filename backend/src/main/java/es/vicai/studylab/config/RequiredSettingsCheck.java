package es.vicai.studylab.config;

import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiSettingsValidator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
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

        // F02: la configuración de IA (proveedores, modelos, precios, claves y tope) se valida antes de crear ningún bean
        AiProperties ai = Binder.get(env).bind("app.ai", Bindable.of(AiProperties.class))
                .orElseGet(() -> new AiProperties(null, null, null, new AiProperties.Budget(null, 80, AiProperties.BudgetMode.WARN, new java.math.BigDecimal("2.00"), new java.math.BigDecimal("10.00")),
                        16000, 60000, new AiProperties.Retry(2, java.time.Duration.ofSeconds(1)), java.time.Duration.ofMinutes(20),
                        new AiProperties.Embedding(768), new AiProperties.Benchmark(true, null, new java.math.BigDecimal("5.00"), "/out")));
        missing.addAll(AiSettingsValidator.validate(ai, env.acceptsProfiles(Profiles.of("prod")),
                env.acceptsProfiles(Profiles.of("benchmark"))));

        // F03: los límites del material tienen que ser positivos (si no, nada se podría subir o se subiría sin límite)
        es.vicai.studylab.material.MaterialProperties material = Binder.get(env)
                .bind("app.material", Bindable.of(es.vicai.studylab.material.MaterialProperties.class))
                .orElseGet(es.vicai.studylab.material.MaterialProperties::defaults);
        if (material.maxFileMb() <= 0) missing.add("MATERIAL_MAX_FILE_MB (app.material.max-file-mb, debe ser mayor que 0)");
        if (material.maxDocuments() <= 0) missing.add("MATERIAL_MAX_DOCUMENTS (app.material.max-documents, debe ser mayor que 0)");

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
