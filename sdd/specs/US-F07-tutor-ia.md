## US-F07 · Tutor IA por lección

### Como alumno (adulto o menor) y, en su panel (F08), como adulto tutor de un menor
### Quiero preguntar mis dudas a un tutor de IA dentro de cada lección, que conozca la lección, mi curso y lo que me cuesta
### Para que me explique y me guíe sin hacerme los deberes ni los exámenes, con un tono adecuado a mi edad y sin salirme del curso

### Decisiones del usuario (25-10-2026)
- La respuesta llega **completa, con un indicador de espera** (no palabra a palabra).
- La conversación **se guarda por lección**; **el adulto puede borrarla y el menor no** (su tutor la ve entera, F08).
- Límite propio del tutor: **30 mensajes por hora y 150 al día** por usuario, además del tope mensual de gasto.
- El tutor usa un **resumen corto de lo fallado en los ejercicios del capítulo** (enunciado y resultado), **no** la respuesta literal del alumno.

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Chat en cada lección:** debajo de la lección generada hay un panel de conversación con una caja de texto, «Enviar» y el historial de esa lección. Una lección que aún no está generada no tiene chat. |
| AC-2 | **Contexto del tutor:** conoce el texto de la lección, su capítulo y objetivo, y un resumen del curso (tema, enfoque, nivel, objetivo y títulos del temario). Todo viaja al modelo como **datos**. |
| AC-3 | **Material propio:** si el curso tiene documentos, para cada pregunta se recuperan con `MaterialSearch` fragmentos **solo de esos documentos y del propietario**, numerados y sin nombre de documento; el tutor cita con `[n]` y solo se aceptan citas que existan. La respuesta lista «Fuente n (página)» y sin material no hay fuentes. |
| AC-4 | **Refuerzo:** si el alumno falló o resolvió a medias ejercicios de ese capítulo (F06), el tutor recibe un resumen corto (enunciado y resultado: fallado o a medias), **nunca la respuesta literal del alumno**. Sin fallos, no se envía nada. |
| AC-5 | **Coste acotado:** tarea `TUTOR` de la capa de IA (modelo por configuración, medio o barato); solo se envían los últimos **6 intercambios** y un máximo de caracteres de historial; la respuesta tiene su propio tope de salida y de longitud (4 000 caracteres). **No** usa `GenerationLock`. |
| AC-6 | **Comportamiento pedagógico (instrucciones fijas):** tono adecuado a la audiencia («escolar» o «general»), claro y sin jerga; explica, da pistas y ejemplos y **no resuelve un examen ni un ejercicio por el alumno**; si la pregunta no tiene que ver con el curso, redirige con amabilidad; responde en español. |
| AC-7 | **Datos, no instrucciones:** la pregunta, el historial, la lección, los fragmentos y los fallos van como datos etiquetados y escapados; una pregunta o un material con «ignora tus instrucciones» o con `</datos>` no cambia la plantilla ni su comportamiento. |
| AC-8 | **Menores:** la pregunta pasa por `ContentGuard` antes de enviarse (si coincide, no se envía, el menor ve «Contenido inapropiado. Consulta con tu adulto.» y el intento se guarda marcado para su tutor); los fragmentos se filtran antes de enviarse; y la respuesta del modelo se filtra antes de mostrarse y guardarse (si coincide, se sustituye por un texto fijo y se marca). La auditoría lleva ids, nunca el contenido. |
| AC-9 | **Conversación guardada por lección:** `GET` devuelve el hilo de esa lección; un adulto puede **vaciar el suyo** (`DELETE`); un menor no puede (403, su tutor lo ve). Se conservan los últimos 400 mensajes por lección y usuario (nunca menos del doble del límite diario, o la poda dejaría ese límite sin efecto). |
| AC-10 | **Límites por usuario en la BD:** 30 mensajes por hora y 150 al día (cuentan todos los envíos, también los bloqueados o fallidos) y **una respuesta a la vez por usuario**; con mensajes claros y sin perder lo escrito. |
| AC-11 | **Tope de gasto y fallos del proveedor sin error técnico:** si se alcanza el tope personal o global o el proveedor falla, el alumno ve un mensaje claro en el propio chat, conserva su pregunta en la caja y no queda ningún mensaje huérfano en el hilo. |
| AC-12 | **Registro:** cada respuesta queda en `ai_usage` como tarea `TUTOR` con usuario y curso (cuenta en el coste del curso y en «Resultados»), sin contenido. |
| AC-13 | **Validación y seguridad:** pregunta de 1 a 1 000 caracteres y sin caracteres de control; sesión y CSRF; filtra por propietario; un curso, lección o mensaje ajenos o inexistentes responden igual (404). El adulto tutor **no** lee el hilo de su menor por esta API (su panel es F08). |
| AC-14 | **Privacidad:** ni logs ni auditoría llevan preguntas, respuestas, lecciones ni fragmentos. El menor ve un aviso de que su adulto tutor puede ver sus conversaciones. |
| AC-15 | **Interfaz:** el chat es usable en móvil, con indicador de espera, estados de error amables, las respuestas con el visor de Markdown sin HTML (nunca `v-html`), las fuentes citadas y el aviso «generado por IA». |
| AC-16 | **Respuesta válida o error amable:** una respuesta vacía, cortada o inservible se reintenta una vez; si sigue mal, el alumno ve «No he podido responder, inténtalo de nuevo» y no se guarda nada. |

### Casos borde
- Pregunta en blanco, de más de 1 000 caracteres, con caracteres de control o con HTML: 400 o texto plano, nunca interpretado.
- Pregunta que pide **la solución de un ejercicio o de un examen**, que intenta cambiar las instrucciones («ignora lo anterior») o que pega `</datos>`: va como dato y el tutor sigue guiando sin resolver.
- **Doble clic en «Enviar»** o dos pestañas a la vez: una sola respuesta en marcha por usuario; la segunda recibe «espera a que termine» sin perder el texto.
- El alumno cierra la pantalla **mientras el tutor responde**: la respuesta se guarda y la ve al volver.
- La lección **se regenera o falla** más tarde: el hilo anterior se conserva.
- Un **documento borrado** a mitad: el tutor sigue sin fuentes, sin error.
- Un menor cuya pregunta o respuesta del modelo salta el filtro: mensaje acordado o texto fijo, y queda marcado para el tutor.
- Tope alcanzado a mitad de una conversación: el hilo anterior se ve; la nueva pregunta se rechaza con un mensaje claro.
- Historial largo: solo viajan los últimos 6 intercambios; el resto se conserva pero no se envía.
- El adulto **vacía el hilo** mientras otra pestaña tiene una respuesta en marcha: la respuesta llega a un hilo nuevo, sin romperse.

### Fuera de alcance (v1)
- Voz, imágenes y archivos adjuntos en el chat; memoria a largo plazo entre cursos; respuesta palabra a palabra (streaming).
- El **panel del adulto tutor** (F08): F07 guarda las conversaciones con su propietario y las marcas de bloqueo para que lo lea.
- Chat dentro de ejercicios y exámenes (solo lecciones), valorar o editar mensajes sueltos y borrar mensajes de uno en uno.
- Medir con un modelo real la calidad pedagógica del tutor (backlog; solo el usuario con sus claves).

### Definición de hecho
- Todos los AC con tests (backend, frontend y entorno) y verificados con el stack levantado con un modelo simulado, sin ninguna llamada a una API de pago.
- Informe de testing, `security-checklist` y `llm-integration-review` (toca endpoints, entrada de usuario, material y llamadas a IA), ADR nuevos en `ARCHITECTURE.md` y reglas de F07 en `CLAUDE.md`.
- Ni preguntas, ni respuestas, ni lecciones, ni fragmentos en logs ni auditoría; el filtro de menores aplicado a la entrada, a los fragmentos y a la salida.
