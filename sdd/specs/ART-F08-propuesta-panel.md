# F08 · Panel del adulto tutor — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F08-panel-tutor-adulto.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 58), `PROJECT.md`. Todo es **solo lectura**: no hay tablas nuevas de datos, ni llamadas a IA, ni dependencias nuevas.

## ART-001 · Stack

Sin cambios: Vue 3 + TypeScript, Spring Boot (JdbcClient), PostgreSQL. No se añaden librerías. Las llamadas a IA no existen en esta feature (no se usa `AiGateway`), así que no hay tarea, plantilla ni variable nueva.

## ART-002 · Modelo de datos (`V8__guardian.sql`)

No hay entidades nuevas: el panel lee lo que ya guardaron F01 a F07, siempre filtrando por el **id del alumno** (el `owner_id` de esas tablas) tras comprobar el vínculo.

| Dato | De dónde sale | Para qué |
|---|---|---|
| Vínculo adulto → alumno | `guardianship` (F01), `AccessGuard.requireStudentOf` | AC-8 |
| Cursos y avance | `course`, `course_piece`, `lesson_progress`, `exam_attempt` (F05, F06) | AC-2, AC-3 |
| Intentos | `exercise_attempt`, `exam_attempt` (F06) | AC-4 |
| Conversaciones | `tutor_message` (F07): `OK`, `BLOCKED`, `FILTERED` | AC-5 |
| Avisos | `tutor_message` (`BLOCKED`/`FILTERED`), `course_intake` (`BLOCKED`), `audit_event` (`STUDY_ANSWER_BLOCKED`, `COURSE_CONTENT_BLOCKED`) | AC-6 |
| Consumo del mes | `ai_usage` por usuario, mes en curso (F02); tope de `BudgetService.userLimit` | AC-7 |

Única migración: un **índice** para leer los avisos de la auditoría por actor (hoy solo hay por sujeto):

```sql
CREATE INDEX audit_event_actor_idx ON audit_event (actor_user_id, action, occurred_at);
```

(`audit_event` es de solo añadir: un índice no la modifica.) El rol de la aplicación ya tiene `SELECT` en todas las tablas leídas.

## ART-003 · Endpoints

Todos bajo `/api/tutor/students/{id}/…` (`SecurityConfig` ya exige `ROLE_TUTOR` en `/api/tutor/**`), todos `GET`, **sin CSRF necesario y sin ningún método que modifique**. Cada uno empieza por `AccessGuard.requireStudentOf`: alumno ajeno, inexistente o id mal formado → **404 igual** (y `ACCESS_DENIED` en la auditoría). Los ids de curso y pieza se buscan **con el id del alumno** como propietario: uno de otro alumno → 404.

```
GET /api/tutor/students/{id}/overview
    → { student:{id, displayName, active}, lastActivityAt?, alerts:{last30Days},
        usage:{month, spendUsd, limitUsd, reached, byTask:[{task, spendUsd, calls}]},
        courses:[{id, title, status, percent?, lessonsDone, lessonsTotal, examsPassed, examsTotal, createdAt}] }          AC-1, 2, 7        (no se audita)
GET /api/tutor/students/{id}/courses/{course}
    → { id, title, status, overview: <mismo cálculo de avance que ve el alumno: topics[lessons, exam{attempts,best,last,passed}]> }   AC-3 (no se audita)
GET /api/tutor/students/{id}/courses/{course}/attempts
    → { truncated, pieces:[{pieceId, kind, title, exercises?:[{item, statement, attempts:[{answer, selfResult?, aiResult?, aiComment?, createdAt}]}],
        exams?:[{submittedAt, correct, total, percent, passed, failed:[questionText]}]}] }      AC-4          audita GUARDIAN_VIEW screen=ATTEMPTS
GET /api/tutor/students/{id}/conversations
    → [{courseId, courseTitle, pieceId, lessonTitle, messages, blocked, filtered, lastAt}]      AC-5          audita screen=CONVERSATIONS
GET /api/tutor/students/{id}/courses/{course}/pieces/{piece}/tutor
    → { messages:[{id, role, status, content, sources?, createdAt}] }   (mismo formato que ve el alumno, sin canClear)   AC-5   audita screen=THREAD
GET /api/tutor/students/{id}/alerts
    → [{type, at, courseId?, courseTitle?, pieceId?, lessonTitle?, topic?, category?}]  más reciente primero, máximo 100       AC-6   audita screen=ALERTS
```

Tipos de aviso (`type`): `TUTOR_QUESTION_BLOCKED`, `TUTOR_ANSWER_FILTERED`, `COURSE_TOPIC_BLOCKED` (con `topic` y `category`), `STUDY_ANSWER_BLOCKED`, `COURSE_CONTENT_BLOCKED`.

Validaciones y límites (backend):
- ids con formato UUID (si no, 404); ningún parámetro de consulta libre en v1 (sin filtros ni paginación del cliente: lo acota el servidor).
- `attempts`: los últimos **20 intentos por ejercicio y por examen** y como mucho **200 intentos en total** (`truncated: true` si se recorta); `conversations`: hilos con mensajes visibles; `alerts`: 100.
- Las respuestas no llevan ids de otros usuarios, correo ni identificador de acceso del alumno.
- Auditoría: `GUARDIAN_VIEW` (actor = adulto, sujeto = alumno, detalles `{screen, courseId?, pieceId?}`), **nunca contenido**; se escribe fuera de cualquier transacción.
- Errores: 404 (vínculo o recurso), 401 sin sesión, 403 para un alumno menor (rol). Sin 5xx con detalle.

## ART-004 · Estructura de componentes

```
router: /alumnos/:id                              GuardianStudentView    AC-1, 2, 6, 7, 12
        /alumnos/:id/cursos/:course               GuardianCourseView     AC-3, 4, 12
        /alumnos/:id/cursos/:course/lecciones/:piece   GuardianThreadView   AC-5, 11, 12
StudentsPanel.vue          (existente)  + enlace «Ver actividad» por alumno                         AC-1
components/GuardianAlerts.vue           lista de avisos con enlace a hilo o curso                     AC-6, 11
components/GuardianUsage.vue            gasto del mes frente al tope y reparto por tarea             AC-7
components/GuardianCourses.vue          cursos del alumno con su avance                              AC-2
components/GuardianAttempts.vue         intentos de ejercicio y de examen de un curso (solo texto)    AC-4, 11
components/ThreadView.vue               hilo de solo lectura: `MarkdownContent` para el tutor, texto para el alumno, marca de BLOCKED/FILTERED   AC-5, 11
HomeView.vue               (existente)  aviso al menor nombra también respuestas e intentos           AC-10
```

Ninguna pantalla tiene botones que escriban. Los textos del alumno y del modelo se pintan como texto o con `MarkdownContent` (nada de `v-html`). Los errores no enseñan códigos del backend.

## ART-005 · Estructura de carpetas (solo lo nuevo)

```
backend/src/main/java/es/vicai/studylab/guardian/
  GuardianController.java      GET bajo /api/tutor/students/{id}/…; cada método pasa por AccessGuard
  GuardianService.java         arma las vistas; llama a StudyService/UsageRepository por id de alumno; audita fuera de transacción
  GuardianRepository.java      consultas de solo lectura (todas con owner_id = alumno): conversaciones, avisos, intentos con enunciado
backend/src/main/resources/db/migration/V8__guardian.sql
backend/src/test/java/es/vicai/studylab/guardian/   (Access, Overview, Course, Attempts, Conversations, Alerts, Usage, Audit, Architecture, ReadOnly)
frontend/src/views/Guardian{Student,Course,Thread}View.vue
frontend/src/components/Guardian{Alerts,Usage,Courses,Attempts}.vue, ThreadView.vue
frontend/tests/views/Guardian*.spec.ts, tests/api/guardian.spec.ts
```

Cambios mínimos en lo existente: `StudyService` gana métodos que reciben el **id del alumno** (`progressFor`, `overviewFor`) y los actuales los llaman con el del usuario; `UsageRepository` gana el reparto del mes por tarea.

## ART-006 · Wireframe

```
┌ Panel de Hugo ──────────────────────────────  Volver ┐
│ ⚠ Avisos (3)                                          │
│  25 oct  Pregunta bloqueada al tutor · Lección 1.1 → ver conversación
│  24 oct  Tema de curso rechazado: «…» (violencia)
│  23 oct  Respuesta de ejercicio no enviada a corregir · Curso Historia
│ ───────────────────────────────────────────────────── │
│ IA este mes: 0,42 USD de 2,00 USD     Lecciones 0,30 · Ejercicios 0,07 · Tutor 0,05 │
│ ───────────────────────────────────────────────────── │
│ Cursos                                                │
│  Segunda Guerra Mundial   READY   ▓▓▓▓▓░░░ 62 %  →   │
│  Fracciones               GENERATING …                 │
│ ───────────────────────────────────────────────────── │
│ Conversaciones con el tutor                            │
│  Historia · Lección 1.1   12 mensajes · 1 bloqueado → │
└────────────────────────────────────────────────────────┘
Curso: avance por tema, exámenes (mejor/última nota) y, desplegables, los intentos con su respuesta y el veredicto.
Hilo: mensajes del alumno y del tutor, con las marcas «Bloqueado» y «Filtrado»; solo lectura.
```

## ART-007 · ADR (propuestos; se numeran a continuación de ADR-58)

### ADR-59: El panel es un paquete de solo lectura que reutiliza los servicios por id de alumno
- Motivo: el adulto ve lo mismo que el alumno, calculado igual (el avance, los resultados) y sin duplicar reglas. Se añaden a `StudyService` variantes que reciben el id del alumno; cada endpoint empieza por `AccessGuard.requireStudentOf` y el id de curso o pieza se
  busca con el alumno como propietario (uno de otro alumno → 404). `guardian/` no escribe nada salvo la auditoría y un test de arquitectura lo exige (ni `INSERT`, `UPDATE` ni `DELETE`, ni `@PostMapping`).
- Alternativas descartadas: dejar que el adulto use los endpoints del alumno con otro propietario (rompería «todo dato se filtra por su propietario»), copiar las consultas de F06 (dos fuentes de verdad), una vista materializada (sin necesidad a escala familiar).
- Revisitar si: un alumno pasa a tener más de un adulto (v2) o aparece un rol de administración.

### ADR-60: Los avisos se calculan de lo que ya se guarda, sin tabla nueva
- Motivo: lo que el filtro para ya deja rastro: `tutor_message` (`BLOCKED`, `FILTERED`), `course_intake` (`BLOCKED`, con tema y categoría «para el tutor», ADR-37) y la auditoría (`STUDY_ANSWER_BLOCKED`, `COURSE_CONTENT_BLOCKED`, con ids). Una consulta con `UNION` los
  ordena y acota a 100. Solo hace falta un índice por actor en `audit_event`. El adulto ve el texto en el hilo o en el tema rechazado (decidido en FASE 1); la auditoría sigue sin contenido.
- Alternativas descartadas: una tabla `guardian_alert` escrita por cada feature (acopla F04 a F07 con el panel y duplica datos), avisos por correo (fuera de alcance).
- Revisitar si: hay muchos eventos por alumno (paginar) o se purgan los intentos bloqueados (F4-1): el aviso desaparecería con el dato.

### ADR-61: Cada consulta de actividad se audita con ids
- Motivo: leer conversaciones y respuestas de un menor es una operación sensible, y el menor tiene derecho a saber que ocurre. `GUARDIAN_VIEW` registra adulto, alumno y pantalla (`ATTEMPTS`, `CONVERSATIONS`, `THREAD`, `ALERTS`) y los ids de curso y pieza, nunca el contenido.
  El resumen y el consumo, que no llevan textos del alumno, no se auditan. Se escribe fuera de transacción (lección de F01).
- Alternativas descartadas: auditar todo (ruido), no auditar (sin trazabilidad), mostrar al menor «tu adulto te vio» (F09+).
- Revisitar si: el menor debe poder consultar quién vio su actividad.

### ADR-62: Consumo del alumno: gasto del mes frente a su tope, repartido por tarea
- Motivo: `ai_usage` ya guarda coste por usuario y tarea; el tope personal del alumno lo da `BudgetService.userLimit` (ADR-29). Se muestra USD y número de llamadas, no tokens ni modelos (decidido en FASE 1). El gasto es del mes en curso, igual que `/api/ai/usage`.
- Alternativas descartadas: tokens y modelos por llamada (detalle sin utilidad para un adulto), un histórico de meses (v2).
- Revisitar si: se quiere cambiar el tope de un alumno desde el panel (hoy es configuración, fuera de alcance).

## Impacto en lo existente

- `StudyService`: nuevas variantes por id de alumno (sin cambiar los endpoints del alumno); `UsageRepository`: reparto por tarea; `HomeView`: texto del aviso; `StudentsPanel`: enlace al panel; `router`: tres rutas.
- Sin cambios en `SecurityConfig` (`/api/tutor/**` ya exige adulto), `GlobalExceptionHandler` ni en la capa de IA.
- `StudyController` mantiene el 404 del adulto sobre los endpoints del alumno (el panel usa los suyos).

## Riesgos y puntos abiertos

- **Un alumno desactivado**: `isTutorOf` debe seguir devolviendo verdadero para consultarlo (hay que comprobarlo; si excluye desactivados, el panel lo corrige y se prueba).
- **Volumen de intentos**: el recorte (20 por ejercicio, 200 en total) mantiene la respuesta pequeña; si un adulto necesita el histórico completo, F-posterior.
- **Aviso por tema rechazado**: lleva el texto del tema (puede ser delicado); solo lo ve el adulto vinculado y queda auditado.
- **Sin límite de frecuencia** en el panel: son lecturas de un adulto autenticado sobre datos acotados; si se abusa, límite por usuario en la BD (A2 ya cubre otras features).
- **F7-6 y F6-6** no se tocan aquí.
