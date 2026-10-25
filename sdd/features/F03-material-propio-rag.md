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

---

## Actualización tras la implementación (FASE 8, 25-10-2026)

Estado: **hecha y probada** (backend 440 tests, frontend 107, entorno 28; cobertura del backend 93 %). Especificación refinada:
`sdd/specs/US-F03-material-propio-rag.md`; diseño: `sdd/specs/ART-F03-propuesta-material.md`; informe: `sdd/reports/F03-testing-report.md`;
pendientes: `sdd/BACKLOG.md` (F3-1 a F3-7).

**Cambios de alcance decididos con el usuario durante el ciclo**
- El material es una **biblioteca del usuario**, no de un curso (los cursos llegan con F04/F05). Límites por defecto: 10 MB por fichero y 20 documentos.
- **Los menores también pueden subir**, con aviso a su tutor, que lo ve en solo lectura y no puede borrarlo. Condición: antes de enviar su texto a un
  modelo de pago debe existir el filtrado de contenido (A3, F04).
- La **búsqueda es solo interna** (`MaterialSearch`): sin endpoint ni pantalla en F03.

**Casos borde que no estaban en la ficha y aparecieron al implementar**
- Un DOCX con **macros**, un ZIP corriente o un `.doc` antiguo se rechazan por su contenido, aunque se llamen `.docx`.
- Una **bomba ZIP** (60 MB en 61 KB) y una **entidad externa XXE** dentro de un DOCX: el primero acaba en `TOO_LARGE`, el segundo en `CORRUPT`, sin leer nada local.
- **Spring MVC volcaba al log los nombres de los documentos** con el nivel DEBUG (ADR-36); y el 403 de un tutor al borrar **no quedaba auditado** porque la transacción
  se deshacía con la excepción.
- Una subida **sin sesión y sin token CSRF** responde 403 (no 401): el CSRF se comprueba antes, como en el resto de la API.
- Un propietario **dado de baja** conserva sus documentos pero no se procesan, listan ni recuperan.
- **Texto que no es UTF-8** (p. ej. Latin-1) se rechaza con un mensaje claro en lugar de indexar caracteres rotos.

**Fuera de alcance real (documentado, no hecho)**
Límite de frecuencia por usuario en la subida (F3-6, 🟠), aislar la extracción de PDF en un proceso aparte (F3-7), actualizar PDFBox a la última 3.0.x (F3-4),
reindexado al cambiar de modelo de embeddings (F3-2), OCR y más formatos (F3-3), índice vectorial (F3-5) y el filtrado de contenido de menores (F3-1 / A3).
