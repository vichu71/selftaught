package es.vicai.studylab.ai.provider;

import java.time.Duration;

/** Una petición de imagen ya ensamblada (F12). {@code size} es el lado en píxeles de una imagen cuadrada y {@code quality} el nivel del proveedor (low, medium…). */
public record ImageCall(String model, String prompt, int size, String quality, Duration timeout) {

    /** El prompt es el motivo del usuario ya saneado: nunca sale en un {@code toString}, que acaba en logs y excepciones. */
    @Override
    public String toString() {
        return "ImageCall[model=" + model + ", size=" + size + ", quality=" + quality + "]";
    }
}
