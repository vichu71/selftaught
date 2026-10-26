# CLAUDE.md — Study Lab (nombre provisional)

Web de ayuda al estudio con IA: el usuario dice qué quiere aprender y la IA monta el curso. Proyecto
familiar con base multiusuario. Es un laboratorio, no formación reglada.

## Cómo se trabaja aquí
- El proyecto se construye por features con SDD. Fuente de verdad: `sdd/PROJECT.md` (manda sobre
  cualquier suposición) y `sdd/features/`. Proceso: `sdd/SDD_PROCESS.md`.
- Para arrancar una feature: `/sdd-feature F01` (o F02...F10).
- No se escribe código antes de tener tests (FASE 4 antes que FASE 6). Si algo no está claro, se pregunta.
- Stack: Vue 3 + TypeScript, Spring Boot, PostgreSQL + pgvector, Docker Compose, embeddings con Ollama.

## Reglas que no se discuten
- Todo endpoint comprueba quién puede llamarlo y filtra los datos por propietario.
- Validación en el backend; SQL parametrizado; nada de `v-html` con datos de usuario o de IA.
- Secretos solo en variables de entorno del backend; si falta uno obligatorio, la app falla al arrancar.
- Toda llamada a IA pasa por la capa de proveedor (`AiGateway`), queda registrada (tokens y coste, nunca el contenido),
  avisa al llegar al 80 % y al 100 % del tope mensual (corte opcional con `AI_BUDGET_MODE=BLOCK`) y respeta una sola
  generación concurrente por usuario. Los tests usan un proveedor falso: ningún test llama a una API de pago.
- Usuario menor: datos mínimos, filtrado de contenido y adulto tutor que ve toda su actividad.
- Al terminar una feature que toque seguridad o IA: skills `security-checklist` y `llm-integration-review`.

## Docker
Todo se construye, arranca y prueba con Docker Compose (ver `sdd/PROJECT.md` sección 4b). No instales
dependencias en el host. Nunca montes `docker.sock`, uses `privileged` ni leas/escribas `.env`.
`down -v`, borrar volúmenes y `system prune` solo si el usuario lo pide.

## Herramientas
Ver `sdd/TOOLING.md` (skills, hooks y por qué no hace falta un plugin).

## Comandos útiles
El `Makefile` envuelve `docker compose`; el equipo de desarrollo es Windows **sin `make`**, así que se usan los comandos
equivalentes (`ENV_FILE=ruta make …` pasa otro fichero de variables). Detalle en `README.md`.
- `make up` / `make down` / `make logs` / `make health`: levantar, parar, ver logs y estado del entorno.
- `make test-backend` · `make test-frontend` · `make test-env`: tests en contenedor (BD efímera del perfil `test`). Sin `make`:
  `docker compose --env-file <vars> -f compose.yaml --profile test run --rm --build backend-test` (o `frontend-test`) y
  `docker compose … --profile test rm -sf db-test` al terminar; `sh scripts/tests/run-env-tests.sh` para el entorno.
- `SEED_PASSWORD='…' make seed` (o `sh scripts/seed.sh`) · `make reset-dev-db`: datos de ejemplo y reinicio de la BD de
  desarrollo (este último borra datos: solo si el usuario lo pide).
- `make pull-embedding-model` (descarga `nomic-embed-text` en el contenedor `ollama`) · `make benchmark` (**gasta dinero real**: solo
  lo lanza el usuario, con sus claves y el tope del run; nunca desde un test ni en el arranque normal).
- **Nunca leer ni escribir el `.env` real.** Para verificar con el stack levantado se usa un fichero de variables ficticio fuera
  del repositorio (`--env-file`). Ese stack deja volúmenes con contraseñas ficticias: antes de usar el `.env` real hay que borrar
  `studylab_db-data` (la BD solo fija sus contraseñas al crearse), y eso solo lo decide el usuario.
- En PowerShell `bash` es WSL (sin distribución): para scripts `sh` usar la herramienta Bash (Git Bash) con `MSYS_NO_PATHCONV=1`.

## Reglas de negocio de F01
- Contrato HTTP y criterios de aceptación: `sdd/specs/US-F01-base-cuentas-roles.md`. Arquitectura y ADR: `ARCHITECTURE.md`.
- Solo el adulto da de alta menores; el menor entra con un identificador generado por el sistema, sin correo.
- Baja de menor = desactivación (nunca borrado). Todo evento relevante va a `audit_event` (solo se añade, sin secretos).
- El adulto se registra con correo verificado y recupera la contraseña por correo. Respuestas genéricas: nunca revelar si una cuenta existe.
- Un menor ajeno o inexistente responde igual (404). Sesión en cookie HttpOnly; nada de tokens en `localStorage`.
- Variables obligatorias en `.env.example`; el `.env` real no se lee ni se escribe desde aquí.

## Reglas de F02 (capa de IA)
- Las features usan `AiGateway` y `GenerationLock`; **nadie importa un adaptador** (`ai.provider`) ni escribe un nombre de modelo o un host
  de proveedor fuera de `ai/` (lo comprueba `AiArchitectureTest`). Contrato y errores: `sdd/specs/US-F02-capa-ia-control-gasto.md`.
- El texto del usuario o del material va **siempre** como dato (`AiRequest.data(...)`), nunca en la plantilla de instrucciones.
- Nunca se registra ni se loguea contenido de prompts o respuestas, ni mensajes del proveedor, ni claves. Las claves van en cabeceras,
  no en la URL. No se envía `temperature`.
- Un modelo nuevo exige su precio en `ai-pricing.properties` (con la fecha en que se verificó) y su tarea en la configuración.
- Una tarea nueva de IA debe tener su propio tope de salida y su plantilla en `prompts/`.
- Los tests de adaptadores solo prueban el formato **según la documentación**; la confirmación definitiva es el run del benchmark.
- Una llamada que el proveedor **cobra** se contabiliza siempre aunque falle o venga vacía (`AiException.costUsd()`); un adaptador nunca
  descarta el uso de una respuesta 2xx. Un modelo con razonamiento puede agotar el límite pensando y devolver texto vacío con `LENGTH`.
- Una generación larga **renueva** el cerrojo (`Lease.renew()`) entre llamadas de IA y aborta si devuelve `false`.
- Una `AiException` que sale por un endpoint ya tiene estado HTTP y mensaje seguro en `GlobalExceptionHandler`: no la envuelvas ni
  mapees a mano. En producción la URL base de un proveedor de pago debe ser `https://`.
- Cada usuario tiene un **tope mensual propio** que corta siempre (`USER_LIMIT`, A1/ADR-29): menor 2 USD, adulto 10 USD. Aún falta el límite de frecuencia (A2).
- Verificar un adaptador nuevo también por HTTP real (servidor simulado en la red de Docker) y no solo con `MockRestServiceServer`.

## Reglas de F03 (material propio)
- Contrato y criterios de aceptación: `sdd/specs/US-F03-material-propio-rag.md`; diseño y ADR-30 a 35: `sdd/specs/ART-F03-propuesta-material.md` y `ARCHITECTURE.md`.
- El material es una **biblioteca del usuario** (no de un curso). **Toda** consulta sobre `source_chunk` lleva `owner_id` en el mismo
  `WHERE` (lo comprueba `MaterialArchitectureTest`); la recuperación solo se hace por `MaterialSearch.search`.
- El tipo del fichero se decide por su **contenido**, nunca por la extensión ni el `Content-Type`. El nombre se sanea y **nunca** se usa como
  ruta: nada de `java.io.File`/`java.nio.file` en `material/` (el original va en `bytea` hasta procesar y luego se borra).
- El texto extraído es **dato, no instrucción**: sale de `MaterialSearch` como `Fragment` y las features lo pasan con `AiRequest.data(...)`.
  `material/` no construye prompts ni llama al modelo. **Antes de enviar texto de un menor a un modelo de pago hace falta el filtrado de
  contenido (A3).**
- Ni los logs ni la auditoría llevan nombre ni contenido del documento; ningún mensaje de PDFBox o del proveedor llega al usuario (solo un `error_kind`).
- Los tests no tocan disco ni Ollama: ficheros generados en memoria (`MaterialFixtures`), embeddings del proveedor falso (8 dimensiones) y
  `MaterialWorker.processPending()` síncrono (el hilo de fondo está apagado con `app.material.worker.enabled=false`).
- Un PDF o un DOCX malicioso es la entrada más peligrosa de la app: cualquier cambio en `extract/` se prueba con ficheros adversarios
  (cifrado, bomba ZIP, XXE, JavaScript, muchas páginas) y PDFBox se actualiza revisando sus avisos de seguridad.

## Reglas de F04 (asistente de creación de curso)
- Contrato y criterios de aceptación: `sdd/specs/US-F04-asistente-creacion-curso.md`; diseño y ADR-37 a 41: `sdd/specs/ART-F04-propuesta-asistente.md` y `ARCHITECTURE.md`.
- **Antes de enviar a un modelo de pago cualquier texto de un menor** (tema, material, mensaje al tutor IA) se pasa por `safety.ContentGuard.localCheck`. Es la condición de ADR-34 y la
  capa local del filtro (ADR-39); F05 y F07 la usan, no escriben su propio filtro. El mensaje al menor es siempre «Contenido inapropiado. Consulta con tu adulto.», **sin categoría ni motivo**.
- Las respuestas del modelo **nunca** se usan sin pasar por un parser de esquema estricto (`IntakeParser` es el patrón): ids generados por el servidor, longitudes y campos acotados, y un
  fallo de formato degrada a valores por defecto, no a un error técnico. Una tarea nueva que devuelva JSON sigue el mismo patrón.
- Lo que vuelve al cliente de un asistente con IA lo guarda **el servidor** (`course_intake`): el cliente solo devuelve ids de opciones. La ficha del curso (`course.sheet`, `schemaVersion: 1`)
  es el contrato con F05: si cambia, sube la versión.
- F04 solo envía el **tema** al modelo; nunca texto de documentos (lo comprueba `CourseArchitectureTest`). Los temas y los intentos bloqueados no van a logs ni a la auditoría con su texto.
- Límites por usuario contados en la BD (no por IP): `COURSE_INTAKES_PER_HOUR` y `COURSE_MAX_PENDING`. Una consulta rechazada por límite no deja fila ni llama al modelo.

## Reglas de F05 (generación del curso)
- Contrato y criterios de aceptación: `sdd/specs/US-F05-generacion-curso.md`; diseño y ADR-42 a 47: `sdd/specs/ART-F05-propuesta-generacion.md` y `ARCHITECTURE.md`.
- La generación vive en `generation/` y es el **único** paquete que usa `MaterialSearch` (`course/` sigue sin poder leer material). Quien use `MaterialSearch` para un menor aplica también `ContentGuard`
  (lo comprueba `GenerationArchitectureTest`). La cola son las filas de `course_piece`: **una pieza `DONE` no se regenera nunca por sí sola (F09: solo si el propietario lo pide)**; el generador solo mira `PENDING` y `FAILED`.
- Cada petición a la IA lleva el dato `pieza` (`temario|leccion|ejercicios|examen`) y `audiencia`; el material va como `fuentes` numeradas **sin nombres de documento**. La salida pasa siempre por su validador
  (`OutlineParser`, `LessonValidator`, `ExercisesParser`, `ExamParser`: devuelven vacío, nunca lanzan). Una pieza reintenta una vez; si falla, `FAILED` y el curso sigue (acaba en `PAUSED/CONTENT`).
- Una generación larga renueva el cerrojo antes de **cada** llamada (`lease.renew()`); si devuelve `false`, el curso vuelve a la cola y no se escribe nada. Tope de gasto o proveedor caído = `PAUSED` con motivo,
  sin llamadas ni reintentos automáticos; «Reanudar» tiene enfriamiento de 60 s por curso.
- Para un menor: valoración `appropriate` del temario (cierra F4-5), `ContentGuard` sobre fragmentos antes de enviarlos y sobre lo generado antes de guardarlo; ni logs ni auditoría llevan el contenido.
- Frontend: el Markdown se pinta solo con `MarkdownContent` (tokens de `marked.lexer` → elementos Vue de una lista cerrada). **Nada de `v-html`**, nada de `marked.parse` ni `Renderer`; HTML crudo,
  imágenes y enlaces que no sean `http(s)` salen como texto. `marked` va con versión exacta y solo se importa en ese componente. Cambios en el visor se prueban con entradas hostiles.
- Los tests no tocan el proveedor real: `FakeProvider.respondUsing(...)` responde según la pieza pedida, y `app.generation.worker.enabled=false` apaga el hilo de fondo (`CourseGenerator.processPending()` es síncrono).
- Los ejercicios y exámenes guardan la solución (F06 los corregirá): `ExamParser` exige una única opción correcta. Un curso nunca supera `app.generation.max-pieces` (150).
- Lecciones de F05: en un test de seguridad del visor se comprueban los **atributos de los elementos** (`on…`), no el HTML como cadena (el HTML crudo se muestra como texto y puede nombrarlos). `ai_generation_lock.owner` es un `uuid`: un test que lo cambie usa `gen_random_uuid()`. El reclamo de la cola no toca a quien ya tiene un cerrojo vigente (ADR-48).

## Reglas de F06 (estudio)
- Contrato y criterios de aceptación: `sdd/specs/US-F06-estudio.md`; diseño y ADR-49 a 53: `sdd/specs/ART-F06-propuesta-estudio.md` y `ARCHITECTURE.md`.
- El paquete `study/` lee piezas hechas (`course_piece`) y escribe `lesson_progress`, `exercise_attempt` y `exam_attempt`: **toda** consulta lleva `owner_id` (lo comprueba `StudyArchitectureTest`). Un intento o una pieza ajenos responden 404 igual que uno inexistente; el tutor no los lee por esta API (F08).
- **La solución de un ejercicio y la opción correcta de un examen no salen del servidor hasta que el alumno responde o entrega** (ADR-50). La pieza de F05 ya no las lleva; la exportación del curso sí (es su contenido). Un examen se corrige siempre en el servidor, sin IA.
- El **progreso no se guarda, se calcula** (ADR-49): lección hecha = fila en `lesson_progress` con su pieza `DONE`; examen superado = un intento entregado con `correct·100 ≥ total·pass-percent` (50). El denominador son todas las piezas del temario.
- Un examen es un intento de **dos pasos e idempotente** (ADR-51): entregar dos veces devuelve el mismo resultado y un 400 no gasta el intento.
- «Corregir con IA» usa la tarea **`ASSESSMENT`** con la plantilla `grade-exercise` (sin tarea ni variable nuevas), con la respuesta, el enunciado y la solución **como datos**; veredicto validado por `GradeParser` (un reintento). Una por intento, 20 al día y una a la vez por usuario;
  un fallo conserva la respuesta. Para un menor, `ContentGuard` sobre su respuesta **antes** de enviarla y sobre el comentario antes de guardarlo.
- **Las respuestas del alumno no van a logs ni a la auditoría** (solo ids y resultados). Límites en la BD (`app.study.*`): 120 envíos por hora, 20 correcciones al día, 200 intentos por pieza.
- Frontend: el contenido del modelo y del alumno se pinta siempre como texto o con `MarkdownContent`; nada de `v-html`. Los estados de carga y los errores nunca enseñan el código del backend.
- Lecciones de F06: los envíos por hora se cuentan sobre los intentos conservados (`max-submissions-per-hour` ≤ `max-attempts-per-piece`, ADR-54). Un botón que desaparece tras el primer clic no se puede pulsar dos veces en un test: se prueba un clic y que ya no está. La verificación con el stack usa el servidor simulado `f06-stub` (responde también a la corrección).

## Reglas de F07 (tutor IA por lección)
- Contrato y criterios de aceptación: `sdd/specs/US-F07-tutor-ia.md`; diseño y ADR-55 a 58: `sdd/specs/ART-F07-propuesta-tutor.md` y `ARCHITECTURE.md`.
- El paquete `tutor/` usa la tarea **`TUTOR`** (sin tarea ni variable nueva) y **nunca `GenerationLock`**: ese cerrojo es solo de la generación de cursos (ADR-20). «Una respuesta a la vez» y los límites (30/h, 150/día) viven en la BD con un cerrojo por usuario (ADR-55): la pregunta nace `PENDING`,
  pasa a `OK` con su respuesta o a `FAILED` si el modelo falla (nunca se ve) y los fallos y los bloqueos **cuentan** para el límite. Se conservan 400 mensajes por lección (`max-stored` ≥ 2 × `max-per-day`, o la poda deja el límite sin efecto); vaciar reinicia el contador (F7-6).
- Todo lo que va al modelo es **dato** etiquetado: audiencia, curso, capítulo, lección (≤ 12 000 caracteres), fallos, fuentes, historial (6 intercambios, 6 000 caracteres) y pregunta. De lo que falló el alumno solo van **enunciado y resultado**, nunca su respuesta ni la solución; nada de los exámenes (ADR-56).
- **Menores:** `ContentGuard` sobre la pregunta (antes de enviarla), sobre los fragmentos (antes de enviarlos) y sobre la respuesta (antes de mostrarla); los bloqueos quedan en el hilo como `BLOCKED` o `FILTERED` para su tutor y la auditoría lleva ids, nunca el texto (ADR-57). Quien use `MaterialSearch` aplica también el filtro.
- La respuesta se valida con `TutorAnswerValidator` (no vacía, ≤ 4 000 caracteres, sin HTML crudo ni enlaces peligrosos, citas `[n]` que existan; sin material no hay citas), con un reintento (ADR-58); sin streaming. En el frontend se pinta siempre con `MarkdownContent`.
- El hilo es por lección y usuario; el adulto lo vacía, el **menor no** (403 `THREAD_PROTECTED`, su tutor lo ve) y el tutor adulto no lo lee por esta API hasta F08. Ni logs ni auditoría llevan preguntas, respuestas, lecciones ni fragmentos (lo comprueba `TutorArchitectureTest`).

## Reglas de F08 (panel del adulto tutor)
- Contrato y criterios de aceptación: `sdd/specs/US-F08-panel-tutor-adulto.md`; diseño y ADR-59 a 62: `sdd/specs/ART-F08-propuesta-panel.md` y `ARCHITECTURE.md`.
- El paquete `guardian/` es de **solo lectura**: solo `GET`, sin SQL que escriba (salvo la auditoría), sin `AiGateway`, sin HTML (lo comprueba `GuardianArchitectureTest`). **Todo** endpoint empieza por `AccessGuard.requireStudentOf`; sin vínculo, alumno inexistente o id mal formado → 404 igual.
  El curso y la pieza se buscan con el **id del alumno** como propietario: los de otro alumno → 404. Toda consulta lleva `owner_id` (o `user_id` en `ai_usage`, `actor_user_id` en `audit_event`).
- El adulto ve respuestas, intentos y conversaciones de su menor (decisión de F08), pero **nunca** el texto de lecciones, ejercicios ni exámenes generados, ni las soluciones. Los avisos de tutor apuntan al hilo sin repetir la pregunta; el de tema rechazado sí lleva el tema.
- Intentos, conversaciones, hilo y avisos se **auditan** como `GUARDIAN_VIEW` con ids (nunca contenido), fuera de transacción. Ni logs ni auditoría llevan lo que escribió el alumno. Lo devuelto va recortado: 20 intentos por ejercicio, 200 en total y 100 avisos.
- Un alumno desactivado se puede consultar (`isTutorOf` no mira el estado). El aviso al menor de que su adulto lo ve vive en `HomeView` y en el chat del tutor: si cambia, que siga nombrando respuestas, intentos y conversaciones.

## Reglas de F09 (edición de temario y compartir por código)
- Contrato y criterios de aceptación: `sdd/specs/US-F09-edicion-compartir.md`; diseño y ADR-63 a 66: `sdd/specs/ART-F09-propuesta-edicion-compartir.md` y `ARCHITECTURE.md`.
- `courseedit/` y `share/` **no hablan nunca con el modelo** (nada de `AiGateway`, `MaterialSearch` ni `GenerationLock`): editar crea o limpia piezas `PENDING` y devuelve el curso a la cola de F05, que genera solo lo nuevo con su cerrojo y su control de gasto. Toda edición bloquea la fila del curso (`FOR UPDATE`),
  se rechaza con `COURSE_BUSY` si está en cola o generándose y sube `course.version`. **Una pieza hecha solo se regenera ante una petición explícita del propietario** (amplía la regla de F05); lo que dependía de ella se borra y sus dependientes quedan `outdated`.
- Un **menor**: todo lo que escribe al editar (títulos, resúmenes, objetivos, lecciones) y toda la copia que canjea pasan por `ContentGuard.localCheck` **antes** de guardarse, enviarse a la IA o crearse; si salta, mensaje acordado, auditoría `COURSE_EDIT_BLOCKED` o `COURSE_COPY_BLOCKED` con ids y aviso en el panel del adulto (F08).
- El **código** de compartir: 80 bits de `SecureRandom`, solo se guarda su SHA-256 (`share_code.code_hash`), se enseña una vez, nunca va a logs ni a la auditoría. El canje busca por hash (con caducidad y revocación en la misma consulta, es la única lectura de un curso ajeno sin `owner_id`) y **todo fallo responde el mismo 404**.
  Límites por usuario en la BD (`course_action`): 20 ediciones con IA, 10 fallos y 10 copias por hora; nada por IP (D1).
- La **copia** (`CourseCopier`) crea un curso `READY` con ids nuevos y `origin_id`/`origin_version`, sin documentos, fuentes (se quitan las marcas `[n]`), progreso, intentos, conversaciones, consultas ni uso de IA; el detalle solo enseña `copy.originVersion`, nunca el id del original. Un test cuenta filas y busca ids.
- Todo lo que cambia datos exige sesión y CSRF; un curso, tema, capítulo, pieza o código ajenos responden 404 igual que uno inexistente; el adulto tutor no edita los cursos de su menor.

## Reglas de F10 (rediseño del frontend «Mapa»)
- Contrato y criterios de aceptación: `sdd/specs/US-F10-rediseno-frontend.md`; diseño y ADR-67 a 72: `sdd/specs/ART-F10-propuesta-rediseno.md` y `ARCHITECTURE.md`. Prototipos: `sdd/design/F10/`. **F10 es solo de frontend**: sin migraciones, endpoints ni cambios en `backend/`.
- **Colores, espacios, radios, sombras y tipografías solo en `src/styles/tokens.css`** (un test lo comprueba: ninguna vista ni otro CSS lleva `#hex`, `rgb()` ni `hsl()`). Las vistas se hacen con los componentes de `src/ui/`; los diálogos y pestañas, con Reka UI. Nada de `confirm()` ni `alert()` del navegador.
- Todo par de color nuevo en `tokens.css` entra en `tests/design/contrast.spec.ts` (AA: 4,5:1 texto y 3:1 elementos, en claro y en oscuro). Un estado nunca se dice solo con color: también con forma, texto o icono.
- El mapa (`src/map/`) se dibuja con **elementos Vue**, nunca con cadenas ni `v-html`; `layoutMap` es una función pura y debajo del SVG va siempre la lista equivalente. Los ejercicios no son parada. Un título de la IA o del usuario es siempre texto.
- Ningún recurso de terceros: fuentes e iconos van en el paquete (Fontsource, Lucide) y la política de nginx sigue siendo `default-src 'self'`. Las dependencias del frontend las lista el test de entorno (versión exacta): añadir una exige actualizarlo con el motivo.
- Se conservan los `data-testid` y las reglas de F01–F09 que viven en la interfaz: el aviso al menor de que su adulto ve respuestas, intentos y conversaciones; el aviso «generado por IA»; el mensaje sin categoría para menores; los códigos de compartir que se ven una sola vez; el visor `MarkdownContent`.
- Movimiento solo dentro de `prefers-reduced-motion: no-preference`; objetivos táctiles de al menos 44 px (`--size-tap`); móvil probado a 360–430 px.
- Lecciones de F10: **Vitest sustituye los `.css` por una cadena vacía** (también con `?raw`): las pruebas de estilos leen los ficheros con `node:fs` desde `process.cwd()`. El diálogo de Reka UI se teletransporta al `body`: se busca en `document.body` y se limpia en `afterEach`; las pestañas de Reka se activan con `mousedown`, no con `click`. Un slot de VTU dado como cadena es una plantilla (usa una función para probar texto). **Nada se da por bueno solo con tests de DOM**: la pasada en el navegador encontró texto invisible, nombres solapados y tramos que cruzaban títulos. El navegador de la app puede estar oculto (viewport 0×0): `resize_window` a un tamaño concreto devuelve capturas.

## Reglas de F11 (piel «Mundo que crece», rama aparte)
- Contrato y criterios de aceptación: `sdd/specs/US-F11-piel-mundo-que-crece.md`; diseño y ADR-73 a 77: `sdd/specs/ART-F11-propuesta-mundo.md` y `ARCHITECTURE.md`. **Solo frontend**; vive en `feature/F11-piel-mundo-que-crece` y reemplaza a «Mapa» allí. Las reglas de F10 siguen valiendo salvo las de tipografías (ahora Fredoka y Newsreader).
- La **fase de una planta** sale solo de `world/plantStage.ts` (0 / 1–39 / 40–99 / 100; sin porcentaje, semilla «Preparándose») y siempre se dice también con texto. Las ilustraciones son plantillas Vue con **clases que leen tokens**: ningún `fill`/`stroke` con color a mano, ni `<image>`, `foreignObject` ni imágenes externas.
- El camino se dibuja con `map/roadPath.ts` (función pura) sobre las posiciones de `layoutMap`; la lista equivalente, los ids (`map-item-*`, `map-link-*`) y los roles de F10 no cambian. Todo par de color nuevo (cielo, hierba, camino) entra en `tests/design/contrast.spec.ts`.

## Lecciones de F01 (no obvias en el código)
- Todo flujo con sesión o CSRF se prueba **también sin `csrf()` de MockMvc** (con cookies reales, en una clase con su propio
  contexto: `csrf()` sustituye el repositorio CSRF del contexto compartido) y con **el stack levantado**: así se vio que la
  cookie `XSRF-TOKEN` se borraba tras la primera petición autenticada (ADR-15).
- Los límites por IP solo valen si el proxy pasa la IP real; hoy no (`sdd/BACKLOG.md`, D1). No añadir más límites por IP sin resolverlo.
- Pendientes de seguridad y despliegue: `sdd/BACKLOG.md`; informe completo: `sdd/reports/F01-testing-report.md`.
- En los tests de entorno, `docker compose config --format json` da el entorno de un servicio como **lista** `CLAVE=valor` cuando se combinan dos ficheros (compose.dev.yaml) y como objeto con uno solo.
- En Vitest, un botón `type="submit"` no envía un formulario de un montaje sin documento: usa `@click` en el botón (y `@submit.prevent` para la tecla Intro). Y `uploadedAndProcessed` procesa los documentos pendientes de TODOS los usuarios: sube el que debe quedar pendiente el último.
- Con datos del usuario (nombres de documentos, mensajes) en el cuerpo de una respuesta, Spring MVC los volcaba al log a nivel DEBUG: cada feature con datos personales
  añade su test de logs (como `MaterialArchitectureTest`) y ese componente queda en INFO (ADR-36). Una excepción dentro de un `@Transactional` deshace también la
  auditoría que se escribió antes: audita fuera de la transacción si el caso es un rechazo.

## Lo que NO hacer en F01
Login social, panel de administración, 2FA, varios tutores por menor, borrado definitivo de cuentas, HTTPS/Caddy
(se resuelve al desplegar) y cualquier llamada a IA (llega en F02).
