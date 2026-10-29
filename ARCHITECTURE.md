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

## Panel del adulto tutor (F08)

```
 Adulto ──► GET /api/tutor/students/{id}/…        (ROLE_TUTOR en /api/tutor/**; todo es de solo lectura)                                         AC-1..13
   1. AccessGuard.requireStudentOf(adulto, id): sin vínculo, inexistente o mal formado → 404 igual + ACCESS_DENIED en la auditoría (ADR-59)
   2. el curso y la pieza se buscan con el id del ALUMNO como propietario: los de otro alumno → 404
   3. overview ─► cursos y avance (StudyService, variantes por id de alumno: el mismo cálculo que ve él), última actividad, avisos de 30 días y consumo del mes (ADR-62)
      courses/{c} ─► avance por tema y notas de examen; nunca el texto de lecciones, ejercicios ni exámenes
   4. attempts · conversations · …/tutor · alerts ─► respuestas del alumno, hilos con BLOCKED/FILTERED y avisos; cada consulta audita GUARDIAN_VIEW con ids (ADR-61)
 Avisos (ADR-60): tutor_message BLOCKED/FILTERED · course_intake BLOCKED (con tema y categoría) · audit_event STUDY_ANSWER_BLOCKED y COURSE_CONTENT_BLOCKED del alumno
```

El paquete `guardian` no escribe nada (salvo la auditoría), no habla con el modelo y no genera HTML: un test de arquitectura lo exige. Recorta lo que devuelve: 20 intentos por ejercicio y 200 en total, 100 avisos. El menor ve en su pantalla principal
que su adulto tutor puede ver sus cursos, respuestas, intentos y conversaciones. Ni los logs ni la auditoría llevan lo que el alumno escribió.

## Edición de temario y compartir por código (F09)

```
 Propietario ──► PUT/POST/DELETE /api/courses/{c}/topics|chapters|pieces/…                                                                     AC-1..7
   1. transacción con el curso bloqueado (FOR UPDATE): en cola o generándose → 409 COURSE_BUSY; menor: ContentGuard sobre lo que escribe (422, auditoría COURSE_EDIT_BLOCKED)
   2. renombrar · reordenar · quitar (cascada: piezas, progreso, intentos y hilo) · editar el texto de una lección (borra sus fuentes) · sube course.version
   3. añadir tema o capítulo / regenerar una pieza hecha: límite de 20 por hora y usuario (course_action) y tope de piezas (150) ─► piezas PENDING y el curso vuelve a la cola de F05
      (ADR-63); el generador de siempre crea SOLO lo nuevo con su cerrojo y su control de gasto. Los dependientes (ejercicios, examen del tema) quedan outdated, no se regeneran solos (ADR-66)
 POST /api/courses/{c}/share ─► código de 80 bits, solo su SHA-256 en share_code; se muestra una vez (ADR-64)           GET …/share · DELETE …/share/{id} (revocar)
 POST /api/courses/redeem {code} ─► normaliza y busca por hash (caducidad y revocación en la misma consulta) ─► 404 igual para cualquier fallo, 429 por usuario (course_action)
      ─► CourseCopier: curso nuevo READY del que canjea, ids nuevos, origin_id/origin_version, SIN material, fuentes ni datos del original; MENOR: ContentGuard sobre TODO el texto o no se crea nada (ADR-65)
```

El curso lleva `version` (sube con cada edición guardada) y, si es una copia, `origin_id` y `origin_version`; el detalle del curso solo enseña `copy.originVersion`, nunca el id del original. Lo nuevo lo genera siempre la cola: ni la edición ni
el canje llaman al modelo (un test de arquitectura lo exige). Ni el código ni los textos editados van a logs ni a la auditoría.

## Interfaz: sistema de diseño (F10; la piel actual es «Mundo», F11)

F10 no toca el backend, la base de datos ni los contratos HTTP: rehace la presentación del frontend y su navegación.

```
 index.html ─► main.ts ─► styles/ tokens.css (colores, espacios, radios, bordes, sombras, tipografías; claro y oscuro) · fonts.css (@font-face locales) · base.css (foco, lectura) · motion.css (reduced-motion)
 App.vue ─► según meta de la ruta: layouts/AuthShell (acceso, sin navegación)  |  layouts/AppShell (NavBar + Breadcrumb + <main id="contenido">)
 ui/       botón · etiqueta · tarjeta · campo · aviso · progreso · estado (carga/vacío/error) · pestañas y diálogo (sobre Reka UI)      solo presentación, sin datos de negocio
 map/      layoutMap.ts (función pura) ─► CourseMap.vue (SVG con elementos Vue + <ol> equivalente) ─► MapStation.vue · MapLegend.vue
 views/    las de siempre, vestidas con ui/ y map/ · Inicio = ContinueCard («sigue donde lo dejaste») + líneas con progreso + menores
```

- **Datos del mapa:** `GET /api/courses`, `/api/study/progress`, `/api/courses/{id}` (temas → capítulos → piezas con `status`, `outdated`) y `/api/courses/{id}/study` (lecciones hechas, exámenes superados). No hay endpoint nuevo (ADR-67).
- **Estado de una parada:** `done` (lección hecha o examen superado), `now` (la primera lección lista y sin hacer; el examen solo cuando todas las lecciones de su tema están hechas), `todo`; más marcas independientes `queued`, `failed` y `outdated`. Los ejercicios cuelgan de su lección y no son parada.
- **Colocación:** ancho = una zona por tema apilada en vertical (el ancho nunca desborda, un curso de 150 piezas solo crece en alto; los temas lejanos se pliegan si hay más de 40 paradas); estrecho (< 640 px) = una columna. Cada parada es un círculo (lección) o un cuadrado (examen), y su estado se dice también con texto.
- **Seguridad del frontend (se mantiene):** nada de `v-html` ni de SVG montado como cadena; el texto de la IA o del usuario es siempre texto Vue; el Markdown solo con `MarkdownContent`; ningún recurso de terceros (fuentes incluidas); la política de nginx sigue siendo `default-src 'self'`.
- **Dependencias:** `reka-ui`, `lucide-vue-next` y tres paquetes de Fontsource (Bricolage Grotesque, Newsreader, Space Mono), todas con versión exacta; el test de entorno las lista a propósito.
- Se vio al implementar: el mapa es un SVG con tramos en ángulo recto (salen hacia un lado para no cruzar el nombre de la parada), el nombre se parte en dos líneas en ancho y va a la derecha de la parada en móvil; los ejercicios se abren desde la lista (no son parada); «Lista» distingue una lección preparada y sin estudiar de una en cola; el diálogo de confirmación lo teletransporta Reka UI al `body`.
- **Piel intercambiable:** colores y formas viven en `tokens.css` y en `ui/`; la variante «Mundo que crece» (prototipo en `sdd/design/F10/MundoC.dc.html`) se podría probar cambiando esa capa sin rehacer las vistas.

## Piel «Mundo que crece» (F11)

F11 sustituye la piel «Mapa» (F10) por «Mundo» y es **la interfaz definitiva** (decisión del usuario, 26-10-2026; fusionada en `main`). «Mapa» queda en el historial de Git y en `sdd/design/F10/` (ADR-73). Sigue siendo solo frontend: sin migraciones, endpoints ni cambios de contrato.

```
 styles/tokens.css  mismos NOMBRES del contrato de F10 + paisaje (--color-sky, grass, leaf, trunk, soil, sun, fruit, cloud, road); claro y noche      fonts.css: Fredoka + Newsreader (ADR-77)
 world/   plantStage.ts (porcentaje → seed|sprout|tree|fruit, ADR-74) · PlantIllustration.vue · Landscape.vue · Mascot.vue · Wanderer.vue      SVG como plantillas Vue, colores por clases con tokens (ADR-75)
 map/     layoutMap.ts (sin cambios de contrato) ─► roadPath.ts (puntos → trazo suave, función pura, ADR-76) ─► CourseMap.vue (colinas por tema, camino, piedras y puertas, lista equivalente)
 Inicio   CoursesPanel: una planta por curso (data-stage, fase dicha con texto) + el mismo enlace, estado y barra de progreso · ContinueCard integrada en el paisaje
```

- **Fase de la planta:** 0 % semilla, 1–39 % brote, 40–99 % árbol, 100 % árbol con frutos; sin porcentaje (en cola o generándose) es una semilla con «Preparándose». El texto de la fase va siempre junto a la ilustración.
- **Camino:** las paradas siguen siendo `circle[data-kind="lesson"]` (piedras) y `rect[data-kind="exam"]` (puertas), con el mismo orden, estados y lista equivalente que en F10; solo cambia el trazo (curvo) y las zonas (colinas). «Tú estás aquí» lo marca el personaje (`map-wanderer`) y la etiqueta en texto.
- **Seguridad y accesibilidad (se mantienen):** nada de `v-html`, `<image>`, `foreignObject` ni recursos externos (lo comprueba la prueba de seguridad ampliada a `world/` y `map/`); contraste AA sobre los tokens, incluidos los pares sobre cielo y hierba, en claro y en noche; movimiento solo con `no-preference`.
- Se vio al implementar: el camino sale de los puntos de las paradas más desvíos hacia un lado en los cambios de fila (el nombre cuelga debajo de la parada); lo recorrido es la parte inicial mientras los tramos consecutivos estén hechos; el personaje va junto a la parada actual (en ancho, a su derecha sobre el camino); un `width: 100%` general de `.plant` pisaba el tamaño de cada contexto y se quitó; tras reconstruir, el navegador puede conservar el `index.html` antiguo (BACKLOG F11-6).
- **Tutor flotante:** en una lección el tutor es un botón fijo («Pregunta al tutor», `TutorLauncher.vue`) que abre un panel junto al texto, sin ir al final de la página; el chat sigue montado al cerrar (no se pierde lo escrito), el foco va a la pregunta al abrir y vuelve al botón con Escape o al cerrar. No aparece en ejercicios ni exámenes. Los límites, el filtro de menores y el aviso al menor son los de F07 (no cambian).
- **«Dejarlo por hoy»:** botón de la barra de navegación (`NavBar.vue`) que sube `restKey` en el store `ui` y navega a `/`; `App.vue` usa esa clave en el `RouterView`, así que la vista se monta de nuevo (también si ya estás en el Inicio) y se cierra todo lo abierto en ella (panel del tutor, diálogo de Reka, formularios de edición). No cierra la sesión ni borra nada del servidor; lo escrito y sin enviar se pierde. En el móvil solo se ve el icono (con nombre accesible).
- **Dependencias:** `@fontsource-variable/fredoka` entra con versión exacta; salen `@fontsource-variable/bricolage-grotesque` y `@fontsource/space-mono`. El test de entorno se actualiza a propósito.

## Ilustraciones de portada generadas por IA (F12)

```
 Asistente (adulto, con proveedor de imagen) ──► «Con ilustraciones» (apagado; coste estimado con tope: precio por imagen × máx. de temas)     GET /api/illustrations/offer
 POST /api/courses {…, illustrations: true} ──► course.illustrations (el menor o sin proveedor: 400 ILLUSTRATIONS_UNAVAILABLE) · course.sheet NO cambia (ADR-79)
 IllustrationWorkerLoop ─► IllustrationGenerator.processPending()                                                                           AC-4..8
    1. enqueueMissing: una fila PENDING por tema de cada curso READY con illustrations=true que no la tenga (no recrea REMOVED, ni reintenta FAILED/BLOCKED)
    2. reclama la siguiente (SKIP LOCKED) · adulto activo · GenerationLock del usuario (renovado antes de llamar) · límites por hora y tope mensual ANTES de llamar
    3. ImagePromptBuilder: plantilla prompts/illustration.txt + motivo saneado y entre comillas (ADR-81); si no pasa → BLOCKED sin llamada
    4. AiGateway.generateImage → AiProvider.image (OpenAI/Google; el falso en tests) · reintento transitorio acotado · MODERATION → BLOCKED sin reintento · coste por imagen en ai_usage (tarea IMAGE)
    5. ImageProcessor (ADR-80): tipo por contenido, bytes y píxeles acotados, ≥ 64 px, ≤ 768 px, JPEG sin metadatos, ≤ 300 kB → course_image.content (bytea) · si no pasa → FAILED
 GET /api/courses/{c}/topics/{t}/image ─► image/jpeg · Cache-Control: private · ETag · nosniff · ajeno/inexistente/sin imagen: 404 igual
 POST …/image/regenerate · DELETE …/image ─► COURSE_BUSY si el curso se genera · REMOVED conserva la fila · límites (20 ediciones con IA/h, 3 regeneraciones/tema, imágenes/h)
 Copia por código (F09) ─► CourseCopier copia las imágenes DONE (ids nuevos) si quien canjea es adulto; la copia nace con illustrations=false; un menor no recibe imágenes (ADR-82)
```

- **Una imagen por tema, fuera de `course_piece`:** tabla `course_image` (V10) con propietario y clave compuesta; no cuenta para las 150 piezas ni para el progreso, y un fallo, un tope o una pausa de imágenes **nunca** cambia `course.status`. El hilo descubre lo que falta (ADR-79), así que los temas añadidos con IA en F09 se cubren solos.
- **Estados:** `PENDING`, `DONE`, `FAILED` (no se reintenta solo: cada intento cuesta), `BLOCKED` (negativa del proveedor o tema que no pasa el saneado) y `REMOVED`. El detalle del curso enseña `image: {status, alt}` por tema (nada si no hay fila o está `REMOVED`), y la pieza gana `topicId` e `image` para pintar la cabecera de la lección. Nunca bytes, prompt ni mensajes del proveedor.
- **Texto alternativo:** determinista («Ilustración generada por IA sobre «título»»): el modelo de texto no ve la imagen y no puede describirla de verdad (ADR-81).
- **Gasto:** el precio por imagen vive en `ai-pricing.properties` (`image-pricing[proveedor/modelo].per-image`, con la fecha en que se verificó). Una tarea `IMAGE` sin proveedor deja las ilustraciones desactivadas y la app arranca igual; con proveedor pero sin precio o sin clave, no arranca. Cuenta para el tope mensual del usuario y los avisos al 80 % y al 100 %; una llamada cobrada se contabiliza aunque la imagen venga vacía o inválida.
- **Menores:** no generan ilustraciones (el interruptor no se ofrece y el servidor lo rechaza) y su copia de un curso compartido no lleva imágenes: una imagen no se puede filtrar con `ContentGuard`.
- **Interfaz:** `TopicCover.vue` pinta la imagen con su rótulo «Ilustración generada por IA» y su alt, y la planta de F11 si está pendiente, falló, se bloqueó, no hay o no carga; `IllustrationToggle.vue` es el interruptor del asistente; la edición del curso gana «Quitar» y «Volver a generar» con confirmación que dice que cuenta para el gasto.
- **Benchmark de imágenes:** `make benchmark-images` (perfil `benchmark-images`, un solo uso, con tope en USD y confirmación) pide una imagen por tema y modelo a los modelos de `APP_AI_BENCHMARK_IMAGE_MODELS` y deja en `sdd/benchmarks/` un informe y una página HTML estática para compararlas. Lo lanza solo el usuario con sus claves.
- **Reglas de arquitectura:** `ImageArchitectureTest` exige que `illustrations/` solo hable con `AiGateway`, el cerrojo y sus registros, que no use el sistema de ficheros ni construya HTML, que toda consulta sobre `course_image` lleve `owner_id` (salvo la de la cola) y que `ai/` no conozca `illustrations/`.

## Borrado de cursos (F13)

```
 Propietario adulto ──► DELETE /api/courses/{id}  ·  POST /api/courses/delete {ids:[1..50]}                                    AC-1..12
   0. menor → 403 CHILD_CANNOT_DELETE (antes de tocar la BD; ni el tutor adulto borra, su 404 es el de «ajeno»)                        (ADR-86)
   1. una transacción: SELECT … WHERE id = ANY(:ids) AND owner_id = :owner ORDER BY id FOR UPDATE  (menos filas que ids → 404 igual para ajeno e inexistente)
   2. alguno PENDING_GENERATION o GENERATING → 409 COURSE_BUSY, nada cambia; READY, PAUSED y FAILED se borran
   3. UPDATE course_intake (vacía tema, preguntas y documentos; la fila se queda para el límite por hora) ─► DELETE FROM course ─► cascada del esquema (ADR-85)
   4. auditoría COURSE_DELETED por curso, solo ids, dentro de la transacción; los rechazos no se auditan
```

Sin IA ni cerrojo de generación (un test de arquitectura lo exige) y sin migración de tablas: `V11` solo da `DELETE` sobre `course` al rol de la aplicación. `ai_usage` no se toca; las copias de otros quedan con `origin_id` nulo y sus códigos dejan de valer. Un trabajador de ilustraciones con una llamada en vuelo
termina sin escribir (el guardado exige la fila `PENDING`, ADR-82) y su coste ya está contado.

## Panel del propietario (F14)

```
 Sesión ──► GET /api/owner/{ai,prices,accounts,usage,health,users}   ·   POST …/users/{id}/{deactivate,reactivate,revoke-sessions}   ·   PUT/DELETE …/users/{id}/limit
   1. OwnerGuard.require(user): adulto con sesión (ACTIVE ⇒ correo verificado) cuyo correo coincide con OWNER_EMAIL (sin mayúsculas ni espacios)  (ADR-87)
        no propietario con sesión → 404 igual que una ruta inexistente (y ACCESS_DENIED con su id si es adulto); anónimo → 401; sin CSRF → 403
   2. OwnerRateLimiter: 120 consultas y 60 acciones por hora y propietario, contadas en owner_action (429 OWNER_RATE_LIMIT)                    (ADR-89)
   3. lectura: SQL agregado con parámetros nombrados, rango fijo 7 | 30 | 90 días (400 si no), solo ai_usage.source = 'APP' (el benchmark aparte)
   4. acción: una transacción con SELECT … FOR UPDATE del usuario ─► cambio ─► auditoría OWNER_* con ids (nunca correo ni nombre)
        el propietario no se desactiva ni se revoca a sí mismo (400 CANNOT_TARGET_SELF); tope en user_limit_override (0–500 USD)               (ADR-88)
```

- **Quién es el propietario:** nadie lo es por defecto. El rol no se guarda ni se pide por API: se calcula en cada petición comparando el correo de la sesión con `OWNER_EMAIL`. Sin la variable, todo el panel responde 404 y la app arranca igual. `AuthUser`, `UserKind` y `SecurityConfig` no cambian; `GET /api/me` añade `owner: true` solo para él (para el enlace del menú; no es un permiso).
- **Qué ve:** gasto de IA (hoy, mes, proyección, % del tope global, serie diaria, reparto por modelo, proveedor, tarea y usuario, errores, bloqueos, duración media y p95, coste por curso y por usuario activo, precios vigentes con su fecha de verificación), cuentas y conexiones (altas, estados, activos 1/7/30 días, sesiones abiertas, inicios de sesión correctos, fallidos y bloqueados), uso de la app, salud (colas, fallos, errores de IA de 24 h, bloqueos de contenido, tamaño de la BD) y una lista paginada de usuarios. **Nunca** contenido: ni títulos ni temas ni respuestas ni conversaciones ni nombres de documento; de un menor, solo identificador de acceso, estado, fechas, gasto, tope, nº de cursos y tutor (ADR-91).
- **El tope por usuario** pasa a poder cambiarse sin reiniciar: `BudgetService` lee `user_limit_override` (clave primaria) y si no hay fila usa el de la configuración; sigue **cortando siempre** y lo ven igual el propio usuario, su tutor y el panel.
- **Reglas de arquitectura** (`OwnerArchitectureTest`): el paquete `owner/` no habla con el modelo ni con el material, solo escriben `OwnerUserRepository` y `OwnerRateLimiter`, ninguna consulta nombra columnas de contenido, el nombre y el correo solo salen en una rama `kind = 'ADULT'`, toda consulta es parametrizada y la ordenación es una lista cerrada.
- **Frontend:** `OwnerView` (`/propietario`, solo si `me.owner`) con cuatro pestañas (Gasto, Usuarios, Uso, Salud), selector de rango y «Actualizar» (nada se refresca solo). Las gráficas (`LineChart`, `BarList`) son **elementos Vue y SVG** con colores de `tokens.css` y su tabla equivalente debajo; los estados se dicen también con texto (ADR-90).

## Resultados y botón «Volver» (F15)

```
 Sesión ──► GET /api/results          cursos propios: piezas hechas, con error y pendientes, llamadas e importe (un menor, sin importes)   (F05)
        ──► GET /api/ai/usage         mes, llamadas, gasto y tope propios; un adulto, también sus menores (contrato fijado por test)       (F02/F08)
        ──► GET /api/ai/usage/tasks   gasto del mes de quien llama repartido por tarea; un menor, solo {task, calls}                      (F15, ADR-92)
   ResultsView pide las tres en paralelo y cada una falla por separado: si falta una, su bloque dice «No se pudo cargar» y el resto se ve
```

- **Backend:** un solo cambio, `GET /api/ai/usage/tasks` en `AiUsageController`, con la consulta que ya existía (`UsageRepository.userMonthByTask`, F08). Sin migración, sin SQL nuevo, sin parámetros (el usuario sale siempre de la sesión) y sin importes para un menor. `/api/ai/usage` no cambia.
- **Pantalla:** `ResultsView` compone tarjetas de cifras (`StatCard`: veces que la IA ayudó, gasto del mes, cursos listos), el **medidor del tope** (`results/TopeMeter`, sobre `UiProgress`: «Vas bien» < 80 %, «Cerca del tope» 80–99 %, «Tope alcanzado» ≥ 100 %, con icono y palabras), el gasto por tarea (`BarList`), «Tus peques» (un medidor por menor, solo para un adulto con menores) y los cursos como **tarjetas de jardín** (`results/CourseCard`: la planta de F11 según `done/total`, barra de piezas, estado, errores y pendientes escritos). Un **menor no ve ningún importe ni tope** en toda la pantalla.
- **Botón «Volver»:** `ui/BackLink` (enlace con aspecto de botón secundario, flecha ←, 44 px, sin subrayado) sustituye al enlace «Volver al inicio» de Resultados, Curso, Mi material, Tengo un código y Nuevo curso (que dice «Cancelar»), con los mismos `data-testid` (`back-home`, `cancel-home`).
- **Componentes compartidos:** `StatCard`, `BarList` y `format.ts` suben de `src/owner/` a `src/ui/` (clases `ui-*`); `LineChart` y `DataTable` siguen siendo del panel (ADR-93).

## Inicio y Mi material (F16)

Solo frontend: sin backend, sin migración y sin librerías. Mismo estilo que Resultados (F15), con componentes de `ui/` y los tokens de siempre; todos los `data-testid`, los avisos legales al menor y la lógica de subida, borrado y alta se conservan.

- **Inicio:** «Tu espacio de estudio» son tres **tarjetas-atajo** (`ui/ActionCard`: un enlace entero con icono, título y descripción; la de Resultados se describe sin dinero para un menor). «Mis menores» son **tarjetas** (`StudentCard`: estado en palabras con icono, identificador y botones `Ver actividad`, `Cambiar contraseña`, `Dar de baja`/`Reactivar`) y el alta va plegada en «Añadir un menor» (`ui/Disclosure`, abierta si no hay menores; se oculta con `v-show` para no perder lo escrito). Cursos, menores y documentos sin nada usan `ui/EmptyNote` (planta o mascota de F11 con un mensaje amable).
- **Mi material:** la subida es una **zona** (`material/DropZone`) que es la `<label>` del `<input type="file">` real, que sigue siendo el único control (teclado, lector de pantalla, selector del sistema; solo se oculta visualmente). **Arrastrar y soltar** entrega el primer fichero a la **misma** subida, con las mismas comprobaciones de tipo, tamaño y vacío y los mismos mensajes; con varios, se coge el primero y se avisa. Mientras sube, la zona se desactiva. Los documentos son **tarjetas** (`MaterialList`: icono por tipo, nombre, tipo y tamaño, estado con icono —los textos de siempre: Subido, Procesando, Listo y Error—, motivo del error en una frase y «Borrar» con su confirmación).
- **Resultados:** «Tus peques» gana el botón «Ver actividad» a `/alumnos/{id}` (el panel del tutor de F08, que sigue exigiendo ser su tutor).

## Alegría y colorido (F17)

Solo frontend: sin backend, sin migración y **sin librerías** (confeti y destellos son CSS). Contrato: `sdd/specs/US-F17-alegria-y-color.md`; diseño y ADR-95 a 97: `sdd/specs/ART-F17-propuesta-alegria-color.md`.

- **Zonas de color:** Mapa (verde), Mi material (azul), Resultados (ámbar) y Propietario (violeta); el naranja queda solo para el botón primario. La zona sale **solo de la ruta** (`layouts/zone.ts`, `zoneOf`, función pura; Login y rutas sin zona son «neutral»). `AppShell` pone `data-zone` en `.shell` y el CSS lo traduce a tres variables (`--zone-fill`, `--zone-on`, `--zone-ink`) que leen las páginas, las tarjetas (`.ui-card` lleva un borde superior de 6 px: la forma marca la zona) y las etiquetas.
- **Tokens:** tres por zona en `tokens.css` (`--color-zone-*`, `--color-on-zone-*`, `--color-zone-*-ink`), en claro y en noche, todos en `tests/design/contrast.spec.ts` (AA).
- **Barra:** cada enlace lleva su icono en una **burbuja** (`.nav-bubble`) con el color de su zona (`data-zone` en el enlace); el activo va relleno y con sombra gruesa abajo, además de `aria-current`. Saludo «¡Hola, {primer nombre}!» como texto (`layouts/greeting.ts`, recortado a 20 caracteres) y «Dejarlo por hoy» y «Cerrar sesión» más discretos. La barra inferior de móvil usa las mismas burbujas.
- **Páginas:** `ui/PageHeader` (burbuja de zona, título y acciones) en Inicio, Mi material, Resultados, Propietario, Curso, Lección, Nuevo curso, Tengo un código, Login, Registro y el panel del tutor; `ui/UiTag` gana los estados `ok`, `warn`, `bad` e `info` **con icono**.
- **Alegría medida:** gesto de la mascota (logo), destello de la planta al 100 % (`.plant--bloom`), «¡Hecho!» con confeti al marcar una lección (`ui/Celebration`: texto en `role="status"` y 12 `<span>` decorativos `aria-hidden`) y saludo por franja del día en Inicio (reloj del navegador). **Todo `animation`, `transition` y `@keyframes` vive dentro de `prefers-reduced-motion: no-preference`** (lo comprueba `tests/design/f17.spec.ts`).

## Material del curso (F18)

Contrato: `sdd/specs/US-F18-material-del-curso.md`; diseño y ADR-98 y 99: `sdd/specs/ART-F18-propuesta-material-del-curso.md`. **Backend mínimo** (una lectura), sin migración y sin librerías. El material sigue siendo una **biblioteca del usuario** (F03); cambia cómo se usa.

- **Subir dentro de «Nuevo curso»:** en el paso del material, con «Sí», la misma `DropZone` de Mi material («Añade los apuntes de este curso») sube con `material.upload`, **sin segundo camino de subida**. El documento subido se **marca solo** cuando llega a `READY` (`material/autoSelect.ts`, función pura, máximo 5); mientras tanto no se puede elegir. Los documentos ya subidos siguen disponibles («o elige alguno que ya tengas»).
- **Pantalla del curso:** la tarjeta «Material de este curso» (`components/CourseMaterial`) lista los documentos que `GET /api/courses/{id}` ya devolvía (`course_document`, F04).
- **Mi material:** cada documento propio dice «Usado en» con chips a sus cursos o «Sin usar todavía», a partir de `GET /api/courses/material-usage` → `{usage: {documentId: [{id, title}]}}`, solo de quien llama (consulta con `owner_id`, más reciente primero, techo de 2000 filas). `DocumentView` **no cambia**. Borrar un documento usado avisa de en cuántos cursos está y que los ya creados no cambian.

## Fotos de apuntes (F19)

> **Estado: especificada y con tests (FASES 1 a 5); el código está pendiente (FASE 6).** Contrato: `sdd/specs/US-F19-fotos-de-apuntes.md`; diseño y ADR-100 a 105: `sdd/specs/ART-F19-propuesta-fotos-apuntes.md` (con el contrato que fijan los tests en su última sección).

Una foto de apuntes (JPEG o PNG) entra a la **biblioteca de material** como un documento más (`media_type = IMAGE`); un modelo de visión la lee y el texto resultante sigue el flujo de F03 (fragmentos, embeddings, `MaterialSearch`). Nada más cambia: `DocumentView`, la relación curso–documento (F18) y la recuperación son los mismos.

- **Tipo y entrada no fiable (ADR-101):** el tipo se decide por la firma y por poder leer la cabecera; los píxeles se acotan **antes de decodificar** (`PhotoProcessor.inspect`); al procesar se decodifica, se aplica la orientación EXIF, se reduce a 1568 px y se **re-codifica a JPEG sin metadatos**. Solo eso llega al modelo; el original (en `bytea`, como el resto) se borra al terminar en `READY` y en `ERROR`.
- **Lectura = tarea `OCR` de `AiGateway` (ADR-100):** `AiProvider.vision(VisionCall)` (OpenAI y Google; Anthropic y Ollama no la implementan) y `AiGateway.readImage`. Es opcional como `IMAGE`: sin `APP_AI_TASKS_OCR_*` las fotos quedan **desactivadas** y la app arranca; con modelo y sin precio de tokens no arranca. Se registra en `ai_usage` con tarea `OCR` (nunca la imagen ni el texto) y cuenta para el tope propio del usuario.
- **Salida = sobre JSON validado:** `{"appropriate": bool, "text": "…"}` por `ImageReadParser` (patrón de `IntakeParser`; devuelve vacío, nunca lanza). El texto leído es **dato no fiable**: va a los fragmentos y sale solo por `MaterialSearch` hacia `AiRequest.data(...)`.
- **Una lectura de pago no se reintenta (ADR-102):** `IMAGE` queda fuera de `requeueOrFail` y `recoverInterrupted` de la cola de F03; un fallo tras la llamada, incluido el de los embeddings, es `ERROR` (`IMAGE_INVALID`, `NO_TEXT`, `READ_FAILED`, `BUDGET`, `INDEXING_FAILED` o `BLOCKED`).
- **Cerrojo (ADR-103):** la lectura usa `GenerationLock` con `renew()` antes de la llamada; si está ocupado el documento vuelve a `UPLOADED` **sin gastar intento** (`releaseBusy`).
- **Menores (ADR-104):** solo suben fotos si su tutor lo activa (`child_setting`, `PUT/GET /api/tutor/students/{id}/photo-material`, paquete `childsettings/`); la misma llamada devuelve `appropriate` y `ContentGuard` revisa el texto leído antes de indexar. Un bloqueo es `ERROR`/`BLOCKED` con el mensaje sin categoría, auditoría `DOCUMENT_BLOCKED` con ids y aviso en el panel del tutor (sin texto).
- **Límite diario (ADR-105):** 20 fotos por usuario y día contadas al subir en `material_action` (429 `IMAGE_READ_RATE_LIMIT`, sin fila ni llamada); nunca por IP.
- **Interfaz:** `GET /api/material/capabilities` → `{images}` decide si la `DropZone` ofrece fotos (con aviso de privacidad); «Foto» y «Leído por IA» en la lista; interruptor «Permitir fotos de apuntes» en el panel del alumno.

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

-- F08 (migración V8): sin tablas nuevas; solo un índice para leer los avisos de la auditoría por actor
audit_event         + índice audit_event_actor_idx (actor_user_id, action, occurred_at)

-- F09 (migración V9)
course              + version (int, 1), origin_id (→ course, ON DELETE SET NULL), origin_version
course_piece        + outdated (boolean): «la lección cambió»; se limpia al regenerar
course_image        F12: id, course_id + owner_id + topic_id (FK compuestas, CASCADE), status (PENDING | DONE | FAILED | BLOCKED | REMOVED), error_kind, attempts, regenerations, alt_text, mime, size_bytes (≤ 300 kB), content bytea · una por tema · course.illustrations (boolean, por defecto false)
share_code          id, course_id + owner_id (FK compuesta → course, CASCADE), code_hash (SHA-256, único; el código NO se guarda), created_at, expires_at, revoked_at, uses
-- F13 (migración V11): sin tablas ni columnas nuevas; GRANT DELETE ON course al rol de la aplicación (el resto cae por las FK en cascada; ai_usage.course_id no tiene FK y se conserva)
-- F14 (migración V12): panel del propietario
user_limit_override user_id (PK, → app_user), limit_usd (0–500, 2 decimales), set_by (→ app_user), set_at · una fila solo si el propietario fijó un tope; manda sobre la configuración
owner_action        id, owner_id (→ app_user), kind (QUERY | ACTION), created_at · límite de frecuencia del panel (120 y 60 por hora; se podan las de más de 2 h)
audit_event         + índice (action, occurred_at) para contar eventos por día · ai_usage y audit_event no cambian de permisos (el panel solo los lee)
course_action       id, user_id, kind (AI_EDIT | AI_IMAGE | REDEEM_OK | REDEEM_FAIL), created_at · límites por usuario de ediciones con IA, canjes y fallos (se purgan al día)
course_topic / course_chapter + permiso DELETE para el rol de la aplicación
-- F19 (migración V13, pendiente): fotos de apuntes
source_document     media_type admite también IMAGE (CHECK ampliado); la imagen original vive en content (bytea) solo hasta procesar
ai_usage            task admite también OCR
child_setting       child_id (PK, → app_user), photo_material (boolean, false), updated_by (→ app_user), updated_at · sin fila = desactivado; el rol de la aplicación no borra
material_action     id, owner_id (→ app_user), kind (IMAGE_UPLOAD), created_at · contador diario de fotos; solo SELECT e INSERT para el rol de la aplicación
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
| `GET /api/ai/usage/tasks` | autenticado | gasto del mes de quien llama por tarea (F15): `{month, tasks:[{task, calls, spendUsd?}]}` de más a menos; un menor recibe solo `task` y `calls`, nunca importes; 401 sin sesión |
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
| `GET /api/tutor/students/{id}/overview` · `/courses/{c}` · `/courses/{c}/attempts` · `/conversations` · `/courses/{c}/pieces/{p}/tutor` · `/alerts` | adulto vinculado | panel de solo lectura de un alumno (F08): resumen y consumo · avance de un curso · intentos · conversaciones · hilo · avisos; 401 · 403 (menor) · 404 (sin vínculo, alumno, curso o pieza ajenos). Intentos, conversaciones, hilo y avisos se auditan (`GUARDIAN_VIEW`, solo ids) |
| `PUT/DELETE/POST /api/courses/{id}/topics[/{t}]` · `…/topics/order` · `…/topics/{t}/chapters[/order]` · `PUT/DELETE …/chapters/{ch}` | propietario | editar el temario (F09): renombrar, reordenar, quitar y añadir (201); devuelven el detalle del curso; 400 · 404 · 409 `COURSE_BUSY` · 422 `COURSE_TOO_BIG`, `EDIT_NOT_ALLOWED` (menor) · 429 `EDIT_RATE_LIMITED` |
| `PUT …/pieces/{pid}/content` · `POST …/pieces/{pid}/regenerate` | propietario | texto de una lección (1–20 000 caracteres) · regenerar una pieza hecha con IA (202); mismos errores |
| `POST/GET /api/courses/{id}/share` · `DELETE …/share/{sid}` | propietario | crear un código (201 `{id, code, expiresAt}`, el código solo esa vez; 409 `COURSE_NOT_READY`, `SHARE_LIMIT`) · ver su estado · revocar (204, idempotente) |
| `POST /api/courses/redeem` | cualquier usuario con sesión | `{code}` → 201 `{id, title}` (la copia); 404 igual para cualquier código que no valga · 422 `COPY_NOT_ALLOWED` (menor y el filtro salta) · 429 `CODE_RATE_LIMITED` |
| `DELETE /api/courses/{id}` · `POST /api/courses/delete` | propietario adulto | borrar uno o varios cursos (F13): `{ids}` de 1 a 50, todo o nada; 204 · 400 · 403 `CHILD_CANNOT_DELETE` (menor) · 404 (ajeno, inexistente o mal formado, igual) · 409 `COURSE_BUSY` |
| `GET /api/owner/ai` · `/accounts` · `/usage` `?range=7|30|90` · `/prices` · `/health` | propietario (`OWNER_EMAIL`) | informes de solo lectura del panel (F14): gasto de IA, cuentas y conexiones, uso, precios vigentes y salud; 400 (rango) · 401 · 404 (no propietario, igual que una ruta inexistente) · 429 `OWNER_RATE_LIMIT` |
| `GET /api/owner/users?kind=&status=&sort=spend|activity|created&dir=&page=&size=` | propietario | lista paginada (25 por defecto, máximo 100) sin contenido; de un menor solo su identificador de acceso, estado, fechas, gasto, tope, cursos y tutor |
| `POST /api/owner/users/{id}/deactivate` · `…/reactivate` · `…/revoke-sessions` | propietario | 204 idempotente; 400 `CANNOT_TARGET_SELF` (uno mismo) y para activar una cuenta sin verificar · 404 (id inexistente o mal formado) · 429; auditan `OWNER_USER_DEACTIVATED`, `OWNER_USER_REACTIVATED`, `OWNER_SESSIONS_REVOKED` |
| `PUT /api/owner/users/{id}/limit` `{limitUsd}` · `DELETE …/limit` | propietario | fijar el tope mensual de IA de un usuario (0,00–500,00, dos decimales; 400 si no) o volver al de por defecto; 204; auditan `OWNER_LIMIT_SET`, `OWNER_LIMIT_CLEARED` |
| `POST /api/material` (F19) | autenticado | acepta además JPEG y PNG: 400 `IMAGE_INVALID` · 403 `CHILD_IMAGES_NOT_ENABLED` · 413 `MATERIAL_TOO_LARGE` o `IMAGE_TOO_LARGE` · 415 (WebP, GIF, SVG o fotos desactivadas) · 429 `IMAGE_READ_RATE_LIMIT` |
| `GET /api/material/capabilities` (F19) | autenticado | `{images}`: fotos activas en el servidor y, para un menor, activadas por su tutor |
| `GET/PUT /api/tutor/students/{id}/photo-material` (F19) | adulto tutor de `{id}` | `{enabled}`; `PUT` con CSRF, idempotente, audita solo si cambia; 404 igual para cualquier otro caso; 409 con fotos desactivadas o alumno desactivado |
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
│     src/main/java/es/vicai/studylab/guardian                    # F08: panel del adulto tutor (solo lectura): resumen, intentos, conversaciones, avisos y consumo
│     src/main/java/es/vicai/studylab/{courseedit,share}          # F09: editar temario y lecciones, regenerar; códigos de compartir, canje y copia
│     src/main/java/es/vicai/studylab/coursedelete                # F13: borrado de cursos (uno o varios), todo o nada, con auditoría
│     src/main/java/es/vicai/studylab/owner                       # F14: panel del propietario (guardia, límites, informes agregados, lista y acciones sobre cuentas)
│     src/main/java/es/vicai/studylab/illustrations                # F12: cola de ilustraciones, saneado del prompt, procesado de la imagen, entrega y edición
│     src/main/java/es/vicai/studylab/{material/image,childsettings}  # F19 (pendiente): PhotoProcessor e ImageReadParser; permiso de fotos por menor
│     src/main/resources/db/migration/   # migraciones Flyway (V1 cuentas, V2 IA, V3 material, V4 cursos, V5 generación, V6 estudio, V7 tutor, V8 índice del panel, V9 edición y códigos, V10 ilustraciones, V11 permiso de borrado, V12 panel del propietario)
│     src/main/resources/safety/blocked-terms.txt                # términos del filtro de menores, editable sin recompilar
│     src/main/resources/{prompts,benchmark}/ · ai-pricing.properties           # plantillas, temas y precios por modelo
│     src/test/                          # tests de integración (caja negra por HTTP)
├─ frontend/                         # Vue 3 + TS: views, components, stores, api, router, tests
│  ├─ src/{styles,ui,layouts,map}    # F10: tokens y estilos base, componentes base, marco de navegación, mapa del curso
│  ├─ src/owner · src/results        # F14: gráficas (LineChart, DataTable), lista y acciones del panel · F15: TopeMeter y CourseCard de Resultados
│                                    # (F15: ui/ gana BackLink, StatCard, BarList y format.ts)
│  └─ src/material                   # F16: DropZone (zona de subida); ui/ gana ActionCard, Disclosure y EmptyNote; components/ gana StudentCard
│  └─ src/layouts · src/ui           # F17: zone.ts (zoneOf) y greeting.ts; ui/ gana PageHeader y Celebration; UiTag con estados; tokens de zona en styles/
│  └─ src/world                      # F11 (rama de la piel «Mundo»): fases de planta e ilustraciones SVG; map/ gana roadPath.ts
├─ sdd/design/F10/                   # prototipos de diseño de referencia (Mapa y Mundo)
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

### ADR-59: El panel es un paquete de solo lectura que reutiliza los servicios por id de alumno
- Motivo: el adulto ve lo mismo que el alumno, calculado igual (avance y resultados), sin duplicar reglas: `StudyService` gana variantes que reciben el id del alumno. Cada endpoint empieza por `AccessGuard.requireStudentOf` y el curso o la pieza se buscan con el alumno como
  propietario (los de otro alumno → 404). `guardian/` no escribe nada salvo la auditoría: un test de arquitectura prohíbe métodos que no sean `GET`, SQL de escritura, llamadas al modelo y HTML.
- Se vio al implementar: el formato de los mensajes del hilo lo comparten el alumno y el panel (`TutorViews`) para no tener dos; `StudyService` ganó `overviewFor` y `courseSummaries`; y el gasto de un alumno por debajo de un céntimo se muestra como «menos de 0,01 USD».
- Alternativas descartadas: dejar que el adulto use los endpoints del alumno con otro propietario (rompe «todo dato se filtra por su propietario»), copiar las consultas de F06 (dos fuentes de verdad), una vista materializada (sin necesidad a escala familiar).
- Revisitar si: un alumno pasa a tener más de un adulto (v2) o aparece un rol de administración.

### ADR-60: Los avisos se calculan de lo que ya se guarda, sin tabla nueva
- Motivo: lo que el filtro para ya deja rastro: `tutor_message` (`BLOCKED`, `FILTERED`), `course_intake` (`BLOCKED`, con tema y categoría «para el tutor», ADR-37) y la auditoría (`STUDY_ANSWER_BLOCKED`, `COURSE_CONTENT_BLOCKED`, con ids). Una consulta con `UNION` los ordena y
  acota a 100; solo hace falta un índice por actor en `audit_event`. Los avisos de tutor no repiten el texto (el adulto lo ve en el hilo) y el de tema rechazado sí lo lleva.
- Alternativas descartadas: una tabla `guardian_alert` escrita por cada feature (acopla F04, F06 y F07 con el panel y duplica datos), avisos por correo (fuera de alcance).
- Revisitar si: hay muchos eventos por alumno (paginar) o se purgan las consultas bloqueadas (F4-1): el aviso desaparecería con el dato.

### ADR-61: Cada consulta de actividad se audita con ids
- Motivo: leer conversaciones y respuestas de un menor es una operación sensible y el menor tiene derecho a saber que ocurre. `GUARDIAN_VIEW` registra adulto, alumno y pantalla (`ATTEMPTS`, `CONVERSATIONS`, `THREAD`, `ALERTS`) con los ids de curso y pieza, nunca el contenido.
  El resumen y el curso (sin textos del alumno) no se auditan. Se escribe fuera de cualquier transacción (lección de F01).
- Alternativas descartadas: auditar todo (ruido), no auditar (sin trazabilidad), mostrar al menor «tu adulto te vio» (posterior).
- Revisitar si: el menor debe poder consultar quién vio su actividad.

### ADR-62: Consumo del alumno: gasto del mes frente a su tope, repartido por tarea
- Motivo: `ai_usage` ya guarda coste por usuario y tarea y el tope personal lo da `BudgetService.userLimit` (ADR-29). Se muestra USD y número de llamadas por tarea, no tokens ni modelos. Es el gasto del mes en curso, igual que `/api/ai/usage`.
- Alternativas descartadas: tokens y modelos por llamada (detalle sin utilidad para un adulto), un histórico de meses (posterior).
- Revisitar si: se quiere cambiar el tope de un alumno desde el panel (hoy es configuración).

### ADR-63: Editar es operar sobre las filas de F05 con el curso bloqueado; lo nuevo lo genera la cola, y regenerar solo por petición del propietario
- Motivo: el temario y las piezas ya son filas con estado (ADR-42). Cada edición es una transacción que bloquea la fila del curso (`FOR UPDATE`), comprueba que no esté en cola ni generándose, cambia las filas y sube `version`. Añadir un tema o un capítulo crea sus piezas `PENDING` y devuelve el curso a la cola:
  el generador solo mira `PENDING` y `FAILED`, así que genera **solo lo nuevo** con su cerrojo, su tope de piezas y su control de gasto, sin segundo camino hacia el modelo. Regenerar una pieza `DONE` pasa por `PENDING` solo ante una petición explícita del propietario, con confirmación en la interfaz (amplía ADR-42: «una pieza hecha no se regenera nunca» pasa a «nunca por sí sola»);
  lo que dependía de ella (intentos, progreso, hilo) se borra y sus dependientes se marcan `outdated`. Añadir y regenerar cuentan para un límite por hora y usuario en la BD (`course_action`).
- Se vio al implementar: el tope de piezas se comprueba **antes** que el límite por hora (un rechazo por tamaño no gasta ninguna petición), reordenar con una lista que no es exactamente la misma (también con ids de otro curso) es un 400 y las ediciones que no usan IA no cuentan para el límite.
- Alternativas descartadas: un generador de edición aparte (duplica cerrojo, gasto y validadores), regenerar los ejercicios al editar la lección (coste imprevisible), permitir edición durante la generación (carreras con el generador).
- Revisitar si: se quiere editar ejercicios y preguntas a mano, o editar mientras se genera.

### ADR-64: Los códigos son 80 bits aleatorios guardados como hash, mostrados una vez; el canje no distingue por qué falla y se limita por usuario
- Motivo: un código es un secreto de acceso a un contenido. Se genera con `SecureRandom` (16 caracteres de un alfabeto de 32 sin ambiguos), se guarda solo su SHA-256 (una fuga de la BD no regala cursos) y se enseña una vez; el propietario ve su estado y puede revocarlo.
  El canje normaliza el texto, busca por hash y devuelve el mismo 404 para mal escrito, caducado, revocado, inexistente o de un curso que ya no está terminado (sin enumeración). Los fallos y las copias por hora se cuentan por usuario en la BD, no por IP (D1). Es la única consulta del proyecto que lee un curso ajeno sin `owner_id`:
  va por hash, comprueba caducidad y revocación en la misma sentencia y solo entrega al canje, nunca al llamante.
- Alternativas descartadas: guardar el código en claro (se podría ver de nuevo, pero una fuga lo expondría todo), enlaces con el UUID del curso (adivinables si se filtran), límite por IP (no fiable hasta D1).
- Revisitar si: se quiere volver a ver un código (se crearía otro) o añadir más límites cuando D1 esté resuelto.

### ADR-65: La copia es un fork de lo hecho, sin fuentes ni datos del original, y con el filtro de menores sobre todo el texto
- Motivo: quien canjea recibe el temario y el contenido de las piezas hechas, con ids nuevos y como propietario, con `origin_id` y `origin_version` (para avisar de versiones nuevas en el futuro) y su propia `version` 1. No se copian los documentos, las fuentes (se quitan también las marcas `[n]`),
  el progreso, los intentos, las conversaciones, las consultas del asistente ni el uso de IA: lo que no tiene columna que lo enlace no puede colarse, y un test cuenta filas y busca ids del original. Solo se comparten cursos `READY`, así que la copia nace completa. Si quien canjea es un menor, todo el texto pasa por `ContentGuard`
  antes de crearla y, si salta, no se crea nada y su adulto tutor lo ve en el panel (ADR-39, 57 y 60).
- Se vio al implementar: el aviso de una copia bloqueada que ve el adulto tutor no lleva el id del curso ajeno (solo el tipo y la fecha), y la fecha del bloqueo no cuenta como canje para el límite de copias.
- Alternativas descartadas: compartir por referencia (el original cambiaría bajo la copia), copiar también las fuentes (filtra ids de documentos de otro usuario), copiar progreso (datos del usuario).
- Revisitar si: se avisa de versiones nuevas del original o se permite copiar cursos a medias.

### ADR-66: Lo desactualizado se marca, no se regenera, y la versión sube con cada edición
- Motivo: editar una lección deja intactos sus ejercicios y el examen del tema, pero los marca `outdated` y el propietario decide si regenerarlos (con su coste). Añadir o quitar un capítulo marca el examen del tema. Regenerar limpia la marca. `version` sube una unidad por edición guardada y la copia guarda la del original en ese momento.
- Alternativas descartadas: una versión por pieza (excesivo), no marcar nada (el alumno estudiaría ejercicios que ya no encajan sin saberlo).
- Revisitar si: se pueden editar ejercicios a mano.

### ADR-67: El Inicio y el mapa se calculan en el cliente con los datos que ya hay; «sigue donde lo dejaste» = la primera lección lista sin hacer del curso terminado con más avance (sin llegar al 100 %)
- Motivo: la lista de cursos ya llega ordenada y `StudyOverview` dice qué está hecho; no hace falta endpoint ni migración. Mantener el backend intacto hace que F10 sea reversible y que sus tests no se muevan.
- Alternativas descartadas: guardar la última lección vista (columna y endpoint nuevos); calcularla en el servidor.
- Se vio al implementar: con «primer curso de la lista» salía un curso sin avance antes que otro con avance; se cambió a «el de más avance que no llega al 100 %».
- Revisitar si: se quiere «lo último que estudié» entre varios cursos (un campo `lastStudiedAt` en la lista de cursos).

### ADR-68: El mapa es SVG propio con una función de colocación pura y una lista equivalente debajo
- Motivo: es una línea con pocas formas; una función pura se prueba sin navegador y el SVG con elementos Vue escapa el texto por construcción. La lista debajo da teclado y lector de pantalla sin trabajo extra.
- Alternativas descartadas: librería de grafos o de diagramas (peso y superficie de seguridad sin necesidad); canvas (no accesible); componer el SVG como cadena (riesgo de inyección).
- Revisitar si: aparecen ramas o prerrequisitos entre lecciones (hoy el curso es lineal).

### ADR-69: Tokens CSS en un fichero y componentes base propios; Reka UI solo para lo que lleva comportamiento
- Motivo: la identidad es nuestra y debe poder cambiar de piel; el foco, el teclado y ARIA de diálogos y pestañas son difíciles de acertar, así que se delegan.
- Alternativas descartadas: un kit con estilo propio (se nota que es de serie, justo lo que se quería evitar); Tailwind (valores repartidos por las plantillas); todo a mano (accesibilidad de diálogos a medias).
- Revisitar si: Reka UI deja de mantenerse o pesa demasiado en el empaquetado.

### ADR-70: Fuentes autoalojadas y sin recursos externos
- Motivo: la política `default-src 'self'` y la privacidad del menor (ninguna petición a terceros desde la app). Se sirven desde el mismo origen con `font-display: swap`, solo latín y los pesos usados.
- Alternativas descartadas: Google Fonts por enlace (viola la política y filtra la IP); fuentes del sistema (pierde la identidad).
- Revisitar si: el peso de las fuentes se nota en conexiones lentas (menos pesos o una sola familia variable).

### ADR-71: Contraste AA como prueba automática sobre los tokens, en claro y en oscuro
- Motivo: el estilo neo-brutalista usa mucho color plano y es fácil dejar pares ilegibles; un test que lee `tokens.css` y calcula las razones impide que un cambio de paleta rompa la accesibilidad sin que nadie lo vea.
- Alternativas descartadas: revisión manual; herramienta externa en integración continua.
- Revisitar si: se cambia de piel (el test se reutiliza tal cual).

### ADR-72: Los tests de estructura se actualizan a propósito y los `data-testid` se conservan salvo que su elemento desaparezca
- Motivo: reducir el diff de pruebas y asegurar que ninguna regla de F01–F09 (aviso al menor, visor Markdown, mensaje sin categoría, códigos que se ven una vez) pierde su prueba al mover el HTML.
- Alternativas descartadas: reescribir los tests desde cero (riesgo de perder casos borde); congelar la estructura antigua (impide el rediseño).
- Revisitar si: el número de pruebas que cambian en una vista se dispara (dividir la vista en entregas).

### ADR-73: «Mundo» sustituye a «Mapa» en su rama y conserva los nombres de los tokens
- Motivo: así las vistas, los componentes y casi todos los tests de F10 se reutilizan; solo cambian los valores y lo que el diseño pide. El usuario compara dos ramas, no dos pieles dentro de una.
- Alternativas descartadas: selector de piel (doble mantenimiento y una decisión de producto y de almacenamiento que nadie pidió); una rama que copie las vistas (se desincroniza).
- Revisitar si: se decide que convivan (tokens por tema de datos y pruebas para ambas).

### ADR-74: La fase de la planta es una función pura del porcentaje, y siempre se dice con texto
- Motivo: es lo único que la ilustración sabe del curso; una función total y probada en sus fronteras (0, 1, 39, 40, 99, 100, ausente) evita sorpresas, y el texto asegura que no depende de ver la ilustración ni el color.
- Alternativas descartadas: guardar la fase en el servidor (no hace falta); decidirla dentro del componente (no se prueba sin montar).
- Revisitar si: se quieren más fases o fases por tipo de curso.

### ADR-75: Las ilustraciones son componentes SVG coloreados por clases con tokens
- Motivo: la regla «colores solo en `tokens.css`» (F10) debe seguir valiendo para las ilustraciones, y así el modo oscuro y un cambio de piel las alcanzan sin tocarlas; el SVG se escribe como plantilla Vue, nunca como cadena.
- Alternativas descartadas: imágenes SVG/PNG en ficheros (colores fuera de los tokens y una petición más por imagen); atributos `fill` con colores fijos (rompen la prueba y el modo oscuro).
- Revisitar si: el número de ilustraciones crece tanto que convenga un sistema de iconos.

### ADR-76: El camino se curva con una función pura sobre la colocación existente
- Motivo: la posición de las paradas ya está resuelta y probada (`layoutMap`); curvar el trazo es presentación, y una función pura de puntos a `d` se prueba sin navegador (sin salir del ancho). El contrato accesible no cambia.
- Alternativas descartadas: una colocación nueva para el paisaje (duplica lógica y pruebas); una librería de rutas (peso y superficie sin necesidad).
- Revisitar si: aparecen ramas o prerrequisitos entre lecciones (ADR-68).

### ADR-77: Fredoka para titulares y etiquetas, Newsreader para leer; se retiran Bricolage y Space Mono
- Motivo: identidad redondeada sin perder la lectura cómoda; menos peso total (se quitan tres ficheros de fuente y se añade uno).
- Alternativas descartadas: una sola familia redondeada también para leer (cansa en lecciones largas); mantener las tres de F10 (peso y dos identidades mezcladas).
- Revisitar si: la lectura de lecciones se queja de la tipografía (probar otra de lectura redondeada).

### ADR-78: La imagen es una tercera modalidad de `AiProvider`/`AiGateway`, con precio por imagen en un mapa propio
- Motivo: el gateway ya da tope de gasto, avisos, registro, reintentos acotados y cerrojo; una imagen cobra por unidad y no por token, así que `image-pricing` (por `proveedor/modelo`) evita tocar el registro `Price` que usan todos los demás. Una llamada que el proveedor cobra se contabiliza siempre.
- Alternativas descartadas: un cliente de imágenes aparte (duplica presupuesto y registro); meter el precio por imagen en `Price` (rompe construcciones y pruebas existentes).
- Revisitar si: un proveedor de imagen cobra por tokens (entonces, mismo mecanismo que el texto).

### ADR-79: Las ilustraciones viven fuera de `course_piece`, con su tabla y su hilo, y el curso no las espera
- Motivo: no son contenido que estudiar; así no tocan el tope de 150, el progreso (ADR-49) ni `course.status`, y un fallo, un tope o una pausa de imágenes nunca pausa un curso. El hilo **descubre** lo que falta (`enqueueMissing`) en vez de que `generation/` lo encole: cubre cursos nuevos y temas añadidos con IA (F09) sin acoplar paquetes. `REMOVED` conserva la fila para que no se vuelva a encolar. El interruptor es una columna del curso, no un campo de la ficha: `course.sheet` (contrato con F05) no cambia.
- Alternativas descartadas: una pieza `IMAGE` en la cola de F05 (contaminaría contadores, estados y pausas); generar durante la creación del curso (bloquea y encarece los fallos); el interruptor en la ficha (obligaría a subir su `schemaVersion`).
- Revisitar si: se quieren imágenes por lección o varias por tema.

### ADR-80: La imagen del proveedor es entrada no fiable y se re-codifica
- Motivo: viene de un tercero y se guarda y se sirve a un navegador. Se decide su tipo por contenido, se acotan bytes y píxeles antes de decodificar (bombas de descompresión), se descartan las imágenes diminutas y las cabeceras mentirosas, se reduce a 768 px y se re-codifica a JPEG sin metadatos (quita EXIF y C2PA; la etiqueta «generada por IA» es de la interfaz). Sin dependencias: `javax.imageio`. WebP no se admite (el JDK no lo lee) y se pide PNG.
- Alternativas descartadas: guardar los bytes tal cual (metadatos y ficheros hostiles); una librería de imágenes (superficie nueva).
- Revisitar si: un proveedor solo ofrece WebP.

### ADR-81: El prompt es plantilla + motivo saneado, y el texto alternativo es determinista
- Motivo: las APIs de imagen no separan instrucciones y datos; el único dato es el motivo (título y resumen del tema), así que se sanea (sin caracteres de control, marcas ni comillas), se acota y se rodea de una plantilla fija con el estilo común, y si no pasa, no hay llamada. El modelo de texto no ve la imagen, por lo que un alt «descriptivo» sería inventado: se usa «Ilustración generada por IA sobre «título»». Una instrucción dentro del título no se puede detectar de forma fiable: la defensa es la estructura (va después de la plantilla y entre comillas) y la moderación del proveedor.
- Alternativas descartadas: dejar al modelo de texto escribir el prompt (superficie de inyección y un coste más); alt text generado.
- Revisitar si: se usa un modelo con visión para describir la imagen.

### ADR-82: Cinco estados, límites en la BD y copia pasiva
- Motivo: `PENDING`, `DONE`, `FAILED`, `BLOCKED` (negativa del proveedor, sin reintento) y `REMOVED`. Ni `FAILED` ni `BLOCKED` se reintentan solos (coste); regenerar es explícito, limitado (20 ediciones con IA por hora de F09, 3 regeneraciones por tema, imágenes por hora) y contado en la BD. La **copia** por código lleva las imágenes `DONE` pero nace con `illustrations = false`: así nunca se cobra una generación al que canjea sin pedirla. Un menor no genera ni recibe imágenes (no se pueden filtrar con `ContentGuard`). Detalle surgido en la verificación (FASE 7): el guardado del resultado solo escribe sobre filas `PENDING`, así que una imagen quitada mientras se generaba no revive (el coste de esa llamada ya está contado).
- Alternativas descartadas: reintentos automáticos (gasto imprevisible); copiar el interruptor (cobraría al canjeador).
- Revisitar si: se filtran imágenes (moderación propia) y se abre a menores.

### ADR-83: El benchmark de imágenes es un perfil aparte que solo lanza el usuario
- Motivo: gasta dinero real y necesita claves; como el de texto (ADR-24), vive en un servicio de Compose de un solo uso (`make benchmark-images`) con tope en USD, 3 temas fijos y los modelos que indique `APP_AI_BENCHMARK_IMAGE_MODELS`; deja el informe y un `index.html` estático con las imágenes lado a lado y su coste real para compararlas a ojo. Nunca se ejecuta desde un test ni en el arranque.
- Alternativas descartadas: ampliar el benchmark de texto (otro formato y otro coste); medir la calidad automáticamente (no es fiable para imágenes).
- Revisitar si: se quiere puntuar con un modelo con visión.

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

### ADR-87: Propietario por variable de entorno, calculado en cada petición, con 404 uniforme
- Motivo: no existe rol global y crear uno en la BD abre la puerta a escalada (un endpoint mal protegido, una inyección o un adulto que se cambie el rol). Un correo en el entorno del servidor solo lo cambia quien controla el despliegue. Se compara el correo de la sesión (ya `ACTIVE`, luego verificado) en cada petición, sin cachés ni columnas. Quien tiene sesión y no es propietario recibe el mismo 404 que una ruta inexistente: no se revela que el panel existe. No hace falta tocar `AuthUser`, `UserKind` ni `SecurityConfig`; un anónimo recibe el 401 y una escritura sin CSRF el 403 que da cualquier otra ruta.
- Alternativas descartadas: `is_owner` en `app_user` (editable por cualquier bug de escritura sobre la tabla); cuenta aparte con 2FA (fuera de v1, rompe «adulto = alumno + tutor»); `hasRole("OWNER")` en `SecurityConfig` (daría 403, que delata la ruta).
- Revisitar si: hay más de un propietario o gestión de roles (entonces tabla de roles con auditoría y 2FA).

### ADR-88: El tope de gasto por usuario pasa a la BD como excepción al valor de la configuración
- Motivo: el propietario debe ajustar un tope sin reiniciar. Una tabla `user_limit_override` con una fila solo cuando hay excepción deja el comportamiento actual intacto (menor 2 USD, adulto 10 USD) y el tope **sigue cortando siempre** (A1/ADR-29). `BudgetService` lo consulta en cada llamada (una lectura por clave primaria); lo ven igual el usuario, su tutor y el panel. Se acota a 0–500 USD para evitar errores de teclado.
- Alternativas descartadas: guardar todos los topes en la BD (migración de datos y un valor nuevo por usuario creado); cachear el valor (un cambio tardaría en cortar); topes por rol editables (otro modelo de datos).
- Revisitar si: se quiere un tope por semana o por tarea.

### ADR-89: Estadísticas agregadas en SQL, con rangos fijos, índice por acción y límites en la BD
- Motivo: el panel es la consulta más pesada de la app; si no se acota, un rango abierto o un refresco en bucle cargarían la BD. Rangos fijos (7/30/90), todo con `GROUP BY` en SQL y parámetros nombrados, sin traer filas; índice nuevo `(action, occurred_at)` en `audit_event`; límites por propietario contados en la BD (120 consultas y 60 acciones por hora, como el resto de límites de la app, nunca por IP: D1). Sin refresco automático. La lista de usuarios calcula gasto y actividad para ordenar sobre todos los usuarios: es válido para una base familiar (cientos de cuentas) y se anota el límite.
- Alternativas descartadas: vistas materializadas o tablas de resumen (datos que se desfasan y un trabajo más); caché en memoria (se pierde al reiniciar y complica los tests); métricas con Prometheus (fuera de v1).
- Revisitar si: hay miles de cuentas o el panel tarda más de un segundo; entonces, resumen diario precalculado.

### ADR-90: Gráficas como elementos Vue y SVG, sin librería, siempre con su tabla
- Motivo: ninguna librería de gráficas está en el paquete y la política de nginx es `default-src 'self'`; añadir una cambia la lista de dependencias exactas del test de entorno y trae superficie nueva. Dos tipos de gráfica (serie diaria y barras de reparto) se dibujan con elementos Vue como el mapa (F10/F11), con colores de `tokens.css`. Cada gráfica lleva debajo su tabla equivalente: es accesible y es lo que prueban los tests de DOM; un estado nunca se dice solo con color.
- Alternativas descartadas: Chart.js/ECharts (peso, dependencia, canvas inaccesible); `v-html` con SVG generado (prohibido).
- Revisitar si: hacen falta gráficas interactivas (zoom, comparar periodos).

### ADR-91: El propietario ve cifras de cuenta, no contenido, y de un menor solo lo mínimo
- Motivo: encaja con «datos mínimos» para menores (F01) y con F08 (el tutor ve el contenido; el propietario, no). La lista enseña de un menor su identificador de acceso, estado, fechas, gasto, tope y tutor; nunca su nombre visible, temas, cursos, respuestas ni conversaciones. Un test de arquitectura impide que `owner/` consulte columnas de contenido y otro recorre el JSON buscando campos prohibidos. El propietario puede desactivar a un menor porque desactivar es reversible (F01); no puede borrar cuentas.
- Alternativas descartadas: que el propietario vea nombres y temas de menores (innecesario para operar); lista solo agregada (no permitiría actuar sobre una cuenta concreta).
- Revisitar si: se pide moderación de contenido (sería una feature con filtros y auditoría propias).

### ADR-92: El gasto por tarea del propio usuario es un endpoint aparte
- Motivo: `GET /api/ai/usage` tiene su contrato de privacidad fijado por test (claves exactas de `you`, también la del menor). Añadirle un campo cambiaría ese contrato y arrastraría sus tests; un endpoint de solo lectura (`/api/ai/usage/tasks`) con la consulta que ya existe desde F08 deja lo anterior intacto, y deja que la pantalla se siga viendo si una de las dos lecturas falla. El usuario sale siempre de la sesión: no hay parámetros ni ids que validar, y un menor nunca recibe importes.
- Alternativas descartadas: ampliar `you` con `byTask` (rompe el contrato y sus tests); que el cliente pida el panel del tutor (no es suyo y un menor no tiene acceso); calcularlo en el cliente a partir de otro dato (no existe).
- Revisitar si: hace falta el reparto por tarea de varios meses (entonces un parámetro de mes validado en el servidor).

### ADR-93: Los componentes de cifras (`StatCard`, `BarList`) y el botón «Volver» son de `ui/`
- Motivo: dos pantallas ya los usan (el panel del propietario y Resultados) y los componentes genéricos viven en `src/ui/` desde F10. Moverlos evita que Resultados dependa de la carpeta del propietario y deja un único sitio con el estilo (clases `ui-*`, tokens). `BackLink` sustituye cinco enlaces subrayados sueltos por un botón único, con `data-testid` y destino iguales, para cambiar su aspecto en un solo lugar. `LineChart` y `DataTable` se quedan en `owner/` porque solo el panel los usa.
- Alternativas descartadas: importar de `owner/` desde Resultados (acopla pantallas no relacionadas); copiar los componentes (dos sitios que cambian a la vez); una carpeta nueva `charts/` (otra categoría para dos ficheros).
- Revisitar si: un tercer sitio necesita `LineChart` o `DataTable` (entonces suben también).

### ADR-94: Atajos, plegables y estados vacíos son componentes de `ui/`; la zona de subida mejora el `<input type="file">` sin sustituirlo
- Motivo: tres pantallas repiten la misma idea (un enlace con frase, un bloque que se abre y se cierra, un «todavía no hay nada»): `ActionCard`, `Disclosure` y `EmptyNote` la dejan en un sitio con el estilo y los tokens, igual que `BackLink` y `StatCard` (ADR-93). Los cambios son de **forma** y conservan los `data-testid`, los textos legales al menor y la lógica de subida, borrado y alta. La zona de subida es una `<label>` del `<input type="file">` real, que sigue siendo el único control (teclado, lector de pantalla, selector del sistema); arrastrar y soltar solo entrega el primer fichero a la misma subida, con las mismas comprobaciones: no hay un segundo camino de subida que probar ni que proteger. El formulario de alta se oculta con `v-show` para no perder lo escrito.
- Alternativas descartadas: sustituir el `<input>` por una zona que no sea su etiqueta (se pierde la accesibilidad); una librería de subida con arrastrar y soltar (dependencia nueva para un gesto); `v-if` en el formulario (se pierde lo escrito al plegarlo y cambia el DOM que prueban los tests de F01); cambiar los textos de estado (rompe tests sin aportar).
- Revisitar si: se quiere subir varios ficheros a la vez o ver una vista previa (entonces cambia el almacén y la API).

### ADR-95: El color de zona es una variable CSS que pone el marco a partir de la ruta
- Motivo: una sola fuente de verdad (`zoneOf`) para la barra, las páginas y los tests; las vistas no llevan estilos por zona y cambiar un acento es cambiar un token. Se calcula por la ruta (como ya hacía `NavBar`) y no con `meta` en el router.
- Alternativas descartadas: `meta.zone` en cada ruta (dos fuentes que se desincronizan); clases por vista (estilos duplicados); un color por usuario (fuera de alcance).
- Revisitar si: una pantalla debe enseñar dos zonas a la vez.

### ADR-96: Tres tonos por zona (relleno, texto sobre relleno y texto sobre superficie) y el estado nunca solo con color
- Motivo: un relleno vivo no sirve como color de texto; separar los tonos permite AA en claro y en noche. La forma (borde grueso, burbuja con icono, texto) lleva el significado: el color acompaña.
- Alternativas descartadas: un solo color por zona (falla el contraste); colores a mano por componente (rompe el test de tokens).
- Revisitar si: se añaden más zonas o un tema de alto contraste.

### ADR-97: La alegría es CSS, de una pasada y solo con movimiento permitido
- Motivo: el confeti, el destello y el gesto de la mascota no deben añadir dependencias ni ocultar información; sin movimiento, el texto («¡Hecho!») sigue saliendo.
- Alternativas descartadas: una librería de confeti o Lottie (dependencia y `canvas` inaccesible); animaciones continuas (distraen al estudiar).
- Revisitar si: se quiere una celebración distinta por usuario o por curso.

### ADR-98: El uso de cada documento es un endpoint aparte en `course/`
- Motivo: la relación es de cursos (`course_document`), `material/` no debe leer tablas de cursos y `DocumentView` tiene su contrato fijado por tests (como pasó con `/api/ai/usage`, ADR-92).
- Alternativas descartadas: ampliar `DocumentView` con `courses` (rompe el contrato y acopla paquetes); calcularlo en el cliente con el detalle de cada curso (N peticiones).
- Revisitar si: hace falta el uso por documento dentro de un listado paginado.

### ADR-99: El asistente sube con la misma subida de Mi material y marca el documento al estar listo
- Motivo: un solo camino de subida (mismas comprobaciones, límites y mensajes) y un curso solo puede usar documentos `READY` (`readyDocuments` lo exige en el servidor).
- Alternativas descartadas: subir y enlazar en un paso del servidor (cambiaría F04 y mezclaría el procesado con el asistente); dejar elegir documentos sin procesar (el servidor los rechaza).
- Revisitar si: el procesado de documentos deja de ser asíncrono.

### ADR-100: La lectura de una foto es una tarea propia (`OCR`) de `AiGateway`, con una operación de visión en el puerto
- Motivo: es una llamada de pago con su modelo, tope de salida y precio; el registro de gasto, los topes y los avisos ya viven en la pasarela. El puerto gana `vision(VisionCall)` (la audiencia viaja como dato, nunca en la plantilla).
- Alternativas descartadas: reutilizar `AiRequest.data` (solo texto); llamar a un adaptador desde `material/` (rompe ADR-18).
- Revisitar si: aparece otra tarea multimodal (generalizar `VisionCall`) o el benchmark muestra que un proveedor cobra por imagen (añadir `image-price`).

### ADR-101: La foto es entrada no fiable y se re-codifica antes de salir
- Motivo: bombas de píxeles, metadatos (GPS) y formatos políglota. Se acotan los píxeles desde la cabecera, se decodifica, se endereza, se reduce y se re-codifica a JPEG sin metadatos; al modelo solo llega eso y el original se borra.
- Alternativas descartadas: enviar el original (filtra EXIF y lo que sobre en un políglota); guardar la imagen (privacidad y espacio); reutilizar `illustrations/ImageProcessor` (otro paquete, 768 px y 300 kB; unificar ambos queda como deuda).
- Revisitar si: se pide ver la foto original en la interfaz.

### ADR-102: Una lectura de pago no se reintenta y no se guarda el texto entre intentos
- Motivo: cada intento cuesta y la cola de F03 reintenta por naturaleza. `IMAGE` queda fuera de `requeueOrFail` y de `recoverInterrupted`: tras la llamada todo fallo es `ERROR`, también el de los embeddings. Al arrancar, un `IMAGE` en `PROCESSING` acaba en `READ_FAILED`.
- Alternativa descartada: persistir el texto leído hasta indexar (un dato más en reposo para ahorrar una llamada).
- Revisitar si: el fallo de embeddings tras una lectura pagada resulta frecuente.

### ADR-103: La lectura reutiliza `GenerationLock` y espera sin gastar intento
- Motivo: la regla de F02 es una sola llamada de IA concurrente por usuario. **Amplía el alcance que ADR-20 daba al cerrojo** (que F07 evitó a propósito): una foto puede esperar a una generación larga y al revés. El documento vuelve a `UPLOADED` con `releaseBusy` (resta el intento que sumó `claimNext`).
- Alternativa descartada: un cerrojo propio de lectura (cambia la regla global).
- Revisitar si: se notan esperas largas.

### ADR-104: Las fotos de un menor exigen el permiso de su tutor y dos defensas tras la lectura
- Motivo: una imagen no se puede filtrar antes de enviarla (ADR-34 solo cubre texto). El permiso por menor traslada la decisión al adulto; `appropriate` en la **misma** llamada y `ContentGuard` sobre el texto leído evitan indexar lo inapropiado. El ajuste vive en `childsettings/` porque `guardian/` es de solo lectura (ADR-59); el menor no puede cambiarlo.
- Alternativas descartadas: prohibirlo (no responde a lo pedido); clasificar con una llamada previa (el doble de gasto y la imagen se envía igual).
- Revisitar si: el benchmark muestra que `appropriate` en la misma llamada no es fiable.

### ADR-105: El límite diario de fotos se cuenta al subir, en `material_action`
- Motivo: 429 síncrono sin fila ni llamada, resistente al borrado y por usuario (nunca por IP, D1).
- Alternativas descartadas: contar `ai_usage` (asíncrono, mezcla reintentos, es de solo añadir) o `source_document` (se esquiva borrando).
- Revisitar si: hay más acciones de material con límite (una tabla común).
