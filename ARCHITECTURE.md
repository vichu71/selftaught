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
│     src/main/resources/db/migration/   # migraciones Flyway (V1 cuentas, V2 IA, V3 material, V4 cursos, V5 generación)
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
