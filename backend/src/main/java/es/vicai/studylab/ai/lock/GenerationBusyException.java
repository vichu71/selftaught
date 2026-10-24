package es.vicai.studylab.ai.lock;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;

/** El usuario ya tiene una generación de curso en marcha. */
public class GenerationBusyException extends AiException {

    public GenerationBusyException() {
        super(AiErrorKind.GENERATION_BUSY, "Ya hay una generación en curso. Espera a que termine");
    }
}
