# US-F04 · Asistente de creación de curso

> Historia refinada (FASE 1 de SDD) tras las decisiones del usuario. Los tests citan estos AC.

### Como alumno (adulto o menor, sin conocimientos técnicos)
### Quiero decir qué quiero estudiar y responder unas pocas preguntas sencillas
### Para obtener un curso a mi medida, sin tener que saber de cursos ni de IA

## Decisiones del usuario (25-10-2026)

- **«Aceptar» guarda el curso como pendiente de generar**: nace la tabla de cursos con su ficha y el estado `PENDING_GENERATION`; F05 lo recogerá y generará.
  Aquí también se enlaza el material de F03 al curso.
- **Filtro de temas para menores**: si un menor pide algo inapropiado, **no se crea** y ve el mensaje «**Contenido inapropiado. Consulta con tu adulto.**»
  (el usuario dio también la variante «no se puede crear este contenido»; el texto exacto se confirma en la revisión de esta fase).
  Mi propuesta de diseño, a confirmar: el intento queda **registrado para el tutor** (que ya ve toda la actividad de su menor) y filtro en **dos capas**:
  lista de términos explícitos en local + valoración del modelo dentro de la misma llamada. Sin desbloqueo por el tutor en v1.
- **Límites por usuario (A2)**: 30 consultas al asistente por hora y 5 cursos pendientes de generar (por variable de entorno).
- La edad del menor **no se guarda** (datos mínimos): el nivel de partida se pregunta dentro del asistente.

## Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | Pantalla «Nuevo curso» con una caja «¿Qué quieres estudiar?» (3 a 200 caracteres tras recortar espacios). Sin jerga técnica; usable con el dedo en móvil. Se llega desde el inicio. |
| AC-2 | Paso «¿Tienes información que aportar?» Sí/No. Con «Sí» se eligen entre 1 y 5 documentos **propios y listos** de «Mi material» (F03) o se sube uno nuevo. Con «No» se sigue sin material. |
| AC-3 | `POST /api/courses/intake` (sesión + CSRF) guarda el tema y los documentos elegidos, pasa el filtro, llama al modelo (tarea nueva `INTAKE`, con su propia plantilla y tope de salida) y devuelve `{intakeId, questions}`: **de 0 a 3 preguntas relevantes** para el tema, cada una con 2 a 5 opciones, **una marcada por defecto** y siempre la opción «Como tú veas». |
| AC-4 | La salida del modelo se **valida contra un esquema estricto** (nº de preguntas, longitudes, ids, una opción por defecto). Si no es válida se reintenta una vez; si sigue mal, el asistente continúa **sin preguntas** y con valores por defecto, nunca con un error técnico ni con texto del modelo sin validar. |
| AC-5 | `POST /api/courses` `{intakeId, answers}` valida cada respuesta contra las preguntas **guardadas por el servidor** (no las que diga el cliente), aplica los valores por defecto a lo no contestado o «Como tú veas» y crea el curso `PENDING_GENERATION` con su **ficha**: `schemaVersion`, tema, enfoque, nivel de partida, objetivo, número de temas (3 a 12, 6 por defecto), examen por tema (sí/no), y ids de los documentos. Responde 201. |
| AC-6 | La ficha es el **contrato con F05**: JSON versionado, documentado en la especificación, sin datos personales ni texto de los documentos. Un curso solo se crea una vez por consulta: repetir «Aceptar» o recargar no crea otro (idempotente). |
| AC-7 | Resumen en lenguaje llano antes de aceptar («Vamos a montar un curso de… sobre…, con N temas y examen en cada tema») y botón **Aceptar**. Tras aceptar se lleva a una pantalla del curso con su estado («en cola, tu curso se está preparando») y el aviso «generado por IA». |
| AC-8 | **Menores**: antes de llamar al modelo, el tema pasa por una **lista local de términos explícitos** (sin tildes ni mayúsculas, con variantes de espaciado); además, la llamada al modelo devuelve una valoración «apropiado para escolares». Si cualquiera la rechaza, no se crea nada y la API responde 422 `TOPIC_NOT_ALLOWED` con el mensaje «Contenido inapropiado. Consulta con tu adulto.». Los temas escolares legítimos que tratan asuntos delicados (guerras, reproducción, historia) **deben pasar**. |
| AC-9 | Un tema bloqueado se **registra** (estado `BLOCKED` de la consulta, con su texto y categoría) para que lo vea el tutor en el panel de F08, y en la auditoría (`COURSE_TOPIC_BLOCKED`, sin el texto). El menor no ve la categoría ni el motivo. A los adultos no se les aplica este filtro. |
| AC-10 | **Límite de frecuencia (A2)**: más de 30 consultas por hora por usuario → 429 `RATE_LIMITED` con mensaje claro; más de 5 cursos pendientes → 409 `COURSE_LIMIT_REACHED`. Se cuenta por usuario, no por IP. |
| AC-11 | El texto del usuario va al modelo **solo como dato** (`AiRequest.data`), nunca en la plantilla. F04 **no envía al modelo texto de ningún documento**, solo el tema. Un tema como «ignora las instrucciones…» no cambia la plantilla ni la salida validada. |
| AC-12 | Toda llamada queda en `ai_usage` (tarea `INTAKE`, usuario, sin contenido) y respeta el tope por usuario y el global: si se alcanza el tope personal, mensaje claro («has llegado a tu límite de uso este mes»). Si la IA falla o tarda, mensaje amable y **el tema no se pierde**: se puede reintentar. |
| AC-13 | Validación en backend de todo: longitud y caracteres del tema (sin control, sin solo símbolos o emojis), nº de documentos (≤ 5, sin repetir), que sean del usuario y estén `READY`, respuestas que coincidan con sus preguntas, consulta no caducada (1 hora). Un id ajeno o inexistente responde igual (404). |
| AC-14 | Todos los endpoints exigen sesión y CSRF y filtran por propietario. `GET /api/courses` lista los cursos del usuario; `GET /api/courses/{id}` da uno suyo. Un curso o consulta ajenos, 404. |
| AC-15 | Auditoría de solo añadir: `COURSE_INTAKE_STARTED`, `COURSE_CREATED` y `COURSE_TOPIC_BLOCKED`, con ids y categoría, **sin el tema ni nombres de documentos**; los logs tampoco los llevan. |
| AC-16 | Interfaz del asistente en pasos (tema → información → preguntas y resumen), con «Atrás», estados de carga con texto («pensando tu curso…»), errores amables, accesible (foco visible, etiquetas) y **sin jerga**. «Tus cursos» aparece en el inicio con su estado. |

## Contrato de la ficha (v1, para F05)

```json
{ "schemaVersion": 1, "topic": "Inglés de 2º de la ESO", "focus": "Gramática y vocabulario", "startLevel": "Sé algo",
  "goal": "Aprobar el curso", "topicsCount": 6, "examPerTopic": true, "documentIds": ["…"] }
```
`focus`, `startLevel` y `goal` salen de las respuestas del asistente (o del valor por defecto elegido por la IA); son texto corto (≤ 120 caracteres), nunca instrucciones.

## Casos borde
- Tema vacío, de 2 o de 300 caracteres, solo símbolos o emojis, con saltos de línea o caracteres de control, con etiquetas HTML.
- Tema muy genérico («historia») o en otro idioma: el asistente pregunta lo que haga falta o sigue con valores por defecto.
- Tema con intento de inyección («ignora tus instrucciones y…», «</datos>…»).
- Un menor pide un tema explícito con tildes, mayúsculas, espaciado o sustituciones de letras; un menor pide un tema escolar delicado (la Segunda Guerra Mundial, la reproducción humana).
- Un adulto pide el mismo tema que se bloquea a un menor: se crea.
- El modelo devuelve JSON roto, más de 3 preguntas, opciones sin valor por defecto o texto fuera del formato; el modelo no responde o tarda.
- Se alcanza el tope de gasto personal o el global, el límite de 30 consultas/hora o el de 5 cursos pendientes.
- Doble clic en «Aceptar», recarga de la página, consulta caducada o ya usada, respuesta a una pregunta que no existe.
- Un documento elegido se borra o aún no está `READY` entre el paso 2 y «Aceptar»; documentos repetidos o ajenos.
- Un menor dado de baja; un tutor intentando ver una consulta de su menor (no es de F04: llega en F08).

## Fuera de alcance (v1)
Generar el curso (F05), editar la ficha tras aceptar, test diagnóstico de nivel, panel del tutor con la actividad y los intentos bloqueados (F08), desbloqueo de un
tema por parte del tutor, filtrado de las respuestas del tutor IA (F07), guardar la edad del menor, idiomas distintos del español, plantillas de curso y compartir (F09).

## Definición de hecho
- Los 16 AC tienen test y pasan en contenedor, con cobertura ≥ 80 %. Ningún test llama a una API de pago (proveedor falso).
- Verificado con el stack levantado y el proveedor falso, y una vez con el modelo real si el usuario lo decide (coste de céntimos).
- Filtro de menores comprobado con una batería de temas bloqueados y de temas escolares legítimos que deben pasar.
- `README`, `ARCHITECTURE.md` y `CLAUDE.md` actualizados. `security-checklist` y `llm-integration-review` pasadas e informadas.
- Se cierran A2 (límite de frecuencia del asistente) y A3 (punto de enganche del filtro de menores, que queda reutilizable para F05 y F07).

## Cambios respecto a lo especificado (FASE 8, 25-10-2026)

- **AC-3:** la respuesta lleva por pregunta solo `id`, `text` y `options[{id, label, isDefault}]`: el campo de la ficha y los valores quedan en el servidor (`course_intake.questions`). La valoración del modelo no se expone.
- **AC-4:** el esquema exige de 2 a 4 opciones **del modelo** con exactamente una por defecto (el servidor añade «Como tú veas»), campos distintos y conocidos, `topicsCount` entre 3 y 12 y `examPerTopic` `true`/`false`. Un JSON dentro de un bloque de código se acepta.
- **AC-5:** si la consulta ya está consumida, `POST /api/courses` devuelve **200** con el mismo curso e ignora las respuestas; con la consulta caducada, **410** `INTAKE_EXPIRED`; con documentos borrados o no listos, **404**.
- **AC-8:** si el JSON del modelo es inservible para un **menor**, el tema se acepta (ya pasó la lista local) y F05 debe volver a comprobarlo; la valoración del modelo es la segunda capa solo cuando existe.
- **AC-10:** «pendientes» son los cursos `PENDING_GENERATION` y `GENERATING`; una consulta rechazada por límite no deja fila ni llama al modelo. El orden de comprobación es: documentos, pendientes, frecuencia y filtro.
- **AC-12:** un fallo de la IA no guarda ninguna consulta (el tema no se pierde en el cliente).
- **AC-16:** el resumen muestra cada pregunta con la respuesta elegida; «Atrás» conserva el tema, los documentos y las respuestas.
- **Añadido:** la tarea `INTAKE` amplía el CHECK de `ai_usage` (V4) y el proveedor falso gana `respondWithSequence` para probar reintentos.
