# F06 · Estudio — FASE 2: Proposal Artifacts

> Propuesta para confirmar antes de la FASE 3. Cita los AC de `US-F06-estudio.md`. Los ADR (49 a 53) se pasan a `ARCHITECTURE.md` en la FASE 5.

## ART-001 · Stack

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Backend | Paquete nuevo **`study`** (progreso, intentos de ejercicio y de examen, corrección con IA) | F05 deja `generation/` para generar; estudiar es otra responsabilidad y lee las piezas hechas (siempre con `owner_id`) |
| IA | Tarea **`ASSESSMENT` existente** + plantilla nueva `grade-exercise` (salida JSON corta, `maxOutputTokens` bajado por petición) | No añade una tarea ni una variable de entorno obligatoria: quien ya genera ejercicios ya tiene el modelo configurado (ADR-52) |
| Base de datos | PostgreSQL: migración `V6__study.sql` | Estado de lección, intentos de ejercicio y de examen |
| Frontend | Vue 3 + Pinia, sin librerías nuevas | `MarkdownContent` ya existe; nada que instalar |

**Dependencias nuevas:** ninguna. **Configuración nueva** (`app.study.*`, valores de ingeniería sin variable de entorno): nota mínima del examen (50 %), envíos por hora (120), correcciones de IA al día (20) e intentos guardados por pieza (200).

## ART-002 · Modelo de datos (`V6__study.sql`)

```
course_piece  + UNIQUE (id, owner_id)               -- para poder enlazar con clave compuesta

lesson_progress
  owner_id, piece_id (una lección), course_id, completed_at
  PK (piece_id, owner_id) · FK compuesta (piece_id, owner_id) → course_piece · FK (course_id, owner_id) → course · ON DELETE CASCADE

exercise_attempt                                    -- un envío de UN ejercicio (item_index) de una pieza de ejercicios
  id uuid PK, owner_id, course_id, piece_id, item_index smallint (0..7),
  answer text (≤ 2 000), self_result varchar(9) nulo (CORRECT|PARTIAL|INCORRECT),
  ai_result varchar(9) nulo, ai_comment varchar(600) nulo, ai_requested_at timestamptz nulo,
  created_at · FK compuestas por (…, owner_id) hacia pieza y curso, ON DELETE CASCADE
  índice (owner_id, created_at)                      -- cuenta envíos por hora y correcciones por día

exam_attempt
  id uuid PK, owner_id, course_id, piece_id (un EXAM), status (STARTED|SUBMITTED), answers jsonb nulo ([int|null] por pregunta),
  correct smallint nulo, total smallint nulo, started_at, submitted_at nulo
  FK compuestas igual que arriba · índice (piece_id, owner_id, status, submitted_at)
```
- **El progreso no se guarda: se calcula** al consultarlo (ADR-49): lección hecha = hay fila en `lesson_progress` y su pieza está `DONE`; examen superado = existe un intento `SUBMITTED` con `correct * 100 >= total * pass-percent`. Una sola fuente de verdad, sin contadores que se desincronicen.
- Curso: `(lecciones hechas + exámenes superados) / (lecciones + exámenes del temario)`; el denominador cuenta todas las piezas aunque aún no estén generadas. Tema: igual, con sus lecciones y su examen. Los ejercicios no cuentan.
- **Retención:** tras cada envío se borran los intentos más antiguos de esa pieza y ese usuario por encima de 200; los exámenes `STARTED` con más de un día se borran al empezar otro.
- El rol `studylab_app` recibe `SELECT/INSERT/UPDATE/DELETE` sobre las tablas nuevas y nada que cambie el esquema. No se añade ningún valor a `ai_usage.task`.

## ART-003 · Endpoints

Todos exigen sesión (por defecto en `SecurityConfig`) y CSRF en los que modifican; un curso, pieza o intento ajenos o inexistentes responden **404 idéntico**. El tutor no lee los intentos de su menor por esta API (F08).

```
GET  /api/study/progress                                         → [{courseId, percent}]  para «Tus cursos»                                         AC-1
GET  /api/courses/{id}/study                                     → { percent, lessonsDone, lessonsTotal, examsPassed, examsTotal,
                                                                    topics:[{id, percent, lessons:[{pieceId, status, done}], exam:{pieceId, status, attempts, best, last, passed}|null}] }   AC-2, 4, 12
PUT  /api/courses/{id}/pieces/{pid}/done          {done: bool}   → 204; solo lecciones DONE; idempotente                                                   AC-3
GET  /api/courses/{id}/pieces/{pid}               (F05, cambia)  → lección con navigation:{previous,next} · ejercicios SIN solución · examen SIN opción correcta ni explicación   AC-3, 5, 8

POST /api/courses/{id}/pieces/{pid}/exercises/{i}/attempts       {answer}  → 201 {attemptId, solution, explanation}; 400 · 404 · 409 pieza no DONE · 429 límite por hora   AC-5, 12, 13, 15
PUT  …/exercises/{i}/attempts/{aid}/result                       {result}  → 204; CORRECT|PARTIAL|INCORRECT                                                 AC-5
POST …/exercises/{i}/attempts/{aid}/ai-grade                     → 200 {verdict, comment}; 409 ya pedida · 422 respuesta bloqueada (menor) · 429 límite diario o ya hay una · AI_* tal cual   AC-6, 7, 15
GET  /api/courses/{id}/pieces/{pid}/attempts                     → historial: ejercicios (por ejercicio, más reciente primero) o exámenes (fecha, nota)   AC-11

POST /api/courses/{id}/pieces/{pid}/exam/attempts                → 201 {attemptId, questions:[{text, options}]}; reutiliza uno STARTED; 409 si la pieza no está DONE    AC-8, 12
POST …/exam/attempts/{aid}/submit                                {answers:[int|null]} → 200 {correct, total, percent, passed, questions:[{text, options, chosen, correct, explanation, ok}]}
                                                                  repetir la entrega devuelve el mismo resultado (idempotente); 400 nº de respuestas o índices inválidos        AC-8, 9, 10, 13
GET  …/exam/attempts/{aid}                                       → el resultado de un intento ya entregado («qué fallé»)                                  AC-11
```
**Orden de comprobaciones de «Corregir con IA»:** propietario y esquema → ya pedida (409) → límite por hora/día (429) → una a la vez por usuario (429) → menor: `ContentGuard` sobre la respuesta (422) → `AiGateway.complete(ASSESSMENT)` con un reintento si el JSON no vale → guardar. Cualquier fallo conserva la respuesta y el alumno puede autocorregirse.

## ART-004 · Estructura de componentes

```
router: /cursos/:id (se amplía) · /cursos/:id/piezas/:pieceId (se amplía) · /cursos/:id/piezas/:pieceId/intentos (historial, protegida)

components/CoursesPanel.vue            AC-1   porcentaje junto a cada curso (stores/study.ts)
views/CourseStatusView.vue             AC-2, 12   el temario muestra lección hecha / pendiente / aún no generada, avance por tema y mejor nota del examen
 └─ components/CourseOutline.vue                  (se amplía con el estado de estudio)
views/PieceView.vue                    AC-3, 5, 8   según el tipo de pieza:
 ├─ components/LessonControls.vue                 «Marcar como hecha» (desmarcable) y enlaces anterior / siguiente
 ├─ components/ExercisePractice.vue               caja de respuesta, «Enviar», solución y explicación tras enviar, «Lo he acertado / Casi / No», «Corregir con IA»
 ├─ components/ExamTake.vue                       preguntas con una opción por pregunta, aviso de las que faltan, «Entregar»
 ├─ components/ExamResult.vue                     nota, aprobado o no, y cada pregunta con tu respuesta, la correcta y la explicación
 └─ components/AttemptHistory.vue    AC-11        lista de intentos; abrir uno muestra qué falló
stores/study.ts (Pinia)                           progreso del curso, marcar, intentos, corrección con IA y estados de carga y error
api/client.ts                                     getStudy, getStudyProgress, setLessonDone, sendExerciseAnswer, setExerciseResult, aiGrade, startExam, submitExam, getAttempts, getExamAttempt
```
`ExerciseList.vue` y `ExamView.vue` de F05 se sustituyen por los de arriba (ya no pueden mostrar la solución desde la pieza). Todo texto del modelo y del alumno se pinta con `{{ }}` o con el visor sin HTML; los tests usan `data-testid`.

## ART-005 · Estructura de carpetas (solo lo nuevo)

```
backend/src/main/java/es/vicai/studylab/study/     StudyController · StudyService · StudyRepository · ExamGrader · GradeParser · GradingService · StudyProperties · StudyException
backend/src/main/resources/db/migration/V6__study.sql
backend/src/main/resources/prompts/grade-exercise.txt
backend/src/test/java/es/vicai/studylab/study/     StudyTestBase · ProgressTest · LessonDoneTest · ExerciseAttemptTest · AiGradingTest · GradeParserTest · ExamTest ·
                                                   ExamResultTest · AttemptHistoryTest · StudyAccessTest · StudyLimitsTest · StudySchemaTest · StudyArchitectureTest
frontend/src/components/{LessonControls,ExercisePractice,ExamTake,ExamResult,AttemptHistory}.vue · frontend/src/stores/study.ts · frontend/src/views/AttemptsView.vue
frontend/tests/…                                   (componentes, store, cliente y navegación)
```

## ART-006 · Wireframe

```
┌ Java básico ─────────────────────────┐   ┌ Lección: ¿Qué es Java? ──────────────┐
│ Tu avance 38 %  ███████░░░░░░░░░░    │   │ ← Volver al curso                     │
│ ▸ Tema 1 · Introducción      100 %   │   │ # ¿Qué es Java?                       │
│    ✓ Lección: ¿Qué es Java?          │   │ …                                     │
│    · Ejercicios (3)                  │   │ [ ✓ Marcar como hecha ]               │
│    ✓ Examen del tema   80 % · superado│  │ ← Anterior            Siguiente →     │
│ ▸ Tema 2 · Variables          33 %   │   └───────────────────────────────────────┘
│    ✓ Lección: Tipos                  │   ┌ Ejercicio 2 de 3 (Normal) ───────────┐
│    ◌ Lección: Operadores  aún no gen.│   │ Escribe una función que…             │
│    · Examen del tema   sin hacer     │   │ [ tu respuesta…                    ]  │
└──────────────────────────────────────┘   │ [ Enviar ]   [ Corregir con IA ]      │
┌ Examen: Introducción ────────────────┐   │ Solución: …  Explicación: …           │
│ 1. ¿Qué imprime…?   ( ) A ( ) B ( ) C│   │ ¿Cómo te fue? (Lo acerté)(Casi)(No)   │
│ 2. …                                 │   │ La IA dice: Casi. «Falta…»  (puede    │
│ Te faltan 1 sin contestar [ Entregar]│   │ equivocarse)                          │
└──────────────────────────────────────┘   └───────────────────────────────────────┘
┌ Resultado: 4 de 5 · 80 % · Tema superado ┐
│ 3. ¿…?  Tu respuesta: B ✗  Correcta: C   │
│    Porque…                [ Repetir ] [ Historial ]
└──────────────────────────────────────────┘
```

## ART-007 · ADR (propuestos; se numeran a continuación de ADR-48)

### ADR-49: El progreso se calcula al consultarlo, no se guarda
- Motivo: lo único que el alumno decide es «marcar como hecha» (una fila por lección) y entregar exámenes (una fila por intento). Con eso, el avance del curso, de cada tema y el «superado» salen de una consulta; no hay contadores que se desincronicen cuando una pieza se genera tarde, se desmarca una lección o se reanuda un curso. El denominador son todas las piezas del temario, así que un curso a medias no aparenta más avance.
- Alternativas descartadas: guardar porcentajes por curso y tema (hay que recalcularlos en cada cambio y tras cada generación), marcar la lección al abrirla (cuenta sin leer).
- Revisitar si: hay miles de cursos por usuario (entonces, una tabla de resumen).

### ADR-50: El servidor corrige el examen y la opción correcta no sale hasta entregar
- Motivo: si la pieza de examen de F05 entrega `correct` y la explicación, «autoevaluación» es solo un adorno. Desde F06 la pieza solo lleva texto y opciones; el servidor guarda las respuestas, las corrige sin IA y devuelve el resultado con la opción correcta y la explicación **después** de entregar. Los ejercicios siguen la misma regla: la solución llega al enviar la respuesta. La exportación del curso sigue llevando todo (es el contenido del propio alumno).
- Alternativas descartadas: corregir en el cliente (visible en las herramientas del navegador), dejarlo como está y esconderlo en la interfaz.
- Revisitar si: se quiere un modo de examen sin exportación hasta entregar.

### ADR-51: El examen es un intento de dos pasos e idempotente
- Motivo: «Empezar» crea un intento `STARTED` (y reutiliza uno abierto) y «Entregar» lo cierra; repetir la entrega devuelve el mismo resultado en lugar de duplicarlo, y las preguntas que se ven son las del intento. Los intentos abiertos antiguos se purgan y solo cuentan los entregados. Cada pieza conserva los últimos 200 intentos por usuario.
- Alternativas descartadas: una sola petición que crea el intento al entregar (doble clic duplica y no hay forma de saber qué preguntas vio), intentos sin límite (crece sin fin).
- Revisitar si: se necesita un tiempo máximo por examen.

### ADR-52: Autocorrección siempre, «Corregir con IA» opcional y con la tarea que ya existe
- Motivo: los ejercicios de F05 son de respuesta abierta; la autocorrección (ver la solución y decir cómo te fue) no cuesta nada ni depende del modelo, y la IA se ofrece como segunda opinión, siempre con aviso de que puede equivocarse y sin impedir que el alumno decida. Se reutiliza `ASSESSMENT` (quien genera ejercicios ya tiene ese modelo) con una plantilla propia y un límite de salida bajado por petición: no añade variables obligatorias. Límites: una corrección por intento, 20 al día por usuario (contadas en la BD) y una a la vez por usuario (en memoria: una sola instancia). La respuesta del alumno, la solución y el enunciado van como **datos**; el veredicto se valida con un esquema estricto (un reintento) y el comentario de un menor pasa por `ContentGuard`.
- Alternativas descartadas: corregir siempre con IA (coste en cada intento y dependencia del proveedor), una tarea nueva `GRADING` (una variable obligatoria más para quien ya tiene el stack).
- Revisitar si: hay varias instancias del backend (el límite de «una a la vez» debe pasar a la BD) o se añade un motor de cálculo para matemáticas.

### ADR-53: Las respuestas del alumno no se registran y los límites se cuentan en la BD
- Motivo: lo que escribe un alumno (sobre todo un menor) es dato personal: ni logs ni auditoría llevan respuestas, solo ids y resultados. Los envíos por hora (120) y las correcciones de IA al día (20) se cuentan por usuario sobre las propias tablas, sin depender de la IP (D1). Cierra A2 para F06.
- Alternativas descartadas: limitar por IP (no vale detrás del proxy), no limitar (un bucle de «Corregir con IA» gasta hasta el tope).
- Revisitar si: el tutor necesita ver el texto exacto de las respuestas de su menor (F08: lo leerá de estas mismas tablas, no de los logs).

## Impacto en lo existente
`generation` (`CourseContentService.piece` deja de enviar la solución de los ejercicios y la opción correcta y la explicación del examen, y añade `navigation` a la lección; `ExerciseList.vue` y `ExamView.vue` se sustituyen),
`course_piece` (una restricción única nueva), `CoursesPanel` y `CourseStatusView`/`CourseOutline` (avance y estado de estudio), el test de política del frontend (se mantiene), `GlobalExceptionHandler` (sin cambios: los errores de IA ya
tienen su estado). Los tests de F05 que comprueban `correct`/`solution` en la pieza se actualizan a la nueva regla. `SecurityConfig`, nginx y Compose no cambian.

## Riesgos y puntos abiertos
- **Calidad de «Corregir con IA»:** un veredicto equivocado puede desanimar o confundir; por eso es opcional, avisa y el alumno manda. Solo el usuario puede medir con su modelo cuántas veces acierta.
- **Coste:** hasta 20 correcciones al día por usuario, a céntimos cada una; el tope personal las corta igual que al resto.
- **Un menor y la solución:** la solución de un ejercicio de F05 ya pasó el filtro de menores al generarse; la respuesta del menor se filtra antes de enviarla al modelo.
- **La exportación** incluye soluciones y respuestas correctas: es el contenido propio del alumno, pero rompe la «autoevaluación» si la usa antes de examinarse.
- **El tutor** (F08) necesitará leer estos intentos con permiso propio; hoy ninguna API lo permite.
- **Un curso con piezas falladas** no llega al 100 % hasta que «Reanudar» las regenere: es lo correcto, y la pantalla ya lo explica.
