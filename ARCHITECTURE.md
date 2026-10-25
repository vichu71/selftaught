# Arquitectura — Study Lab (nombre provisional)

> Se actualiza al cerrar cada feature. Estado: **F01 (base técnica, cuentas y roles)**, **F02 (capa de IA y control de gasto)**,
> **F03 (material propio, subida e indexado)** y **F04 (asistente de creación de curso)** implementadas y probadas.
> Informes de pruebas y seguridad: `sdd/reports/F01-testing-report.md`, `F02-testing-report.md`, `F03-testing-report.md` y `F04-testing-report.md`;
> resumen de cada ciclo en `sdd/reports/F0x-ciclo-completado.md`. Pendientes: `sdd/BACKLOG.md`.

## Visión general

```
 Navegador ──► nginx (frontend, 127.0.0.1:8080)
                 │  /            → web Vue 3 (ficheros estáticos)
                 │  /api/*       → proxy interno ──► backend (Spring Boot :8080, red interna)
                 │                                     │
                 │                                     ├──► db (PostgreSQL + pgvector)
                 │                                     ├──► SMTP (Mailpit en dev / servidor real en prod)
                 │                                     └──► ollama (embeddings: `nomic-embed-text`, 768 dimensiones)
 Un único origen: la cookie de sesión es SameSite=Strict y no hace falta CORS.
```

Flujo de sesión: `login` → el backend crea una fila en `auth_session` (guarda solo el hash del token) y
entrega la cookie `STUDYLAB_SESSION` (HttpOnly) más `XSRF-TOKEN` para el CSRF. Cada petición pasa por
el filtro de sesión → `AccessGuard` (permisos) → controlador. Todo evento relevante se anota en `audit_event`.

## Capa de IA (F02)

```
 feature (F04–F07) ──► AiGateway ──► PromptAssembler    plantilla fija + datos aparte, escapados        (AC-16)
                          │  ├────► BudgetService        gasto del mes, avisos al 80 % y 100 %, corte opcional  (AC-8, 9)
                          │  ├────► UsageRepository      ai_usage: un registro por intento, sin contenido      (AC-6)
                          │  └────► reintentos (máx. 2)  solo ante timeout, 429 transitorio y 5xx              (AC-12)
                          ▼
                     AiProvider (puerto)
       ├─ FakeProvider (dev y tests)   ├─ AnthropicProvider   ├─ OpenAiProvider   ├─ GeminiProvider
       └─ OllamaProvider (embeddings, contenedor `ollama`)
 GenerationLock ── una generación de curso a la vez por usuario, fila en BD con caducidad de 20 min,
                   renovable mientras la generación sigue viva (`Lease.renew()`)                                (AC-11)
 GlobalExceptionHandler ── una AiException que sale por un endpoint tiene su estado HTTP y un mensaje genérico (ADR-28)
 BenchmarkRunner ── solo con el perfil `benchmark` (servicio de Compose de un solo uso, tope propio)         (AC-17)
```

Las features piden una **tarea** (`OUTLINE`, `LESSON`, `ASSESSMENT`, `TUTOR`, `EMBEDDING`); qué proveedor y modelo la atienden es
configuración. El texto del usuario o del material viaja siempre como dato, nunca dentro de las instrucciones.

## Material propio (F03)

```
 Navegador ──► POST /api/material (multipart)                       AC-1..5
                 │  MaterialService ── valida (tamaño, nº de documentos, firma del fichero), sanea el nombre,
                 │                     calcula el hash y guarda el ORIGINAL en source_document.content (bytea)   → 202 UPLOADED
                 ▼
          MaterialWorker (un hilo; cola en BD: UPLOADED → PROCESSING → READY | ERROR)                            AC-9
                 │  FileTypeDetector ─► PdfTextExtractor | DocxTextExtractor | PlainTextExtractor              AC-5..7
                 │  Chunker ─► fragmentos de ≈ 1 000 caracteres con solape, con página y desplazamiento
                 │  AiGateway.embed (tarea EMBEDDING → Ollama, coste 0) en lotes de 16
                 └─► UNA transacción: bloquea el documento, guarda fragmentos + vectores, borra el original    AC-8
 feature futura (F05/F07) ──► MaterialSearch.search(userId, consulta, k, documentos?)                           AC-12
                 │  embedding de la consulta + similitud por coseno, siempre con owner_id = userId
                 └─► List<Fragment>: texto, documento, página, posición, puntuación → se pasan al modelo con AiRequest.data(...)  AC-13
```

El material es una **biblioteca del usuario** (no pertenece a un curso). Lo ve su propietario y, en solo lectura, el tutor de un menor;
solo el propietario lo borra (borrado real: documento, fragmentos y vectores). La búsqueda no se expone por HTTP en F03.

## Asistente de creación de curso (F04)

```
 Navegador ──► POST /api/courses/intake {topic, documentIds}                                                  AC-1..5, 8..13
                 │  1. valida el tema y los documentos (propios y READY)  ·  2. límites por usuario: 30 consultas/h y 5 cursos pendientes (A2)
                 │  3. si es MENOR: ContentGuard.localCheck(tema) ── bloquea sin llamar al modelo ─► 422 TOPIC_NOT_ALLOWED
                 │  4. AiGateway.complete(INTAKE): tema + audiencia (escolar|general) como DATOS → JSON con valoración y 0–3 preguntas
                 │  5. IntakeParser valida el JSON contra un esquema estricto (un reintento; si falla, 0 preguntas y valores por defecto)
                 │  6. MENOR + «no apropiado» ─► consulta BLOCKED (queda para el tutor, F08) ─► 422  ·  si no, consulta OPEN con las preguntas
                 ▼                                                                       guardadas por el SERVIDOR (course_intake)
 Navegador ──► POST /api/courses {intakeId, answers}                                                          AC-5..7, 13, 14
                 │  SheetFactory: respuestas validadas contra las preguntas guardadas + valores por defecto → ficha v1
                 └─► course (PENDING_GENERATION) + course_document   (idempotente: un curso por consulta)  ──►  F05 lo recogerá
 safety.ContentGuard ── capa local reutilizable por F05 y F07 antes de enviar texto de un menor a un modelo de pago (ADR-39, cierra A3)
```

La **ficha del curso** (`course.sheet`, JSON con `schemaVersion: 1`) es el contrato con F05: tema, enfoque, nivel de partida, objetivo, número de temas (3 a 12),
examen por tema y ids de documentos. F04 solo envía al modelo el **tema**; nunca el texto de un documento. Las respuestas del modelo nunca llegan al cliente sin validar.

## Generación del curso (F05)

```
 course (PENDING_GENERATION) ──► GenerationWorkerLoop (2 hilos, sondeo 3 s; apagado en tests) ──► CourseGenerator.processPending()
   1. reclama un curso con FOR UPDATE SKIP LOCKED, de un propietario activo y sin otro curso GENERATING  ·  GenerationLock.acquire (una generación por usuario)
   2. sin temario: AiGateway OUTLINE (ficha + audiencia como DATOS) ─► OutlineParser ─► temas, capítulos y TODAS las piezas (PENDING) en una transacción
        └─ menor y «appropriate=false» ─► FAILED / TOPIC_NOT_ALLOWED (cierra F4-5), sin generar nada
   3. por cada pieza PENDING o FAILED, en orden seq:  lease.renew() (si false, aborta sin escribir)  ·  con material: MaterialSearch (k = 4) ─► SourcePack
        (fragmentos numerados, solo de los documentos del curso; para un menor se descartan los que ContentGuard bloquea)  ·  AiGateway LESSON | ASSESSMENT
        ─► LessonValidator | ExercisesParser | ExamParser  ·  menor: ContentGuard sobre lo generado  ·  DONE
   4. validación fallida: un reintento (para un menor, con instrucción reforzada); si falla otra vez, FAILED y sigue con la siguiente pieza
   5. AiException USER_LIMIT / BUDGET_BLOCKED ─► PAUSED / BUDGET · PROVIDER_* / CONFIG ─► PAUSED / PROVIDER · USER_NOT_ALLOWED ─► vuelve a la cola · cerrojo perdido ─► vuelve a la cola
   6. fin: todo DONE ─► READY  ·  alguna FAILED ─► PAUSED / CONTENT  ·  libera el cerrojo y audita (sin contenido)
 POST /api/courses/{id}/resume ─► PAUSED → PENDING_GENERATION (enfriamiento de 60 s); el generador continúa solo por lo que falta; una pieza DONE no se regenera nunca
```

Cada petición a la IA lleva los datos `pieza` (`temario | leccion | ejercicios | examen`) y `audiencia` (`escolar | general`); las lecciones con material llevan además `fuentes` (fragmentos numerados, sin
nombres de documento) y los ejercicios la `leccion` ya generada. La lección es Markdown y los ejercicios y exámenes JSON estricto (ADR-43). El frontend pinta el Markdown con `marked.lexer` y elementos Vue de
una lista cerrada, sin HTML (ADR-45). El paquete `course/` sigue sin poder leer material: la generación vive en `generation/`, el único que usa `MaterialSearch`.

## Estudio (F06)

```
 Alumno ──► GET /api/courses/{id}/study          progreso por lección, tema y curso, calculado en el servidor (ADR-49)               AC-1..4, 12
       ──► PUT …/pieces/{lección}/done           «Marcar como hecha» (idempotente; desmarcable)                                          AC-3
       ──► GET …/pieces/{pieza}                  lección con anterior/siguiente · ejercicios SIN solución · examen SIN opción correcta (ADR-50)
 Ejercicio:  POST …/exercises/{i}/attempts {answer} ──► guarda el intento y devuelve solución y explicación                               AC-5
             PUT  …/attempts/{a}/result            el alumno marca CORRECT | PARTIAL | INCORRECT
             POST …/attempts/{a}/ai-grade ──► «Corregir con IA» (ADR-52): propietario → ya pedida → límites (hora/día) → una a la vez →
                                              menor: ContentGuard sobre la respuesta → AiGateway ASSESSMENT (datos: enunciado, solución, respuesta)
                                              → GradeParser (esquema estricto, un reintento) → veredicto y comentario (menor: comentario filtrado)   AC-6, 7
 Examen:     POST …/exam/attempts ──► intento STARTED con las preguntas sin respuestas (reutiliza uno abierto)                             AC-8
             POST …/exam/attempts/{a}/submit {answers} ──► ExamGrader corrige sin IA y devuelve nota, opción correcta y explicación; entregar
                                              otra vez devuelve el mismo resultado (ADR-51); nota mínima 50 % para superar el tema         AC-9, 10
             GET  …/attempts · …/exam/attempts/{a} ──► historial y «qué fallé»                                                           AC-11
```

El **progreso no se guarda**: una lección cuenta si hay fila en `lesson_progress` y su pieza está `DONE`, y un examen si algún intento entregado llega al 50 %. El denominador son todas las piezas del temario.
La solución de un ejercicio y la opción correcta de un examen **no salen del servidor** hasta que el alumno responde o entrega (la exportación del curso sí las lleva: es su propio contenido). Las respuestas del alumno
no van a logs ni a la auditoría. Los límites (120 envíos por hora, 20 correcciones de IA al día, 200 intentos guardados por pieza) se cuentan en la BD.

## Tutor IA por lección (F07)

```
 Alumno ──► POST /api/courses/{c}/pieces/{lección}/tutor {question}                                                                          AC-1..16
   1. valida ids (404), lección DONE (409) y pregunta de 1 a 1 000 caracteres sin caracteres de control (400)
   2. transacción con cerrojo por usuario (pg_advisory_xact_lock): límites 30/h y 150/día (429 TUTOR_RATE_LIMITED), respuesta en marcha (429 TUTOR_BUSY) y
      reserva la pregunta como PENDING (ADR-55)
   3. MENOR: ContentGuard.localCheck(pregunta) ── coincide ─► pregunta BLOCKED + respuesta fija BLOCKED, auditoría TUTOR_QUESTION_BLOCKED, 201 sin llamar al modelo (ADR-57)
   4. contexto como DATOS (ADR-56): audiencia · curso (tema, enfoque, nivel, objetivo, títulos) · capítulo y objetivo · lección (≤ 12 000 caracteres) ·
      fallos del capítulo (enunciado y «fallado»/«a medias», sin la respuesta del alumno) · historial (6 intercambios, 6 000 caracteres) ·
      fuentes: MaterialSearch sobre los documentos del curso (SourcePack; menor: sin los fragmentos que rechaza ContentGuard)
   5. AiGateway.complete(TUTOR) .course(id) → TutorAnswerValidator (no vacía, ≤ 4 000, sin HTML crudo, citas que existan); un reintento; si no, FAILED y 502 TUTOR_FAILED (ADR-58)
   6. MENOR: ContentGuard sobre la respuesta ── coincide ─► texto fijo FILTERED + auditoría TUTOR_ANSWER_FILTERED
   7. una transacción: pregunta → OK, respuesta OK/FILTERED con sus fuentes [{n, page}], poda a 400 mensajes por lección; 201 con los dos mensajes
 GET …/tutor    ─► hilo de la lección (OK, BLOCKED y FILTERED; nunca PENDING ni FAILED) y canClear (adulto)
 DELETE …/tutor ─► el adulto vacía su hilo; el menor recibe 403 THREAD_PROTECTED porque su adulto tutor lo ve (F08)
```

El hilo vive en `tutor_message`. La pregunta nace `PENDING` (reserva el límite y la «respuesta a la vez» con un cerrojo de la BD, así que vale con varias instancias) y, si el modelo falla, pasa a `FAILED` y no se ve: no hay mensajes huérfanos y los
fallos cuentan para el límite. El tutor usa la tarea `TUTOR` (sin `GenerationLock`) y no recibe nada de los exámenes ni las soluciones de los ejercicios. Ni logs ni auditoría llevan lo que se dice en el chat.

## Modelo de datos

```
app_user      id, kind (ADULT|MINOR), display_name, email (adulto), login_id (menor),
              password_hash, status (PENDING_VERIFICATION|ACTIVE|DEACTIVATED), created_at, updated_at
guardianship  tutor_id → app_user, student_id → app_user (UNIQUE: un tutor por menor en v1)
email_token   user_id, purpose (VERIFY_EMAIL|RESET_PASSWORD), token_hash, expires_at, used_at
auth_session  user_id, token_hash, created_at, expires_at, revoked_at
login_attempt kind (LOGIN|MAIL), identifier_hash, ip, succeeded, at   (MAIL = cuota de envíos de correo por IP)
audit_event   id, occurred_at, actor_user_id, subject_user_id, action, outcome, ip, details (JSONB)

-- F02 (migración V2)
ai_usage            id, request_id, attempt, occurred_at, user_id (nulo), course_id (nulo), source (APP|BENCHMARK), task,
                    provider, model, input_tokens, output_tokens, tokens_estimated, cost_usd, status (OK|ERROR|BLOCKED),
                    error_kind, finish_reason, duration_ms        -- solo se añade; sin columnas de contenido
ai_budget_alert     month (aaaa-mm), threshold (80|100), raised_at  -- PK (month, threshold): un aviso por mes y umbral
ai_generation_lock  user_id (PK → app_user), owner, acquired_at, expires_at

-- F03 (migración V3)
source_document     id, owner_id → app_user, display_name (≤ 120, saneado), media_type (PDF|DOCX|TEXT), size_bytes, content_sha256,
                    status (UPLOADED|PROCESSING|READY|ERROR), error_kind, attempts, page_count, chunk_count,
                    content (bytea, solo hasta procesar; luego NULL), created_at, updated_at, processing_started_at
                    UNIQUE (owner_id, content_sha256) · UNIQUE (id, owner_id)
source_chunk        id, document_id + owner_id (FK compuesta → source_document, ON DELETE CASCADE), ordinal, page, start_offset,
                    end_offset, text, embedding (vector, sin dimensión fija), embedding_model     -- índice por owner_id; sin índice ANN

-- F04 (migración V4)
course_intake       id, owner_id → app_user, topic (≤ 200), document_ids (jsonb), status (OPEN|CONSUMED|BLOCKED), questions (jsonb, nulo si se bloqueó),
                    blocked_category (EXPLICIT|VIOLENCE|DRUGS|SELF_HARM|OTHER), created_at, expires_at (+1 h)   -- índice (owner_id, created_at): cuenta A2
course               id, owner_id → app_user, intake_id (UNIQUE → course_intake), title, status (PENDING_GENERATION|GENERATING|READY|FAILED),
                    sheet (jsonb, ficha v1), created_at, updated_at · UNIQUE (id, owner_id)
course_document     course_id, document_id, owner_id · PK (course_id, document_id)
                    FK compuestas (course_id, owner_id) → course y (document_id, owner_id) → source_document, ambas ON DELETE CASCADE
ai_usage            el CHECK de `task` admite también 'INTAKE'

-- F05 (migración V5)
course              + status 'PAUSED' · stop_reason (BUDGET|PROVIDER|CONTENT en pausa; TOPIC_NOT_ALLOWED|LIMIT en fallo) · resumed_at · completed_at
course_topic        id, course_id + owner_id (FK compuesta → course, CASCADE), position, title (≤ 120), summary (≤ 300) · UNIQUE (course_id, position) · UNIQUE (id, owner_id)
course_chapter      id, topic_id + owner_id (FK compuesta → course_topic, CASCADE), course_id, position, title (≤ 120), objective (≤ 200)
course_piece        id, course_id, owner_id, topic_id, chapter_id (nulo en el examen), kind (LESSON|EXERCISES|EXAM), seq (UNIQUE por curso), status (PENDING|DONE|FAILED),
                    error_kind, attempts, content_md (lección), content_json (ejercicios, examen), sources (jsonb [{n, documentId, page}], sin FK), created_at, generated_at
ai_usage            índice por course_id: el coste del curso se suma al consultarlo, no se guarda

-- F06 (migración V6)
course_piece        + UNIQUE (id, owner_id) para enlazar con clave compuesta
lesson_progress     owner_id, piece_id (lección), course_id, completed_at · PK (piece_id, owner_id) · FK compuestas → course_piece y course, ON DELETE CASCADE
exercise_attempt    id, owner_id, course_id, piece_id, item_index (0..7), answer (≤ 2 000), self_result (CORRECT|PARTIAL|INCORRECT), ai_result, ai_comment (≤ 600),
                    ai_requested_at (cuenta las correcciones de IA del día), created_at · índice (owner_id, created_at)
exam_attempt        id, owner_id, course_id, piece_id (examen), status (STARTED|SUBMITTED), answers (jsonb), correct/total (0 ≤ correct ≤ total), started_at, submitted_at · índice por propietario

-- F07 (migración V7)
tutor_message       id, owner_id, course_id, piece_id (una lección), role (USER|TUTOR), status (PENDING|OK|FAILED|BLOCKED|FILTERED), content (≤ 6 000),
                    sources (jsonb [{n, page}], solo respuestas con material), created_at · FK compuestas → course_piece y course, ON DELETE CASCADE ·
                    índices (owner_id, piece_id, created_at) y (owner_id, created_at)
```

Un `ADULT` exige correo; un `MINOR` exige `login_id` y nunca tiene correo (restricciones `CHECK` en BD).
Los roles se derivan de `kind`: adulto = alumno + tutor, menor = alumno.

## Endpoints (F01)

| Método y ruta | Quién | Notas |
|---|---|---|
| `POST /api/auth/register` | público | 202 siempre; la cuenta queda pendiente |
| `POST /api/auth/verify-email` · `resend-verification` | público | token de un solo uso (24 h) |
| `POST /api/auth/login` | público | cookie de sesión; 429 tras 5 fallos (o 20 por IP) |
| `POST /api/auth/logout` | autenticado | revoca la sesión |
| `POST /api/auth/forgot-password` · `reset-password` | público | token de un solo uso (1 h) |
| `GET /api/me` | autenticado | datos mínimos |
| `GET/POST /api/tutor/students` | adulto | lista y alta de sus menores |
| `POST /api/tutor/students/{id}/reset-password` · `deactivate` · `reactivate` | adulto tutor de `{id}` | 404 si no es suyo |
| `GET /api/ai/usage` | autenticado | adulto: su gasto, el estado del tope global y el de sus menores; menor: solo su nº de llamadas |
| `POST /api/material` | autenticado (adulto o menor) | multipart `file`; 202; 400 vacío · 413 grande · 415 tipo · 409 límite o duplicado |
| `GET /api/material` · `GET /api/material/{id}` | propietario; el tutor ve los de sus menores | `{ own, minors }`; ajeno o inexistente = 404 |
| `DELETE /api/material/{id}` | solo el propietario | 204; el tutor recibe 403 (auditado); ajeno = 404 |
| `POST /api/courses/intake` | autenticado (adulto o menor) | `{topic, documentIds}` → `{intakeId, questions}`; 400 · 404 documento · 409 `COURSE_LIMIT_REACHED` · 422 `TOPIC_NOT_ALLOWED` (menores) · 429 `RATE_LIMITED` |
| `POST /api/courses` | propietario de la consulta | `{intakeId, answers}` → 201 curso (repetido: 200 el mismo); 400 respuesta inválida · 404 · 409 · 410 `INTAKE_EXPIRED` |
| `GET /api/courses` · `GET /api/courses/{id}` | propietario | ajeno o inexistente = 404; el tutor aún no ve los de su menor (F08). El detalle (F05) añade `stopReason`, `progress {done,total}`, `costUsd` y el temario con el estado de cada pieza |
| `GET /api/courses/{id}/pieces/{pieceId}` | propietario | lección (`markdown`), ejercicios o examen; el contenido solo llega si la pieza está `DONE`; ajena = 404 |
| `POST /api/courses/{id}/resume` | propietario | 202; 409 si no está en pausa · 429 `RESUME_TOO_SOON` (60 s) |
| `GET /api/courses/{id}/export` | propietario | JSON versionado con el curso; citas como «Fuente n», sin nombres de documento, ids ni datos del usuario |
| `GET /api/results` | autenticado | cursos propios con piezas hechas, falladas y pendientes, llamadas y coste; un menor no recibe importes |
| `GET /api/study/progress` · `GET /api/courses/{id}/study` | propietario | porcentaje de cada curso · progreso por lección, tema y curso (F06); ajeno = 404 |
| `PUT /api/courses/{id}/pieces/{pid}/done` | propietario | `{done}`; 204; 409 si la lección no está hecha · 400 si no es una lección |
| `POST …/pieces/{pid}/exercises/{i}/attempts` | propietario | `{answer}` (1–2 000) → 201 `{attemptId, solution, explanation}`; 409 pieza no hecha · 429 `STUDY_RATE_LIMITED` |
| `PUT …/exercises/{i}/attempts/{a}/result` · `POST …/ai-grade` | propietario | autocorrección (204) · «Corregir con IA» (200 `{verdict, comment}`); 409 `AI_GRADING_ALREADY` · 422 `ANSWER_NOT_ALLOWED` (menores) · 429 `AI_GRADING_LIMIT`, `GRADING_BUSY` · 502 `GRADING_FAILED` |
| `POST …/pieces/{pid}/exam/attempts` · `…/{a}/submit` · `GET …/{a}` | propietario | empezar (sin respuestas) · entregar `{answers}` (200 nota y correcciones, idempotente) · ver un intento entregado |
| `GET …/pieces/{pid}/attempts` | propietario | historial `{kind, attempts}`, el más reciente primero |
| `GET/POST/DELETE /api/courses/{id}/pieces/{pid}/tutor` | propietario | hilo de la lección · pregunta `{question}` (201 `{user, tutor}`; 400 · 404 · 409 lección sin generar · 429 `TUTOR_RATE_LIMITED`, `TUTOR_BUSY`, `AI_*` · 502 `TUTOR_FAILED`) · vaciar (adulto: 204; menor: 403 `THREAD_PROTECTED`) |
| `GET /actuator/health` | red interna | solo `status`, sin detalles |

## Estructura de carpetas

```
selftaught/
├─ compose.yaml · compose.dev.yaml   # servicios/perfiles; puertos de desarrollo aparte
├─ .env.example                      # variables documentadas (el .env real vive solo en tu máquina)
├─ Makefile                          # envoltorio de docker compose
├─ backend/                          # Spring Boot (Maven)
│  └─ src/main/java/es/vicai/studylab/{auth,user,audit,mail,security,config}
│     src/main/java/es/vicai/studylab/ai/{provider,prompt,budget,lock,benchmark}   # F02: capa de IA
│     src/main/java/es/vicai/studylab/material/{extract,chunk}   # F03: subida, extracción, troceado, cola y búsqueda
│     src/main/java/es/vicai/studylab/{course,safety}            # F04: asistente y cursos; filtro de menores reutilizable (ContentGuard)
│     src/main/java/es/vicai/studylab/generation                 # F05: cola, generador, validadores de salida, SourcePack, hilos de fondo
│     src/main/java/es/vicai/studylab/study                       # F06: progreso, intentos de ejercicio y examen, corrección con IA
│     src/main/java/es/vicai/studylab/tutor                       # F07: hilo por lección, contexto, límites, filtro de menores y llamada al modelo
│     src/main/resources/db/migration/   # migraciones Flyway (V1 cuentas, V2 IA, V3 material, V4 cursos, V5 generación, V6 estudio, V7 tutor)
│     src/main/resources/safety/blocked-terms.txt                # términos del filtro de menores, editable sin recompilar
│     src/main/resources/{prompts,benchmark}/ · ai-pricing.properties           # plantillas, temas y precios por modelo
│     src/test/                          # tests de integración (caja negra por HTTP)
├─ frontend/                         # Vue 3 + TS: views, components, stores, api, router, tests
├─ db/init/01-roles.sh               # roles de BD (dueño y aplicación) y extensiones; se ejecuta al crear la BD
├─ scripts/                          # seed y tests de política del entorno
└─ sdd/                              # PROJECT.md, features/, specs/, reports/, benchmarks/ (informes del benchmark), proceso SDD
```

## Decisiones técnicas (ADR)

### ADR-1: Sesiones con token opaco guardado en servidor, no JWT
- Motivo: se revocan al instante (bajas, cambio de contraseña) y no hay clave de firma que custodiar.
- Alternativas descartadas: JWT con refresh token (revocar es complejo).
- Revisitar si: hay varios backends sin BD compartida.

### ADR-2: Un único origen con nginx como proxy
- Motivo: evita CORS y permite cookie `SameSite=Strict`.
- Alternativas descartadas: dominios separados con CORS.
- Revisitar si: el frontend pasa a una CDN en otro dominio.

### ADR-3: Argon2id para contraseñas
- Motivo: recomendación actual de OWASP.
- Alternativas descartadas: bcrypt (válido, pero corta a 72 bytes).
- Revisitar si: el coste de hash afecta al rendimiento del login.

### ADR-4: Identificador de acceso del menor generado por el sistema
- Motivo: el nombre visible puede repetirse; el identificador es único global, con sufijo corto si ya existe.
- Alternativas descartadas: usuario único elegido a mano; "código de familia" en el login.
- Revisitar si: el espacio de identificadores se satura.

### ADR-5: Baja = desactivación y auditoría de solo añadir
- Motivo: se necesita saber todo lo que ha pasado. `audit_event` tiene trigger contra UPDATE/DELETE y el rol de la
  app solo puede INSERT/SELECT. La auditoría no guarda secretos ni el correo de cuentas inexistentes.
- Alternativas descartadas: borrado real de menores.
- Revisitar si: llega una petición de borrado por RGPD.

### ADR-6: Bloqueo de login en BD (`login_attempt`)
- Motivo: sin librerías ni Redis; 5 fallos seguidos por cuenta o 20 por IP en 15 minutos (un login correcto reinicia el
  contador de la cuenta); el identificador se guarda hasheado. La misma tabla (`kind = MAIL`) limita a 10 envíos de
  correo por IP y 15 minutos en registro, reenvío y recuperación, para que la API no sirva para enviar correo masivo.
- Alternativas descartadas: Bucket4j, Redis.
- Revisitar si: hay varias instancias del backend, o si hace falta limitar por destinatario (ver `sdd/BACKLOG.md`).

### ADR-7: Respuestas genéricas anti-enumeración
- Motivo: registro, reenvío, recuperación y login responden igual exista o no la cuenta (el 404 de menores ajenos
  también es idéntico al de ids inexistentes).

### ADR-8: Correo por SMTP desde variables de entorno
- Motivo: `SPRING_MAIL_*`, `MAIL_FROM`, `MAIL_ENABLED`. En `dev` va a Mailpit; en producción el correo no puede
  desactivarse y, si falta SMTP, la app no arranca. Gmail exige contraseña de aplicación.
- Revisitar si: se supera el límite diario del proveedor.

### ADR-9: Un tutor por menor y nombre visible único entre los menores activos de un tutor
- Motivo: simplifica la tutela y evita el doble envío del alta.
- Revisitar si: se necesitan varios tutores por menor.

### ADR-10: Perfiles de Compose
- Motivo: `prod` publica solo el frontend en `127.0.0.1`; `dev` (con `compose.dev.yaml`) añade backend, BD y Mailpit
  también en `127.0.0.1`; `test` usa `db-test` efímera y dos contenedores de un solo uso (`backend-test`,
  `frontend-test`, etiquetados `studylab.runner` y sin healthcheck). Backend y frontend corren con sistema de ficheros de
  solo lectura, sin capacidades y sin root. HTTPS (Caddy) se resuelve al desplegar.
- Desviación respecto a `PROJECT.md` §4b: el perfil `dev` **no** tiene recarga en caliente; el frontend de dev es el mismo
  nginx de producción. Revisitar si el ciclo de desarrollo del frontend se vuelve lento.

### ADR-11: CSRF solo en peticiones autenticadas
- Motivo: los endpoints públicos de `/api/auth` no tienen sesión que abusar; el login entrega `XSRF-TOKEN`
  para el resto. Con `SameSite=Strict` es una segunda capa.

### ADR-12: Tests de caja negra por HTTP con SMTP en memoria
- Motivo: los tests solo dependen de la API y de la BD de pruebas (GreenMail en lugar de Mailpit), así que no
  fijan detalles internos y no envían correo real.
- Límite conocido: MockMvc no ejercita cookies reales con `csrf()`; todo flujo de sesión o CSRF nuevo debe tener además un
  test sin `csrf()` (ver `CsrfCookieFlowTest`) y una prueba con el stack levantado.

### ADR-13: JDBC (JdbcClient) en lugar de JPA
- Motivo: pocas consultas, todas explícitas y parametrizadas; sin ORM entre el SQL y los permisos de BD. El rol de la
  aplicación no tiene DDL y la tabla `audit_event` es de solo añadir, algo que un ORM no deja ver.
- Alternativas descartadas: Spring Data JPA.
- Revisitar si: el modelo de dominio de cursos (F05+) crece lo bastante como para justificar un ORM.

### ADR-14: Dos roles de BD y vistas sin carga diferida
- Motivo: `studylab_owner` solo lo usa Flyway; `studylab_app` no puede cambiar el esquema. El frontend importa sus vistas
  directamente porque la app es pequeña.

### ADR-15: Sin estrategia de "autenticación nueva" para el token CSRF
- Motivo: el filtro de sesión autentica cada petición sin `HttpSession`; Spring Security lo trataba como un login nuevo y
  borraba la cookie `XSRF-TOKEN` en la primera petición autenticada, de modo que las escrituras siguientes daban 403 en el
  navegador (lo detectó la prueba de humo con el stack real; MockMvc no lo veía). Se sustituye esa estrategia por una
  vacía: el token se genera en cada login. Regresión cubierta por `CsrfCookieFlowTest`, que usa cookies reales y un
  contexto de Spring propio (el `csrf()` de MockMvc sustituye de forma permanente el repositorio CSRF del contexto
  compartido).

### ADR-16: Límites por IP y proxy (pendiente para el despliegue)
- Los límites de intentos por IP dependen de que `X-Forwarded-For` lleve la IP real del cliente. nginx la sobrescribe con
  `$remote_addr`; en Docker Desktop (y detrás de un proxy como Caddy) ese valor es la IP del proxy, así que todos los
  usuarios compartirían cuota. Al desplegar hay que configurar `real_ip` en nginx y pasar `X-Forwarded-Proto` del proxy
  de TLS para que la cookie salga con `Secure`.

### ADR-17: Comprobación de secretos como listener y correo síncrono sin propagar errores
- Motivo (secretos): `RequiredSettingsCheck` escucha `ApplicationPreparedEvent`, así falla antes de crear ningún bean y
  ve también las propiedades que inyectan los tests; el error nombra las propiedades que faltan y nunca sus valores.
- Motivo (correo): el envío es síncrono, fuera de la transacción, y un fallo de SMTP se registra sin destinatario ni enlace y
  no cambia la respuesta (para no revelar si la cuenta existe). Contrapartida: la respuesta tarda más cuando se envía un
  correo, lo que permite una enumeración por tiempo (🟡 en `sdd/BACKLOG.md`).
- Revisitar si: se pasa a envío asíncrono con cola.

### ADR-18: Capa de IA propia con adaptadores HTTP, no Spring AI
- Motivo: la capa debe controlar reintentos, timeouts, conteo de tokens y errores, y no registrar contenido. Los tres APIs de chat y
  el de embeddings son estables y simples; una capa propia es más fácil de razonar y se prueba sin red (`MockRestServiceServer`).
  F02 no añade ninguna dependencia nueva.
- Alternativas descartadas: Spring AI (acopla versiones, trae reintentos propios y una autoconfiguración que exige claves).
- Revisitar si: F03 necesita su `VectorStore`, o si el número de proveedores crece mucho.

### ADR-19: Tope mensual en modo `WARN` por defecto
- Motivo (decisión del usuario): sus cuentas de proveedor ya tienen tope duro, así que la app avisa al 80 % y al 100 % (auditoría +
  log + endpoint) y sigue. El modo `BLOCK` (`AI_BUDGET_MODE=BLOCK`) rechaza las llamadas al alcanzar el tope o si su peor caso lo
  superaría; el sobrepaso en `BLOCK` queda acotado por las llamadas en curso. Un proveedor real exige un tope explícito.
- Consecuencia: cambia `PROJECT.md` §3 y §5 y la regla de `CLAUDE.md`, que decían "corte automático".
- Revisitar si: se abre la app a más personas.

### ADR-20: Cerrojo de generación en BD con caducidad de 20 minutos
- Motivo: una caída del backend no deja a nadie bloqueado; el lease lleva un propietario, así que una generación antigua no libera el
  cerrojo de una nueva. Solo aplica a generaciones de curso: el chat del tutor no lo usa.
- **Cambio al implementar:** un curso grande puede tardar más de 20 minutos y, si el cerrojo caducara con la generación viva, un segundo clic
  abriría una generación paralela. El lease tiene `renew()` (amplía la caducidad) y devuelve `false` si ya no es suyo, en cuyo caso la
  generación debe abortar. F05 lo llama entre una llamada de IA y la siguiente.
- Alternativas descartadas: semáforo en memoria (no sobrevive a reinicios ni a varias instancias), advisory locks (se pierden sin avisar).
- Revisitar si: hace falta limitar también el chat del tutor.

### ADR-21: Precios en un fichero de configuración versionado
- Motivo: un modelo sin precio impide arrancar (nunca coste 0 por omisión). El coste es una **estimación**: Gemini factura el
  razonamiento como salida y los modelos con "thinking" lo incluyen en `output_tokens`; el benchmark contrasta el estimado con el
  panel de cada proveedor.
- Revisitar si: los precios cambian a menudo (se podría consultar una fuente externa).

### ADR-22: No se guardan prompts ni respuestas
- Motivo: privacidad de menores. `ai_usage` solo tiene metadatos y es de solo añadir. Un test comprueba que ni los logs llevan
  contenido, mensajes del proveedor o claves.

### ADR-23: Reintentos: máximo 2, solo ante errores transitorios
- Motivo: reintentan timeout, 429 con `retry-after` y 5xx (incluido el 529 de Anthropic). **Nunca** ante clave inválida, petición
  inválida, modelo inexistente, respuesta inválida ni **cuota agotada** (en Anthropic, un tope de gasto llega como 400 o como 429 sin
  `retry-after`; en OpenAI, como 429 `insufficient_quota`): son errores permanentes y reintentarlos solo gasta tiempo.
- Timeouts: el cliente HTTP de cada proveedor usa el mayor timeout de las tareas que lo emplean, no el de cada tarea (limitación conocida).
- Revisitar si: aparece un patrón de errores transitorios que no se cubra.

### ADR-24: Benchmark como servicio de un solo uso con tope propio
- Motivo: gasta dinero real, así que solo existe con el perfil `benchmark` (nunca en el arranque normal), no publica puertos y se
  detiene al alcanzar su tope (por defecto unos 5 USD). El arnés se prueba con el proveedor falso; el run real lo hacemos con las
  claves del usuario. Curso tipo = 1 ficha + 8 lecciones + 9 evaluaciones.
- **Cambio al implementar:** un modelo que falla (clave, nombre mal escrito, cuota) no tumba el run: se anota en una sección "Fallos" del informe,
  se omite el resto de ese modelo y el run sigue con los demás. Sin esto se habría perdido el informe entero por un solo error. Las llamadas de
  un modelo de razonamiento que agotan el límite pensando se registran con su coste (ver ADR-28).

### ADR-25: URL de Ollama configurable, por defecto el contenedor
- Motivo (decisión del usuario): `OLLAMA_BASE_URL=http://ollama:11434`, así la misma imagen de Docker sirve en producción. Para usar
  el Ollama de la máquina bastaría `http://host.docker.internal:11434`.

### ADR-26: Separación instrucciones/datos por construcción
- Motivo: `AiRequest` no tiene ningún modo de colocar datos en la parte de instrucciones. `PromptAssembler` carga una plantilla fija
  (`prompts/<id>.txt`, el id no puede salirse de esa carpeta) y envía los datos en un mensaje aparte, con etiquetas validadas y los
  símbolos `<`, `>` y `&` escapados, para que un dato no pueda cerrar la sección ni abrir otra.

### ADR-27: Credenciales de los proveedores y parámetros no enviados
- Motivo: las claves van siempre en cabeceras, nunca en la URL (Gemini las admite por parámetro, pero eso las dejaría en logs y
  excepciones). No se envía `temperature`: algunos modelos nuevos la rechazan, y se usa el valor por defecto del proveedor.
- **Cambio al implementar:** en producción la URL base de un proveedor de pago debe empezar por `https://`; si no, el backend no arranca
  (una clave nunca viaja en claro). Fuera de producción se admite `http://` para servidores de prueba locales.
- Pendiente de confirmar con el run real: la cabecera `x-goog-api-key` de Gemini y los campos de OpenAI (su documentación no estuvo
  accesible al verificarlos). Confirmado con servidor real: el formato de Ollama (`/api/embed`, 768 dimensiones y 404 con `{"error": …}`).

### ADR-28: Respuestas facturadas pero vacías, y errores de IA hacia HTTP
- Motivo (contabilidad): un modelo con razonamiento puede agotar `max_tokens` pensando y devolver solo bloques de pensamiento. El proveedor
  **cobra** esos tokens, así que los adaptadores devuelven el resultado vacío **con su uso** y es el gateway quien decide: si terminó por
  `LENGTH` se devuelve (texto vacío, coste registrado, fila `OK`); si no, se registra una fila `ERROR` con su coste y se lanza `INVALID_RESPONSE`
  llevando ese coste en la excepción (`AiException.costUsd()`), que el benchmark suma a su gasto. Solo un cuerpo sin la estructura esperada
  (`content`, `choices`, `candidates`) es una respuesta inválida del adaptador. Antes se registraba con coste 0 y el gasto quedaba subestimado.
- Motivo (HTTP): `GlobalExceptionHandler` traduce cada `AiErrorKind` a un estado y un mensaje genérico (`BUDGET_BLOCKED` 429, `GENERATION_BUSY`
  409, `INPUT_TOO_LARGE` 413, `USER_NOT_ALLOWED` 403, `USER_LIMIT` 429 (ADR-29), `PROVIDER_QUOTA` y `PROVIDER_UNAVAILABLE` 503, el resto 502), con código `AI_<TIPO>`.
  Así ninguna feature improvisa su propio mapeo ni filtra texto del proveedor.
- Revisitar si: se necesita distinguir más casos hacia el frontend.

### ADR-29: Tope de gasto por usuario (A1)
- Motivo: en modo `WARN` (decisión del usuario) el tope global solo avisa, así que un usuario, también un menor, podía consumir todo el
  presupuesto antes de que nadie lo notara; el único freno duro eran los topes de las cuentas de los proveedores.
- Decisión: cada usuario tiene un **tope mensual propio** (mes natural de Madrid, como el global) con importe distinto para menores
  (`AI_USER_LIMIT_MINOR_USD`, 2,00 USD) y adultos (`AI_USER_LIMIT_ADULT_USD`, 10,00 USD). **Corta siempre**, también en `WARN`: se
  rechaza la llamada si el gasto del usuario más el peor caso de la llamada supera su tope (`USER_LIMIT`, HTTP 429, auditoría
  `AI_USER_LIMIT_REACHED`, fila `BLOCKED`, sin importes). El gasto cuenta todo intento que el proveedor cobró. Los embeddings locales (coste 0) nunca se
  bloquean. El tope personal se comprueba antes que el global.
- `GET /api/ai/usage`: el adulto ve su tope y el de cada menor suyo con `limitReached`; el menor solo ve `limitReached`, nunca importes.
- Límites conocidos: con varias llamadas simultáneas del mismo usuario el sobrepaso queda acotado por las llamadas en curso (la generación
  de curso va con `GenerationLock`). El importe no lo cambia el tutor desde la app: es configuración.
- Revisitar si: el tutor debe poder ajustar el tope de cada menor, o hace falta un límite de frecuencia además del de gasto (A2).

### ADR-30: El material es una biblioteca del usuario, no de un curso
- Motivo: los cursos no existen hasta F04/F05. El propietario es la frontera de aislamiento; la lista de documentos de una búsqueda es un filtro adicional.
- Alternativas descartadas: tabla de cursos mínima en F03 (adelanta decisiones de F04/F05 y habría que migrarla).
- Revisitar si: F05 necesita que un curso "congele" qué documentos usó (tabla de unión `course_document`).

### ADR-31: Tipo por firma y extracción acotada, sin Apache POI
- Motivo: la extensión y el `Content-Type` los pone el cliente. El tipo se decide por los bytes iniciales (`%PDF-`, ZIP con `[Content_Types].xml` y
  `word/document.xml` y sin macros, texto UTF-8 sin NUL). PDFBox solo lee texto (límite de páginas, sin JS ni enlaces); el DOCX se lee con ZIP+StAX con
  DTD y entidades externas desactivadas y límites de entradas y tamaño descomprimido; todo en una tarea con tiempo máximo.
- Alternativas descartadas: Apache Tika/POI (muchas dependencias y superficie de ataque), confiar en la extensión.
- Revisitar si: se admiten más formatos (entonces, otra librería con su propia revisión). PDFBox es la única dependencia nueva: revisar sus avisos de seguridad al actualizar.

### ADR-32: Cola de procesado en la base de datos, con el original solo hasta procesarlo
- Motivo: el estado ya es una columna, sobrevive a reinicios y no necesita broker. El original se guarda en `bytea` hasta `READY`/`ERROR` y luego se
  borra: no hay ficheros en disco (contenedor de solo lectura), ni nombres usados como ruta, ni material duplicado. Reclamo con `FOR UPDATE SKIP LOCKED`;
  al arrancar, los `PROCESSING` vuelven a `UPLOADED` y tras 3 intentos fallidos el documento pasa a `ERROR`. Los fragmentos se guardan en **una
  transacción** que antes bloquea la fila del documento: si se borró mientras tanto, no se guarda nada.
- Alternativas descartadas: `@Async` en memoria (se pierde al reiniciar), carpeta de ficheros (superficie de path traversal), broker externo (sobreingeniería).
- Revisitar si: hay mucho volumen (varios workers; `SKIP LOCKED` ya lo permite) o hay que conservar el original (OCR, reindexado).

### ADR-33: Búsqueda vectorial exacta, filtrada siempre por propietario; columna `vector` sin dimensión
- Motivo: con ≤ 20 documentos por usuario el escaneo exacto con `owner_id` en el `WHERE` es rápido y **no puede devolver fragmentos de otro usuario por
  efecto del índice**: un índice ANN (HNSW) filtrado puede devolver menos resultados de los pedidos. La columna sin dimensión fija evita atarla al modelo
  y a la configuración de tests; cada fragmento guarda su `embedding_model` y la búsqueda solo compara con el vigente. La FK compuesta
  `(document_id, owner_id)` hace imposible un fragmento con un propietario distinto al de su documento.
- Alternativas descartadas: HNSW/IVFFlat ahora (complejidad y riesgo de filtrado sin necesidad), `vector(768)` fijo.
- Revisitar si: un usuario supera ≈ 100 000 fragmentos, o si se cambia el modelo (hay que reindexar: no hay reindexado en v1).

### ADR-34: El texto del documento es dato, nunca instrucción
- Motivo: un PDF puede contener "ignora tus instrucciones". Los fragmentos salen de `MaterialSearch` como `Fragment` y las features los pasan con
  `AiRequest.data(...)`, nunca dentro de una plantilla (`PromptAssembler` ya los separa y escapa). Un test de arquitectura impide que `material/`
  construya prompts o llame al modelo.
- Condición registrada: antes de que F05/F07 envíen texto de un **menor** a un modelo de pago debe existir el filtrado de contenido (A3).
- Revisitar si: el contenido se muestra al usuario como HTML (hoy no: solo texto plano).

### ADR-35: Borrado real y auditoría sin contenido
- Motivo: es material del usuario (no una cuenta): borrar significa borrar documento, fragmentos y vectores. La auditoría registra id, tipo y
  resultado (`DOCUMENT_UPLOADED`, `DOCUMENT_READY`, `DOCUMENT_FAILED`, `DOCUMENT_DELETED`), **sin nombre ni contenido**. Los logs tampoco los llevan.
- Revisitar si: hace falta papelera o recuperar documentos.

### ADR-36: Spring MVC no registra cuerpos de respuesta (privacidad del material)
- Motivo (descubierto en la FASE 7): con nivel DEBUG, Spring MVC escribe en el log el cuerpo de cada respuesta, y con material eso incluía los nombres de los
  documentos del usuario. Un operador que subiera el nivel para depurar los habría volcado a los logs. `logging.level.org.springframework.web.servlet.mvc.method.annotation`
  se fija en INFO y `MaterialArchitectureTest` lo comprueba capturando los logs con el raíz en DEBUG.
- Alternativas descartadas: confiar en que nadie suba el nivel; ofuscar los nombres en la respuesta.
- Revisitar si: otra feature devuelve datos personales en el cuerpo (menores, conversaciones del tutor): añadir su propio test de logs.

### ADR-37: La consulta del asistente vive en el servidor
- Motivo: las preguntas las decide el modelo; si el cliente las devolviera con sus respuestas, podría inventar preguntas, valores o «campos de la ficha». Guardar la consulta con sus
  preguntas validadas permite validar las respuestas contra lo que el servidor generó, hacer idempotente el «Aceptar» (`course.intake_id` único), contar el límite de frecuencia por
  usuario (consultas de la última hora, sin depender de la IP: D1) y conservar los intentos bloqueados para el tutor.
- Alternativas descartadas: asistente sin estado (el cliente reenvía preguntas y respuestas), sesión HTTP (el backend es sin estado).
- Revisitar si: el volumen obliga a purgar consultas antiguas (hay que fijar una retención: F4-1 del backlog).

### ADR-38: Una sola llamada al modelo, con salida JSON validada y degradación sin errores
- Motivo: tema, valoración de idoneidad y preguntas salen de **una** llamada (coste mínimo). Los adaptadores no usan la «salida estructurada» de cada proveedor, así que el modelo responde con
  JSON en texto y `IntakeParser` lo valida contra un esquema estricto (máx. 3 preguntas, 2 a 4 opciones con una por defecto, campos conocidos y sin repetir, longitudes acotadas) antes de usarlo.
  Los ids los genera el servidor y siempre se añade «Como tú veas». Un fallo de formato nunca llega al usuario: un reintento y, si falla, 0 preguntas con valores por defecto.
- Alternativas descartadas: dos llamadas (una para filtrar y otra para preguntar: más cara y más lenta), confiar en el texto del modelo sin validar.
- Revisitar si: se elige un proveedor con salida estructurada fiable para todos los modelos.

### ADR-39: `ContentGuard` en dos capas, reutilizable (cierra A3)
- Motivo: para un menor, la capa local (términos explícitos, normalizados sin tildes, mayúsculas, símbolos ni espaciado) responde **sin llamar al modelo** y sin coste; la capa del modelo valora
  «apropiado para escolares» dentro de la llamada del asistente. La capa local está en el paquete `safety` para que F05 (lecciones) y F07 (tutor) la usen **antes de enviar texto de un menor a un
  modelo de pago**, que es la condición que dejó ADR-34. La lista de términos es un fichero de recursos. A los adultos no se les aplica. El mensaje al menor es «Contenido inapropiado. Consulta con tu adulto.»
  y nunca revela la categoría; el tutor ve el intento (F08).
- Riesgo asumido: falsos positivos (un tema escolar legítimo bloqueado: el mensaje remite al adulto) y falsos negativos con otros idiomas o disfraces; no sustituye a la supervisión del tutor.
- Alternativas descartadas: solo lista (deja pasar temas disfrazados), solo modelo (cuesta una llamada por intento y depende del proveedor), servicio de moderación externo (otro proveedor y otra política de datos).
- Revisitar si: el tutor necesita desbloquear un tema concreto, o hay que cubrir más idiomas.

### ADR-40: El curso nace en F04, con su ficha versionada y su enlace a documentos
- Motivo: «Aceptar» necesita guardar algo y F05 necesita un sitio donde leer el contrato. `course.sheet` es JSONB con `schemaVersion`; `course_document` enlaza con las claves compuestas que ya
  protegen el material (nadie puede usar un documento ajeno). Estados `PENDING_GENERATION | GENERATING | READY | FAILED` definidos desde ya para no migrar el CHECK en F05.
- Alternativas descartadas: no guardar nada hasta F05, columnas separadas para cada campo de la ficha (cada cambio de la ficha sería una migración).
- Revisitar si: F05 necesita consultar por campos de la ficha (entonces se promocionan a columnas).

### ADR-41: Límite de frecuencia por usuario contado en la base de datos (cierra A2 del asistente)
- Motivo: hoy los límites por IP no valen detrás de nginx (D1). La frecuencia (30 consultas por hora) y los cursos pendientes (5) se cuentan por usuario sobre `course_intake` y `course`;
  una consulta rechazada por límite no deja fila ni llama al modelo.
- Revisitar si: los endpoints de F05/F07 necesitan un contador genérico reutilizable (extraerlo entonces).

### ADR-42: La generación es una cola en la BD de piezas con estado
- Motivo: un curso son decenas de llamadas que duran minutos. Con una fila por pieza (`PENDING | DONE | FAILED`) el progreso es una consulta, reanudar es seguir por lo que falta, un reinicio no pierde ni repite nada
  y «nunca se regenera lo ya generado» deja de ser una promesa del código para ser una condición del `WHERE`. Reclamo con `SKIP LOCKED` y `GenerationLock` renovado entre llamadas (cierra A4).
- Alternativas descartadas: un documento JSON del curso entero (no se puede preguntar por piezas ni reanudar a medias), tareas en memoria (se pierden al reiniciar), un broker de mensajes (sobreingeniería).
- Revisitar si: hay muchos usuarios a la vez (subir los hilos o separar el proceso).

### ADR-43: Lecciones en Markdown validado, ejercicios y exámenes en JSON estricto; una pieza que falla no tumba el curso
- Motivo: el texto largo se escribe mejor y falla menos como Markdown que como cadena dentro de un JSON; los ejercicios y exámenes, que F06 tendrá que corregir, necesitan estructura. Cada tipo tiene su validador (patrón de
  `IntakeParser`: vacío ante cualquier fallo, nunca lanza). Un fallo se reintenta una vez; si persiste, la pieza queda `FAILED` y el curso sigue, para no perder 40 piezas buenas por una mala. Al final, el curso se pausa con `CONTENT` y reanudar reintenta solo las fallidas.
- Alternativas descartadas: todo en JSON, abortar el curso ante la primera pieza inválida, guardar lo que el modelo diga sin validar.
- Revisitar si: se adopta la salida estructurada de los proveedores (el JSON dejaría de romperse).

### ADR-44: Pausa por tope o por proveedor, reanudación manual con enfriamiento
- Motivo: sin reintentos automáticos que gasten sin que nadie lo vea. Al alcanzar el tope (`USER_LIMIT`, `BUDGET_BLOCKED`) o caer el proveedor, el curso pasa a `PAUSED` con su motivo y no hace más llamadas. «Reanudar» tiene un enfriamiento
  de 60 s por curso (A2) y solo vale en `PAUSED`; un curso reanudado vuelve a la cola y continúa por lo que falta.
- Alternativas descartadas: reintento automático cada N minutos, estados distintos para cada causa de pausa.
- Revisitar si: hace falta avisar por correo cuando una pausa dure mucho.

### ADR-45: El Markdown se pinta sin HTML: tokens de `marked.lexer` a elementos Vue de una lista permitida
- Motivo: el contenido lo escribe un modelo y puede intentar cualquier cosa. En lugar de convertir a HTML y sanear (el punto donde siempre falla algo), `marked` solo **lee** el texto en tokens y `MarkdownContent` construye
  elementos Vue con una lista cerrada (títulos, párrafos, listas, énfasis, código, citas, tablas, enlaces `http(s)` con `rel="noopener noreferrer nofollow"`). El HTML crudo, las imágenes, los enlaces con otro esquema y las fórmulas salen
  como texto; la profundidad y el nº de tokens están acotados. No hay `v-html` ni `innerHTML`, y el test de política del frontend exige que `marked` solo se importe en `MarkdownContent` y solo se use `lexer`.
- Alternativas descartadas: `marked` + DOMPurify (dos librerías y una cadena HTML en medio), lector propio (más código crítico), `v-html` con sanear (prohibido).
- Revisitar si: se necesitan fórmulas (KaTeX, que añadiría su propia revisión) o imágenes.

### ADR-46: Con material propio, solo fragmentos de los documentos del curso, numerados y verificados
- Motivo: la lección cita lo que usa. `SourcePack` numera los fragmentos recuperados (solo de los documentos enlazados al curso y del propietario, con `MaterialSearch`) y guarda el mapa `[n] → documento y página`. El modelo recibe los textos como
  **datos**, sin los nombres de los documentos; la lección se acepta solo si sus marcadores `[n]` existen. Para un menor, antes de enviar nada al modelo se descartan los fragmentos que coinciden con `ContentGuard` (condición de ADR-34, cierra F3-1).
  La exportación cita «Fuente n» con la página, nunca el nombre del documento.
- Alternativas descartadas: enviar el documento entero, permitir citas sin comprobar, citar por nombre de fichero.
- Revisitar si: se quiere citar con enlace al documento dentro de la aplicación (F06).

### ADR-47: El contenido generado para un menor se comprueba antes de guardarse y el tema se vuelve a valorar en el temario
- Motivo: F04 acepta el tema de un menor aunque la valoración del modelo falte (F4-5). El temario trae su propia valoración: si el modelo lo juzga no apto para escolares, el curso pasa a `FAILED / TOPIC_NOT_ALLOWED` sin generar nada. Además todo lo
  generado para un menor pasa por la capa local de `ContentGuard` antes de guardarse (un reintento con instrucción reforzada; si persiste, pieza `FAILED` y auditoría `COURSE_CONTENT_BLOCKED` sin el contenido).
- Riesgo asumido: la lista local es corta y solo en español; la valoración del modelo y el tutor son las otras capas.
- Revisitar si: el tutor debe poder aprobar o rechazar contenido concreto (F08).

### ADR-48: Los ejercicios esperan a su lección y el reclamo respeta el cerrojo vigente
- Motivo: los ejercicios se escriben a partir del texto de la lección; sin ella no se pide nada y la pieza queda `PENDING` hasta que «Reanudar» reintente la lección (ahorra llamadas que saldrían incoherentes).
  El reclamo de la cola ignora a los usuarios con un cerrojo vigente o un curso ya generándose, de modo que ni se llena la auditoría de denegaciones ni se disputa el cerrojo; al arrancar, los cerrojos de los
  propietarios de cursos interrumpidos se liberan (una sola instancia: nada se está generando de verdad). Un fallo inesperado pausa el curso en vez de repetirse en bucle.
- Alternativas descartadas: generar ejercicios sin lección (incoherentes), intentar el cerrojo siempre y aceptar las denegaciones, dejar caducar los cerrojos tras un reinicio (hasta 20 minutos de espera).
- Revisitar si: hay más de una instancia del backend (el arranque no podría liberar cerrojos ajenos).

### ADR-49: El progreso se calcula al consultarlo, no se guarda
- Motivo: lo único que el alumno decide es «marcar como hecha» (una fila por lección) y entregar exámenes (una fila por intento). Con eso, el avance del curso, el de cada tema y el «superado» salen de una consulta;
  no hay contadores que se desincronicen cuando una pieza se genera tarde, se desmarca una lección o se reanuda un curso. El denominador son todas las piezas del temario, así que un curso a medias no aparenta más avance.
- Alternativas descartadas: guardar porcentajes por curso y tema (hay que recalcularlos en cada cambio y tras cada generación), marcar la lección al abrirla (cuenta sin leer).
- Revisitar si: hay miles de cursos por usuario (entonces, una tabla de resumen).

### ADR-50: El servidor corrige el examen y la opción correcta no sale hasta entregar
- Motivo: si la pieza de examen de F05 entrega `correct` y la explicación, «autoevaluación» es solo un adorno. Desde F06 la pieza solo lleva texto y opciones; el servidor guarda las respuestas, las corrige sin IA
  y devuelve el resultado con la opción correcta y la explicación **después** de entregar. Los ejercicios siguen la misma regla: la solución llega al enviar la respuesta. La exportación del curso sigue llevando todo
  (es el contenido del propio alumno).
- Alternativas descartadas: corregir en el cliente (visible en las herramientas del navegador), dejarlo como está y esconderlo en la interfaz.
- Revisitar si: se quiere un modo de examen sin exportación hasta entregar.

### ADR-51: El examen es un intento de dos pasos e idempotente
- Motivo: «Empezar» crea un intento `STARTED` (y reutiliza uno abierto) y «Entregar» lo cierra; repetir la entrega devuelve el mismo resultado en lugar de duplicarlo, y las preguntas que se ven son las del intento.
  Los intentos abiertos antiguos se purgan y solo cuentan los entregados. Cada pieza conserva los últimos 200 intentos por usuario.
- Alternativas descartadas: una sola petición que crea el intento al entregar (doble clic duplica y no hay forma de saber qué preguntas vio), intentos sin límite (crece sin fin).
- Revisitar si: se necesita un tiempo máximo por examen.

### ADR-52: Autocorrección siempre, «Corregir con IA» opcional y con la tarea que ya existe
- Motivo: los ejercicios de F05 son de respuesta abierta; la autocorrección (ver la solución y decir cómo te fue) no cuesta nada ni depende del modelo, y la IA se ofrece como segunda opinión, siempre con aviso de que
  puede equivocarse y sin impedir que el alumno decida. Se reutiliza `ASSESSMENT` (quien genera ejercicios ya tiene ese modelo) con una plantilla propia (`grade-exercise`) y un límite de salida bajado por petición:
  no añade variables obligatorias. Límites: una corrección por intento, 20 al día por usuario (contadas en la BD) y una a la vez por usuario (en memoria: una sola instancia). La respuesta del alumno, la solución y el
  enunciado van como **datos**; el veredicto se valida con un esquema estricto (un reintento) y el comentario de un menor pasa por `ContentGuard`.
- Alternativas descartadas: corregir siempre con IA (coste en cada intento y dependencia del proveedor), una tarea nueva `GRADING` (una variable obligatoria más para quien ya tiene el stack).
- Revisitar si: hay varias instancias del backend (el límite de «una a la vez» debe pasar a la BD) o se añade un motor de cálculo para matemáticas.

### ADR-53: Las respuestas del alumno no se registran y los límites se cuentan en la BD
- Motivo: lo que escribe un alumno (sobre todo un menor) es dato personal: ni logs ni auditoría llevan respuestas, solo ids y resultados. Los envíos por hora (120) y las correcciones de IA al día (20) se cuentan por usuario
  sobre las propias tablas, sin depender de la IP (D1). Cierra A2 para F06.
- Alternativas descartadas: limitar por IP (no vale detrás del proxy), no limitar (un bucle de «Corregir con IA» gasta hasta el tope).
- Revisitar si: el tutor necesita ver el texto exacto de las respuestas de su menor (F08: lo leerá de estas mismas tablas, no de los logs).

### ADR-54: Los envíos por hora se cuentan sobre los intentos conservados
- Motivo: el límite por usuario (120 envíos por hora entre ejercicios y exámenes) se calcula contando filas de `exercise_attempt` y de `exam_attempt` de la última hora; no hay ningún contador aparte que se
  desincronice. La retención (200 intentos por pieza) borra lo antiguo, así que el límite por hora no puede superar al de intentos por pieza o la poda dejaría el límite sin efecto. Los valores por defecto lo cumplen.
- Alternativas descartadas: un contador por usuario y hora (otra tabla que mantener y purgar), no podar (la BD crece sin fin).
- Revisitar si: se quiere cambiar alguno de los dos valores (F6-6: validarlo al arrancar) o si el tutor necesita conservar todos los intentos de un menor (F08).

### ADR-55: El hilo del tutor vive en la BD y los mensajes provisionales sostienen los límites y la «respuesta a la vez»
- Motivo: la pregunta del alumno se guarda como `PENDING` antes de llamar al modelo: reserva el límite por hora y por día (contando también lo bloqueado y lo fallido, así un bucle de fallos no gasta sin freno) y impide dos respuestas a la vez por usuario con un cerrojo de la BD
  en la misma transacción, sin estado en memoria (vale con varias instancias; resuelve para el tutor lo que F6-2 deja pendiente en la corrección con IA). Si el modelo falla, el mensaje pasa a `FAILED` y no se ve: no quedan preguntas sin respuesta en el hilo.
- Alternativas descartadas: contar solo lo guardado (los fallos no limitarían), un conjunto en memoria (solo una instancia), guardar el intercambio al terminar (no hay forma de reservar el límite ni de saber que hay uno en marcha).
- Los límites se cuentan sobre los mensajes **conservados**, así que el tope de conservación por lección (`app.tutor.max-stored`, 400) no puede bajar del doble del límite diario (150): con 200, quien preguntara 150 veces en una lección nunca llegaría al límite porque la poda se llevaría sus preguntas
  antiguas (se vio al implementar; mismo invariante que ADR-54). Por la misma razón, **vaciar la conversación reinicia el contador** de quien lo hace: solo un adulto puede y el tope mensual por usuario sigue cortando (F7-6).
- Revisitar si: el volumen de mensajes obliga a purgar `FAILED` y `PENDING` antiguos por separado, o si F7-6 se resuelve contando las llamadas `TUTOR` de `ai_usage`.

### ADR-56: El tutor recibe un contexto acotado y, de lo que falló el alumno, solo enunciado y resultado
- Motivo: la lección (recortada a 12 000 caracteres), el resumen del curso, el capítulo y los fragmentos del material van como datos, y el historial se limita a los últimos 6 intercambios y 6 000 caracteres: es el mayor gasto recurrente (F02). Para reforzar, se envía el enunciado
  y «fallado» o «a medias» de los ejercicios del capítulo, **nunca** lo que escribió el alumno ni la solución: así el tutor no puede filtrar lo que no tiene, ni se mandan al modelo textos de un menor sin necesidad. El tutor no recibe nada de los exámenes.
- Alternativas descartadas: enviar la lección entera y todo el historial (coste), mandar la respuesta del alumno para explicar el fallo con precisión (privacidad y filtro adicional), dar al tutor las soluciones (podría revelarlas).
- Revisitar si: se mide que sin la respuesta literal el refuerzo es pobre (backlog, F7-1).

### ADR-57: Filtro de menores en tres puntos y los bloqueos quedan en el hilo
- Motivo: para un menor, `ContentGuard` se aplica a la pregunta (antes de enviarla), a los fragmentos del material (antes de enviarlos, condición de ADR-34) y a la respuesta del modelo (antes de mostrarla). Un bloqueo no es un error: queda en el hilo con una respuesta fija y una marca (`BLOCKED` o `FILTERED`)
  para que su adulto tutor lo vea (F08), igual que los temas bloqueados de F04. El mensaje al menor es siempre «Contenido inapropiado. Consulta con tu adulto.» (pregunta) o un texto fijo (respuesta), sin categoría. La auditoría lleva ids, nunca el texto.
- Riesgo asumido: la lista local es corta y solo en español (F4-3); el modelo recibe la instrucción de tono escolar como segunda capa.
- Alternativas descartadas: rechazar con un error HTTP (no quedaría en el hilo para el tutor), no filtrar la salida.
- Revisitar si: el tutor adulto debe poder desbloquear una pregunta concreta (F08).

### ADR-58: La respuesta llega completa, validada y con un reintento; sin streaming
- Motivo: el filtro de menores y la validación de citas necesitan el texto entero antes de mostrarlo, el coste de cada llamada se registra exacto y los adaptadores de la capa de IA no hacen streaming. La respuesta se acota a 4 000 caracteres, no puede traer HTML crudo ni citas a fragmentos que no se enviaron (como en F05);
  una respuesta vacía o inservible se reintenta una vez y, si sigue mal, el alumno recibe un error amable y no se guarda nada. Un indicador de espera cubre los segundos de la llamada.
- Alternativas descartadas: streaming (cambia todos los adaptadores y deja pasar texto sin filtrar), aceptar cualquier texto del modelo sin validar.
- Revisitar si: la espera con un modelo real resulta larga para el alumno.
