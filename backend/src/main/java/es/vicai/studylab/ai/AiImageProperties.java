package es.vicai.studylab.ai;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración propia de la imagen (F12): precio por imagen de cada modelo, tamaño y calidad que se piden y parámetros del benchmark de imágenes. Va aparte de
 * {@link AiProperties} (mismo prefijo) para no cambiar el registro que usan todas las demás tareas (ADR-78). La tarea {@code app.ai.tasks.image.*} sí vive en
 * {@code AiProperties}: sin proveedor, las ilustraciones quedan desactivadas.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiImageProperties(
        /** Precio por imagen, por "proveedor/modelo", en USD. */
        Map<String, ImagePrice> imagePricing,
        @DefaultValue("1024") int imageSize,
        @DefaultValue("low") String imageQuality,
        @DefaultValue ImageBenchmark imageBenchmark) {

    public AiImageProperties {
        imagePricing = imagePricing == null ? Map.of() : Map.copyOf(imagePricing);
    }

    public record ImagePrice(BigDecimal perImage, java.time.LocalDate verifiedOn) {}

    /** Parámetros del run del benchmark de imágenes (solo con el perfil `benchmark-images`). */
    public record ImageBenchmark(String models, @DefaultValue("2.00") BigDecimal budgetUsd, @DefaultValue("/out") String outDir) {}

    /** Precio de una imagen de ese modelo; sin precio configurado no hay imagen (nunca se asume coste 0). */
    public BigDecimal priceOf(String provider, String model) {
        ImagePrice price = imagePricing.get(provider + "/" + model);
        if (price == null || price.perImage() == null || price.perImage().signum() < 0) throw AiException.of(AiErrorKind.CONFIG);
        return price.perImage();
    }

    public static AiImageProperties defaults() {
        return new AiImageProperties(null, 1024, "low", new ImageBenchmark(null, new BigDecimal("2.00"), "/out"));
    }
}
