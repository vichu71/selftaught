package es.vicai.studylab.ai.prompt;

/** Prompt ya montado: instrucciones fijas por un lado, datos del usuario por otro. */
public record AssembledPrompt(String instructions, String dataMessage) {}
