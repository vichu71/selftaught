package es.vicai.studylab.ai.prompt;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(String id) {
        super("No existe la plantilla de prompt '" + id + "'");
    }
}
