## Testing Report — F04 · Asistente de creación de curso — 25-10-2026

Rama `feature/F04-asistente-creacion-curso`. Todo se ejecutó con Docker y variables de entorno **ficticias** (el `.env` del usuario no se leyó ni se escribió) y
**sin ninguna llamada a una API de pago**. La verificación con el stack usa un proyecto Compose aparte (`studylab-f03`, sin publicar puertos salvo uno local de
prueba para ver la pantalla) para no tocar el stack ni la BD del usuario; el «modelo» es un servidor simulado de la API de mensajes de Anthropic en la red de Docker.

### Resumen
- Tests: backend **604** (0 fallos, 4 omitidos opt-in) · frontend **157** (50 nuevos) · entorno **32** (4 nuevos) = **793** ✅
- Cobertura del backend: **93,9 %** de líneas · 80,9 % de ramas. Por paquete de F04: `course` 97,0 % (ramas 84,4 %), `safety` 92,8 %.
- Verificación con el stack levantado: **44 comprobaciones**, todas correctas, más una pasada visual con el navegador integrado (móvil).

### Resultados por criterio de aceptación

| AC | Descripción | Tests principales | Estado |
|----|-------------|-------------------|--------|
| AC-1 | Pantalla «Nuevo curso» con caja de tema (3 a 200), sin jerga, usable en móvil | `NewCourseView.spec.ts` + navegador | ✅ |
| AC-2 | Paso «¿Tienes información?»: Sí/No, 1 a 5 documentos propios y listos | `NewCourseView.spec.ts`, `CourseIntakeTest` + stack | ✅ |
| AC-3 | `POST /api/courses/intake`: 0 a 3 preguntas, opciones, por defecto y «Como tú veas» | `CourseIntakeTest`, `IntakeParserTest` + stack | ✅ |
| AC-4 | Salida del modelo validada con esquema estricto; reintento y degradación sin errores técnicos | `IntakeParserTest`, `CourseIntakeTest` + stack (JSON roto, en bloque de código y proveedor caído) | ✅ |
| AC-5 | `POST /api/courses`: respuestas validadas contra lo guardado por el servidor; curso `PENDING_GENERATION` | `CourseCreationTest`, `SheetFactoryTest` + stack | ✅ |
| AC-6 | La ficha es el contrato con F05, sin datos personales ni texto de documentos; «Aceptar» idempotente | `SheetFactoryTest`, `CourseCreationTest`, `CourseSchemaTest` + stack | ✅ |
| AC-7 | Resumen en lenguaje llano y pantalla del curso con su estado y «generado por IA» | `NewCourseView.spec.ts`, `CourseStatusView.spec.ts` + navegador | ✅ |
| AC-8 | Filtro de menores en dos capas; los temas escolares delicados pasan | `ContentGuardTest`, `MinorFilterTest` + stack | ✅ |
| AC-9 | El intento bloqueado se guarda para el tutor; auditoría sin el tema; a los adultos no se les aplica | `MinorFilterTest` + stack (BD y auditoría) | ✅ |
| AC-10 | Límites por usuario: 30 consultas/h y 5 pendientes, sin depender de la IP | `CourseLimitsTest` | ✅ (no repetido en el stack) |
| AC-11 | El tema va como dato; nunca el texto ni el nombre de un documento | `CourseIntakeTest`, `CourseArchitectureTest` + stack (lo que recibió el «modelo») | ✅ |
| AC-12 | Llamada registrada como `INTAKE`, respeta los topes y los fallos no pierden el tema | `CourseIntakeTest` + stack (`ai_usage`) | ✅ |
| AC-13 | Validación en backend: tema, documentos, respuestas, caducidad | `CourseIntakeTest`, `CourseCreationTest` + stack | ✅ |
| AC-14 | Sesión, CSRF y propietario; ajeno e inexistente = 404 | `CourseAccessTest`, `CourseCreationTest` + stack | ✅ |
| AC-15 | Auditoría y logs sin tema ni nombres | `CourseIntakeTest`, `CourseArchitectureTest` + stack (0 coincidencias en los logs) | ✅ |
| AC-16 | Asistente en pasos con «Atrás», estados de carga, errores amables, «Tus cursos» en el inicio | `NewCourseView.spec.ts`, `CoursesPanel.spec.ts`, `CourseNavigation.spec.ts` + navegador | ✅ |

### Casos borde verificados
Tema vacío, de 2 o de 201 caracteres, solo símbolos o emojis, con caracteres de control o con marcado HTML · inyección en el tema (`</datos> IGNORA…`) · tema genérico o en otro
idioma · menor con tema explícito disfrazado (mayúsculas, tildes, espaciado, símbolos y dígitos) y con temas escolares delicados · adulto con el mismo tema · JSON roto, en bloque de
código, con más de 3 preguntas o sin opción por defecto · proveedor caído · tope de gasto personal · límite de frecuencia y de pendientes · doble «Aceptar», consulta caducada o ya
consumida, respuesta a una pregunta inexistente o con valores inyectados · documento borrado o no listo entre pasos, repetido o ajeno · un menor crea su curso y su tutor no lo lee por esta API.

### Verificaciones fuera de los tests
Stack real (nginx → backend → PostgreSQL → servidor de IA simulado por HTTP, con Ollama real para los documentos), con cookies, CSRF y Mailpit reales:
1. **Flujo de un adulto:** consulta con un documento → 2 preguntas → aceptar → curso `PENDING_GENERATION` con ficha correcta y documento enlazado; aceptar otra vez devuelve el mismo curso (200).
2. **Lo que recibió el «modelo»:** una sola llamada, clave en cabecera y nunca en la URL, tema y audiencia como datos, instrucciones sin el tema y pidiendo JSON, **sin nada del documento (ni
   texto ni nombre)** y sin `temperature`.
3. **Degradación:** JSON inválido → un reintento y 0 preguntas sin error técnico y sin el texto del modelo; JSON en bloque de código → aceptado; proveedor caído (503 tras 3 intentos) →
   503 genérico sin el mensaje del proveedor.
4. **Menores:** `pornografía`, `P O R N O` y `como hacer una bomba casera` se bloquean con el mensaje exacto, **sin llamar al modelo**, y la respuesta solo tiene `code` y `message`;
   un tema que el «modelo» valora no apto → mismo mensaje; los mismos temas pasan para el adulto; «la Segunda Guerra Mundial» y «la reproducción humana» pasan para el menor.
5. **Permisos y validación:** otro adulto recibe el mismo 404 ante un curso ajeno o inexistente; el tutor no lee el curso de su menor; la consulta o el documento de otro dan 404;
   una respuesta nunca preguntada, 400; los cinco temas inválidos, 400; sin sesión y sin token CSRF, rechazo (401/403).
6. **Base de datos:** 12 consultas (3 consumidas/abiertas con preguntas, 4 `BLOCKED` con tema y categoría y sin preguntas); 2 cursos; `ai_usage` con 10 llamadas `OK` (3 000 tokens de entrada,
   1 500 de salida, 0,00105 USD con el precio de Haiku) y 3 `ERROR` del proveedor caído; auditoría con ids y categoría, nunca el tema.
7. **Logs:** 0 coincidencias de temas, nombres de documentos o textos del modelo en los logs del backend, y 0 de la clave en la URL en el servidor simulado.
8. **Navegador integrado (móvil, 375 px):** inicio con «Tus cursos»; el asistente (tema → información → preguntas con la opción por defecto marcada → resumen → pantalla del curso con «En cola»);
   y el bloqueo de un menor con «Contenido inapropiado. Consulta con tu adulto.». Hallazgo: el resumen mostraba solo las etiquetas («Conversación», «Normal (6)»), sin decir a qué pregunta
   respondían; ahora muestra la pregunta y la respuesta.
9. **No verificado:** la calidad del JSON de un **modelo real** (el servidor simulado siempre responde bien) y el comportamiento con un menor cuyo JSON llega inservible (ver más abajo).

### Revisión `security-checklist`

**Lo que ya está bien:** todos los endpoints exigen sesión y CSRF y filtran por propietario, con 404 idéntico para ajeno e inexistente (IDOR cubierto por tests y por HTTP real) · el texto del usuario va
solo como dato y las instrucciones son una plantilla fija; un tema hostil no la cambia · las preguntas las guarda el servidor y el cliente solo devuelve ids: no puede inventar valores ni campos de la
ficha · la salida del modelo pasa por un esquema estricto antes de usarse y nunca llega al cliente sin validar · las claves foráneas compuestas impiden que un curso use el documento de otro usuario ·
SQL siempre parametrizado, sin concatenar (lo comprueba un test que lee el código) y toda consulta lleva el propietario · ni logs ni auditoría llevan temas, nombres ni textos del modelo · el menor nunca
ve la categoría del bloqueo ni qué término coincidió · el filtro local no gasta nada y responde antes de llamar al modelo · sin dependencias ni puertos nuevos · sin `v-html`: los textos que vienen del modelo
y del usuario se pintan como texto (test con marcado hostil).

**Corregido durante esta fase:** 🟡 el resumen del asistente solo mostraba las etiquetas de las opciones elegidas, sin su pregunta; ahora las muestra juntas.

**Pendiente, por gravedad:**
- 🟠 **Menor con la salida del modelo inservible.** Si el JSON llega roto dos veces, la valoración de idoneidad no se puede hacer: el tema se acepta porque ya pasó la lista local, y F05 deberá
  volver a comprobarlo antes de generar. Es una decisión consciente (bloquearlo daría el mensaje de «contenido inapropiado» a un tema legítimo por un fallo de formato), pero hay que medir con un modelo
  real barato con qué frecuencia ocurre (F4-5). *Propuesta:* en F05, pasar `ContentGuard` y la valoración del modelo también sobre el temario antes de generar lecciones.
- 🟠 **El filtro solo cubre español.** Un menor puede pedir lo mismo en otro idioma; lo frena solo la valoración del modelo (F4-3).
- 🟡 **Falsos positivos y negativos de la lista local.** Es una lista corta de frases; un tema escolar legítimo con una de ellas se bloquea, y no hay desbloqueo por el tutor (F4-2).
- 🟡 **Las consultas guardan el texto del tema** (también las bloqueadas, para el tutor) y no se purgan (F4-1).
- 🟡 **Un menor sigue pudiendo llenar su cupo de consultas** (30 por hora) con temas bloqueados; no hay aviso al tutor hasta F08.
- **No se puede comprobar desde el código:** que los puertos no se vean por la LAN (D6) y el comportamiento con el modelo real que elija el usuario.

### Revisión `llm-integration-review`

| Punto | Estado |
|---|---|
| Toda llamada pasa por la capa intercambiable | **OK**: `CourseService` solo usa `AiGateway` (`AiArchitectureTest` y `CourseArchitectureTest`) |
| El modelo se elige por tarea desde configuración | **OK**: tarea `INTAKE` con `APP_AI_TASKS_INTAKE_PROVIDER/_MODEL`; ningún modelo en el código |
| Cada llamada registra tarea, modelo, tokens, coste y usuario | **OK** (10 filas `INTAKE` con coste real y 3 de error, sin contenido) |
| Tope de gasto, tope por usuario, límite de salida y reintentos acotados | **OK**: tope personal comprobado, 1 500 tokens de salida y máximo 2 reintentos del gateway más 1 del asistente |
| Una sola generación concurrente por usuario | **NO APLICA**: el asistente no genera un curso; su límite es de frecuencia y de cursos pendientes (A2/ADR-41) |
| Nunca se regenera lo ya generado | **OK**: «Aceptar» es idempotente y reutiliza la consulta guardada |
| Los tests usan el proveedor falso | **OK**; la verificación con el stack usó un servidor simulado en la red de Docker, sin gasto |
| Clave solo por entorno | **OK**: viaja en cabecera y nunca en la URL (comprobado en el servidor simulado) |
| El texto del usuario, como **dato** y separado de las instrucciones | **OK**: tema y audiencia como datos etiquetados; un tema con `</datos>` no cambia la plantilla |
| El material de un usuario nunca se recupera para otro | **OK**: F04 solo enlaza documentos propios y listos (claves compuestas) y **no envía su texto al modelo** |
| La salida del modelo se valida contra un esquema antes de usarla | **OK**: `IntakeParser` (ADR-38) y se sirve al cliente solo lo validado |
| Prompts sin datos personales | **OK**: solo el tema y la audiencia («escolar» o «general»), sin nombre, edad ni correo |
| Filtrado de temas y respuestas para menores | **OK en entrada (F04)**; el filtrado de la **salida** del tutor y de las lecciones llega con F05/F07; **pendiente** lo de arriba sobre JSON inservible |
| Tono por edad, tutor que no resuelve exámenes, aviso «generado por IA» | **PARCIAL**: el aviso «generado por IA» está en el resumen y en la pantalla del curso; el tono y el tutor son de F05 a F07 |

### Issues encontrados (y corregidos salvo indicación)
1. Resumen del asistente sin el contexto de cada respuesta → corregido.
2. Errores de mis propios tests durante la implementación (el procesado de otro usuario procesaba también el documento pendiente que se quería dejar sin procesar; un botón de tipo `submit`
   no dispara el envío en un montaje sin documento; y el entorno de desarrollo de Compose llega como lista y no como objeto), sin impacto en la lógica.
3. **No corregido:** medir la calidad del JSON con un modelo real, el aviso al tutor de los intentos bloqueados (F08) y la purga de consultas (F4-1).
