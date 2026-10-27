# F13 · Borrar cursos — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F13-borrar-cursos.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 83) y `PROJECT.md`. Sin IA, sin tareas ni plantillas nuevas, sin librerías nuevas.

## ART-001 · Stack

Sin cambios: Vue 3 + TypeScript (Reka UI vía `UiDialog`), Spring Boot (JdbcClient), PostgreSQL. El paquete nuevo `coursedelete/` **no habla con el modelo** (nada de `AiGateway`, `MaterialSearch` ni `GenerationLock`; lo comprueba una prueba de arquitectura como la de `courseedit/`/`share/`).

## ART-002 · Modelo de datos (`V11__course_delete.sql`)

```
GRANT DELETE ON course TO studylab_app;      -- hoy solo SELECT, INSERT, UPDATE (V4)
```

Nada más cambia de esquema. Lo que ya hace el esquema al borrar una fila de `course`:

| Tabla | Efecto |
|-------|--------|
| `course_document`, `course_topic`, `course_chapter`, `course_piece`, `lesson_progress`, `exercise_attempt`, `exam_attempt`, `tutor_message`, `share_code`, `course_image` | `ON DELETE CASCADE` (clave compuesta con `owner_id`) |
| `course.origin_id` de las copias | `ON DELETE SET NULL` (V9) |
| `ai_usage.course_id` | sin FK: las filas quedan (AC-5) |
| `course_intake` (referenciada por `course.intake_id`) | no cascada; el servicio **vacía** la fila antes de borrar el curso (D5): `topic = ''`, `questions = NULL`, `document_ids = '[]'`; conserva `created_at`, `status` y `owner_id` (límite por hora) |
| `course_action`, `audit_event`, `ai_usage` | no dependen del curso |

- El permiso `UPDATE` sobre `course_intake` ya existe (V4). Las FK en cascada las ejecuta el motor como dueño de las tablas: el test lo comprueba con el **rol real** (`studylab_app`) y no solo con el dueño de las migraciones.
- Una consulta `BLOCKED` de F04 no se vacía: no tiene curso.

## ART-003 · Endpoints

Ambos exigen sesión y CSRF; el propietario es siempre el usuario de la sesión.

```
DELETE /api/courses/{id}        → 204. Id mal formado, ajeno, inexistente o de un menor visto por su tutor → 404 igual.
POST   /api/courses/delete      {ids:[uuid…]} → 204. 1–50 ids, sin repetidos ni mal formados (400 BAD_REQUEST). Todo o nada.
```

Reglas comunes (servicio `CourseDeleteService.delete(user, ids, ip)`, una sola transacción):
1. Si el usuario es menor → 403 `CHILD_CANNOT_DELETE` (antes de tocar la BD; el 404 de ajeno no se mezcla: el menor recibe 403 siempre).
2. `SELECT id, status, intake_id FROM course WHERE id = ANY(:ids) AND owner_id = :owner ORDER BY id FOR UPDATE` (orden fijo contra interbloqueos entre dos borrados y contra las ediciones de F09, que también bloquean la fila). Si devuelve menos filas que ids → 404 sin decir cuál.
3. Si alguna está `PENDING_GENERATION` o `GENERATING` → 409 `COURSE_BUSY` («Hay cursos que se están generando. Espera a que terminen») sin borrar nada.
4. `UPDATE course_intake` (vaciar) y `DELETE FROM course WHERE id = ANY(:ids) AND owner_id = :owner`.
5. Auditoría `COURSE_DELETED` (una por curso, ids de usuario y curso, nunca el título) escrita dentro de la transacción: si falla el borrado, no queda evento.
- Los 404/403/409 no se auditan (son rechazos y no deben ensuciar el registro); un rechazo no cambia nada.
- Sin límite por hora: borrar es barato, idempotente y no llama a la IA; no hay recurso que proteger (anotado en ADR-84).
- El trabajador de ilustraciones y el de generación no cambian: `saveDone` ya exige `status = 'PENDING'` (se prueba que una imagen que termina tras el borrado no escribe ni lanza).
- Logs: el controlador y el servicio no registran ids de petición con títulos; el test de logs de F01 se amplía.

## ART-004 · Estructura de componentes (frontend)

```
HomeView.vue                  lista de cursos: modo «Seleccionar» (casillas con etiqueta, «Seleccionar todos»,
                              contador, botón «Borrar (n)»)                          AC-10, AC-12
CourseStatusView.vue          botón «Borrar curso» (solo adulto)                      AC-10
components/DeleteCoursesDialog.vue   UiDialog: cuántos cursos, lista de títulos como TEXTO, qué se pierde
                              (lecciones, intentos, conversaciones del tutor, imágenes, códigos), qué se conserva
                              (gasto, copias de otros, «Mi material»), enlace «Exportar antes», Cancelar con foco inicial,
                              «Borrar definitivamente»                                 AC-10, AC-11, AC-12
api/client.ts                 deleteCourse(id), deleteCourses(ids)
```
Menores: no ven el modo de selección ni el botón (el servidor lo rechaza igual). El error `COURSE_BUSY` se muestra con un mensaje claro en el diálogo; un 404 en un borrado de un solo curso se trata como «ya borrado» (AC-11). Tras borrar se refresca la lista y se saca de la selección lo que ya no exista. Todo con tokens y componentes `ui/`; sin `v-html`, sin `confirm()`.

## ART-005 · Estructura de carpetas

```
backend/src/main/java/es/vicai/studylab/coursedelete/
  CourseDeleteController.java   DELETE /api/courses/{id}, POST /api/courses/delete
  CourseDeleteService.java      la transacción de ART-003
  CourseDeleteRepository.java   bloqueo, vaciado de la consulta, borrado (todo con owner_id)
  CourseDeleteException.java    COURSE_BUSY, CHILD_CANNOT_DELETE, 404, 400
backend/src/main/resources/db/migration/V11__course_delete.sql
backend/src/test/java/…/coursedelete/   Delete…Test, CourseDeleteArchitectureTest, CourseDeleteLogsTest
frontend/src/components/DeleteCoursesDialog.vue  (+ tests/ de HomeView, CourseStatusView y el diálogo)
```

## ART-006 · Wireframe

```
Mis cursos                                   [Seleccionar]  [Nuevo curso]
┌────────────────────────────────────────────────────────────┐
│ ☑ Egipto antiguo        🌱 Creciendo        (Estudiar)      │
│ ☑ La célula             🌿 Brote                           │
│ ☐ Volcanes              🌳 Completo                        │
└────────────────────────────────────────────────────────────┘
 2 seleccionados  ☐ Todos            [Cancelar]  [Borrar (2)]

┌ Borrar 2 cursos ───────────────────────────────────┐
│ • Egipto antiguo   • La célula                      │
│ Se pierde para siempre: lecciones, ejercicios,      │
│ intentos, conversaciones con el tutor, ilustraciones│
│ y códigos de compartir.                             │
│ Se conserva: tu gasto de IA, tu material y las      │
│ copias que otras personas ya hicieron.              │
│ ¿Quieres guardarlo antes? Exportar curso            │
│        [Cancelar]        [Borrar definitivamente]   │
└─────────────────────────────────────────────────────┘
```

## ART-007 · ADR

### ADR-84: Borrado definitivo en una transacción, todo o nada, sin papelera ni límite
- Motivo: la necesidad es limpiar la lista. El esquema ya cascada casi todo desde `course`; borrar una fila basta, con el `GRANT DELETE` que faltaba. Una papelera exigiría `deleted_at` filtrado en todas las consultas con `owner_id` de F05 a F12 (y sus pruebas de arquitectura) y un purgado; el riesgo de una consulta que olvide el filtro supera el beneficio. La red de seguridad es el diálogo con lo que se pierde y la exportación previa. «Todo o nada» evita estados a medias en el borrado múltiple; se bloquean las filas en orden de id para no entrelazarse con ediciones ni con otro borrado. Sin límite por hora: no cuesta dinero ni toca la IA.
- Alternativas descartadas: papelera con deshacer (ver arriba); borrado lógico solo ocultando (los datos del menor seguirían ahí y los de los adultos sin poder quitarlos); resultados parciales en el borrado múltiple (más casos y mensajes, sin ganancia real).
- Revisitar si: se pierden cursos por error de forma repetida (entonces papelera de 7–30 días) o se añade borrado automático.

### ADR-85: Qué se conserva — gasto, copias y la fila de la consulta
- Motivo: `ai_usage` es el registro de gasto (solo se añade, y los topes mensuales se suman por usuario, no por curso), así que sus filas con un `course_id` huérfano son correctas. Las copias son cursos independientes (F09): `origin_id` pasa a nulo y no cambian. La fila de `course_intake` se conserva **vaciada** porque el límite por hora de consultas se cuenta sobre sus filas: borrarla permitiría crear-y-borrar sin límite; sin el tema ni las preguntas no queda texto del usuario.
- Alternativas descartadas: borrar también `ai_usage` (pierde el registro de gasto); borrar la fila de la consulta (esquiva el límite A2); conservar el tema (es texto del usuario del curso que quiso quitar).
- Revisitar si: el límite de consultas pasa a su propio contador (como `course_action`).

### ADR-86: El menor no borra, y el tutor adulto tampoco
- Motivo: el adulto tutor ve intentos y conversaciones de su menor (F08) y un clic destruiría esa supervisión; borrar el curso de otro está fuera de lo que F09 permite al tutor. El menor recibe 403 siempre (no un 404), porque su propio curso existe y el motivo no es un secreto.
- Alternativas descartadas: el menor borra con aviso al tutor (la actividad ya borrada no se puede ver); el tutor borra cursos de su menor (cambia la regla de propiedad de F09).
- Revisitar si: los cursos de los menores se acumulan en uso real (opción: archivar para el menor, o borrado por el tutor con auditoría).
