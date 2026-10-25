## Testing Report — F03 · Material propio (subida e indexado RAG) — 25-10-2026

Rama `feature/F03-material-propio-rag`. Todo se ejecutó con Docker y variables de entorno **ficticias** (el `.env` del usuario no se leyó ni se escribió).
Ningún test llama a una API de pago; la verificación con el stack usa **Ollama real** (embeddings locales, coste 0) en un proyecto Compose aparte
(`studylab-f03`, sin publicar puertos) para no tocar el stack ni la BD del usuario.

### Resumen
- Tests: backend **440** (0 fallos, 4 omitidos opt-in) · frontend **107** (36 nuevos) · entorno **28** (3 nuevos) = **575** ✅
- Cobertura del backend: **93,4 %** de líneas · 79,7 % de ramas (JaCoCo). Por paquete de F03: `material.chunk` 100 %, `material.extract` 97,1 %, `material` 83,1 %
  (lo no cubierto por los tests es sobre todo `MaterialWorkerLoop`, el hilo de fondo, que los tests apagan a propósito y sí se ejercitó en el stack).
- Verificación con el stack levantado: **29 comprobaciones**, 28 correctas y una cuyo código esperado era erróneo (ver "Verificaciones fuera de los tests").

### Resultados por criterio de aceptación

| AC | Descripción | Tests principales | Estado |
|----|-------------|-------------------|--------|
| AC-1 | `POST /api/material`: documento del usuario, 202 inmediato, estados | `MaterialUploadTest` + stack | ✅ |
| AC-2 | Tipo por contenido (PDF, DOCX, texto); rechazo de `.doc`, macros, ZIP, ejecutables | `FileTypeDetectorTest`, `MaterialUploadTest` + stack | ✅ |
| AC-3 | Tamaño máximo, nº de documentos, fichero vacío, duplicado | `MaterialUploadTest`, `MaterialDocumentLimitTest`, `MaterialExceptionHandlingTest` + stack (10 MB+1 y 12 MB) | ✅ |
| AC-4 | Nombre saneado y nunca usado como ruta | `MaterialUploadTest`, `MaterialArchitectureTest` | ✅ |
| AC-5 | Nada se ejecuta; XXE y bombas de descompresión acotadas | `PdfTextExtractorTest`, `DocxTextExtractorTest` + stack (bomba de 60 MB y XXE) | ✅ |
| AC-6 | Extracción de PDF, DOCX y texto; cifrado, corrupto o sin texto → ERROR claro | `*TextExtractorTest`, `MaterialProcessingTest` + stack | ✅ |
| AC-7 | Fragmentos acotados, con solape, página y posición | `ChunkerTest`, `MaterialProcessingLimitsTest` | ✅ |
| AC-8 | Embeddings por `AiGateway` (Ollama), guardados en pgvector, sin coste y sin contenido en `ai_usage` | `MaterialProcessingTest`, `MaterialSchemaTest` + stack (768 dimensiones, coste 0) | ✅ |
| AC-9 | Procesado asíncrono, reinicios, Ollama caído sin fragmentos a medias | `MaterialProcessingTest` + stack | ✅ |
| AC-10 | Listado propio y de los menores (solo lectura); ajeno = 404 igual que inexistente | `MaterialAccessTest` + stack | ✅ |
| AC-11 | Borrado real, solo del propietario; el tutor recibe 403; durante el procesado no quedan fragmentos | `MaterialAccessTest`, `MaterialDeleteTest` + stack | ✅ |
| AC-12 | `MaterialSearch` solo del propietario; filtro de documentos ajenos sin efecto; `k` acotado | `MaterialSearchTest`, `MaterialArchitectureTest` + SQL con embeddings reales | ✅ |
| AC-13 | El texto recuperado es dato, nunca instrucción | `MaterialSearchTest`, `MaterialArchitectureTest` | ✅ |
| AC-14 | Auditoría y logs sin nombre ni contenido | `MaterialUploadTest`, `MaterialDeleteTest`, `MaterialArchitectureTest` + stack | ✅ |
| AC-15 | Sesión y CSRF en todos los endpoints | `MaterialAccessTest`, `MaterialUploadTest` + stack (cookies reales) | ✅ |
| AC-16 | Pantalla «Mi material»: subir, estados que se actualizan, borrar con confirmación, tutor y menor | `MaterialView.spec.ts`, `material.spec.ts`, `MaterialNavigation.spec.ts` | ✅ (no se ha visto en un navegador) |

### Casos borde verificados
Extensión doble y `Content-Type` falso · nombre con rutas, caracteres de control, 300 caracteres y marcado HTML · PDF cifrado, corrupto, escaneado, con
JavaScript y con demasiadas páginas · DOCX con macros, con entidad externa, con demasiadas entradas y bomba ZIP · texto en Latin-1 · duplicado del
mismo usuario y no de otro · llegar al límite y borrar para volver a subir · subida y borrado simultáneos con el procesado · propietario dado de baja
(no se procesa ni se recupera) · fragmentos con otro modelo de embeddings (se ignoran) · consulta vacía, enorme, `k` fuera de rango · usuario sin
documentos · documento ajeno en el filtro · "ignora las instrucciones" dentro del documento · un menor sube, su tutor lo ve y no puede borrarlo.

### Verificaciones fuera de los tests
Stack real (nginx → backend → PostgreSQL con pgvector → Ollama con `nomic-embed-text`), con cookies, CSRF y Mailpit reales:
1. Subida de TXT, MD, PDF y DOCX: los cuatro responden 202 y acaban `READY` (7, 7, 1 y 1 fragmentos); el PDF registra su página.
2. Rechazos: ejecutable con extensión `.pdf` (415), duplicado (409), vacío (400), 10 MB + 1 byte (413 del backend con su código), **12 MB (413 de nginx,
   sin llegar al backend)**, sin CSRF (403).
3. Adversarios: bomba ZIP de 60 MB (61 KB comprimida) → `ERROR TOO_LARGE`; DOCX con XXE → `ERROR CORRUPT` y nunca se lee `/etc/passwd`; PDF sin texto →
   `ERROR NO_TEXT`. El backend siguió sano.
4. Permisos: el menor sube; el tutor lo ve y el borrado recibe 403; otro adulto recibe el mismo 404 que ante un id inexistente; un menor no ve el material de
   su tutor.
5. **Búsqueda con embeddings reales** (la misma SQL que `MaterialSearch`): «¿cuál es la capital de Francia?» → `geografia.txt` (0,71) · «¿cómo se prepara una
   bechamel?» → `cocina` (0,69) · «¿dónde ocurre la fotosíntesis?» → `resumen.docx` (0,78, por encima del PDF de volcanes, 0,56).
6. Base de datos: los 18 vectores son de 768 dimensiones y modelo `nomic-embed-text`; los originales se pusieron a `NULL` al terminar (también en los
   `ERROR`); `ai_usage` registra 6 llamadas de `EMBEDDING` con coste 0 y sin contenido; la auditoría guarda ids y tipo, sin nombres.
7. Una comprobación **falló por una expectativa mía equivocada**: una subida **sin sesión y sin cookie CSRF** responde 403, no 401, porque el CSRF se
   comprueba antes que la autenticación (igual que en el resto de la API desde F01). Con el token y sin sesión, 401 (lo cubre `MaterialAccessTest`).
8. No se ha comprobado con un navegador real cómo se ve la pantalla ni el sondeo en vivo (solo con Vitest).

### Revisión `security-checklist`

**Lo que ya está bien:** todos los endpoints exigen sesión y filtran por propietario o tutela, con 404 idéntico para ajeno e inexistente (IDOR cubierto por
tests y por HTTP real) · CSRF activo y comprobado con cookies reales · el tipo se decide por contenido y el original no toca el disco (contenedor de solo
lectura, sin volúmenes de subidas) · extracción acotada (páginas, texto, entradas y bytes descomprimidos) y XXE desactivado · toda consulta sobre
`source_chunk` lleva `owner_id` (lo comprueba un test que lee el código) y una clave foránea compuesta impide un fragmento con otro propietario · SQL
siempre parametrizado, sin concatenar · ni logs ni auditoría llevan nombres ni contenido (un test lo comprueba a nivel DEBUG) · sin `v-html` y el nombre del
fichero se pinta como texto · sin secretos nuevos ni puertos nuevos · nginx limita el cuerpo a 11 MB solo en `/api/material`.

**Corregido durante esta fase:**
- 🟠 **Spring MVC registraba el cuerpo de cada respuesta a nivel DEBUG, con los nombres de los documentos.** Lo detectó el test de logs. Se fija en INFO
  para ese componente.
- 🟠 **El intento de un tutor de borrar material de un menor no quedaba auditado**: la transacción se deshacía al lanzar el 403 y se llevaba la fila de
  auditoría. Ahora el borrado va sin transacción envolvente.
- 🟡 `pageCount` salía como 0 en lugar de ausente en documentos sin páginas.

**Pendiente, por gravedad:**
- 🟠 **Sin límite de frecuencia por usuario**: se puede subir y borrar en bucle y tener al hilo de procesado ocupado (A2 del backlog; el límite de 20 documentos no
  frena el ciclo subir-borrar).
- 🟡 **Un PDF patológico puede seguir consumiendo CPU y memoria aunque se agote el tiempo máximo**: `Future.cancel(true)` interrumpe el hilo, pero PDFBox
  no siempre atiende la interrupción. El documento pasa a `ERROR`, pero el hilo de extracción puede seguir hasta terminar. Sin `mem_limit` en los contenedores (D5).
- 🟡 **PDFBox 3.0.5**: sin avisos que afecten a la extracción de texto; el aviso `CVE-2026-23907` (path traversal) está en el ejemplo `ExtractEmbeddedFiles`,
  que este código no usa, y su corrección sale en la 3.0.7. Conviene actualizar a la última 3.0.x (F3-4 del backlog).
- 🟡 Una cuenta con muchos documentos grandes ocupa espacio de vectores (≈ 3 KB por fragmento; hasta ≈ 90 MB en el peor caso por usuario).
- 🟡 Si cambia el modelo de embeddings, los fragmentos antiguos dejan de usarse hasta reindexar (F3-2).
- **No se puede comprobar desde el código:** que los puertos de `:8080`, `:8081`, `:5432` y `:8025` no se vean por la LAN (D6 de F01) y el límite de memoria
  real del contenedor.

### Revisión `llm-integration-review`

| Punto | Estado |
|---|---|
| Toda llamada pasa por la capa intercambiable | **OK**: `MaterialWorker` y `MaterialSearch` solo usan `AiGateway.embed` (lo comprueba `AiArchitectureTest`) |
| El modelo se elige por tarea desde configuración | **OK**: el modelo de embeddings sale de `app.ai.tasks.embedding.model`; cada fragmento lo guarda |
| Cada llamada registra tarea, modelo, tokens, coste y usuario | **OK** (6 filas `EMBEDDING`, coste 0, sin contenido) |
| Tope de gasto, límite de salida, reintentos acotados | **OK / NO APLICA**: los embeddings locales cuestan 0 y no pasan por el tope personal; sin salida de texto |
| Una sola generación concurrente por usuario | **NO APLICA** (no es una generación de curso; un único hilo procesa la cola) |
| Nunca se regenera lo ya generado | **OK**: duplicado por hash rechazado y documento `READY` no se reprocesa |
| Los tests usan el proveedor falso | **OK**; el Ollama real solo se usó en la verificación con el stack |
| Clave solo por entorno | **NO APLICA**: Ollama local, sin clave |
| El texto del material va como **dato**, separado de las instrucciones | **OK / pendiente de uso**: `MaterialSearch` devuelve `Fragment`; un test comprueba que el texto con `</datos>` no cierra la sección ni cambia la plantilla; `material/` no construye prompts |
| El material de un usuario nunca se recupera para otro | **OK**: `MaterialSearchTest`, test de arquitectura, FK compuesta y HTTP real |
| Salida del modelo validada contra un esquema | **NO APLICA** (F03 no genera texto) |
| Prompts sin datos personales | **OK**: solo viaja el texto del propio usuario a Ollama local |
| Filtrado de contenido para menores | **PENDIENTE (A3)**: hoy el texto de un menor solo va a Ollama local; **antes de que F05/F07 lo envíen a un modelo de pago debe existir** (condición de ADR-34) |
| Aviso «generado por IA» | **NO APLICA** (no se muestra contenido generado) |

### Issues encontrados (y corregidos salvo indicación)
1. Cuerpos de respuesta con nombres de documentos en el log a DEBUG → corregido (INFO para ese componente).
2. Auditoría del 403 de borrado perdida por la transacción → corregido.
3. `pageCount` 0 en lugar de nulo → corregido.
4. Un error de mi test de logs (dejaba un fallo simulado activo hasta la búsqueda) y una expectativa de 401 que en realidad es 403 sin CSRF; sin impacto en el código.
5. **No corregido:** el límite de frecuencia por usuario (A2), la CPU de un PDF patológico tras agotar el tiempo, y no haber visto la pantalla en un navegador real.
