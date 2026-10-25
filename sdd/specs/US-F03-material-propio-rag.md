# US-F03 · Material propio (subida e indexado RAG)

> Historia refinada (FASE 1 de SDD) tras las decisiones del usuario. Los tests citan estos AC.

### Como alumno (adulto o menor) que quiere estudiar con sus propios apuntes
### Quiero subir mis documentos (PDF, Word, texto), ver cuándo están listos y borrarlos
### Para que los cursos y el tutor se basen en mi material, que solo yo (y el tutor de un menor) puedo ver

## Decisiones del usuario (25-10-2026)

- **Biblioteca del usuario**: cada documento pertenece a su propietario, no a un curso (los cursos llegan con F04/F05). La recuperación filtra
  siempre por propietario y, opcionalmente, por una lista de documentos que se le pasa; F04/F05 los asociarán a cursos sin tocar F03.
- **Límites por defecto**: 10 MB por fichero y 20 documentos por usuario (variables de entorno).
- **También los menores pueden subir material**, con aviso en la interfaz de que su tutor lo ve. El tutor ve los documentos de sus menores en
  solo lectura. Condición registrada: F03 solo usa Ollama local (sin gasto ni proveedor externo); **el filtrado de contenido de menores
  (A3) debe existir antes de que F05/F07 envíen su texto a un modelo de pago**.
- **La búsqueda es solo interna** (servicio para F05/F07): F03 no la expone por HTTP ni en la pantalla; se prueba con tests.

## Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | `POST /api/material` (multipart, con sesión): sube un fichero y crea un documento del usuario autenticado con estado `UPLOADED` → `PROCESSING` → `READY` o `ERROR`. Responde enseguida (202) sin esperar al indexado. |
| AC-2 | Tipos permitidos: **PDF, Word (.docx) y texto (.txt, .md)**. El tipo se decide por el **contenido** (firma del fichero), no por la extensión ni por el `Content-Type`. Se rechazan `.doc`, macros (`.docm`), ejecutables, comprimidos y cualquier otro tipo (415). |
| AC-3 | Tamaño máximo por fichero (`MATERIAL_MAX_FILE_MB`, 10) y máximo de documentos por usuario (`MATERIAL_MAX_DOCUMENTS`, 20); fichero vacío rechazado (400). Superarlos devuelve 413 y 409 con mensaje claro. Los documentos borrados no cuentan. |
| AC-4 | El nombre se sanea (sin rutas, caracteres de control ni longitud excesiva) y **nunca se usa como ruta de disco**: el original no se guarda en el sistema de ficheros. |
| AC-5 | Nada del fichero se ejecuta ni se interpreta como instrucción: sin JavaScript de PDF, sin macros, sin resolver entidades externas (XXE) ni enlaces, y con límites contra bombas de descompresión (tamaño descomprimido, número de entradas, páginas). |
| AC-6 | Extracción de texto de PDF, DOCX y texto plano. Un PDF/DOCX protegido con contraseña, corrupto o **sin texto** (p. ej. escaneado: no hay OCR) acaba en `ERROR` con un motivo genérico y claro. |
| AC-7 | El texto se trocea en fragmentos de tamaño acotado y con solapamiento, conservando documento, número de orden y posición (página en PDF; desplazamiento en el resto) para poder citar. Un texto por encima del máximo (`MATERIAL_MAX_TEXT_CHARS`) acaba en `ERROR`. |
| AC-8 | Cada fragmento recibe su embedding por `AiGateway.embed` (Ollama local, coste 0) y se guarda en PostgreSQL con pgvector, con la dimensión configurada validada. Cada llamada queda en `ai_usage`, sin contenido. |
| AC-9 | El procesamiento es **asíncrono** y su estado es consultable. Si la app se reinicia a mitad, los documentos pendientes se retoman o acaban en `ERROR`; nunca se quedan en `PROCESSING` para siempre. Si Ollama o su modelo no están, el documento acaba en `ERROR` ("servicio de indexado no disponible"), sin fragmentos a medias. |
| AC-10 | `GET /api/material` lista los documentos del usuario con estado, tipo, tamaño, nº de fragmentos y fecha; `GET /api/material/{id}` da uno. Un adulto ve además los de **sus menores** (solo lectura, indicando de quién son). Un documento ajeno o inexistente responde igual (404). |
| AC-11 | `DELETE /api/material/{id}` borra el documento **y** sus fragmentos y embeddings (borrado real, a diferencia de las cuentas). Solo su propietario; el tutor no borra el material del menor. Un borrado durante el procesamiento impide que queden fragmentos. |
| AC-12 | Servicio interno `MaterialSearch.search(userId, consulta, k, documentos?)`: embedding de la consulta y similitud por coseno **solo entre fragmentos del propietario** (y de los documentos indicados que sean suyos). Devuelve fragmento, documento, posición y puntuación; `k` acotado. Un id de documento ajeno nunca devuelve nada. |
| AC-13 | El texto recuperado es **dato, no instrucción**: se entrega como dato etiquetado (`AiRequest.data`), nunca dentro de una plantilla. Un documento con "ignora las instrucciones anteriores…" se guarda y recupera tal cual sin alterar ninguna plantilla ni cambiar el comportamiento. |
| AC-14 | Auditoría de solo añadir: `DOCUMENT_UPLOADED`, `DOCUMENT_READY`, `DOCUMENT_FAILED`, `DOCUMENT_DELETED` (con id y tipo; **sin contenido ni nombre del fichero**). |
| AC-15 | Todos los endpoints exigen sesión y filtran por propietario/tutela; CSRF activo en las operaciones que modifican. |
| AC-16 | Interfaz «Mi material»: subir fichero (con error claro si el tipo o tamaño no valen), lista con estado (subido, procesando, listo, error) que se actualiza sola, borrar con confirmación, y para el adulto la sección de documentos de sus menores. El menor ve el aviso de que su tutor ve sus documentos. El nombre y el motivo de error se muestran como texto (sin `v-html`). |

## Casos borde
- Fichero con extensión `.pdf` que en realidad es un ejecutable o un ZIP; `Content-Type` falso; extensión doble (`apuntes.pdf.exe`).
- Nombre con `../`, caracteres de control, emojis, 300 caracteres o vacío.
- PDF/DOCX cifrado, corrupto, con 0 páginas, de mil páginas, o DOCX que se descomprime a cientos de MB (bomba ZIP); DOCX con entidades externas.
- Documento solo con imágenes (escaneado), o con texto en otro idioma o con caracteres raros: se indexa lo que haya o falla con motivo claro.
- Subir dos veces el mismo contenido: se rechaza el duplicado del mismo usuario (409) para no gastar indexado; otro usuario puede subir el mismo fichero.
- Llegar al límite de documentos; borrar uno y poder subir otro.
- Subida simultánea de varios ficheros del mismo usuario; cierre de la app durante el procesamiento; Ollama caído o sin el modelo.
- Borrar un documento mientras se procesa; pedir un documento borrado (404).
- Un menor sube material y su tutor lo ve; un tutor intenta ver o borrar el material de un menor ajeno; un menor intenta ver el de otro.
- Consulta vacía, `k` fuera de rango, lista de documentos con ids ajenos o repetidos; usuario sin documentos.
- Un menor dado de baja: su material no se recupera ni se lista (y no se procesa).

## Fuera de alcance (v1)
Enlaces web, vídeo, audio, OCR, compartir material entre usuarios, asociar documentos a cursos (F04/F05), búsqueda expuesta por HTTP o en la
pantalla, búsqueda por palabras clave o híbrida, reordenado de resultados, edición o versiones de un documento, carpetas o etiquetas,
conservar el fichero original, escaneo antivirus, cuota por tamaño total, reintento automático desde `ERROR` (se borra y se vuelve a subir),
y el filtrado de contenido de menores (A3, F04).

## Definición de hecho
- Los 16 AC tienen test y pasan en contenedor, con cobertura ≥ 80 %. Ningún test llama a una API de pago (los embeddings de test usan el proveedor falso; un test opt-in usa el Ollama real).
- Verificado con el stack levantado: subida real de un PDF, un DOCX y un TXT, estado hasta `READY`, búsqueda interna correcta y borrado completo; y el aislamiento entre dos usuarios.
- `README`, `ARCHITECTURE.md` y `CLAUDE.md` actualizados. `security-checklist` y `llm-integration-review` pasadas e informadas.
