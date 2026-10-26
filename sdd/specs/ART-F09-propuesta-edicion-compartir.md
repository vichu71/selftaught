# F09 · Edición de temario y compartir por código — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F09-edicion-compartir.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 62) y `PROJECT.md` (§6 y §7). No hay un segundo generador: lo nuevo lo genera la cola de F05.

## ART-001 · Stack

Sin cambios: Vue 3 + TypeScript, Spring Boot (JdbcClient), PostgreSQL. Sin librerías nuevas. Las ediciones que piden IA usan **la cola, el cerrojo, el tope de piezas y el control de gasto que ya existen** (F05 y F02); la edición a mano y el canje no llaman a ningún modelo. No hay tarea,
plantilla ni variable de IA nuevas; los valores de ingeniería nuevos van en `app.course-edit.*` y `app.share.*`, sin variable de entorno.

## ART-002 · Modelo de datos (`V9__share_edit.sql`)

```
course         + version int NOT NULL DEFAULT 1                  sube con cada edición guardada (AC-7)
               + origin_id uuid NULL REFERENCES course (id) ON DELETE SET NULL       curso del que es copia (AC-10)
               + origin_version int NULL                         versión del original en el momento de copiar
course_piece   + outdated boolean NOT NULL DEFAULT false        «la lección cambió» / «el temario del tema cambió»; se limpia al regenerar (AC-4)

share_code     id uuid PK, course_id + owner_id (FK compuesta → course (id, owner_id) ON DELETE CASCADE),
               code_hash char(64) NOT NULL UNIQUE (SHA-256 del código normalizado; el código NO se guarda),
               created_at, expires_at NOT NULL, revoked_at NULL, uses int NOT NULL DEFAULT 0 · índice (course_id, owner_id)

course_action  id bigserial PK, user_id → app_user, kind ('AI_EDIT' | 'REDEEM_OK' | 'REDEEM_FAIL'), created_at · índice (user_id, kind, created_at)
               contador por usuario de ediciones con IA, canjes con éxito y canjes fallidos (AC-5, AC-12); se purgan los de más de un día
```

- El curso copiado necesita su propia fila `course_intake` (`CONSUMED`, con el tema de la ficha): `course.intake_id` es obligatorio y único.
- Las filas de `course_topic`, `course_chapter` y `course_piece` de la copia tienen ids nuevos, pertenecen a quien canjea y conservan `position` y `seq`.
- `GRANT SELECT, INSERT, UPDATE, DELETE` sobre `share_code` y `course_action` al rol de la aplicación; `course_topic` y `course_chapter` ganan `UPDATE`/`DELETE` (hoy solo `SELECT, INSERT, UPDATE` en `V5`: se añade `DELETE`).
- Lo que **no** se copia no tiene columna que lo enlace: los documentos (`course_document`), las fuentes de las lecciones (`course_piece.sources` queda nulo), el progreso, los intentos, los hilos del tutor y el uso de IA.

## ART-003 · Endpoints

Todos exigen sesión y CSRF y buscan **con el usuario de la sesión como propietario** (ajeno o inexistente → 404 igual). Salvo que se indique, devuelven el detalle del curso actualizado (el de `GET /api/courses/{id}`, que gana `version`, `copy: {originVersion}` y `outdated` por pieza).

```
-- editar el temario (AC-1, 2, 5, 6, 7) — 409 COURSE_BUSY si el curso está en cola o generándose; un solo cambio por petición; sube version
PUT    /api/courses/{c}/topics/order                  {ids:[…]}                        mismos temas, sin repetidos ni ajenos (400/404)
PUT    /api/courses/{c}/topics/{t}                    {title, summary}                 título 1–120, resumen 1–300, sin caracteres de control
DELETE /api/courses/{c}/topics/{t}                    no el último tema; borra sus piezas y con ellas progreso, intentos y conversaciones (400)
POST   /api/courses/{c}/topics                        {title, summary, chapter:{title, objective}}   201; crea tema, capítulo, lección, ejercicios y (si el curso lleva examen por tema) examen PENDING
PUT    /api/courses/{c}/topics/{t}/chapters/order     {ids:[…]}
POST   /api/courses/{c}/topics/{t}/chapters           {title, objective}               201; título 1–120, objetivo 1–200; marca outdated el examen del tema
PUT    /api/courses/{c}/chapters/{ch}                 {title, objective}
DELETE /api/courses/{c}/chapters/{ch}                 no el último capítulo del tema (400); marca outdated el examen del tema
-- editar y regenerar piezas (AC-3, 4)
PUT    /api/courses/{c}/pieces/{p}/content            {markdown}                       solo lecciones DONE, 1–20 000 caracteres; borra sus fuentes; marca outdated sus ejercicios y el examen del tema
POST   /api/courses/{c}/pieces/{p}/regenerate         → 202 pieza PENDING; borra el contenido, los intentos, el progreso y el hilo que dependían de ella; marca outdated a sus dependientes
-- compartir (AC-8, 9, 10, 12)
POST   /api/courses/{c}/share                         {days?}  → 201 {id, code, expiresAt}; days 1–90 (7 por defecto); curso READY (409 COURSE_NOT_READY); máx. 5 activos (409 SHARE_LIMIT); el código se devuelve UNA vez
GET    /api/courses/{c}/share                         → [{id, createdAt, expiresAt, revokedAt?, uses, active}]  sin el código
DELETE /api/courses/{c}/share/{id}                    → 204 idempotente; revoca
POST   /api/courses/redeem                            {code} → 201 {id, title} (la copia); código normalizado (mayúsculas, sin guiones ni espacios, 16 caracteres del alfabeto);
                                                      404 si no vale (mal escrito, caducado, revocado o inexistente: igual) · 429 CODE_RATE_LIMITED · 422 si un menor y el filtro salta (mensaje acordado)
```

Reglas comunes:
- **Límites por usuario en la BD** (`course_action`, con un cerrojo por usuario): añadir con IA y regenerar → 20 por hora (429 `EDIT_RATE_LIMITED`, sin cambiar nada ni llamar al modelo); canjear → 10 fallos y 10 copias por hora (429 `CODE_RATE_LIMITED`). Nada por IP (D1).
- **Menor:** `ContentGuard.localCheck` sobre todo lo que escribe (títulos, resúmenes, objetivos, texto de lección) **antes** de guardarlo o de enviarlo a la IA (422, mensaje acordado, auditoría sin texto) y sobre toda la copia antes de crearla.
- **Auditoría (solo ids):** `COURSE_SHARE_CREATED`, `COURSE_SHARE_REVOKED`, `COURSE_SHARE_REDEEMED` (actor = quien canjea, sujeto = propietario del original), `SHARE_CODE_FAILED`, `COURSE_COPY_BLOCKED` y `COURSE_EDIT_BLOCKED`. Nunca el código ni los textos.
- **Panel de F08:** los dos últimos eventos se suman a los avisos (`COURSE_COPY_BLOCKED`, `COURSE_EDIT_BLOCKED`) con una línea más en su `UNION`.
- Errores: 400 validación · 404 ajeno o inexistente · 409 estado · 422 filtro · 429 límite · sin 5xx con detalle.

## ART-004 · Estructura de componentes

```
router: /cursos/:id/editar        CourseEditView          AC-1..7, 16
        /codigo                   RedeemView              AC-10, 12, 13, 16
CourseStatusView.vue   (existente)  + enlaces «Editar curso» y «Compartir» solo para el propietario, y «copia de un curso (versión n)»
HomeView.vue           (existente)  + enlace «Tengo un código»
components/OutlineEditor.vue        temas y capítulos: subir/bajar, renombrar, añadir, quitar con confirmación         AC-1, 2
components/LessonEditor.vue         texto de la lección en Markdown con vista previa (MarkdownContent) y guardar        AC-3
components/PieceActions.vue         «Regenerar con IA» con confirmación y aviso «la lección cambió» (outdated)          AC-4
components/ShareCodes.vue           crear (caducidad), mostrar el código una vez con «Copiar», lista de estado y revocar AC-8, 9
```

Nada se pinta con `v-html`. Los errores nunca enseñan códigos del backend. No hay arrastrar y soltar (v1: botones).

## ART-005 · Estructura de carpetas (solo lo nuevo)

```
backend/src/main/java/es/vicai/studylab/courseedit/   CourseEditController · CourseEditService (cambios de temario y de lección, regenerar) · CourseEditRepository · CourseEditProperties
backend/src/main/java/es/vicai/studylab/share/        ShareController · ShareService (códigos y canje) · ShareRepository · CourseCopier (la copia y su filtro) · ShareProperties
backend/src/main/resources/db/migration/V9__share_edit.sql
backend/src/test/java/es/vicai/studylab/{courseedit,share}/
frontend/src/views/{CourseEdit,Redeem}View.vue · components/{OutlineEditor,LessonEditor,PieceActions,ShareCodes}.vue
frontend/tests/views/{CourseEdit,Redeem}.spec.ts · tests/components/{OutlineEditor,LessonEditor,ShareCodes}.spec.ts · tests/api/courseedit.spec.ts
```

Cambios en lo existente: `CourseContentService` (detalle: `version`, `copy`, `outdated`), `GuardianRepository` (dos acciones más en los avisos), `application.properties` (comentario de `app.course-edit.*` y `app.share.*`).

## ART-006 · Wireframe

```
┌ Editar «Historia» (versión 4) ──────────────────────────  Volver ┐
│ ⚠ La lección 1.2 cambió: sus ejercicios y el examen pueden estar desactualizados     [Regenerar con IA]
│ 1. Tema: La Antigüedad                                [↑][↓][Renombrar][Quitar][+ Capítulo]
│     1.1 Egipto                                        [↑][↓][Renombrar][Quitar][Editar lección]
│     1.2 Grecia                                        [↑][↓][Renombrar][Quitar][Editar lección]
│ 2. Tema: La Edad Media ...
│ [+ Añadir tema]                                      (se genera con IA al guardar; cuenta para tu límite y tu tope)
├─ Compartir ─────────────────────────────────────────────────────┤
│ [Crear código · caduca en 7 días ▾]   Código nuevo: K7QM-4XAP-9TZE-2HDV  [Copiar]   (solo se ve ahora)
│ Código creado 25 oct · caduca 1 nov · 3 usos · activo                                [Revocar]
└────────────────────────────────────────────────────────────────┘
Tengo un código:  [____-____-____-____] [Obtener mi copia]
```

## ART-007 · ADR (propuestos; se numeran a continuación de ADR-62)

### ADR-63: Editar es operar sobre las filas de F05 con el curso bloqueado; lo nuevo lo genera la cola, y regenerar solo por petición del propietario
- Motivo: el temario y las piezas ya son filas con estado (ADR-42). Cada edición es una transacción que bloquea la fila del curso (`FOR UPDATE`), comprueba que no esté en cola ni generándose (AC-6), cambia las filas y sube `version`. Añadir tema o capítulo crea sus piezas `PENDING`
  y devuelve el curso a la cola: el generador existente solo mira `PENDING` y `FAILED`, así que genera **solo lo nuevo** con su cerrojo, su tope de piezas y su control de gasto, sin segundo camino hacia el modelo. Regenerar una pieza `DONE` pasa por `PENDING` solo ante una petición explícita y confirmada del
  propietario (amplía ADR-42: «una pieza DONE no se regenera nunca» pasa a «nunca por sí sola»). Lo que dependía de la pieza (intentos, progreso, hilo) se borra con confirmación; sus dependientes se marcan `outdated` y no se regeneran solos (decisión del usuario).
- Alternativas descartadas: un generador de edición aparte (duplica cerrojo, gasto y validadores), regenerar los ejercicios al editar la lección (coste imprevisible), permitir edición durante la generación (carreras con el generador).
- Revisitar si: se quiere editar ejercicios y preguntas a mano, o editar mientras se genera.

### ADR-64: Los códigos son 80 bits aleatorios guardados como hash, mostrados una vez; el canje no distingue por qué falla y se limita por usuario
- Motivo: un código es un secreto de acceso a un contenido. Se genera con `SecureRandom` (16 caracteres de un alfabeto de 32 sin ambiguos = 80 bits), se guarda solo su SHA-256 (una fuga de la BD no regala cursos) y se enseña una vez; el propietario ve su estado y puede revocarlo. El canje normaliza el texto, busca por hash y devuelve el mismo 404 para
  mal escrito, caducado, revocado o inexistente (sin enumeración). Los fallos y las copias por hora se cuentan en la BD por usuario (`course_action`, con un cerrojo por usuario), no por IP (D1). Es la única consulta del proyecto que lee un curso ajeno sin `owner_id`; lo hace por hash, comprueba caducidad y revocación en la
  misma sentencia y solo entrega al canje, nunca al llamante.
- Alternativas descartadas: guardar el código en claro (se podría ver de nuevo, pero una fuga lo expondría todo), enlaces con UUID de curso (adivinables si se filtran), límite por IP (no fiable hasta D1).
- Revisitar si: se quiere volver a ver un código (se crearía otro) o añadir más límites cuando D1 esté resuelto.

### ADR-65: La copia es un fork de lo hecho, sin fuentes ni datos del original, y con el filtro de menores sobre todo el texto
- Motivo: quien canjea recibe el temario y el contenido de las piezas hechas, con ids nuevos y como propietario, con `origin_id` y `origin_version` (para avisar de versiones nuevas en el futuro) y su propia `version` 1. No se copian los documentos, las fuentes (se quitan también las marcas `[n]`, que ya no apuntan a nada), el progreso, los intentos,
  las conversaciones, las consultas del asistente ni el uso de IA: lo que no tiene columna que lo enlace no puede colarse. Solo se comparten cursos `READY`, así que la copia nace completa. Si quien canjea es un menor, todo el texto de la copia pasa por `ContentGuard` antes de crearla y, si salta, no se crea nada (ADR-39 y 57).
- Alternativas descartadas: compartir por referencia (el original cambiaría bajo la copia), copiar también las fuentes (filtra ids de documentos de otro usuario), copiar progreso (datos del usuario).
- Revisitar si: se avisa de versiones nuevas del original o se permite copiar cursos a medias.

### ADR-66: Lo desactualizado se marca, no se regenera, y la versión sube con cada edición
- Motivo: editar una lección deja intactos sus ejercicios y el examen del tema, pero los marca `outdated` y el propietario decide si regenerarlos (con su coste). Añadir o quitar un capítulo marca el examen del tema. Regenerar limpia la marca. `version` sube una unidad por edición guardada (no por visita ni por regeneración fallida) y la copia guarda la del
  original en ese momento.
- Alternativas descartadas: marcar también las preguntas ya hechas por el alumno (sin significado), una versión por pieza (excesivo), no marcar nada (el alumno estudiaría ejercicios que ya no encajan sin saberlo).
- Revisitar si: se pueden editar ejercicios a mano.

## Impacto en lo existente

- **F05:** `CourseGenerator` no cambia; la regla «una pieza DONE no se regenera nunca» se matiza (ADR-63) y `CLAUDE.md` lo dice.
- **F06:** borrar una pieza borra su progreso, intentos y hilo por las claves `ON DELETE CASCADE` que ya existen; el avance se recalcula solo (ADR-49).
- **F07:** el hilo de una lección regenerada se borra con ella; las lecciones nuevas tienen su chat vacío.
- **F08:** el panel suma `COURSE_COPY_BLOCKED` y `COURSE_EDIT_BLOCKED` a sus avisos; ve los cursos copiados como cualquier otro del menor.
- `SecurityConfig` sin cambios (todo exige sesión; CSRF en lo que cambia datos).

## Riesgos y puntos abiertos

- **Borrado de datos:** quitar un tema o regenerar elimina progreso y conversaciones sin deshacer; la interfaz lo dice y pide confirmación, y el backend solo lo hace con la petición explícita.
- **Carreras entre edición y generador:** las ediciones exigen que el curso no esté en cola ni generándose y bloquean la fila; `recoverInterrupted` de F05 no se toca.
- **Texto largo de lección (20 000 caracteres)** frente al límite de entrada del modelo al generar ejercicios (`max-input-chars`): una lección editada muy larga puede hacer fallar la regeneración de sus ejercicios con un error amable (`INPUT_TOO_LARGE` ya se trata como pieza fallida).
- **Copia de un menor con contenido dudoso:** la lista local del filtro es corta y en español (F4-3); queda como riesgo ya asumido.
- **Contenido compartido sin revisar:** un adulto puede compartir con un código algo que él mismo editó; no hay moderación (fuera de v1).
