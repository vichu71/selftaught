package es.vicai.studylab.illustrations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros de las ilustraciones (F12): valores de ingeniería sin variable de entorno. Los límites por usuario se cuentan en la BD, no por IP. El hilo de fondo se
 * apaga en los tests ({@code app.illustrations.worker.enabled=false}) y se llama a {@link IllustrationGenerator#processPending()} de forma síncrona.
 */
@ConfigurationProperties(prefix = "app.illustrations")
public record IllustrationProperties(
        /** Imágenes que se piden al proveedor por usuario y hora (cuentan también las que fallan). */
        @DefaultValue("20") int maxPerHour,
        /** Veces que un tema puede volver a generarse. */
        @DefaultValue("3") int maxRegenerationsPerTopic,
        /** Lado máximo de la imagen guardada, en píxeles. */
        @DefaultValue("768") int maxSide,
        /** Lado mínimo aceptado: una imagen de 1×1 no es una ilustración. */
        @DefaultValue("64") int minSide,
        /** Peso máximo de la imagen guardada (300 kB; la BD lo comprueba también). */
        @DefaultValue("307200") int maxBytes,
        /** Lo máximo que se lee de la respuesta de un proveedor antes de decodificar. */
        @DefaultValue("8388608") long maxProviderBytes,
        /** Lo máximo que puede declarar una imagen del proveedor (ancho × alto) antes de decodificarla. */
        @DefaultValue("16000000") long maxPixels,
        @DefaultValue("0.82") float jpegQuality,
        @DefaultValue("120") int maxTitleChars,
        @DefaultValue("300") int maxSummaryChars,
        @DefaultValue Worker worker) {

    public record Worker(@DefaultValue("true") boolean enabled, @DefaultValue("1") int threads, @DefaultValue("5s") Duration poll) {}
}
