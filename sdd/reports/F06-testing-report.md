## Testing Report — F06 · Estudio — 25-10-2026

Rama `feature/F06-estudio`. Todo se ejecutó con Docker y variables de entorno **ficticias** (el `.env` del usuario no se leyó ni se escribió) y **sin ninguna llamada a una API de pago**. La verificación con el
stack usa un proyecto Compose aparte (`studylab-f03`, con un solo puerto local de prueba, `127.0.0.1:18080`) para no tocar el stack ni la BD del usuario; el «modelo» es un servidor simulado de la API de
mensajes de Anthropic en la red de Docker (también responde a la corrección de ejercicios) y los embeddings son de Ollama real.

### Resumen
- Tests: backend **858** (0 fallos, 4 omitidos opt-in; 108 nuevos) · frontend **306** (67 nuevos) · entorno **37** (2 nuevos) = **1 201** ✅
- Cobertura del backend: **94,1 %** de líneas · 82,9 % de ramas. Paquete `study`: **95,7 %** (ramas 87,9 %); `generation` 93,6 %.
- Verificación con el stack levantado: **50 comprobaciones**, todas correctas a la primera, más consultas a la BD, a la auditoría y a los logs, y una pasada con el navegador integrado (móvil, 375 px).
- `npm audit`: 0 vulnerabilidades. Sin dependencias nuevas.

### Resultados por criterio de aceptación

| AC | Descripción | Tests principales | Estado |
|----|-------------|-------------------|--------|
| AC-1 | «Tus cursos» muestra el avance de cada curso; sin temario, sin porcentaje | `ProgressTest`, `StudyOutline.spec.ts` + stack (100 % al completar todo) | ✅ |
| AC-2 | Temario con lecciones hechas, pendientes y no generadas, avance por tema y mejor nota del examen | `ProgressTest`, `StudyOutline.spec.ts` + stack + navegador | ✅ |
| AC-3 | «Marcar como hecha» (desmarcable, idempotente) y lección anterior y siguiente | `LessonDoneTest`, `StudyLesson.spec.ts` + stack + navegador | ✅ |
| AC-4 | Progreso calculado en el servidor, sin que los ejercicios cuenten y con todo el temario como denominador | `ProgressTest` + stack (1 de 18 = 6 %, 13 de 18 = 72 %, 18 de 18 = 100 %) | ✅ |
| AC-5 | Ejercicios con autocorrección; la solución no sale hasta responder | `ExerciseAttemptTest`, `StudyExercises.spec.ts` + stack + navegador | ✅ |
| AC-6 | «Corregir con IA» opcional, validada, registrada, limitada y que nunca pierde la respuesta | `AiGradingTest`, `GradeParserTest`, `StudyLimitsTest` + stack (datos, 409, 503, 502, 429) | ✅ |
| AC-7 | Menores: filtro antes de enviar la respuesta y del comentario antes de guardarlo | `AiGradingTest` + stack (0 bytes de la respuesta llegaron al modelo) | ✅ |
| AC-8 | Examen corregido en el servidor y sin la opción correcta antes de entregar | `ExamTest`, `GenerationAccessTest` + stack + navegador | ✅ |
| AC-9 | Resultado con nota, respuesta elegida, correcta y explicación; superado con el 50 % exacto | `ExamTest`, `StudyExam.spec.ts` + stack | ✅ |
| AC-10 | Repetir sin límite, mejor nota, entrega idempotente | `ExamTest` + stack | ✅ |
| AC-11 | Historial y «qué fallé»; un intento guardado no cambia | `ExerciseAttemptTest`, `ExamTest`, `AttemptsView.spec.ts` + stack | ✅ |
| AC-12 | Mientras se genera, se estudia lo ya hecho; lo que falta no se puede intentar (409) | `ProgressTest`, `ExerciseAttemptTest`, `ExamTest` | ✅ |
| AC-13 | Validación en el backend (respuesta, ejercicio, examen, estado de la pieza) | `ExerciseAttemptTest`, `ExamTest`, `LessonDoneTest` + stack | ✅ |
| AC-14 | Sesión, CSRF y propietario en todo; el tutor no lee los intentos de su menor; respuestas fuera de logs y auditoría | `StudyAccessTest`, `StudyArchitectureTest` + stack (9 de 9 endpoints, 0 coincidencias) | ✅ |
| AC-15 | 120 envíos por hora, 20 correcciones de IA al día, 200 intentos por pieza | `StudyLimitsTest` + stack (el 121.º y la 21.ª se rechazan) | ✅ |
| AC-16 | Usable en móvil, con carga, errores amables y texto sin HTML | specs de las vistas + navegador (375 px, sin scroll horizontal) | ✅ |

### Casos borde verificados
Examen entregado en blanco, con menos o más respuestas, con una opción que no existe o con índices negativos · doble clic en «Entregar» y reenvío con otras respuestas · dos exámenes abiertos y uno de hace
más de un día · «Corregir con IA» con el tope personal alcanzado, el proveedor caído, el veredicto roto una y dos veces, el límite diario agotado y una corrección ya en marcha · respuesta con intento de
inyección (`</respuesta></datos>`) · menor con respuesta bloqueable y con un comentario del modelo bloqueable · respuesta vacía, de solo espacios, de 2 001 caracteres, con caracteres de control y con HTML ·
ejercicio que no existe y pieza de otro tipo · lección sin generar o fallada que no cuenta como hecha · lección desmarcada con un examen superado · curso reanudado a mitad · intentos antiguos que caen fuera de
la ventana de la hora · identificadores no válidos, curso o pieza ajenos, pieza de otro curso del mismo usuario · tutor sobre los datos de su menor · límite por hora con la poda de intentos.

### Verificaciones fuera de los tests
Stack real (nginx → backend → PostgreSQL → Ollama real → servidor de IA simulado por HTTP), con cookies, CSRF y Mailpit reales. Script de 50 comprobaciones:
1. **Progreso:** empieza en 0 % sobre 12 lecciones y 6 exámenes; marcar una lección da el 6 %; desmarcarla vuelve a 0; marcar un ejercicio o enviar un cuerpo sin `done` da 400; con 12 lecciones y un examen superado,
   72 %; con todo hecho, 100 % también en «Tus cursos».
2. **Ejercicios:** la pieza no lleva solución ni explicación; al enviar la respuesta llegan las dos; respuestas no válidas, 400; ejercicio inexistente, 404.
3. **Corregir con IA:** veredicto y comentario; **el enunciado, la solución y la respuesta viajan como datos** y las instrucciones no llevan nada del alumno; clave en cabecera y sin `temperature`; la segunda corrección
   del mismo intento es 409 y no llama al modelo; el historial guarda por separado la respuesta, la autocorrección y la de la IA; a la **21.ª corrección del día se rechaza** (429) sin llamar al modelo.
4. **Examen:** la pieza y el inicio no llevan la opción correcta ni la explicación; empezar con uno abierto lo reutiliza; respuestas de menos o una opción inexistente, 400 y el intento sigue abierto; un intento sin
   entregar no tiene resultado (409); entregar da 3 de 5, 60 %, tema superado, con la elegida, la correcta y la explicación; entregar otra vez devuelve el mismo resultado; repetir deja dos intentos y cuenta la mejor nota.
5. **Permisos:** otro adulto recibe 404 en los 9 endpoints, idéntico al de un curso inexistente; sin sesión se rechaza; sin token CSRF, 403 y no cambia nada.
6. **Fallos y límites:** con el proveedor caído, 503 genérico sin el mensaje del proveedor y con las 3 llamadas del gateway; con el veredicto roto dos veces, 502 amable y solo un reintento; en los dos casos la
   respuesta se conserva y se puede autocorregir; **el envío 121 de la hora se rechaza** y no deja rastro (120 intentos guardados).
7. **Menores:** su respuesta bloqueable no se corrige con IA (422, mensaje acordado sin motivo) y **ni un byte llega al modelo**; puede autocorregirse; una respuesta normal se corrige con audiencia «escolar»; un
   comentario del modelo que el filtro rechaza se sustituye y se conserva el veredicto; su tutor no lee su estudio.
8. **Base de datos y auditoría:** 12 lecciones hechas, 144 intentos de ejercicio, 7 exámenes entregados y ninguno abierto; la auditoría solo lleva `STUDY_ANSWER_BLOCKED` con ids y **0 respuestas**; las llamadas de corrección
   están en `ai_usage` con usuario y curso (la tarea `ASSESSMENT`).
9. **Logs:** 0 coincidencias de respuestas, comentarios, soluciones, texto roto del modelo, mensaje del proveedor o clave en los logs del backend.
10. **Navegador integrado (móvil, 375 px):** el temario muestra «Tu avance», el porcentaje de cada tema y «Sin hacer» en los exámenes; la lección se marca como hecha y enlaza con la siguiente; el ejercicio muestra la
    solución solo tras enviar y ofrece las tres valoraciones y «Corregir con IA» (veredicto «Acertado» con el aviso de que puede equivocarse, y el botón desaparece); el examen no enseña respuestas antes de entregar,
    avisa de las dos preguntas en blanco («Entregar igualmente») y entrega con nota, «Sin contestar», la correcta y la explicación; ninguna página tiene scroll horizontal.
11. **No verificado:** cuántas veces acierta «Corregir con IA» con **un modelo real** (el servidor simulado siempre responde en el formato pedido) y el tope personal alcanzado con una BD real (cubierto por tests).

### Revisión `security-checklist`

**Lo que ya está bien:** todos los endpoints exigen sesión (`anyRequest().authenticated()`), el CSRF cubre los que modifican y toda consulta sobre intentos, progreso y piezas lleva `owner_id` (lo comprueba
`StudyArchitectureTest`); las claves compuestas de la BD impiden escribir estudio sobre una pieza o un curso de otro usuario; un curso, pieza o intento ajenos, un identificador mal formado o una pieza de otro curso
responden el mismo 404 · la solución de un ejercicio y la opción correcta de un examen no salen del servidor hasta responder o entregar, y la corrección del examen es del servidor · las carreras están cerradas en la
BD: marcar es un `INSERT … ON CONFLICT`, la corrección de IA se reserva con un `UPDATE … WHERE ai_requested_at IS NULL`, y entregar es un `UPDATE … WHERE status = 'STARTED'` · SQL siempre parametrizado, sin concatenar ·
validación en el servidor de longitudes, caracteres, índices y estado de la pieza · lo que escribe el alumno solo se pinta como texto y nunca se registra (logs ni auditoría) · la respuesta del alumno va como dato a
la IA y el veredicto se valida con un esquema estricto; un alumno que intente manipularlo solo afecta a su propia autoevaluación, que no cuenta para el progreso · sin secretos, variables, puertos ni dependencias
nuevas.

**Corregido durante esta fase:** ninguno en el código de producción. Dos errores de mis propios tests se corrigieron al implementar (el límite por hora y la retención de intentos, y un doble clic en «Corregir con IA»).

**Pendiente, por gravedad:**
- 🟠 **Calidad de «Corregir con IA» con un modelo real sin medir (F6-1).** Un veredicto equivocado puede desanimar; por eso es opcional, avisa y el alumno manda. *Propuesta:* medir con tu modelo cuántas veces acierta.
- 🟡 **El invariante «envíos por hora ≤ intentos conservados por pieza» solo está documentado.** Con los valores por defecto (120 y 200) se cumple; si alguien sube el primero por encima del segundo, el límite por hora
  dejaría de morder. *Propuesta:* validarlo al arrancar.
- 🟡 **«Una corrección a la vez» vive en memoria (F6-2).** Vale con una sola instancia del backend; con varias habría que pasarlo a la BD.
- 🟡 **La exportación del curso lleva soluciones y respuestas correctas (F6-3).** Es el contenido propio del alumno, pero rompe la autoevaluación si se usa antes de examinarse.
- 🟡 **El tutor aún no ve los intentos de su menor (F6-4, F08):** el dato se guarda y ninguna API lo expone.
- 🟡 **Un examen se puede repetir sin límite**, así que un alumno puede «aprender las respuestas» a base de intentos. Es la decisión de producto (autoevaluación), y el límite de 120 envíos por hora lo acota.
- 🟡 **El filtro local de menores solo cubre español (F4-3)**; la respuesta del menor se filtra con esa lista antes de ir al modelo.
- **No se puede comprobar desde el código:** que los puertos no se vean por la LAN (D6) y el comportamiento con el modelo real que elija el usuario.

### Revisión `llm-integration-review`

| Punto | Estado |
|---|---|
| Toda llamada pasa por la capa intercambiable | **OK**: `GradingService` solo usa `AiGateway`; `StudyArchitectureTest` prohíbe importar un adaptador o `PromptAssembler` |
| El modelo se elige por tarea desde configuración | **OK**: reutiliza `ASSESSMENT` (`APP_AI_TASKS_ASSESSMENT_*`); ningún modelo en el código y ninguna variable nueva |
| Cada llamada registra tarea, modelo, tokens, coste, usuario y curso | **OK**: filas `ASSESSMENT` con usuario y curso (la corrección cuenta en el coste del curso y en la pantalla de resultados), sin contenido |
| Tope de gasto, tope por usuario, límite de salida y reintentos acotados | **OK**: tope personal y global por el gateway; salida bajada a 600 tokens; un reintento propio y los 2 del gateway; 20 al día por usuario y una por intento |
| Una sola generación concurrente por usuario | **OK** para las correcciones (una a la vez, en memoria: F6-2); no usa `GenerationLock`, que es solo de la generación de cursos |
| Nunca se regenera lo ya generado | **OK**: un intento no se vuelve a corregir (409) y un intento guardado no cambia |
| Los tests usan el proveedor falso | **OK**; la verificación con el stack usó un servidor simulado, sin gasto |
| Clave solo por entorno | **OK**: sin cambios; en cabecera y nunca en la URL (comprobado en el servidor simulado) |
| El texto del alumno va como **dato**, separado de las instrucciones | **OK**: etiquetas `enunciado`, `solucion`, `respuesta` y `audiencia`; una respuesta con `</respuesta></datos>` queda escapada y la plantilla no cambia |
| El material de un usuario nunca se recupera para otro | **NO APLICA**: F06 no usa `MaterialSearch` |
| La salida del modelo se valida contra un esquema estricto y nunca se renderiza como HTML | **OK**: `GradeParser` (3 valores y un comentario de hasta 500 caracteres sin marcado); el comentario se pinta como texto |
| Los prompts no incluyen datos personales innecesarios | **OK**: sin nombre, correo ni edad; solo la audiencia («escolar» o «general») y el ejercicio |
| Filtrado de entrada y salida para menores | **OK**: `ContentGuard` sobre la respuesta antes de enviarla y sobre el comentario antes de guardarlo; mismo mensaje acordado y auditoría sin contenido |
| Tono por edad, el tutor no resuelve exámenes por el alumno | **PARCIAL**: la plantilla pide tono escolar para menores y orienta sin dar la solución completa; el tutor IA es F07 |
| Aviso «generado por IA» | **OK**: «La IA puede equivocarse: decide tú cómo te fue» junto a cada veredicto, y el aviso de la lección y de las piezas |

### Issues encontrados (y corregidos salvo indicación)
1. El límite de 120 envíos por hora se contaba sobre intentos que la poda podía borrar: con un tope de intentos menor que el de envíos, el límite no mordía. Con los valores por defecto no ocurre; queda como
   invariante documentado y como hallazgo 🟡 (validarlo al arrancar).
2. Dos tests míos de la FASE 4 se corrigieron: el de límites (usaba una sola pieza con un tope de intentos menor al de envíos) y el de «Corregir con IA» una sola vez (pulsaba dos veces un botón que desaparece al
   primer clic).
3. **No corregido:** medir la calidad de la corrección con un modelo real (F6-1), el aviso al tutor y su panel (F08), y validar el invariante de configuración al arrancar.
