# F07 — Informe de pruebas (FASE 7)

Tutor IA por lección. Rama `feature/F07-tutor-ia`. Contrato: `sdd/specs/US-F07-tutor-ia.md`; diseño y ADR-55 a 58: `sdd/specs/ART-F07-propuesta-tutor.md` y `ARCHITECTURE.md`.

## Resultado

| Comprobación | Resultado |
|---|---|
| Tests del backend (contenedor, BD efímera, proveedor falso) | 975 ejecutados, 0 fallos, 4 omitidos (los mismos de antes) |
| Tests del frontend | 338 de 338; typecheck (`docker build --target build frontend`) correcto |
| Tests de entorno | 39 de 39 |
| Cobertura de líneas | 94,2 % en total (3364/3571); el paquete `tutor` 95,9 % de líneas y 92,8 % de ramas |
| Stack real aislado (nginx → backend → BD → «modelo» simulado por HTTP, cookies y CSRF reales) | 34 comprobaciones, todas correctas |
| Navegador (pantalla de lección con el menor, escritorio y 375 px) | Correcto, sin desbordamiento horizontal |

Un test del frontend falló una vez mientras corría a la vez la cobertura del backend (CPU saturada) y pasó en las dos repeticiones siguientes (338 de 338). No se ha investigado más: no se reprodujo.

## Qué se verificó con el stack levantado

Proyecto aislado `studylab-f03` con contraseñas ficticias y un servidor que imita la API de mensajes de Anthropic (el tutor se apunta a él con `APP_AI_TASKS_TUTOR_PROVIDER=anthropic`); el stack del usuario no se tocó.

- Preguntar: 201 con pregunta y respuesta. El modelo recibe audiencia, curso, capítulo, lección y pregunta como **datos**; las instrucciones no llevan nada del alumno; la clave va en cabecera, sin `temperature`, con tope de salida de 1 000 tokens.
- Historial: la segunda pregunta lleva el intercambio anterior. Fallos: un ejercicio marcado como fallado llega solo como enunciado y resultado, sin la respuesta del alumno ni la solución. Nada de los exámenes.
- Validación: vacía, solo espacios, 1 001 caracteres, carácter de control, valor que no es texto y cuerpo sin pregunta → 400; una pieza que no es lección → 404.
- Respuestas malas del modelo: cita inventada y HTML crudo → un reintento y 502 `TUTOR_FAILED` sin el texto roto; proveedor caído → 503 `AI_PROVIDER_UNAVAILABLE` sin el mensaje del proveedor; nada de eso aparece en la conversación y la misma pregunta se puede repetir.
- Una respuesta a la vez: una segunda pregunta mientras responde → 429 `TUTOR_BUSY` y la primera termina bien.
- Límite: a las 30 preguntas de la hora, la 31.ª → 429 `TUTOR_RATE_LIMITED` sin dejar rastro (60 mensajes).
- Permisos: otro adulto recibe 404 en GET, POST y DELETE, igual que con una lección inexistente; sin sesión se rechaza; sin CSRF, 403.
- Vaciar: el adulto vacía solo esa lección.
- Menores: pregunta bloqueada → 201 con el mensaje acordado, sin llamar al modelo y sin que su texto llegue al servidor simulado; pregunta escolar delicada pasa con audiencia «escolar»; respuesta que el filtro rechaza → texto fijo `FILTERED`; el menor no puede vaciar (403 `THREAD_PROTECTED`); su adulto tutor recibe 404 por esta API (llega con F08).
- Registros: cero apariciones de preguntas, respuestas, lección ni «bomba» en el log del backend. La auditoría guarda `TUTOR_QUESTION_BLOCKED` y `TUTOR_ANSWER_FILTERED` solo con ids. `ai_usage` registra la tarea `TUTOR` con su estado.

## Lo que NO se ha podido verificar

- **Material propio con citas por HTTP real:** el stack aislado no tiene el modelo de embeddings de Ollama; está cubierto por `TutorMaterialTest` (fragmentos numerados, solo documentos del curso y del usuario, citas verificadas) con el proveedor falso.
- **Calidad pedagógica con un modelo real** (que guíe sin resolver, que redirija lo que no es del curso, tono por edad, coste de una conversación): F7-1 y F7-3, solo lo puede medir el usuario con sus claves.
- HTTPS, cabeceras y puertos de despliegue: siguen en el backlog de despliegue (D1 a D6).

## Revisión `security-checklist`

Bien: todos los endpoints exigen sesión; cada consulta lleva `owner_id` y una lección ajena responde igual que una inexistente; CSRF en POST y DELETE; validación en el backend; SQL parametrizado (`TutorArchitectureTest` lo comprueba); nada de `v-html` (el visor `MarkdownContent` pinta HTML crudo y enlaces peligrosos como texto); sin secretos nuevos ni dependencias nuevas; límite por usuario y una sola respuesta a la vez en la BD (también con varias instancias); ni logs ni auditoría con contenido.

Pendiente o a vigilar:
- 🟡 **F7-6 (nuevo):** vaciar la conversación borra también las preguntas que cuentan para el límite por hora y por día. Solo lo puede hacer un adulto (el menor no puede) y el tope mensual del usuario sigue cortando. Arreglo posible: contar las llamadas `TUTOR` de `ai_usage` o una tabla de eventos.
- 🟡 El tope de conservación por lección bajó de 200 a 400 mensajes (≥ 2 × el límite diario): con 200, quien preguntara 150 veces en una lección nunca llegaría al límite del día. Está documentado en `TutorProperties`, ART-F07 y `application.properties`.
- 🟡 Los límites por IP siguen sin valer hasta resolver D1; el tutor no usa ninguno (todo es por usuario).

## Revisión `llm-integration-review`

| Punto | Estado |
|---|---|
| Toda llamada pasa por `AiGateway`; nadie importa un adaptador ni construye prompts | OK (`TutorArchitectureTest`) |
| Modelo elegido por tarea desde configuración (`TUTOR`), sin nombre de modelo en el código | OK |
| Cada llamada registra tarea, modelo, tokens, coste, usuario y curso (nunca el contenido) | OK (comprobado en la BD) |
| Tope mensual propio (corta siempre) y global (aviso), tope de salida por llamada | OK |
| Una sola generación concurrente por usuario | NO APLICA al cerrojo de generación (el tutor no lo usa); su equivalente es «una respuesta a la vez» en la BD: OK |
| Reintentos acotados; un fallo del proveedor no provoca bucles | OK: un reintento del tutor; el proveedor caído deja 3 intentos del gateway como máximo |
| Nunca se regenera lo generado | NO APLICA |
| Tests con el proveedor falso | OK |
| Clave solo en el backend | OK |
| Texto del alumno, lección, fuentes e historial como **datos**, con `<>&` escapados | OK |
| El material solo se busca por propietario y por los documentos del curso | OK |
| La salida se valida antes de guardarla y nunca se pinta como HTML | OK (`TutorAnswerValidator`, `MarkdownContent`) |
| Sin datos personales innecesarios en el prompt | OK (solo «escolar» o «general»; ni nombres ni correos) |
| Menores: filtro en pregunta, fragmentos y respuesta; tono por edad | OK (el tono real depende del modelo: F7-1) |
| El tutor no resuelve exámenes | PARCIAL: no recibe soluciones ni exámenes y las instrucciones piden pistas; que lo cumpla el modelo real es F7-1/F7-3 |
| Aviso «generado por IA» | OK |

## Cambios hechos durante la FASE 7

- `TutorChat`: el foco vuelve a la caja tras cada pregunta y el aviso de IA no repite la frase de la lección.
- `sdd/BACKLOG.md`: F7-6.
