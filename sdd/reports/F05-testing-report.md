## Testing Report — F05 · Generación del curso — 25-10-2026

Rama `feature/F05-generacion-curso`. Todo se ejecutó con Docker y variables de entorno **ficticias** (el `.env` del usuario no se leyó ni se escribió) y **sin ninguna llamada a una API de
pago**. La verificación con el stack usa un proyecto Compose aparte (`studylab-f03`, con un solo puerto local de prueba, `127.0.0.1:18080`) para no tocar el stack ni la BD del usuario; el «modelo»
es un servidor simulado de la API de mensajes de Anthropic en la red de Docker, y los embeddings de los documentos son de Ollama real.

### Resumen
- Tests: backend **739** (0 fallos, 4 omitidos opt-in; 135 nuevos) · frontend **216** (59 nuevos) · entorno **35** (3 nuevos) = **990** ✅
- Cobertura del backend: **93,8 %** de líneas · 82,0 % de ramas. Paquete `generation`: **93,3 %** (ramas 85,0 %); lo no cubierto es sobre todo el hilo de fondo, apagado en los tests, que se
  ejercitó en el stack.
- Verificación con el stack levantado: **59 comprobaciones**, todas correctas a la primera, más consultas a la BD, a la auditoría y a los logs, y una pasada visual con el navegador integrado (móvil).
- `npm audit`: 0 vulnerabilidades (también sin `--omit=dev`). Dependencia nueva: `marked` 18.1.0 (versión exacta, publicada hace 20 días).

### Resultados por criterio de aceptación

| AC | Descripción | Tests principales | Estado |
|----|-------------|-------------------|--------|
| AC-1 | Un proceso en segundo plano recoge el curso; sobrevive a reinicios; ninguna petición espera a la IA | `GenerationFlowTest` (cola, reinicio simulado, baja del propietario), stack (aceptar devuelve `PENDING_GENERATION`; los 3 cursos antiguos de F04 se recogieron solos al arrancar) | ✅ |
| AC-2 | Una sola generación por usuario, cerrojo renovado; otros usuarios no se bloquean | `GenerationFlowTest` (cerrojo, cola, cerrojo perdido) + stack (dos cursos de la misma persona nunca a la vez) | ✅ |
| AC-3 | El temario sale primero, validado y visible antes de la primera lección | `OutlineParserTest`, `GenerationFlowTest` + stack | ✅ |
| AC-4 | Lección y ejercicios por capítulo y examen por tema, en orden; una pieza hecha nunca se regenera | `GenerationFlowTest`, `ExercisesAndExamParserTest` + stack (1 + 30 llamadas exactas) | ✅ |
| AC-5 | Salida validada; un reintento; una pieza que falla no tumba el curso | `LessonValidatorTest`, `ExercisesAndExamParserTest`, `GenerationFlowTest` + stack (ejercicios rotos: 18 hechas, 12 × 2 llamadas) | ✅ |
| AC-6 | Fragmentos del material del curso, como datos y citados; marcadores verificados | `SourcePackTest`, `GenerationMaterialTest` + stack (lo que recibió el «modelo») | ✅ |
| AC-7 | Menores: filtro de fragmentos y de lo generado, valoración del temario, reintento reforzado | `GenerationMinorTest` + stack (temario no apto → `FAILED`; lecciones bloqueables → `CONTENT`) | ✅ |
| AC-8 | Gasto por curso; pausa por tope (personal o global) o por proveedor, sin llamadas en pausa | `GenerationPauseAndResumeTest`, `GenerationGlobalBudgetTest` + stack (PROVIDER y BUDGET) | ✅ |
| AC-9 | Reanudar continúa por lo que falta; 409 si no está en pausa; 429 antes de 60 s | `GenerationPauseAndResumeTest` + stack | ✅ |
| AC-10 | Detalle con temario, estado de cada pieza, progreso, motivo y coste; ajeno = 404 | `GenerationAccessTest` + stack | ✅ |
| AC-11 | Lección, ejercicios y examen legibles solo por su propietario, con «generado por IA» | `GenerationAccessTest`, `PieceView.spec.ts` + stack + navegador | ✅ |
| AC-12 | Exportación JSON versionada sin nombres, ids ni datos del usuario | `GenerationAccessTest` + stack (0 coincidencias de nombre, id, correo) | ✅ |
| AC-13 | Visor con lista cerrada de elementos, sin HTML crudo ni enlaces peligrosos | `MarkdownContent.spec.ts` (entradas hostiles), `security.spec.ts`, `LessonValidatorTest` | ✅ |
| AC-14 | Pantalla del curso: temario, progreso, pausa con motivo y «Reanudar», sondeo cada 3 s que se detiene | `CourseProgress.spec.ts`, `PieceNavigation.spec.ts`, `CourseStatusView.spec.ts` + navegador | ✅ |
| AC-15 | Sesión, CSRF y propietario; auditoría y logs sin contenido | `GenerationAccessTest`, `GenerationArchitectureTest` (logs en DEBUG), `GenerationFlowTest` + stack (0 coincidencias en logs y auditoría) | ✅ |
| AC-16 | Máximo de piezas, límites de longitud y de elementos | `GenerationLimitsTest`, parsers | ✅ |

### Casos borde verificados
Temario con otro número de temas, con capítulos de más o de menos, con títulos repetidos, con marcado, con caracteres de control, dentro de un bloque de código o rodeado de texto · temario «no
apto» sin temas · lección corta, larguísima, de un solo bloque, con `<script>`, con enlaces `javascript:` o `data:`, con citas inexistentes, sin citas pese a haber material o con citas sin
material · un número de lista o un enlace con corchetes no cuenta como cita · ejercicios con 2 o 9 elementos, dificultad inventada, solución vacía o campos largos · examen con 4 o 13 preguntas,
opciones repetidas o vacías, respuesta fuera de rango, en lista o en texto · cerrojo perdido a mitad, propietario dado de baja antes y durante, reinicio a mitad, dos cursos del mismo usuario ·
tope personal ya alcanzado, alcanzado a mitad y tope global en modo `BLOCK` · proveedor caído y clave rechazada · reanudar un curso en cualquier estado, dos veces seguidas y antes de 60 s ·
documento borrado a mitad, pendiente o sin fragmentos · menor con fragmentos, lección, ejercicios y examen bloqueables · un temario que pide más piezas que el máximo · curso ajeno, pieza de otro
curso de la misma persona, id no válido, sin sesión y sin CSRF.

### Verificaciones fuera de los tests
Stack real (nginx → backend → PostgreSQL → Ollama real → servidor de IA simulado por HTTP), con cookies, CSRF y Mailpit reales. Script de 59 comprobaciones:
1. **Curso completo con material:** un documento indexado con Ollama; el curso de 6 temas termina en `READY` con 30 de 30 piezas y el segundo curso de la misma persona espera a que acabe el primero
   (nunca dos `GENERATING`). 31 llamadas exactas (1 temario + 30), temario primero y una sola vez, en el orden lección, ejercicios, lección, ejercicios, examen.
2. **Lo que recibió el «modelo»:** las 12 lecciones llevan fragmentos numerados como **datos**; ningún nombre de documento llega al modelo; las instrucciones no llevan ni el tema ni el material;
   clave en cabecera y nunca en la URL; sin `temperature`; audiencia «general»; los ejercicios se piden a partir de la lección ya generada.
3. **Lectura:** lección con su Markdown, su fuente [1] (solo número y página, sin el documento) y «generado por IA»; ejercicios (3) y examen (5) con estructura; un curso sin material no lleva
   fuentes ni citas; exportación JSON versionada con «Fuente 1» y sin nombres, ids ni correo.
4. **Permisos:** curso, pieza, exportación y reanudar ajenos → 404 idéntico al de un curso inexistente; una pieza pedida dentro de otro curso de la misma persona → 404; sin sesión → rechazo;
   reanudar sin CSRF → 403; reanudar un curso que no está en pausa → 409.
5. **Proveedor caído:** tras el temario, las lecciones fallan con 503 → el curso se pausa por `PROVIDER` conservando el temario; en pausa **no hay más llamadas** (4 en total: el temario y los
   reintentos del gateway); el mensaje del proveedor no llega al usuario; reanudar → 202; si vuelve a caer, reanudar antes de 60 s → 429 `RESUME_TOO_SOON`; recuperado el proveedor y pasados 60 s,
   reanudar pide **solo las 30 piezas que faltaban** (ni el temario ni nada ya hecho) y termina.
6. **Piezas rotas:** los ejercicios salen con texto roto → 18 piezas hechas, 12 falladas con 2 llamadas cada una (43 en total), el curso en pausa por `CONTENT` y el texto del modelo no aparece en la
   respuesta ni en la BD; reanudar reintenta **solo los 12 ejercicios** y termina.
7. **Menores:** un menor genera su curso completo con audiencia «escolar» en todas las llamadas y su tutor no lo lee por esta API (F08); un temario que el modelo juzga no apto → `FAILED` con
   `TOPIC_NOT_ALLOWED`, **una sola llamada** y sin temas ni piezas (el mismo temario para un adulto sí se genera); lecciones con contenido bloqueable → cada una se reintenta una vez con la
   instrucción de refuerzo (12 + 12), ninguna se guarda ni se muestra y el curso queda en pausa por `CONTENT`.
8. **Tope de gasto:** con un gasto previo de 50 USD insertado en la BD, el curso se pausa por `BUDGET`, no hace llamadas en pausa y reanudar con el tope aún alcanzado vuelve a pausar con 0 llamadas
   nuevas.
9. **Base de datos y auditoría:** 6 cursos `READY`, 1 `FAILED` (`TOPIC_NOT_ALLOWED`), 2 en pausa (`CONTENT` y `BUDGET`) y 3 antiguos de F04 en pausa por `PROVIDER` (se recogieron al arrancar, antes de
   que el servidor simulado estuviera listo: es justo el comportamiento previsto). 0 apariciones de «bomba» o del texto roto en piezas o auditoría; la auditoría lleva `STARTED`, `PAUSED`, `RESUMED`,
   `COMPLETED`, `FAILED` y `COURSE_CONTENT_BLOCKED` solo con ids y motivo; 0 cerrojos vivos al terminar; `ai_usage` con el coste por tarea y curso (ASSESSMENT 138 OK, LESSON 97 OK, OUTLINE 9 OK, más
   los errores del proveedor y 2 bloqueos).
10. **Logs:** 0 coincidencias de temas, títulos, textos de lecciones, nombres de documentos, texto roto del modelo, mensaje del proveedor o clave en los logs del backend.
11. **Navegador integrado (móvil, 375 px):** un curso en pausa por el proveedor muestra «En pausa», el motivo en palabras sencillas y «Reanudar»; al pulsarlo termina («30 de 30 piezas preparadas»,
    «Descargar el curso (JSON)», temario con cada pieza como enlace) y la lección se abre con su formato, sin scripts ni imágenes, con «Contenido generado por IA» y sin scroll horizontal.
12. **No verificado:** la calidad de un **modelo real** (que el JSON de ejercicios y exámenes y las citas salgan bien con tus modelos, y cuánto cuesta un curso real); el servidor simulado siempre
    responde en el formato pedido.

### Revisión `security-checklist`

**Lo que ya está bien:** todos los endpoints nuevos exigen sesión (`anyRequest().authenticated()`), el CSRF cubre «Reanudar» y todo se filtra por propietario, con el mismo 404 ante un curso ajeno,
una pieza de otro curso o un id no válido (IDOR cubierto por tests y por HTTP real); el tutor no lee el contenido de su menor hasta F08 · SQL siempre parametrizado, sin concatenar (lo comprueba
`GenerationArchitectureTest`) y toda consulta sobre temas, capítulos y piezas lleva `owner_id`; las claves compuestas de la BD impiden mezclar contenido de usuarios · el texto del usuario y del
material va solo como dato y las instrucciones son plantillas fijas · las cuatro salidas del modelo pasan por un esquema estricto antes de guardarse y ninguna llega al cliente sin validar ·
el visor de Markdown no escribe HTML (sin `v-html`; `marked` solo se importa en un componente y solo para leer tokens, lo comprueba un test de política) · sin secretos nuevos ni variables de
entorno nuevas, sin puertos ni volúmenes nuevos · ni logs ni auditoría llevan temas, títulos, lecciones, nombres ni mensajes del proveedor (0 coincidencias en el stack) · límites de coste:
una sola generación por usuario, tope mensual personal que corta siempre, máximo de 150 piezas por curso, un reintento por pieza y «Reanudar» con enfriamiento de 60 s · `npm audit` sin
vulnerabilidades · la exportación no lleva nombres, ids ni datos personales.

**Corregido durante esta fase:** 🟡 el evento de auditoría `COURSE_GENERATION_STARTED` (AC-15) no se registraba; ahora sí, con test.

**Pendiente, por gravedad:**
- 🟠 **Calidad con un modelo real sin medir (F5-1).** El JSON de ejercicios y exámenes y las citas pueden fallar más con un modelo barato; una pieza rota cuesta dos llamadas y deja el curso en pausa
  hasta que alguien pulse «Reanudar». *Propuesta:* ejecutar un curso con tus modelos y medir cuántas piezas fallan; ajustar plantillas o el límite de salida.
- 🟠 **El filtro de menores solo cubre español (F4-3) y es una lista corta.** Lo que genera el modelo para un menor pasa por esa lista y por la valoración del temario; no hay una valoración del
  modelo sobre cada lección. *Propuesta:* decidir en F07/F08 si se añade una segunda capa (valoración del modelo o moderación) para los menores.
- 🟡 **Enlaces `http(s)` en las lecciones (F5-2).** Se permiten (con `rel` seguro y en pestaña nueva) y su texto lo escribe el modelo: un enlace con texto engañoso podría llevar a un sitio externo.
  La plantilla pide no usar enlaces, pero un material hostil podría inducirlos. *Propuesta:* para menores, no pintar enlaces (solo el texto) o mostrar el dominio al lado.
- 🟡 **Dos hilos de generación (F5-3).** Un curso ocupa un hilo de 10 a 25 minutos; con más de dos personas generando a la vez, los demás esperan en cola (sin pérdida). Suficiente para una familia.
- 🟡 **«Reanudar» repetido gasta de nuevo** las piezas fallidas (hasta el tope mensual del usuario y como mucho una vez por minuto). Es la decisión de producto de ADR-44.
- 🟡 **No hay forma de borrar un curso** ni de purgar sus piezas (F5-4); el gasto queda en `ai_usage` como debe.
- 🟡 **Las lecturas (`GET`) no tienen límite de frecuencia** (A2); con el sondeo de 3 s, cada pestaña abierta hace unas 6 consultas por petición. No llaman a la IA.
- **No se puede comprobar desde el código:** que los puertos no se vean por la LAN (D6) y el comportamiento con el modelo real que elija el usuario.

### Revisión `llm-integration-review`

| Punto | Estado |
|---|---|
| Toda llamada pasa por la capa intercambiable | **OK**: `generation/` solo usa `AiGateway` y `MaterialSearch` (`GenerationArchitectureTest` prohíbe importar un adaptador o `PromptAssembler`) |
| El modelo se elige por tarea desde configuración | **OK**: tareas `OUTLINE`, `LESSON` y `ASSESSMENT` existentes (`APP_AI_TASKS_*`); ningún modelo en el código |
| Cada llamada registra tarea, modelo, tokens, coste, usuario y curso | **OK**: todas con `course_id` (261 filas en la verificación, sin columnas de contenido); el coste del curso es la suma de sus llamadas |
| Tope de gasto con avisos, tope por usuario y límite de salida; reintentos acotados | **OK**: tope personal y global comprobados en el stack; salida acotada por tarea; 2 reintentos del gateway más 1 de la pieza; sin bucles (pausa) |
| Una sola generación concurrente por usuario | **OK**: `GenerationLock` renovado antes de cada llamada y reclamo con `SKIP LOCKED`; comprobado con dos cursos de la misma persona |
| Nunca se regenera lo ya generado | **OK**: una pieza `DONE` no entra en la consulta de piezas por generar; reanudar y reiniciar solo piden lo que falta (30 de 30 y 12 de 12 en el stack) |
| Los tests usan el proveedor falso | **OK** (`respondUsing`); la verificación con el stack usó un servidor simulado, sin gasto |
| Clave solo por entorno | **OK**: sin cambios; en cabecera y nunca en la URL (comprobado en el servidor simulado) |
| El texto del usuario, del material y de las lecciones va como **dato** y separado de las instrucciones | **OK**: etiquetas `pieza`, `audiencia`, `tema`, `fuentes`, `leccion`…; las instrucciones no llevan ni el tema ni el material (comprobado en el servidor simulado) |
| El material de un usuario nunca se recupera para otro | **OK**: `MaterialSearch` con `owner_id` y solo los documentos enlazados al curso (tests con material no enlazado y ajeno) |
| La salida del modelo se valida contra un esquema estricto y nunca se renderiza como HTML | **OK**: cuatro validadores (nunca lanzan) y visor sin HTML; el texto del modelo que no vale ni se guarda ni se muestra ni se registra |
| Los prompts no incluyen datos personales innecesarios | **OK**: sin nombre, correo ni edad; solo la audiencia («escolar» o «general») y la ficha |
| Filtrado de temas y respuestas para menores | **OK en entrada y en salida de F05** (valoración del temario, fragmentos y lo generado); **PARCIAL** por el alcance de la lista local (ver arriba) |
| Tono por edad, tutor que no resuelve exámenes | **PARCIAL**: las plantillas piden tono escolar para menores; el tutor es de F07 |
| Aviso «generado por IA» en el contenido | **OK**: en el detalle del curso, en cada pieza y en la exportación (`generatedByAi`) |

### Issues encontrados (y corregidos salvo indicación)
1. Falta de `COURSE_GENERATION_STARTED` en la auditoría (AC-15) → corregido con test.
2. Errores de mis propios tests de FASE 4, corregidos en la FASE 6: el test del cerrojo perdido escribía un texto en una columna `uuid`, y tres comprobaciones del visor miraban el HTML como cadena
   cuando lo que importa es que ningún elemento tenga atributos `on…`; `CoursesPanel` ahora tolera una respuesta sin lista.
3. Un detalle de la verificación, no un fallo: los cursos antiguos de F04 que esperaban en la cola se recogieron al arrancar el backend, antes de que el servidor simulado respondiera, y quedaron en
   pausa por el proveedor, lo que demuestra la pausa sin gasto y que «Reanudar» los recupera.
4. **No corregido:** medir la calidad con un modelo real (F5-1), los enlaces para menores (F5-2) y el borrado de cursos (F5-4).
