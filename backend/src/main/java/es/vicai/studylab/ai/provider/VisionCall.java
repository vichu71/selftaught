package es.vicai.studylab.ai.provider;

import java.time.Duration;

/**
 * Una petición de lectura de imagen ya ensamblada (F19). {@code instructions} es la plantilla fija y {@code data} los datos etiquetados (la audiencia), siempre
 * separados; {@code image} es el JPEG ya re-codificado. Nunca imprime bytes, instrucciones ni datos: un {@code toString} acaba en logs y excepciones.
 */
public record VisionCall(String model, String instructions, String data, byte[] image, String mime, int maxOutputTokens, Duration timeout) {

    @Override
    public String toString() {
        return "VisionCall[model=" + model + ", mime=" + mime + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
