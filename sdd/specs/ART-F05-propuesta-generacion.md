# F05 · Generación del curso — FASE 2: Proposal Artifacts

> Propuesta para confirmar antes de la FASE 3. Cita los AC de `US-F05-generacion-curso.md`. Los ADR se pasan a `ARCHITECTURE.md` en la FASE 5.

## ART-001 · Stack

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Backend | Paquete nuevo **`generation`** (cola, generador, validadores, repositorio) y ampliación de `course` (vista del curso, reanudar, exportar) | `course/` tiene prohibido leer material y fragmentos (regla de F04): la generación vive aparte y es la única que usa `MaterialSearch` |
| IA | Tareas existentes `OUTLINE`, `LESSON` y `ASSESSMENT` + `AiRequest.course(id)`; plantillas nuevas `generation-outline`, `generation-lesson`, `generation-exercises`, `generation-exam` | Cada tarea ya tiene su modelo, su tope de salida y su precio por configuración; no hace falta ninguna tarea nueva |
| Concurrencia | `GenerationLock` (F02) con `renew()` + cola en la BD con `FOR UPDATE SKIP LOCKED` | Una generación por usuario, sobrevive a reinicios, sin broker |
| Base de datos | PostgreSQL: migración `V5__generation.sql` | Temario, piezas con estado y motivo de pausa |
| Frontend | Vue 3 + Pinia + **una librería nueva: `marked`**, usada **solo para leer el Markdown** (`marked.lexer`), nunca para generar HTML | ADR-45. Alternativa descartada: lector propio (más código y más fallos en una pieza de seguridad) |

**Dependencias nuevas:** solo `marked` (frontend), con versión exacta fijada al instalar y revisada en cada actualización. El backend no añade ninguna.
**Configuración nueva** (`app.generation.*`, valores de ingeniería sin variable de entorno): hilos del proceso (2), sondeo (3 s), máximo de piezas por curso (150), enfriamiento de «Reanudar» (60 s), tope de una lección (30 000 caracteres).

## ART-002 · Modelo de datos (`V5__generation.sql`)

```
course (se amplía)
  status        + 'PAUSED'                        -- PENDING_GENERATION | GENERATING | PAUSED | READY | FAILED  (se reescribe el CHECK)
  stop_reason   varchar(20)                       -- BUDGET | PROVIDER | CONTENT (pausa) · TOPIC_NOT_ALLOWED | LIMIT (fallo); nulo si todo va bien
  resumed_at    timestamptz                       -- último «Reanudar» (enfriamiento)
  completed_at  timestamptz

course_topic                                      -- el temario, como filas para poder preguntar por estado
  id uuid PK, course_id, owner_id, position, title varchar(120), summary varchar(300)
  FK compuesta (course_id, owner_id) → course(id, owner_id) ON DELETE CASCADE · UNIQUE (id, owner_id) · UNIQUE (course_id, position)

course_chapter
  id uuid PK, topic_id, course_id, owner_id, position, title varchar(120), objective varchar(200)
  FK compuesta (topic_id, owner_id) → course_topic(id, owner_id) ON DELETE CASCADE · UNIQUE (id, owner_id) · UNIQUE (topic_id, position)

course_piece                                      -- lo que hay que generar: una fila por lección, por juego de ejercicios y por examen
  id uuid PK, course_id, owner_id, topic_id, chapter_id (nulo en el examen), kind (LESSON | EXERCISES | EXAM),
  seq integer                                     -- orden de generación dentro del curso; UNIQUE (course_id, seq)
  status (PENDING | DONE | FAILED), error_kind varchar(30), attempts smallint,
  content_md text (solo LESSON), content_json jsonb (EXERCISES, EXAM), sources jsonb (solo LESSON con material: [{n, documentId, page}]),
  created_at, generated_at
  FK compuestas por (…, owner_id) hacia curso, tema y capítulo, todas ON DELETE CASCADE

ai_usage: índice por course_id (la columna ya existe desde V2) para sumar el coste de un curso.
```
- **Una pieza `DONE` no se regenera nunca**: el generador solo mira las piezas `PENDING` o `FAILED`. Reanudar o reiniciar no cambia ninguna `DONE`.
- Orden de `seq` por tema: lección y ejercicios de cada capítulo, y al final el examen del tema si la ficha lo pide. Con 6 temas × 3 capítulos y examen, 42 piezas (más la llamada del temario).
- El rol `studylab_app` recibe `SELECT/INSERT/UPDATE` sobre las tablas nuevas y nada que cambie el esquema. Los ids de documento de `sources` no tienen clave foránea (si el usuario borra el documento, la fuente se muestra como «Fuente n»).
- El coste del curso no se guarda: se suma de `ai_usage` por `course_id` al consultarlo (una sola fuente de verdad).

## ART-003 · Esquemas de cada salida (versionados)

```
Temario (OUTLINE) ─ JSON ─ { "schemaVersion":1, "appropriate":true, "category":"NONE",
    "topics":[ { "title","summary", "chapters":[ {"title","objective"} ×2–4 ] } ×(topicsCount de la ficha) ] }
   - exactamente los temas de la ficha; títulos únicos dentro del curso; longitudes acotadas; para un menor, appropriate=false → el curso falla (AC-7)

Lección (LESSON) ─ Markdown ─ entre 400 y 30 000 caracteres, sin HTML crudo (<script, <iframe, <img… se rechazan), sin enlaces javascript:/data:,
   con al menos un título o dos párrafos. Con material: ≥ 1 marcador [n] y todos válidos; sin material: ninguno. Lo no apoyado en el material se marca «Complemento:».

Ejercicios (ASSESSMENT) ─ JSON ─ { "schemaVersion":1, "exercises":[ { "difficulty":"LOW|MEDIUM|HIGH", "statement":≤1000, "solution":≤2000, "explanation":≤1000 } ×3–8 ] }

Examen (ASSESSMENT) ─ JSON ─ { "schemaVersion":1, "questions":[ { "text":≤300, "options":[≤150 ×3–4], "correct": índice de una sola opción, "explanation":≤500 } ×5–12 ] }
```
Los parsers siguen el patrón de `IntakeParser`: devuelven vacío ante cualquier incumplimiento y nunca lanzan. Un JSON dentro de un bloque de código se acepta; texto fuera del JSON, no.
La lección es **Markdown y no JSON** a propósito: el benchmark mostró que así los modelos escriben mejor, y escapar texto largo dentro de JSON es la causa habitual de salidas rotas.

## ART-004 · Endpoints

Todos exigen sesión (por defecto en `SecurityConfig`) y CSRF en los que modifican; un curso o pieza ajenos, 404 (igual que uno inexistente).

```
GET  /api/courses/{id}              → CourseDetailView   (se amplía solo este; el POST y el listado de F04 no cambian)             AC-10
      { …CourseView, "stopReason", "progress":{done,total}, "costUsd",
        "outline":[ {id,title,summary, chapters:[{id,title,objective, lesson:{id,status}, exercises:{id,status}}], exam:{id,status}|null } ] }
GET  /api/courses/{id}/pieces/{pieceId} → PieceView { id, kind, status, title, markdown?, exercises?, questions?, sources:[{n,page}] }   AC-11
      El contenido solo llega si la pieza está DONE; si no, estado y sin contenido
POST /api/courses/{id}/resume       → 202 CourseDetailView   (409 si no está PAUSED · 429 si se pulsó hace < 60 s)                   AC-9, AC-15
GET  /api/courses/{id}/export       → 200 JSON versionado, sin material, sin nombres de documento y sin datos del usuario           AC-12
```

**API interna (Java, paquete `es.vicai.studylab.generation`)**
```
CourseGenerator.processPending() → int          // síncrono (tests y hilo de fondo); reclama un curso por usuario, genera hasta pausar o terminar
CourseGenerator.recoverInterrupted()            // al arrancar: GENERATING → PENDING_GENERATION
OutlineParser · LessonValidator · ExercisesParser · ExamParser · SourcePack (fragmentos numerados + mapa [n] → fuente)
GenerationRepository · GenerationWorkerLoop (hilos de fondo, apagables en tests)
```

**Flujo del generador (un curso):**
1. Reclama un curso `PENDING_GENERATION` de un propietario activo sin otro curso en `GENERATING`; toma el `GenerationLock` (`acquire`); si está ocupado, lo deja en cola.
2. **Si no hay temario:** llamada `OUTLINE` (ficha + audiencia como datos) → `OutlineParser`. Un menor con `appropriate=false` → `FAILED / TOPIC_NOT_ALLOWED`, auditoría y fin. Si es válido, guarda temas, capítulos y **todas las piezas** (`PENDING`) en una transacción.
3. **Para cada pieza `PENDING` o `FAILED`, en `seq`:** `lease.renew()` (si devuelve `false`, aborta sin escribir nada); prepara el encargo (con material: `MaterialSearch` sobre los documentos del curso, k = 4, y para un menor se
   descartan los fragmentos que coinciden con `ContentGuard`); llama a la tarea que toca con `.course(id)`; valida; para un menor pasa el texto por `ContentGuard`; guarda `DONE`.
4. **Si la validación falla:** un reintento (otra llamada). Si falla otra vez, `FAILED` con motivo genérico y **sigue con la siguiente**.
5. **Si la capa de IA lanza** `USER_LIMIT` o `BUDGET_BLOCKED` → `PAUSED / BUDGET`; `PROVIDER_*` o `CONFIG` → `PAUSED / PROVIDER`; `USER_NOT_ALLOWED` (usuario dado de baja) → vuelve a la cola sin cambios. En los tres casos **se detiene** sin más llamadas.
6. **Al terminar las piezas:** todas `DONE` → `READY`; si alguna quedó `FAILED` → `PAUSED / CONTENT` (reanudar solo reintenta esas). Libera el cerrojo y audita el resultado.

## ART-005 · Estructura de componentes

```
router: /cursos/:id (ya existe, se amplía) · /cursos/:id/piezas/:pieceId (protegida)
views/CourseStatusView.vue            AC-14   temario con estado por pieza, progreso, aviso de pausa con motivo y botón «Reanudar»; sondeo cada 3 s mientras PENDING/GENERATING
 └─ components/CourseOutline.vue              temas → capítulos → «Lección» y «Ejercicios» con su estado; «Examen» por tema; cada pieza lista es un enlace
views/PieceView.vue                   AC-11, 13   la lección con formato (o los ejercicios y el examen), las fuentes citadas y «generado por IA»; «Volver al curso»
 ├─ components/MarkdownContent.vue            tokens de `marked.lexer` → elementos Vue de una lista permitida (ADR-45); nunca HTML
 ├─ components/ExerciseList.vue               enunciado, dificultad y solución dentro de «Ver solución» (<details>)
 └─ components/ExamView.vue                   preguntas con sus opciones; «Ver respuestas» muestra la correcta y la explicación (sin corrección interactiva: es F06)
stores/courseDetail.ts (Pinia)        carga, sondeo y reanudar; se detiene al salir o al terminar
api/client.ts                         getCourse (ampliado), getPiece, resumeCourse, exportCourse
CoursesPanel / HomeView               etiqueta nueva «En pausa» y «No se pudo preparar»
```
Todo texto (títulos, lecciones, opciones) se pinta con `{{ }}` o con el visor saneado; el test de política que prohíbe `v-html` e `innerHTML` se mantiene sin excepciones. Los tests usan `data-testid`.

## ART-006 · Estructura de carpetas (solo lo nuevo)

```
backend/src/main/java/es/vicai/studylab/generation/   CourseGenerator · GenerationWorkerLoop · GenerationRepository · GenerationProperties · SourcePack ·
                                                      OutlineParser · LessonValidator · ExercisesParser · ExamParser · views (CourseDetailView, PieceView, ExportView)
backend/src/main/java/es/vicai/studylab/course/       CourseController (+ resume, pieces, export) · CourseService (detalle)
backend/src/main/resources/db/migration/V5__generation.sql
backend/src/main/resources/prompts/                   generation-outline.txt · generation-lesson.txt · generation-exercises.txt · generation-exam.txt
backend/src/test/java/es/vicai/studylab/generation/   OutlineParserTest · LessonValidatorTest · ExercisesParserTest · ExamParserTest · SourcePackTest · GenerationTestBase ·
                                                      GenerationFlowTest · GenerationResumeTest · GenerationBudgetTest · GenerationMaterialTest · GenerationMinorTest ·
                                                      GenerationAccessTest · GenerationExportTest · GenerationSchemaTest · GenerationArchitectureTest
frontend/src/views/PieceView.vue · frontend/src/components/{MarkdownContent,CourseOutline,ExerciseList,ExamView}.vue · frontend/src/stores/courseDetail.ts
frontend/tests/…                                      (visor con marcado hostil, vistas, store, cliente)
```

## ART-007 · Wireframe

```
┌ Inglés de 2º de la ESO ─────────────┐   ┌ Lección: Present simple ────────────┐
│ ◌ Preparando tu curso…  12 de 43    │   │ ← Volver al curso                     │
│ Generado por IA.                    │   │ # Present simple                      │
│ ▸ Tema 1 · Presentarse        ✓     │   │ Usamos el *present simple* para…      │
│    Lección 1 · To be          ✓ Ver │   │ | Persona | Forma |                   │
│    Ejercicios 1               ✓ Ver │   │ |---------|-------|                   │
│    Lección 2 · Saludos        ◌     │   │ Fuentes: [1] apuntes, pág. 3          │
│    Examen del tema            ·     │   │ Generado por IA.                      │
│ ▸ Tema 2 · …                  ·     │   └───────────────────────────────────────┘
└─────────────────────────────────────┘
┌ En pausa ───────────────────────────┐   ┌ No se puede crear este contenido ────┐
│ Has llegado a tu límite de uso este │   │ Contenido inapropiado.               │
│ mes. Llevas 12 de 43. Lo hecho se   │   │ Consulta con tu adulto.              │
│ conserva.        [ Reanudar ]       │   └───────────────────────────────────────┘
└─────────────────────────────────────┘
```

## ART-008 · ADR (propuestos; se numeran a continuación de ADR-41)

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
- Motivo: tu decisión: sin reintentos automáticos que gasten sin que nadie lo vea. Al alcanzar el tope (`USER_LIMIT`, `BUDGET_BLOCKED`) o caer el proveedor, el curso pasa a `PAUSED` con su motivo y no hace más llamadas. «Reanudar» tiene un enfriamiento de 60 s
  por curso (A2) y solo vale en `PAUSED`; un curso reanudado vuelve a la cola y continúa por lo que falta.
- Alternativas descartadas: reintento automático cada N minutos, estados distintos para cada causa de pausa.
- Revisitar si: hace falta avisar por correo cuando una pausa dure mucho.

### ADR-45: El Markdown se pinta sin HTML: tokens de `marked.lexer` a elementos Vue de una lista permitida
- Motivo: el contenido lo escribe un modelo y puede intentar cualquier cosa. En lugar de convertir a HTML y sanear (el punto donde siempre falla algo), `marked` solo **lee** el texto en tokens y `MarkdownContent` construye
  elementos Vue con una lista cerrada (títulos, párrafos, listas, énfasis, código, citas, tablas, enlaces `http(s)` con `rel="noopener noreferrer nofollow"`). El HTML crudo, las imágenes, los enlaces con otro esquema y las fórmulas salen
  como texto; la profundidad y el nº de tokens están acotados. No hay `v-html` ni `innerHTML`, y el test de política del frontend se mantiene tal cual.
- Alternativas descartadas: `marked` + DOMPurify (dos librerías y una cadena HTML en medio), lector propio (más código crítico), `v-html` con sanear (prohibido).
- Revisitar si: se necesitan fórmulas (KaTeX, que añadiría su propia revisión) o imágenes.

### ADR-46: Con material propio, solo fragmentos de los documentos del curso, numerados y verificados
- Motivo: la lección cita lo que usa. `SourcePack` numera los fragmentos recuperados (solo de los documentos enlazados al curso y del propietario, con `MaterialSearch`) y guarda el mapa `[n] → documento y página`. El modelo recibe los textos como
  **datos**, sin los nombres de los documentos; la lección se acepta solo si sus marcadores `[n]` existen. Para un menor, antes de enviar nada al modelo se descartan los fragmentos que coinciden con `ContentGuard` (condición de ADR-34 y F3-1).
  La exportación cita «Fuente n» con la página, nunca el nombre del documento.
- Alternativas descartadas: enviar el documento entero, permitir citas sin comprobar, citar por nombre de fichero.
- Revisitar si: se quiere citar con enlace al documento dentro de la aplicación (F06).

### ADR-47: El contenido generado para un menor se comprueba antes de guardarse y el tema se vuelve a valorar en el temario
- Motivo: F04 acepta el tema de un menor aunque la valoración del modelo falte (F4-5). El temario trae su propia valoración: si el modelo lo juzga no apto para escolares, el curso pasa a `FAILED / TOPIC_NOT_ALLOWED` sin generar nada. Además todo lo
  generado para un menor pasa por la capa local de `ContentGuard` antes de guardarse (un reintento con instrucción reforzada; si persiste, pieza `FAILED` y auditoría `COURSE_CONTENT_BLOCKED` sin el contenido).
- Riesgo asumido: la lista local es corta y solo en español; la valoración del modelo y el tutor son las otras capas.
- Revisitar si: el tutor debe poder aprobar o rechazar contenido concreto (F08).

## Impacto en lo existente
`course` (la vista del curso y los controladores se amplían; el POST y el listado de F04 **no cambian de forma**), CHECK de `course.status` (V5), `ai_usage` (índice), `CoursesPanel` y `CourseStatusView` del frontend, el test de política del frontend (se mantiene, sin excepciones),
`MaterialSearch` (se usa por primera vez fuera de F03), `GenerationLock` (se usa por primera vez), `FakeProvider` (respuestas por tarea además de por secuencia), `application-test.properties` (hilo de generación apagado),
la plantilla de `OUTLINE`, `LESSON` y `ASSESSMENT` del entorno de pruebas. `SecurityConfig`, nginx y Compose no cambian.

## Riesgos y puntos abiertos
- **Calidad y estabilidad con un modelo real:** el JSON de ejercicios y exámenes se rompe más que el de F04. La FASE 7 usa un servidor simulado; la calidad real solo la puedes medir tú con tus claves (coste del orden de 0,1 a 0,5 USD por curso).
- **Tiempo:** unas 43 llamadas de 10 a 40 s son de 10 a 25 minutos por curso. El cerrojo (20 min) se renueva entre llamadas, así que una llamada larga no lo agota, pero si un proveedor tarda más de 20 min en una sola, el cerrojo caduca (se aborta la pieza).
- **Un menor y el tope de 2 USD:** con modelos caros, un curso puede no terminar dentro del tope; el curso se pausa y el tutor tendría que subir el tope (hoy es configuración, F4-2).
- **El visor es una superficie de ataque nueva:** por eso va con su batería de entradas hostiles y el test de política intacto.
- **Fórmulas LaTeX** (Gemini y OpenAI): se piden «sin LaTeX» en las plantillas y, si aparecen, se ven como texto.
