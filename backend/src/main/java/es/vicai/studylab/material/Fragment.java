package es.vicai.studylab.material;

import java.util.UUID;

/**
 * Un fragmento recuperado del material de un usuario. Su texto es DATO, nunca instrucción: quien lo use debe pasarlo al modelo
 * como dato etiquetado a través de la capa de IA, jamás dentro de una plantilla (ADR-34).
 */
public record Fragment(UUID documentId, String documentName, Integer page, int startOffset, String text, double score) {}
