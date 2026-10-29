package es.vicai.studylab.material;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.boot.web.servlet.MultipartConfigFactory;

/**
 * Límites de la subida en el propio servidor (además de nginx): un poco por encima del máximo del servicio, para que sea este quien
 * responda con su código claro, y en memoria (sin ficheros temporales: el contenedor es de solo lectura).
 */
@Configuration
public class MaterialConfig {

    /** El procesador de fotos (F19) con los límites de la configuración: se crea una vez, no guarda estado. */
    @Bean
    es.vicai.studylab.material.image.PhotoProcessor photoProcessor(MaterialProperties props) {
        return new es.vicai.studylab.material.image.PhotoProcessor(props.photos().maxPixels(), props.photos().maxSide());
    }

    @Bean
    MultipartConfigElement multipartConfigElement(MaterialProperties props) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        DataSize limit = DataSize.ofMegabytes(props.maxFileMb() + 1L);
        factory.setMaxFileSize(limit);
        factory.setMaxRequestSize(DataSize.ofMegabytes(props.maxFileMb() + 2L));
        factory.setFileSizeThreshold(DataSize.ofMegabytes(props.maxFileMb() + 2L));
        return factory.createMultipartConfig();
    }
}
