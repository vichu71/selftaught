# F04 · Asistente de creación de curso — FASE 2: Proposal Artifacts

> Propuesta para confirmar antes de la FASE 3. Cita los AC de `US-F04-asistente-creacion-curso.md`. Los ADR se pasan a `ARCHITECTURE.md` en la FASE 5.

## ART-001 · Stack

`PROJECT.md` ya fija el stack: **F04 no añade ninguna dependencia** (Jackson, que ya está, valida el JSON del modelo).

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Backend | Paquetes nuevos `course` (asistente y cursos) y `safety` (filtro de menores, reutilizable) | El filtro no pertenece al asistente: F05 y F07 lo necesitan (A3) |
| IA | Tarea nueva **`INTAKE`** en `AiGateway` (plantilla `prompts/intake.txt`, tope de salida 1 500 tokens, tiempo 60 s) | Regla de F02: toda tarea de IA tiene su plantilla, su tope y su modelo por configuración. Un modelo barato basta |
| Base de datos | PostgreSQL: migración `V4__course.sql` | Cursos, enlace curso↔documentos y consultas del asistente |
| Frontend | Vue 3 + Pinia, sin librerías nuevas | Asistente en pasos y estado del curso |

Configuración nueva: `APP_AI_TASKS_INTAKE_PROVIDER` / `_MODEL` (en desarrollo, el proveedor falso), `COURSE_INTAKES_PER_HOUR` (30) y `COURSE_MAX_PENDING` (5). La caducidad
de una consulta (1 hora), el rango de temas (3 a 12, 6 por defecto) y los términos bloqueados son valores de ingeniería, no de entorno.

## ART-002 · Modelo de datos (`V4__course.sql`)

```
course_intake                                  -- una consulta al asistente (también las bloqueadas)
  id            uuid PK
  owner_id      uuid NOT NULL → app_user
  topic         varchar(200) NOT NULL          -- el tema tal como lo escribió (lo verá el tutor si se bloquea, F08)
  document_ids  jsonb NOT NULL DEFAULT '[]'    -- documentos elegidos (se revalidan al aceptar)
  status        varchar(10) NOT NULL           -- OPEN | CONSUMED | BLOCKED
  questions     jsonb                          -- preguntas validadas, guardadas por el SERVIDOR; nulo si se bloqueó
  blocked_category varchar(20)                 -- EXPLICIT | VIOLENCE | DRUGS | SELF_HARM | OTHER  (solo si BLOCKED)
  created_at    timestamptz NOT NULL DEFAULT now()
  expires_at    timestamptz NOT NULL           -- created_at + 1 h
  INDEX (owner_id, created_at)                 -- cuenta las consultas de la última hora (A2)

course
  id            uuid PK
  owner_id      uuid NOT NULL → app_user
  intake_id     uuid NOT NULL UNIQUE → course_intake   -- un curso por consulta: idempotencia (AC-6)
  title         varchar(200) NOT NULL          -- el tema
  status        varchar(20) NOT NULL           -- PENDING_GENERATION | GENERATING | READY | FAILED  (F04 solo usa el primero)
  sheet         jsonb NOT NULL                 -- la ficha v1 (contrato con F05)
  created_at, updated_at  timestamptz
  UNIQUE (id, owner_id)

course_document                                -- qué documentos de «Mi material» usa cada curso
  course_id, document_id, owner_id
  PK (course_id, document_id)
  FK compuesta (course_id, owner_id)   → course(id, owner_id)           ON DELETE CASCADE
  FK compuesta (document_id, owner_id) → source_document(id, owner_id)  ON DELETE CASCADE

ai_usage: se amplía el CHECK de `task` con 'INTAKE'.
```
- Las **claves foráneas compuestas** impiden que un curso use el documento de otro usuario aunque el código fallase (mismo patrón que `source_chunk`, ADR-33).
- Si el usuario **borra un documento**, desaparece su enlace con el curso (el curso sigue existiendo); F05 trabajará con los documentos que queden.
- El rol `studylab_app` recibe `SELECT/INSERT/UPDATE` sobre `course` y `course_intake`, `SELECT/INSERT/DELETE` sobre `course_document`, y nada que cambie el esquema.
- Las consultas bloqueadas guardan el texto del tema, porque el tutor debe poder verlo (F08). Es dato del propio menor, visible solo para él y su tutor.

## ART-003 · Endpoints

Todos exigen sesión (por defecto en `SecurityConfig`) y CSRF en los que modifican.

```
POST /api/courses/intake  {topic, documentIds?} → 201 {intakeId, questions:[{id, text, options:[{id, label, isDefault}]}]}           AC-1..5, 8..13, 15
   400 VALIDATION · 404 documento ajeno/inexistente/no listo · 409 COURSE_LIMIT_REACHED · 422 TOPIC_NOT_ALLOWED (solo menores)
   429 RATE_LIMITED · 429 AI_USER_LIMIT / AI_BUDGET_BLOCKED (capa de IA) · 502/503 errores de IA (ya mapeados)
POST /api/courses         {intakeId, answers:[{questionId, optionId}]} → 201 CourseView  (repetido con la misma consulta: 200 y el mismo curso)   AC-5..7, 13, 14
   400 respuesta que no corresponde a una pregunta · 404 consulta ajena o inexistente · 410 INTAKE_EXPIRED · 409 COURSE_LIMIT_REACHED
GET  /api/courses         → [CourseView]   (los del usuario, más recientes primero)                                               AC-14, 16
GET  /api/courses/{id}    → CourseView     (404 si es ajeno o inexistente)                                                         AC-14

CourseView = { id, title, status, sheet:{ schemaVersion, topic, focus, startLevel, goal, topicsCount, examPerTopic },
               documents:[{id, name}], createdAt }
```
- **Las preguntas las guarda el servidor** (`course_intake.questions`): al aceptar solo se comprueba que cada `optionId` pertenezca a su `questionId` guardado. El cliente no puede inventar
  preguntas ni valores.
- Cada pregunta del modelo lleva un `field` interno (`focus`, `startLevel`, `goal`, `topicsCount` o `examPerTopic`) que **no sale al cliente** y dice qué campo de la ficha rellena.
  «Como tú veas» equivale a la opción marcada por defecto.
- Un menor no recibe nunca la categoría del bloqueo: solo «Contenido inapropiado. Consulta con tu adulto.».

**API interna (Java)**
```
es.vicai.studylab.safety.ContentGuard.localCheck(String texto) → Optional<BlockCategory>     // capa local, sin coste; reutilizable por F05 y F07 (A3)
es.vicai.studylab.course: CourseController · CourseService · IntakeService · IntakeParser (JSON del modelo → esquema estricto) ·
                          SheetFactory (respuestas → ficha) · CourseRepository · CourseProperties
AiTask.INTAKE · prompts/intake.txt
```

**Flujo del intake (una sola llamada al modelo):**
1. Valida el tema y los documentos (propios y `READY`) → cuenta las consultas de la última hora y los cursos pendientes (límites A2).
2. Si el usuario es **menor**: `ContentGuard.localCheck(tema)`. Si bloquea → guarda la consulta `BLOCKED` y responde 422, **sin llamar al modelo**.
3. `AiGateway.complete(INTAKE)` con el tema como dato y la audiencia (`escolar` para menores, `general` para adultos). La plantilla pide un JSON con la valoración
   (`appropriate`, `category`) y de 0 a 3 preguntas.
4. `IntakeParser` valida el JSON contra el esquema (también sin los «```» que algunos modelos añaden). Para un menor, `appropriate=false` → `BLOCKED` y 422. Para un adulto la valoración se ignora.
   Si el JSON es inválido: **un reintento**; si sigue mal, 0 preguntas y valores por defecto.
5. Guarda la consulta `OPEN` con las preguntas validadas y devuelve su id.

## ART-004 · Estructura de componentes

```
router: /nuevo-curso (protegida) · /cursos/:id (protegida)        HomeView: botón «Nuevo curso» y panel «Tus cursos»
views/NewCourseView.vue            AC-1, 16     asistente en pasos con «Atrás»; el tema y el paso se conservan si falla un paso
 ├─ components/CourseTopicStep.vue         «¿Qué quieres estudiar?» (3 a 200 caracteres; el límite se muestra al escribir)
 ├─ components/CourseMaterialStep.vue      «¿Tienes información que aportar?» Sí/No; lista de documentos listos (con su estado) y subida de uno nuevo (reutiliza el store de material)
 ├─ components/CourseQuestionsStep.vue     0 a 3 preguntas con opciones, la de por defecto marcada y «Como tú veas»
 └─ components/CourseSummary.vue           resumen en lenguaje llano y botón Aceptar
views/CourseStatusView.vue         AC-7         «Tu curso está en cola: lo estamos preparando» + ficha + «generado por IA»
components/CoursesPanel.vue        AC-16        «Tus cursos» en el inicio (título y estado)
stores/course.ts (Pinia)           tema, pasos, consulta, preguntas, respuestas, errores, reintento sin perder el tema
api/client.ts                      startIntake, createCourse, listCourses, getCourse
```
Los textos del tema y de las opciones (que vienen del modelo) se pintan con `{{ }}`, nunca con `v-html`. Los tests usan `data-testid`. `HomeView` pasará a llamar a `listCourses`:
sus tests actuales tendrán que simular ese método.

## ART-005 · Estructura de carpetas (solo lo nuevo)

```
backend/src/main/java/es/vicai/studylab/safety/    ContentGuard.java · BlockCategory.java
backend/src/main/java/es/vicai/studylab/course/    CourseController · CourseService · IntakeService · IntakeParser · SheetFactory ·
                                                   CourseRepository · CourseProperties · CourseSheet · CourseView · Question · …
backend/src/main/resources/db/migration/V4__course.sql
backend/src/main/resources/prompts/intake.txt
backend/src/main/resources/safety/blocked-terms.txt          # categoría y términos, uno por línea; editable sin recompilar el código
backend/src/test/java/es/vicai/studylab/safety/    ContentGuardTest (batería de temas bloqueados y de temas escolares legítimos)
backend/src/test/java/es/vicai/studylab/course/    IntakeParserTest · SheetFactoryTest · CourseIntakeTest · CourseCreationTest · CourseAccessTest ·
                                                   CourseLimitsTest · MinorFilterTest · CourseArchitectureTest · CourseSchemaTest
frontend/src/views/NewCourseView.vue · CourseStatusView.vue      frontend/src/components/Course*.vue      frontend/src/stores/course.ts
frontend/tests/…                                   (vistas, store y cliente)
compose.yaml · compose.dev.yaml · .env.example · application-test.properties · scripts/tests/environment.test.mjs   (tarea INTAKE y límites)
```

## ART-006 · Wireframe

```
┌ Nuevo curso ────────────────────────┐   ┌ Nuevo curso ────────────────────────┐
│ ¿Qué quieres estudiar?              │   │ Una pregunta rápida                 │
│ ┌─────────────────────────────────┐ │   │ ¿En qué quieres centrarte?          │
│ │ inglés de 2º de la ESO          │ │   │  (•) Gramática y vocabulario        │
│ └─────────────────────────────────┘ │   │  ( ) Conversación                   │
│ 3 a 200 letras                      │   │  ( ) Como tú veas                   │
│              [ Continuar ]          │   │ ¿Qué nivel tienes ahora?            │
└─────────────────────────────────────┘   │  ( ) Empiezo de cero  (•) Sé algo   │
 Paso 2: «¿Tienes información?»  Sí / No  │  ( ) Como tú veas                   │
   Sí → ☑ apuntes.pdf (listo)             │       [ Atrás ]   [ Continuar ]     │
        ☐ resumen.docx (procesando…)      └─────────────────────────────────────┘
        [ Subir otro ]
┌ Resumen ────────────────────────────┐   ┌ Contenido inapropiado ──────────────┐
│ Vamos a montar un curso de          │   │ Contenido inapropiado.              │
│ «inglés de 2º de la ESO»:           │   │ Consulta con tu adulto.             │
│ · Gramática y vocabulario           │   │             [ Volver ]              │
│ · 6 temas, con examen en cada uno   │   └─────────────────────────────────────┘
│ · Con tus 1 documento(s)            │   ┌ Tu curso ───────────────────────────┐
│ Generado por IA.                    │   │ inglés de 2º de la ESO              │
│  [ Atrás ]        [ Aceptar ]       │   │ ◌ En cola: lo estamos preparando…   │
└─────────────────────────────────────┘   └─────────────────────────────────────┘
```

## ART-007 · ADR (propuestos; se numeran a continuación de ADR-36)

### ADR-37: La consulta del asistente vive en el servidor
- Motivo: las preguntas las decide el modelo; si el cliente las devolviera con sus respuestas, podría inventar preguntas, valores o «campos de la ficha». Guardar la consulta con sus preguntas
  validadas permite validar las respuestas contra lo que el servidor generó, hacer idempotente el «Aceptar» (`course.intake_id` único), contar el límite de frecuencia por usuario (consultas de la última hora,
  sin depender de la IP: D1) y conservar los intentos bloqueados para el tutor.
- Alternativas descartadas: asistente sin estado (el cliente reenvía preguntas y respuestas), sesión HTTP (el backend es sin estado).
- Revisitar si: el volumen obliga a purgar consultas antiguas (hay que fijar una retención).

### ADR-38: Una sola llamada al modelo, con salida JSON validada y degradación sin errores
- Motivo: tema, valoración de idoneidad y preguntas salen de **una** llamada (coste mínimo). Los adaptadores no usan la «salida estructurada» de cada proveedor, así que el modelo responde con JSON en texto y
  `IntakeParser` lo valida contra un esquema estricto antes de usarlo. Un fallo de formato nunca llega al usuario: un reintento y, si falla, 0 preguntas con valores por defecto.
- Alternativas descartadas: dos llamadas (una para filtrar y otra para preguntar: más cara y más lenta), confiar en el texto del modelo sin validar.
- Revisitar si: se elige un proveedor con salida estructurada fiable para todos los modelos.

### ADR-39: `ContentGuard` en dos capas, reutilizable (cierra A3)
- Motivo: para un menor, la capa local (términos explícitos, normalizados sin tildes, mayúsculas, símbolos ni espaciado) responde **sin llamar al modelo** y sin coste; la capa del modelo valora
  «apropiado para escolares» dentro de la llamada del asistente. La capa local está en el paquete `safety` para que F05 (lecciones) y F07 (tutor) la usen **antes de enviar texto de un menor a un modelo de pago**,
  que es la condición que dejó ADR-34. La lista de términos es un fichero de recursos, sin recompilar el código.
- Riesgo asumido: falsos positivos (un tema escolar legítimo bloqueado: el mensaje remite al adulto) y falsos negativos con otros idiomas o disfraces; no sustituye a la supervisión del tutor.
- Alternativas descartadas: solo lista (deja pasar temas disfrazados), solo modelo (cuesta una llamada por intento y depende del proveedor), servicio de moderación externo (otro proveedor y otra política de datos).
- Revisitar si: el tutor necesita desbloquear un tema concreto, o hay que cubrir más idiomas.

### ADR-40: El curso nace en F04, con su ficha versionada y su enlace a documentos
- Motivo: «Aceptar» necesita guardar algo y F05 necesita un sitio donde leer el contrato. `course.sheet` es JSONB con `schemaVersion`; `course_document` enlaza con las claves compuestas que ya protegen el material
  (nadie puede usar un documento ajeno). Estados `PENDING_GENERATION | GENERATING | READY | FAILED` definidos desde ya para no migrar el CHECK en F05.
- Alternativas descartadas: no guardar nada hasta F05, columnas separadas para cada campo de la ficha (cada cambio de la ficha sería una migración).
- Revisitar si: F05 necesita consultar por campos de la ficha (entonces se promocionan a columnas).

### ADR-41: Límite de frecuencia por usuario contado en la base de datos (cierra A2 del asistente)
- Motivo: hoy los límites por IP no valen detrás de nginx (D1). La frecuencia (30 consultas por hora) y los cursos pendientes (5) se cuentan por usuario sobre `course_intake` y `course`.
- Revisitar si: los endpoints de F05/F07 necesitan un contador genérico reutilizable (extraerlo entonces).

## Impacto en lo existente
`AiTask` (+`INTAKE`), `AiSettingsValidator` (la tarea nueva es obligatoria), `V2` no cambia: `V4` amplía su CHECK de `ai_usage`; `compose.yaml`, `compose.dev.yaml` y `.env.example` (variables de la tarea y
límites); `application-test.properties` (tarea `INTAKE` con el proveedor falso y su precio); `GlobalExceptionHandler` (los nuevos códigos de error); `AiSchemaTest` y los tests que listan tareas; tests de `HomeView`
(nuevo `listCourses`). `SecurityConfig` no cambia: todo lo no público ya exige sesión.

## Riesgos y puntos abiertos
- **Falsos positivos del filtro:** temas escolares delicados bloqueados (educación sexual, drogas y salud). El mensaje remite al adulto y el intento lo ve el tutor; no hay desbloqueo en v1.
- **Calidad del JSON con modelos baratos:** mitigada con validación y degradación; se medirá con un modelo real en la FASE 7.
- **Retención de consultas:** guardan el texto del tema; hay que fijar cuándo se purgan (backlog).
- **El tutor aún no ve los intentos:** el dato queda guardado y auditado, pero la pantalla llega con F08.
