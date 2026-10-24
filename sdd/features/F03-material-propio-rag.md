# F03 — Material propio (subida e indexado RAG)

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

El usuario puede aportar su propio material (PDF, Word, texto) para que el curso y el tutor se basen
en él, al estilo NotebookLM. El material es **privado** de su propietario.

Flujo: subir ficheros → extraer texto → trocear → generar embeddings (local, Ollama) → guardar en
pgvector. Después, dado una consulta, recuperar los fragmentos más relevantes **solo del material
del usuario y del curso en cuestión**, con referencia a documento y posición para poder citar.

Debe mostrarse el estado del procesamiento (subido, procesando, listo, error) y poder borrar un
documento (y con él sus fragmentos y embeddings).

Seguridad: tipos permitidos (PDF, Word, texto), tamaño máximo, nombre saneado, sin ejecutar nada del
fichero; el texto extraído se trata como datos y nunca como instrucciones (prompt injection);
aislamiento estricto por propietario en la recuperación.

Fuera de alcance: enlaces web, vídeo, audio, OCR de documentos escaneados, compartir material entre
usuarios.
