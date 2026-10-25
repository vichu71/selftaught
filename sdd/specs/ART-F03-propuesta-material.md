# F03 · Material propio — FASE 2: Proposal Artifacts

> Propuesta para confirmar antes de la FASE 3. Cita los AC de `US-F03-material-propio-rag.md`. Los ADR se pasan a `ARCHITECTURE.md` en la FASE 5.

## ART-001 · Stack

`PROJECT.md` ya fija el stack; F03 solo añade lo siguiente.

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Backend (nueva dependencia) | **Apache PDFBox 3.0.x** (Apache 2.0) | Única librería nueva. Extrae texto de PDF sin ejecutar JavaScript ni abrir enlaces; permite acotar páginas y memoria |
| Backend (sin dependencia) | Extractor de DOCX propio con `ZipInputStream` + StAX del JDK | Apache POI arrastra muchas dependencias; un DOCX solo necesita `word/document.xml`. Control total de límites (entradas, tamaño descomprimido) y DTD/entidades externas desactivadas |
| Backend | Detección de tipo por firma (bytes iniciales) | La extensión y el `Content-Type` los controla el cliente |
| Base de datos | PostgreSQL + **pgvector** (ya instalado en la imagen `pgvector/pgvector:pg17`) | Migración `V3__material.sql`. Se usa por JDBC con `CAST(:v AS vector)`, sin librería |
| Embeddings | `AiGateway.embed` → Ollama del contenedor (`nomic-embed-text`) | Coste 0; ya verificado en F02 |
| Frontend | Vue 3 + Pinia (sin librerías nuevas) | Subida con `FormData`, sondeo del estado |
| Proxy | nginx: bloque propio `location /api/material` con `client_max_body_size 11m` | Hoy `/api/` admite 1 MB y cortaría cualquier subida |

Configuración nueva (`app.material.*`): `MATERIAL_MAX_FILE_MB` (10) y `MATERIAL_MAX_DOCUMENTS` (20) por entorno; el resto (páginas máximas 500, texto máximo
1 500 000 caracteres, DOCX: 1 000 entradas y 50 MB descomprimidos, fragmento ≈ 1 000 caracteres con solape 150, lote de embeddings 16) en `application.properties`.

## ART-002 · Modelo de datos (`V3__material.sql`)

```
source_document
  id                 uuid PK
  owner_id           uuid NOT NULL → app_user(id)
  display_name       varchar(120) NOT NULL        -- nombre saneado, solo para mostrar
  media_type         varchar(5)   NOT NULL        -- PDF | DOCX | TEXT
  size_bytes         integer      NOT NULL CHECK (> 0)
  content_sha256     char(64)     NOT NULL        -- detecta duplicados del mismo usuario
  status             varchar(12)  NOT NULL        -- UPLOADED | PROCESSING | READY | ERROR
  error_kind         varchar(30)                  -- motivo genérico (no el mensaje de ninguna librería)
  attempts           smallint     NOT NULL DEFAULT 0
  page_count         integer, chunk_count integer NOT NULL DEFAULT 0
  content            bytea                        -- el original, SOLO hasta procesarlo; se pone a NULL en READY o ERROR
  created_at, updated_at, processing_started_at   timestamptz
  UNIQUE (owner_id, content_sha256)    UNIQUE (id, owner_id)

source_chunk
  id                 bigserial PK
  document_id        uuid NOT NULL, owner_id uuid NOT NULL   -- FK compuesta (document_id, owner_id) → source_document, ON DELETE CASCADE
  ordinal            integer NOT NULL                        -- orden en el documento; UNIQUE (document_id, ordinal)
  page               integer                                  -- solo PDF
  start_offset, end_offset integer NOT NULL                   -- posición en el texto extraído, para citar
  text               text NOT NULL
  embedding          vector NOT NULL                          -- sin dimensión fija (ver ADR-33)
  embedding_model    varchar(80) NOT NULL                     -- con qué modelo se generó
  INDEX (owner_id)
```
- `owner_id` repetido en el fragmento y la FK compuesta hacen **imposible** un fragmento cuyo propietario no sea el de su documento.
- Borrar un documento borra sus fragmentos y vectores (`CASCADE`). El rol `studylab_app` recibe `SELECT/INSERT/UPDATE/DELETE` sobre ambas tablas y nada más.
- Un menor dado de baja conserva sus documentos, pero no se listan, procesan ni recuperan (se filtra por `app_user.status = 'ACTIVE'`).
- Tamaño estimado: un vector de 768 dimensiones ocupa ≈ 3 KB; 20 documentos de 10 MB de texto llenaría 100 MB por usuario como peor caso.

## ART-003 · Endpoints

Todos exigen sesión (por defecto en `SecurityConfig`) y CSRF en los que modifican.

```
POST   /api/material            multipart "file" → 202 DocumentView           AC-1..5, 14, 15
   400 MATERIAL_EMPTY / MATERIAL_BAD_REQUEST · 413 MATERIAL_TOO_LARGE · 415 MATERIAL_TYPE_NOT_ALLOWED
   409 MATERIAL_LIMIT_REACHED / MATERIAL_DUPLICATE
GET    /api/material            → { own: [DocumentView], minors: [{ student:{id,displayName}, documents:[DocumentView] }] }   AC-10, 15
   "minors" solo para un adulto con menores a su cargo; solo documentos de menores activos
GET    /api/material/{id}       → DocumentView   (propietario, o su tutor en solo lectura; si no, 404)                        AC-10, 15
DELETE /api/material/{id}       → 204 (solo el propietario; el tutor recibe 403 y se audita; ajeno o inexistente, 404)         AC-11, 14, 15

DocumentView = { id, name, mediaType, sizeBytes, status, errorKind?, chunkCount, pageCount?, createdAt, ownedByMe }
```
Validaciones en el backend: tamaño (también antes de leer el cuerpo entero: límite multipart de Spring), firma del contenido, nombre saneado (sin rutas, sin
caracteres de control, ≤ 120), fichero no vacío, límite de documentos y duplicado por hash. Los errores salen por `GlobalExceptionHandler` con código y mensaje
genéricos (nunca el de PDFBox ni el nombre del fichero).

**API interna (Java, paquete `es.vicai.studylab.material`)**
```
MaterialSearch.search(userId, consulta, k, documentIds?) → List<Fragment(documentId, documentName, page, startOffset, text, score)>   AC-12, 13
     k entre 1 y 20; consulta de hasta 2 000 caracteres; SQL siempre con owner_id = :userId (y status READY, modelo vigente)
MaterialService (subir, listar, obtener, borrar) · MaterialWorker (cola) · TextExtractor: PdfTextExtractor, DocxTextExtractor, PlainTextExtractor
FileTypeDetector · Chunker · MaterialRepository · MaterialController · MaterialProperties
```

**Cola de procesado (AC-9):** un hilo que cada 2 s reclama un documento `UPLOADED` con `FOR UPDATE SKIP LOCKED`, lo pasa a `PROCESSING`, extrae, trocea,
genera embeddings por lotes y guarda todo los fragmentos **en una transacción** que antes bloquea la fila del documento (si se borró mientras tanto, no se
guarda nada). Al arrancar, los `PROCESSING` vuelven a `UPLOADED`; tras 3 intentos fallidos el documento pasa a `ERROR`.

## ART-004 · Frontend

```
router: /material (protegida)            HomeView: tarjeta "Mi material" con enlace
views/MaterialView.vue                   AC-16
 ├─ components/MaterialUpload.vue        elegir/arrastrar fichero; valida tipo y tamaño (solo comodidad) y muestra el error claro
 ├─ components/MaterialList.vue          filas con nombre, tipo, tamaño, estado, borrar con confirmación
 │    └─ components/MaterialStatus.vue   insignia: Subido · Procesando · Listo · Error (+ motivo en texto)
 ├─ components/MinorsMaterial.vue        solo adultos con menores: documentos de cada menor, solo lectura
 └─ aviso al menor: "Tu tutor puede ver los documentos que subes"
stores/material.ts (Pinia)               carga, sube, borra y sondea cada 3 s mientras haya documentos en curso (se detiene al salir)
api/client.ts                            listMaterial, getMaterial, uploadMaterial (FormData + X-XSRF-TOKEN), deleteMaterial
```
Todo texto del usuario (nombre del fichero, motivos) se pinta con interpolación `{{ }}`, nunca con `v-html`. Los tests usan `data-testid`.

## ART-005 · Estructura de carpetas (solo lo nuevo)

```
backend/src/main/java/es/vicai/studylab/material/
  MaterialController.java  MaterialService.java  MaterialRepository.java  MaterialProperties.java
  MaterialWorker.java  MaterialSearch.java  Fragment.java  DocumentView.java  MaterialException.java
  extract/ FileTypeDetector.java  TextExtractor.java  PdfTextExtractor.java  DocxTextExtractor.java  PlainTextExtractor.java  ExtractedText.java
  chunk/   Chunker.java  Chunk.java
backend/src/main/resources/db/migration/V3__material.sql
backend/src/test/java/es/vicai/studylab/material/  (MaterialUploadTest, MaterialValidationTest, MaterialExtractionTest, MaterialProcessingTest,
  MaterialAccessTest, MaterialDeleteTest, MaterialSearchTest, MaterialSecurityTest, ChunkerTest, MaterialLiveOllamaTest[opt-in], ficheros de ejemplo generados en el test)
frontend/src/views/MaterialView.vue   frontend/src/components/Material{Upload,List,Status}.vue  MinorsMaterial.vue
frontend/src/stores/material.ts       frontend/src/**/__tests__ o *.spec.ts (como en F01)
frontend/nginx.conf (location /api/material)   compose.yaml / .env.example (MATERIAL_*)   scripts/tests/environment.test.mjs (+ comprobación nginx)
```

## ART-006 · Wireframe

```
┌ Mi material ────────────────────────────────────────────┐
│ ┌─ Subir un documento ───────────────────────────────┐  │
│ │  [ Elegir fichero ]   o arrástralo aquí            │  │
│ │  PDF, Word (.docx) o texto · máx. 10 MB            │  │
│ └────────────────────────────────────────────────────┘  │
│ (menor) ⓘ Tu tutor puede ver los documentos que subes   │
│                                                          │
│ Mis documentos (3 de 20)                                 │
│  apuntes-tema1.pdf   PDF  1,2 MB  ● Listo     [Borrar]  │
│  resumen.docx        DOCX 80 KB   ◌ Procesando…         │
│  foto-escaneada.pdf  PDF  3 MB    ✖ Error: sin texto    │
│                                          [Borrar]       │
│ Material de mis menores (solo lectura)                   │
│  Hugo · fracciones.txt  TEXTO  4 KB  ● Listo             │
└──────────────────────────────────────────────────────────┘
```

## ART-007 · ADR (propuestos; se numeran a continuación de ADR-29)

### ADR-30: El material es una biblioteca del usuario, no de un curso
- Motivo: los cursos no existen hasta F04/F05. El propietario es la frontera de aislamiento; la lista de documentos de una búsqueda es un filtro adicional.
- Alternativas descartadas: tabla de cursos mínima en F03 (adelanta decisiones de F04/F05 y habría que migrarla).
- Revisitar si: F05 necesita que un curso "congele" qué documentos usó (tabla de unión `course_document`).

### ADR-31: Tipo por firma y extracción acotada, sin Apache POI
- Motivo: la extensión y el `Content-Type` los pone el cliente. El tipo se decide por los bytes iniciales (`%PDF-`, ZIP con `[Content_Types].xml` y `word/document.xml`, texto UTF-8 sin NUL). PDFBox solo lee texto (límite de páginas, sin JS ni enlaces); el DOCX se lee con ZIP+StAX con DTD y entidades externas desactivadas y límites de entradas y tamaño descomprimido; todo en una tarea con tiempo máximo.
- Alternativas descartadas: Apache Tika/POI (muchas dependencias y superficie de ataque), confiar en la extensión.
- Revisitar si: se admiten más formatos (entonces, otra librería con su propia revisión).

### ADR-32: Cola de procesado en la base de datos, con el original solo hasta procesarlo
- Motivo: el estado ya es una columna, sobrevive a reinicios y no necesita broker. El original se guarda en `bytea` hasta `READY`/`ERROR` y luego se borra: no hay ficheros en disco (contenedor de solo lectura), ni nombres usados como ruta, ni material duplicado.
- Alternativas descartadas: `@Async` en memoria (se pierde al reiniciar), carpeta de ficheros (superficie de path traversal), broker externo (sobreingeniería).
- Revisitar si: hay mucho volumen (varios workers; `SKIP LOCKED` ya lo permite) o hay que conservar el original (OCR, reindexado).

### ADR-33: Búsqueda vectorial exacta, filtrada siempre por propietario; columna `vector` sin dimensión
- Motivo: con ≤ 20 documentos por usuario el escaneo exacto con `owner_id` en el `WHERE` es rápido y **no puede devolver fragmentos de otro usuario por efecto del índice**: un índice ANN (HNSW) filtrado puede devolver menos resultados de los pedidos. La columna sin dimensión fija evita atarla al modelo y a la configuración de tests; cada fragmento guarda su `embedding_model` y la búsqueda solo compara con el vigente.
- Alternativas descartadas: HNSW/IVFFlat ahora (complejidad y riesgo de filtrado sin necesidad), `vector(768)` fijo.
- Revisitar si: un usuario supera ≈ 100 000 fragmentos, o si se cambia el modelo (hay que reindexar: lo anota el backlog).

### ADR-34: El texto del documento es dato, nunca instrucción
- Motivo: un PDF puede contener "ignora tus instrucciones". Los fragmentos salen de `MaterialSearch` como `Fragment` y las features los pasan con `AiRequest.data(...)`, nunca dentro de una plantilla (`PromptAssembler` ya los separa y escapa). Un test de arquitectura impide que `material/` construya prompts.
- Condición registrada: antes de que F05/F07 envíen texto de un **menor** a un modelo de pago debe existir el filtrado de contenido (A3).
- Revisitar si: el contenido se muestra al usuario como HTML (hoy no: solo texto plano).

### ADR-35: Borrado real y auditoría sin contenido
- Motivo: es material del usuario (no una cuenta): borrar significa borrar documento, fragmentos y vectores. La auditoría registra id, tipo y resultado, **sin nombre ni contenido**.
- Revisitar si: hace falta papelera o recuperar documentos.

## Impacto en lo existente
`SecurityConfig` (sin cambios: ya exige sesión), `GlobalExceptionHandler` (errores de material y `MaxUploadSizeExceededException` → 413), `application.properties` (límite multipart 11 MB, en memoria),
`nginx.conf` (límite de cuerpo), `compose.yaml` y `.env.example` (`MATERIAL_MAX_FILE_MB`, `MATERIAL_MAX_DOCUMENTS`), `RequiredSettingsCheck` (los dos límites > 0), test de arquitectura (`material/` sin proveedores ni plantillas).

## Riesgos y puntos abiertos
- **PDFBox y sus avisos de seguridad:** hay que fijar una versión reciente y revisar sus CVE al actualizar (se anota en el backlog).
- **Bombas de descompresión y PDFs patológicos:** mitigado con límites y tiempo máximo, pero un PDF malicioso sigue siendo la entrada más peligrosa de la app: se prueba con ficheros adversarios generados en los tests.
- **Hilo de procesado y memoria:** un solo documento a la vez; el contenedor del backend aún no tiene `mem_limit` (D5 del backlog).
- **Cambio de modelo de embeddings:** deja los fragmentos antiguos sin usar hasta reindexar (no hay reindexado en v1).
