package es.vicai.studylab.ai.provider;

/**
 * Puerto de la capa de IA: lo único que conoce a un proveedor concreto. Cada adaptador implementa solo lo que su proveedor
 * ofrece; lo demás lanza UnsupportedOperationException (la configuración ya impide pedírselo).
 */
public interface AiProvider {

    String name();

    ChatResult chat(ChatCall call);

    EmbedResult embed(EmbedCall call);
}
