package es.vicai.studylab.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Ajustes propios de la aplicación. Los obligatorios los comprueba RequiredSettingsPostProcessor al arrancar. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicUrl,
        @DefaultValue Mail mail,
        @DefaultValue("PT168H") Duration sessionTtl) {

    public record Mail(@DefaultValue("true") boolean enabled, String from) {}

    /** URL pública sin barra final, para construir los enlaces de los correos. */
    public String baseUrl() {
        return publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }
}
