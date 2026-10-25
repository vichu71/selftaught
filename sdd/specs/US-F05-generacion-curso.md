# US-F05 · Generación del curso

> Historia refinada (FASE 1 de SDD) tras las decisiones del usuario. Los tests citan estos AC.

### Como alumno (adulto o menor) que ha aceptado la ficha de un curso
### Quiero que la IA monte el curso por fases —temario, lecciones, ejercicios y examen— y poder leerlo según se va generando
### Para empezar a estudiar sin esperar a que termine todo, sin que se gaste dinero de más y sin perder lo ya generado

## Decisiones del usuario (25-10-2026)

- **Progreso por consulta periódica** (cada 3 s mientras se genera), como el material de F03. No hay SSE ni WebSocket en v1.
- **Visor con formato (Markdown) en F05**: adelanta la parte de lectura de F06. Se renderiza **sin HTML**: a elementos de la interfaz a partir de los símbolos del Markdown, con una
  lista de elementos permitidos. Hay que decidir en la FASE 2 si se usa una librería de lectura de Markdown o un lector propio mínimo.
- **Pausa y botón «Reanudar»**: si se alcanza el tope de gasto o el proveedor falla, el curso queda en pausa con el motivo claro, sin gastar más; el usuario lo reanuda. Nunca se regenera lo ya hecho.
- Arrastrado de F03 y F04: **el contenido de un menor pasa por `ContentGuard` antes de enviarse a un modelo de pago** (fragmentos de su material) **y antes de guardarse**, y el tema de un menor se
  vuelve a valorar con el modelo en el temario (cierra el riesgo F4-5).
- Se usan los modelos por tarea ya configurables (`OUTLINE`, `LESSON`, `ASSESSMENT`); el benchmark apunta a un modelo barato para ficha y lecciones y a uno mejor para ejercicios y exámenes.

## Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | Un curso `PENDING_GENERATION` (de F04) lo recoge un **proceso en segundo plano**, no la petición HTTP. Sobrevive a reinicios: al arrancar, un curso `GENERATING` vuelve a la cola y sigue por la primera pieza no terminada. |
| AC-2 | **Una sola generación concurrente por usuario** (`GenerationLock`, renovado entre llamada y llamada de IA; aborta si lo pierde). Un segundo curso del mismo usuario espera en cola; los de otros usuarios no se bloquean entre sí. |
| AC-3 | **El temario sale primero**: una llamada `OUTLINE` con la ficha devuelve un JSON versionado validado (tantos temas como pide la ficha, de 2 a 4 capítulos por tema, títulos y objetivos acotados). Se guarda, el curso pasa a `GENERATING` y el temario es visible en segundos. |
| AC-4 | Después se genera **en orden**, por capítulo: **lección** (Markdown) y **ejercicios** (JSON con solución comentada), y, si la ficha lo pide, un **examen por tema** (JSON, tipo test con respuesta correcta y explicación). Cada pieza tiene su estado (`PENDING`, `DONE`, `FAILED`) y **una pieza `DONE` nunca se regenera**. |
| AC-5 | **Cada salida del modelo se valida antes de guardarse**: la lección como texto acotado y sin HTML crudo; ejercicios y examen contra un esquema estricto (nº de elementos, longitudes, una respuesta correcta). Si no es válida se reintenta **una vez**; si vuelve a fallar, esa pieza queda `FAILED` con un motivo genérico y la generación **sigue con la siguiente**. |
| AC-6 | **Con material propio**: para cada capítulo se recuperan fragmentos con `MaterialSearch` **solo de los documentos del curso y del propietario**, se envían como **datos** y la lección **cita la fuente** con marcadores `[n]` válidos; lo que añade el modelo por su cuenta se marca como «Complemento». Los marcadores que no correspondan a ningún fragmento se rechazan. Sin material, la lección no lleva citas. |
| AC-7 | **Menores**: antes de enviar un fragmento de material al modelo se pasa por `ContentGuard` y se descartan los que coinciden; el contenido generado se pasa por `ContentGuard` antes de guardarlo (si coincide, un reintento con instrucción reforzada; si persiste, la pieza queda `FAILED` y se audita); y el temario incluye la **valoración de idoneidad**: si el modelo dice que el tema no es apto para escolares, el curso pasa a `FAILED` sin generar nada y el menor ve «Contenido inapropiado. Consulta con tu adulto.». |
| AC-8 | **Gasto**: cada llamada se registra con el usuario y **el curso** (`ai_usage.course_id`), y el coste acumulado del curso se puede consultar. Si se alcanza el tope personal o el global, el curso pasa a `PAUSED` con motivo `BUDGET`; si el proveedor falla tras los reintentos del gateway, a `PAUSED` con motivo `PROVIDER`. Mientras está en pausa no se hace ninguna llamada. |
| AC-9 | `POST /api/courses/{id}/resume` (sesión + CSRF, solo el propietario): vuelve a poner en cola un curso `PAUSED` y la generación **continúa por lo que falta** (piezas no `DONE`, incluidas las `FAILED`). Rechaza si no está en pausa (409) o si se pulsó hace menos de 60 s (429). |
| AC-10 | **Progreso consultable**: `GET /api/courses/{id}` incluye el temario con el estado de cada pieza, el progreso (piezas terminadas de las totales), el motivo de la pausa y el coste acumulado; un curso ajeno o inexistente responde 404. |
| AC-11 | **Contenido legible**: endpoints de solo lectura para una lección (Markdown y fuentes citadas), los ejercicios de un capítulo y el examen de un tema; solo del propietario. Todo contenido lleva el aviso «generado por IA». |
| AC-12 | **Exportación**: `GET /api/courses/{id}/export` devuelve el curso como JSON versionado (`schemaVersion`) que **no incluye el material original, los nombres de los documentos, ni datos del usuario** (las fuentes se citan como «Fuente n» con su página). |
| AC-13 | **Visor con formato saneado**: el Markdown se pinta como elementos de la interfaz construidos por la propia aplicación; se permiten títulos, párrafos, listas, énfasis, código, citas, tablas y enlaces `http(s)` con `rel` seguro; el HTML crudo, las imágenes, los enlaces `javascript:`/`data:` y las fórmulas se muestran como **texto** o se descartan. Nunca `v-html`. |
| AC-14 | **Interfaz**: la pantalla del curso muestra el temario con el estado de cada lección (en cola, generándose, lista, con error), el progreso, el aviso de pausa con su motivo y el botón **Reanudar**, y abre la lección al pulsarla; se actualiza sola cada 3 s mientras se genera y no consulta más cuando termina. Sin jerga. |
| AC-15 | **Seguridad**: todos los endpoints exigen sesión y CSRF (los que modifican) y filtran por propietario; la auditoría registra `COURSE_GENERATION_STARTED`, `…_PAUSED`, `…_RESUMED`, `…_COMPLETED`, `…_FAILED` y `COURSE_CONTENT_BLOCKED` con ids y motivo, **sin temas, títulos ni contenido**; los logs tampoco los llevan. |
| AC-16 | **Acotado**: máximo de piezas por curso (para que un temario descontrolado no dispare las llamadas), tope de caracteres por lección y de elementos por ejercicio o examen, y ninguna petición HTTP espera a la IA. |

## Casos borde
- El temario llega con un número de temas distinto al de la ficha, con títulos repetidos, con HTML, vacío o muy largo; el modelo devuelve la lección en otro idioma, con HTML, con LaTeX o sin contenido.
- Un ejercicio sin solución, un examen con dos respuestas correctas o con una opción repetida; JSON roto o dentro de un bloque de código.
- Marcadores de cita inexistentes (`[7]` con 3 fragmentos); material sin fragmentos relevantes; un documento que el usuario borra a mitad de la generación; un documento aún no listo.
- Un menor cuyo contenido generado o cuyos fragmentos coinciden con la lista local; un tema que el modelo valora no apto en el temario; un menor dado de baja a mitad.
- Se alcanza el tope a mitad de una lección; el proveedor cae; el cerrojo caduca durante una llamada larga; el backend se reinicia a mitad; dos cursos del mismo usuario en cola; dos clics en «Reanudar».
- Un curso ya completo al que se pide reanudar; reanudar un curso ajeno; leer una lección aún no generada o de otro curso.
- Markdown hostil en el visor: `<script>`, `<img onerror>`, `[clic](javascript:alert(1))`, imágenes remotas, tablas enormes, anidamiento extremo.

## Fuera de alcance (v1)
Búsqueda web durante la generación, regenerar lecciones concretas o el temario, edición del temario y compartir (F09), progreso del alumno, intentos y corrección de ejercicios (F06), tutor IA (F07), panel del
tutor con la actividad (F08), revisión humana del contenido, SSE o WebSocket, fórmulas con formato (KaTeX), imágenes y vídeo, y borrar un curso.

## Definición de hecho
- Los 16 AC tienen test y pasan en contenedor, con cobertura ≥ 80 %. Ningún test llama a una API de pago (proveedor falso).
- Verificado con el stack levantado: un curso completo generado con un servidor de IA simulado por HTTP, una pausa por tope y su reanudación, una generación con material (citas válidas) y un menor con contenido bloqueado.
- Una **prueba con un modelo real** barato, si el usuario la hace, para medir la calidad y el coste real por curso.
- `README`, `ARCHITECTURE.md` y `CLAUDE.md` actualizados. `security-checklist` y `llm-integration-review` pasadas e informadas.
- Se cierran A4 (cerrojo con renovación y salida validada), F4-5 (valoración de un menor en el temario) y la parte de F05 de A2 y de F3-1.
