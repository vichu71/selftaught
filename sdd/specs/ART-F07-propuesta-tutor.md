# F07 · Tutor IA por lección — FASE 2: Proposal Artifacts

> Propuesta para confirmar antes de la FASE 3. Cita los AC de `US-F07-tutor-ia.md`. Los ADR (55 a 58) se pasan a `ARCHITECTURE.md` en la FASE 5.

## ART-001 · Stack

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Backend | Paquete nuevo **`tutor`** (hilo, contexto, límites, filtro y llamada al modelo) | Es otra responsabilidad que `study` (progreso e intentos) y que `generation`; reutiliza sus piezas públicas (`SourcePack`, `MaterialSearch`) sin tocarlas |
| IA | Tarea **`TUTOR` existente** (ya tiene modelo, tope de salida y precio por configuración) + plantilla nueva `tutor` | No añade tarea ni variable de entorno obligatoria para quien ya configuró `TUTOR` (en desarrollo usa el proveedor falso) |
| Base de datos | PostgreSQL: migración `V7__tutor.sql` | Hilo por lección con estado de cada mensaje |
| Frontend | Vue 3 + Pinia, sin librerías nuevas | `MarkdownContent` ya pinta las respuestas sin HTML |

**Dependencias nuevas:** ninguna. **Configuración nueva** (`app.tutor.*`, valores de ingeniería sin variable de entorno): mensajes por hora (30) y por día (150), intercambios de historial que se envían (6) y su tope de caracteres (6 000),
longitud máxima de pregunta (1 000) y de respuesta (4 000), mensajes conservados por lección (200) y la ventana en que una respuesta pendiente bloquea otra (2 minutos).

## ART-002 · Modelo de datos (`V7__tutor.sql`)

```
tutor_message
  id uuid PK, owner_id, course_id, piece_id (una LECCIÓN),
  role (USER | TUTOR),
  status (PENDING | OK | FAILED | BLOCKED | FILTERED),
  content varchar(6000),                      -- pregunta (≤ 1 000) o respuesta (≤ 4 000) o texto fijo de un bloqueo
  sources jsonb nulo                          -- solo respuestas con material: [{n, page}] (sin documento ni nombre)
  created_at timestamptz
  FK compuestas (piece_id, owner_id) → course_piece y (course_id, owner_id) → course, ON DELETE CASCADE
  índices (owner_id, piece_id, created_at) y (owner_id, created_at)
```
- **El hilo visible** son los mensajes `OK`, `BLOCKED` y `FILTERED`. Un mensaje del alumno nace `PENDING` (reserva el límite y la «respuesta a la vez»); si el modelo responde pasa a `OK` junto con la respuesta, en una transacción; si el modelo falla
  pasa a `FAILED` y **nunca** se ve (no hay mensajes huérfanos, AC-11). Un `PENDING` abandonado (el backend se cayó) deja de bloquear a los 2 minutos y no se ve.
- **Límites (AC-10):** se cuentan los mensajes del alumno (`role = USER`, en cualquier estado) de la última hora y del último día, de modo que los bloqueados y los fallidos también cuentan. **Una a la vez:** existe un `PENDING` de ese usuario de hace menos de 2 minutos;
  la comprobación y la reserva se hacen en una transacción con un cerrojo de la BD por usuario (`pg_advisory_xact_lock`), así que vale con varias instancias del backend (ADR-55).
- **Retención:** tras cada intercambio se borran los mensajes más antiguos de esa lección y ese usuario por encima de 200 (también los `FAILED`).
- **El adulto vacía su hilo** (`DELETE`): se borran todos sus mensajes de esa lección, incluidos los `PENDING`; la respuesta que llegue después se guarda en un hilo nuevo sin romperse (la actualización del `PENDING` que ya no existe no hace nada y la respuesta se inserta igualmente).
- El rol `studylab_app` recibe `SELECT/INSERT/UPDATE/DELETE` sobre la tabla y nada que cambie el esquema. `ai_usage` ya admite la tarea `TUTOR`; no se toca.

## ART-003 · Endpoints

Todos exigen sesión (por defecto en `SecurityConfig`) y CSRF en los que modifican; un curso, lección o mensaje ajenos o inexistentes responden **404 idéntico**. El adulto tutor no lee el hilo de su menor por esta API (F08).

```
GET    /api/courses/{c}/pieces/{p}/tutor    → { canClear, messages:[{id, role, content, status, sources:[{n,page}], createdAt}] }  más antiguo primero, solo OK/BLOCKED/FILTERED             AC-9
POST   /api/courses/{c}/pieces/{p}/tutor    {question} → 201 { user:{…}, tutor:{…} }                                                                                                  AC-1..8, 10..13, 16
        400 pregunta no válida · 404 lección ajena o que no es una lección · 409 la lección aún no está generada
        429 TUTOR_RATE_LIMITED (hora o día) · 429 TUTOR_BUSY (ya hay una respuesta en marcha) · 429/502/503… los errores de la capa de IA (AI_USER_LIMIT, AI_BUDGET_BLOCKED, AI_PROVIDER_UNAVAILABLE…) tal cual
        502 TUTOR_FAILED (respuesta inservible tras un reintento)
DELETE /api/courses/{c}/pieces/{p}/tutor    → 204 (adulto: vacía su hilo de esa lección) · 403 THREAD_PROTECTED (menor: su tutor lo ve)                                           AC-9
```
Un menor cuya pregunta bloquea el filtro recibe **201** con su mensaje (`BLOCKED`) y la respuesta fija «Contenido inapropiado. Consulta con tu adulto.» (`BLOCKED`): así queda en el hilo para su tutor (F08) sin llamar al modelo.

**Flujo de `POST` (un mensaje):**
1. Valida los ids (404), que sea una lección (404) y esté `DONE` (409), y la pregunta (400: 1–1 000 caracteres, sin caracteres de control salvo saltos de línea).
2. **Transacción con cerrojo por usuario:** límites hora y día (429), respuesta en marcha (429) y reserva el mensaje del alumno como `PENDING`.
3. **Menor:** `ContentGuard.localCheck(pregunta)`; si coincide → el mensaje pasa a `BLOCKED`, se guarda la respuesta fija `BLOCKED`, se audita `TUTOR_QUESTION_BLOCKED` (ids, sin contenido) y se responde 201 sin llamar al modelo.
4. **Contexto como datos:** audiencia; resumen del curso (tema, enfoque, nivel, objetivo y títulos); capítulo y objetivo; la lección (recortada a 12 000 caracteres); los **fallos** del capítulo (enunciado y «fallado» o «a medias», nunca la respuesta del alumno);
   el historial (6 intercambios, 6 000 caracteres) y, si el curso tiene documentos, hasta 4 fragmentos numerados de `MaterialSearch` (solo de los documentos del curso; para un menor, sin los que rechaza `ContentGuard`).
5. `AiGateway.complete(TUTOR)` con `.course(id)` y la salida acotada; respuesta vacía, cortada sin texto o con citas inventadas o HTML crudo → **un reintento**; si sigue mal, `FAILED` y 502.
6. **Menor:** `ContentGuard` sobre la respuesta; si coincide → se guarda un texto fijo `FILTERED` en su lugar (y se audita `TUTOR_ANSWER_FILTERED`, sin contenido).
7. Guarda la respuesta `OK` (o `FILTERED`) con sus fuentes y pasa el mensaje del alumno a `OK`; poda el hilo; responde 201 con los dos mensajes. Un `AiException` (tope, proveedor) marca el mensaje del alumno `FAILED` y sube tal cual.

## ART-004 · Estructura de componentes

```
views/PieceView.vue                       AC-1   en una lección ya generada, debajo del texto:
 └─ components/TutorChat.vue              AC-1, 9, 10, 11, 14, 15   panel de conversación: hilo, caja de pregunta con contador, «Enviar», indicador «El tutor está pensando…», errores amables con el texto conservado,
                                                  «Vaciar la conversación» (solo adultos), aviso al menor de que su tutor la ve, aviso «generado por IA»
     ├─ components/MarkdownContent.vue           respuestas del tutor con el visor sin HTML (ya existe)
     └─ lista de «Fuente n (página)» bajo cada respuesta con material
api/client.ts                             getTutorThread, askTutor, clearTutor
```
El estado vive en el propio componente (una conversación por lección abierta); al cambiar de lección el componente se vuelve a crear. Todo texto del alumno y del tutor se pinta con `{{ }}` o con el visor; los tests usan `data-testid`.

## ART-005 · Estructura de carpetas (solo lo nuevo)

```
backend/src/main/java/es/vicai/studylab/tutor/       TutorController · TutorService · TutorContext · TutorRepository · TutorAnswerValidator · TutorProperties · TutorException
backend/src/main/resources/db/migration/V7__tutor.sql
backend/src/main/resources/prompts/tutor.txt
backend/src/test/java/es/vicai/studylab/tutor/       TutorTestBase · TutorChatTest · TutorContextTest · TutorMaterialTest · TutorMinorTest · TutorLimitsTest · TutorBudgetTest ·
                                                     TutorThreadTest · TutorAnswerValidatorTest · TutorAccessTest · TutorSchemaTest · TutorArchitectureTest
frontend/src/components/TutorChat.vue
frontend/tests/…                                     (chat, estados de error, menores, cliente de la API)
```

## ART-006 · Wireframe

```
┌ Lección: ¿Qué es Java? ──────────────────┐
│ …texto de la lección…                     │
│ [ ✓ Marcar como hecha ]  ← Anterior  →    │
├───────────────────────────────────────────┤
│ Pregúntale al tutor                       │
│ ┌ Tú ───────────────────────────────┐     │
│ │ ¿Qué diferencia hay entre JDK y JRE?│    │
│ └───────────────────────────────────┘     │
│ ┌ Tutor ────────────────────────────┐     │
│ │ El JRE ejecuta programas; el JDK…  │    │
│ │ Fuente 1 (página 3)                │    │
│ └───────────────────────────────────┘     │
│ ◌ El tutor está pensando…                 │
│ [ Escribe tu duda…              ] 12/1000 │
│ [ Enviar ]        [ Vaciar conversación ] │
│ Generado por IA: puede equivocarse.       │
│ (menores) Tu adulto tutor puede ver       │
│ tus conversaciones.                       │
└───────────────────────────────────────────┘
```

## ART-007 · ADR (propuestos; se numeran a continuación de ADR-54)

### ADR-55: El hilo del tutor vive en la BD y los mensajes provisionales sostienen los límites y la «respuesta a la vez»
- Motivo: la pregunta del alumno se guarda como `PENDING` antes de llamar al modelo: reserva el límite por hora y por día (contando también lo bloqueado y lo fallido, así un bucle de fallos no gasta sin freno) y impide dos respuestas a la vez por usuario con un cerrojo de la BD
  en la misma transacción, sin estado en memoria (vale con varias instancias; resuelve para el tutor lo que F6-2 deja pendiente en la corrección con IA). Si el modelo falla, el mensaje pasa a `FAILED` y no se ve: no quedan preguntas sin respuesta en el hilo.
- Alternativas descartadas: contar solo lo guardado (los fallos no limitarían), un conjunto en memoria (solo una instancia), guardar el intercambio al terminar (no hay forma de reservar el límite ni de saber que hay uno en marcha).
- Revisitar si: el volumen de mensajes obliga a purgar `FAILED` y `PENDING` antiguos por separado.

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

## Impacto en lo existente
`generation.SourcePack` y `MaterialSearch` (se usan desde `tutor`; no cambian), `study` (el tutor lee `exercise_attempt` del capítulo; no cambia), `PieceView.vue` (monta el chat en las lecciones), `api/client.ts`, `GlobalExceptionHandler` (sin cambios: los errores de IA ya tienen su estado), `ai_usage` (la tarea `TUTOR` ya está
permitida). `SecurityConfig`, nginx y Compose no cambian. La plantilla `tutor` y el modelo de la tarea `TUTOR` se configuran como las demás tareas.

## Riesgos y puntos abiertos
- **Calidad pedagógica con un modelo real sin medir (F7-1):** que guíe sin resolver, que redirija bien y que el tono sea el adecuado. Solo lo puede medir el usuario con sus claves; las instrucciones fijas son la única garantía hoy.
- **Coste recurrente:** es la llamada más frecuente; con 150 mensajes al día y un modelo barato son céntimos, y el tope personal (menor 2 USD, adulto 10 USD) lo corta igual que al resto.
- **Un alumno que pegue un enunciado de examen** y pida la solución: el tutor no tiene las soluciones ni los exámenes, pero podría razonarlas; las instrucciones piden pistas, no la solución.
- **El filtro local de menores** solo cubre español y es corto (F4-3); la instrucción de tono escolar es la otra capa.
- **El tutor adulto** (F08) leerá estas tablas con su propio permiso; hoy ninguna API lo permite.
