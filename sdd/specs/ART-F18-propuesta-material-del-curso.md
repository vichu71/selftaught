# F18 · Material del curso — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F18-material-del-curso.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 97) y `PROJECT.md`. Backend **mínimo** (un endpoint de lectura), sin migración y sin librerías.

## Ajustes a la FASE 1 que salen de leer el código

| Ajuste | Motivo | Efecto |
|--------|--------|--------|
| **No hace falta migración**: `course_document` (V4) ya enlaza curso y documento con `owner_id` y borrado en cascada | F04 la rellena al aceptar la consulta (`linkDocument`) y `courseDocuments` la lee | AC-4 sin backend; AC-5 solo añade una lectura |
| El endpoint vive en `course/` (`GET /api/courses/material-usage`), **no** en `material/` | `course_document` es de `CourseRepository`; `material/` no debe leer tablas de cursos y `MaterialArchitectureTest` fija sus consultas | La ruta literal gana a `/api/courses/{id}` |
| `DocumentView` **no cambia** | Sus claves exactas las fija un test de F03 (como pasó con `/api/ai/usage`, ADR-92) | «Usado en» llega por el endpoint aparte y se une en el cliente |
| `material.upload` pasa a **devolver el id** del documento subido | El asistente necesita saber cuál marcar; el valor de retorno nuevo no rompe a quien lo ignoraba | AC-2 |
| La selección automática la hace una **función pura** (`autoSelect`) | Se prueba sin red ni DOM: dados los documentos, los ya marcados, los pendientes de marcar y el máximo, devuelve los ids a marcar | AC-2, casos borde |

## ART-001 · Stack

Sin cambios (Vue 3 + TypeScript, Spring Boot, JdbcClient). Iconos Lucide ya en el paquete (`FileText`, `FileType`, `File`, `Library`).

## ART-002 · Modelo de datos y API

**Sin migración.** Un endpoint nuevo, de solo lectura:

```
GET /api/courses/material-usage        (sesión; sin CSRF: no escribe)
200 { "usage": { "<documentId>": [ { "id": "<courseId>", "title": "…" } ] } }
```
- Consulta SQL parametrizada: `SELECT cd.document_id, c.id, c.title FROM course_document cd JOIN course c ON c.id = cd.course_id AND c.owner_id = cd.owner_id WHERE cd.owner_id = :owner ORDER BY c.created_at DESC, c.id`, con `LIMIT 2000` como techo.
- Devuelve solo cursos y documentos **del que llama**; sin uso, `{"usage":{}}`. Sin contenido, hash ni propietario. Un adulto tutor **no** recibe los de su menor (siempre `owner_id` = el que llama).
- El curso ya devuelve `documents: [{id, name}]` en `GET /api/courses/{id}` (sin cambios).

## ART-003 · Estructura de componentes (frontend)

```
api/client.ts            getMaterialUsage(): Promise<Record<string, { id: string; title: string }[]>>
stores/material.ts       upload(file) devuelve el id (o '' si falla); usage (ref) y loadUsage() sin romper la lista si falla
material/autoSelect.ts   autoSelect({ documents, selected, awaiting, max }) → { select: string[]; awaiting: string[] } (pura)   AC-2
stores/course.ts         awaiting (ids subidos desde el asistente que esperan a estar listos)
views/NewCourseView.vue  paso del material: DropZone «Añade los apuntes de este curso», lista «o elige alguno que ya tengas»,
                         watch de los documentos para marcar los listos; arranca/para el sondeo de material
components/CourseMaterial.vue  tarjeta «Material de este curso» (icono por tipo, nombre como texto)                          AC-4
views/CourseStatusView.vue    usa CourseMaterial y quita la línea «Documentos: …»
components/MaterialList.vue   prop `usage`: línea «Usado en» con chips (RouterLink) o «Sin usar todavía»; aviso al confirmar borrado  AC-6, AC-7
views/MaterialView.vue        carga el uso en paralelo y lo pasa a la lista (solo «Mis documentos»)
```
- **Teclado y lector de pantalla:** la zona del asistente es el mismo `DropZone` (la `<label>` del `<input type="file">` real).
- **Texto plano:** títulos de curso y nombres de documento siempre como texto; los chips son `RouterLink` con clase `ui-tag` (sin subrayado).
- **CSS** (`components.css`, con tokens): `.course-material`, `.course-material-item`, `.doc-usage`, `.usage-chip`.
- Los `data-testid` nuevos: `wizard-dropzone`, `wizard-doc-note`, `course-material`, `course-material-item-{id}`, `doc-usage-{id}`, `doc-usage-chip-{docId}-{courseId}`, `doc-unused-{id}`, `delete-usage-note-{id}`.

## ART-004 · Backend

```
course/CourseRepository.java   documentUsage(UUID ownerId): List<UsageRow> (record UsageRow(UUID documentId, UUID courseId, String title))
course/CourseService.java      materialUsage(AuthUser): MaterialUsage (record con Map<String, List<CourseRef>>), agrupando por documento
course/CourseController.java   @GetMapping("/material-usage")
```
- Nada de `AiGateway`, `MaterialSearch` ni `GenerationLock` (lo comprueba `CourseArchitectureTest`, que ya existe).
- Toda consulta lleva `owner_id`; el endpoint no acepta parámetros.

## ART-005 · Plan de pruebas (para la FASE 4)

| Test | Cubre |
|------|-------|
| `course/MaterialUsageTest` (backend) | sesión obligatoria (401); vacío sin cursos; un curso con dos documentos y un documento en dos cursos, más reciente primero; aislamiento (otro usuario, menor ↔ tutor); solo claves `usage`, `id` y `title`; un documento borrado ya no aparece; sin contenido ni hash · AC-5, AC-8 |
| `api/materialusage.spec` | cliente: ruta, método y forma · AC-5 |
| `material/autoSelect.spec` | marca solo los `READY`, respeta el máximo, no desmarca, deja esperando los pendientes, descarta los que fallaron · AC-2 |
| `views/NewCourseMaterial.spec` | zona de subida con «Sí», sube y marca al estar listo, error con motivo, reutilizar, máximo, texto sin documentos · AC-1, AC-2, AC-3, AC-10 |
| `components/CourseMaterial.spec` y `views/CourseMaterialF18.spec` | tarjeta con iconos, nada sin documentos, texto plano hostil · AC-4, AC-8 |
| `components/MaterialUsage.spec` | «Usado en» con chips, «Sin usar todavía», aviso al borrar, fallo del endpoint sin romper la lista · AC-6, AC-7 |
| Existentes F03–F05, F16 | siguen en verde · AC-9 |

## ART-006 · ADR propuestos (entran en `ARCHITECTURE.md` en la FASE 5)

- **ADR-98: el uso de cada documento es un endpoint aparte en `course/`.** Motivo: la relación es de cursos (`course_document`), `material/` no lee tablas de cursos y `DocumentView` tiene su contrato fijado por tests (ADR-92). Alternativas descartadas: ampliar `DocumentView` con `courses` (rompe el contrato y acopla paquetes); calcularlo en el cliente a partir de todos los cursos (un detalle por curso, N peticiones). Revisitar si: hace falta el uso por documento en el propio listado paginado.
- **ADR-99: el asistente sube con la misma subida de Mi material y marca el documento al estar listo.** Motivo: un solo camino de subida (mismas comprobaciones y mensajes) y un curso solo puede usar documentos `READY` (`readyDocuments`). Alternativas descartadas: subir y enlazar en un paso del servidor (cambia F04); permitir elegir documentos sin procesar (el servidor los rechaza). Revisitar si: el procesado deja de ser asíncrono.

## ART-007 · Nota para F19 (imágenes)

No se implementa aquí. Para que F19 no choque: el tipo de documento (`MaterialType`: PDF, DOCX, TEXT) ya es un enum extensible y la subida decide el tipo **por contenido**; las fotos necesitarían una tarea nueva de IA (`VISION`/OCR) por `AiGateway` con su precio por imagen, tope de salida y plantilla, `ContentGuard` sobre el texto extraído para menores, y el texto leído sería **dato no fiable** (ADR de F03). Se anota en el BACKLOG.
