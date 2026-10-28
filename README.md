# Study Lab (nombre provisional)

Web de ayuda al estudio con IA: dices qué quieres aprender y la IA te monta el curso. Proyecto familiar
con base multiusuario; es un laboratorio, no formación reglada.

> Estado: **F01** (base técnica, cuentas y roles) y **F02** (capa de IA y control de gasto) terminadas, con el primer benchmark real hecho
> (`sdd/benchmarks/`), el tope de gasto por usuario (ADR-29), **F03** (material propio, ADR-30 a 36), **F04** (asistente de creación de curso, ADR-37 a 41),
> **F05** (generación del curso, ADR-42 a 48) y **F06** (estudio, ADR-49 a 54). **F07** (tutor IA, ADR-55 a 58) , **F08** (panel del adulto tutor, ADR-59 a 62) y **F09** (edición de temario y compartir por código, ADR-63 a 66) terminadas; **F13** (borrar cursos, ADR-84 a 86) terminada; **F14** (panel del propietario, ADR-87 a 91) terminada; **F15** (pulido de Resultados y botón «Volver», ADR-92 y 93) terminada. Pendientes: `sdd/BACKLOG.md`.

## Stack

- Frontend: Vue 3 + TypeScript (Vite, Pinia, Vue Router), servido por nginx; diseño propio «Mundo que crece» con tokens CSS, Reka UI (primitivas accesibles), Lucide (iconos) y tipografías autoalojadas
- Backend: Spring Boot 3 (Java 21), Spring Security, Spring JDBC (JdbcClient), Flyway
- Base de datos: PostgreSQL + pgvector
- IA: capa propia con adaptadores para Anthropic, OpenAI y Gemini; proveedor falso en desarrollo y en tests
- Embeddings: Ollama (contenedor `ollama`; `make pull-embedding-model` descarga `nomic-embed-text`)
- Correo: SMTP por variables de entorno (Mailpit en desarrollo)
- Todo en Docker Compose. En tu máquina solo hacen falta **Docker** y **Git**.

## Arrancar en local

1. Crea tu `.env` a partir de la plantilla y rellénalo tú (nunca se sube al repositorio):

   ```bash
   cp .env.example .env
   ```

   Variables obligatorias: `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`, `APP_PUBLIC_URL`.
   Para enviar correo real (`MAIL_ENABLED=true`): `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
   `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` y `MAIL_FROM`. Con Gmail hace falta una
   *contraseña de aplicación*. Si falta una variable obligatoria, el backend no arranca.

2. Levanta el entorno y comprueba que está sano:

   ```bash
   make up        # docker compose --profile dev up -d --build
   make health    # estado de cada servicio
   ```

3. Abre `http://127.0.0.1:8080`. En desarrollo, los correos salen en Mailpit: `http://127.0.0.1:8025`.

Otros comandos:

| Comando | Qué hace |
|---|---|
| `make logs` | Sigue los logs de todos los servicios |
| `make down` | Para el entorno (conserva los datos) |
| `SEED_PASSWORD='tu-clave-demo-1' make seed` | Carga en desarrollo un adulto (`demo@example.com`) y dos menores (Hugo y Lola); la contraseña la eliges tú (≥ 10 caracteres). Es idempotente |
| `make reset-dev-db` | Borra y recrea la BD de **desarrollo** (pide confirmación) |
| `make pull-embedding-model` | Descarga en el contenedor de Ollama el modelo de embeddings (una vez) |
| `make benchmark` | Benchmark de modelos (con claves reales; gasta dinero). Ver "IA y control de gasto" |
| `make benchmark-images` | Benchmark de imágenes (con claves reales; gasta dinero). Ver "Ilustraciones de portada con IA (F12)" |

El `Makefile` es un envoltorio fino de `docker compose`: si no tienes `make` (p. ej. en Windows),
ejecuta el comando `docker compose` equivalente que aparece en cada objetivo.

## IA y control de gasto

Por defecto, en desarrollo **todas las tareas usan el proveedor falso**: no hace falta ninguna clave y no se gasta nada. Para usar
modelos reales se configura, **por tarea**, en tu `.env` (nunca en el código):

| Variable | Para qué |
|---|---|
| `APP_AI_TASKS_<TAREA>_PROVIDER` / `_MODEL` | Proveedor (`anthropic`, `openai`, `gemini`, `ollama`, `fake`) y modelo de cada tarea: `OUTLINE`, `LESSON`, `ASSESSMENT`, `TUTOR`, `EMBEDDING` |
| `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY` | Solo se exige la de los proveedores que alguna tarea use; si falta, el backend no arranca |
| `AI_MONTHLY_LIMIT_USD` | Tope mensual en USD (obligatorio con un proveedor real) |
| `AI_BUDGET_MODE` | `WARN` (por defecto): avisa al 80 % y al 100 % y sigue. `BLOCK`: además rechaza llamadas al alcanzar el tope |
| `AI_USER_LIMIT_MINOR_USD`, `AI_USER_LIMIT_ADULT_USD` | Tope mensual por usuario en USD (2,00 y 10,00 por defecto). Corta siempre, también en `WARN` |
| `OLLAMA_BASE_URL` | Por defecto `http://ollama:11434` (el contenedor) |

- Los precios por modelo están en `backend/src/main/resources/ai-pricing.properties`; un modelo sin precio impide arrancar. Está vacío a
  propósito: se rellena al elegir modelos, con el precio de la página oficial de cada proveedor y la fecha en que lo comprobaste.
- En producción la URL base de un proveedor de pago debe ser `https://`, y el proveedor falso no está permitido.
- Consulta tu gasto del mes con `GET /api/ai/usage` (con sesión). Nada guarda el contenido de prompts ni respuestas.
- **Benchmark** para elegir modelos: `make benchmark` ejecuta los mismos prompts sobre 3 temas y los modelos que indiques, con su
  propio tope de gasto, y deja un informe en `sdd/benchmarks/`. **Gasta dinero real**: solo se ejecuta si lo lanzas tú. Si un modelo falla
  (clave, nombre mal escrito, cuota), se anota en el informe y el run sigue con los demás. Indica los modelos en `APP_AI_BENCHMARK_MODELS`
  (`proveedor/modelo,proveedor/modelo`) y su precio en `ai-pricing.properties` antes de lanzarlo.

## Material propio (F03)

Desde «Mi material» subes tus apuntes (**PDF, Word .docx o texto .txt/.md**) para que los cursos y el tutor se basen en ellos. El material es
**privado**: solo lo ves tú y, si eres menor, tu tutor (en solo lectura). El texto se extrae, se trocea y se indexa con el modelo de embeddings
local (`nomic-embed-text`, sin coste): descárgalo antes con `make pull-embedding-model`. Cada documento pasa por *subido → procesando →
listo* (o *error*, con su motivo) y puedes borrarlo en cualquier momento, con sus fragmentos y vectores.

| Variable | Para qué |
|---|---|
| `MATERIAL_MAX_FILE_MB` | Tamaño máximo de un fichero (10 por defecto). nginx admite hasta 11 MB solo en `/api/material` |
| `MATERIAL_MAX_DOCUMENTS` | Documentos por usuario (20 por defecto); los borrados no cuentan |

- El tipo se decide por el **contenido** del fichero, no por su extensión. No se admiten `.doc` antiguos, macros, escaneados (no hay OCR) ni
  ficheros cifrados. El texto debe estar en UTF-8.
- El fichero original **no se guarda en disco**: vive en la BD solo mientras se procesa. Subir dos veces el mismo contenido se rechaza.
- La búsqueda dentro del material es un servicio interno (`MaterialSearch`) que usarán las features de generación y el tutor; todavía no
  tiene pantalla. Siempre filtra por propietario.
- Si cambias de modelo de embeddings hay que reindexar: los fragmentos antiguos dejan de usarse (sin reindexado automático en v1).

## Crear un curso (F04)

Desde «Tus cursos» → **Nuevo curso**: escribes qué quieres estudiar, dices si tienes información que aportar (documentos de «Mi material»), contestas de 0 a 3 preguntas
sencillas que decide la IA (cada una con su opción por defecto y «Como tú veas») y aceptas el resumen. El curso queda **pendiente de generar**: la generación llega con F05.

| Variable | Para qué |
|---|---|
| `APP_AI_TASKS_INTAKE_PROVIDER` / `_MODEL` | Modelo del asistente (tarea `INTAKE`). Basta uno rápido y barato; en desarrollo, el proveedor falso |
| `COURSE_INTAKES_PER_HOUR` | Consultas al asistente por usuario y hora (30 por defecto) |
| `COURSE_MAX_PENDING` | Cursos pendientes de generar por usuario (5 por defecto) |

- **Menores:** un tema inapropiado no se crea; el menor ve «Contenido inapropiado. Consulta con tu adulto.» y el intento queda guardado para su tutor (su pantalla llega con F08).
  El filtro tiene dos capas (lista de términos local y valoración del modelo) y no se aplica a los adultos. La lista está en `backend/src/main/resources/safety/blocked-terms.txt`.
- El asistente solo envía al modelo el **tema**: nunca el texto de tus documentos.
- La edad del menor no se guarda: el nivel de partida se pregunta dentro del asistente.

## Generación del curso (F05)

Al aceptar el resumen, la IA prepara el curso en segundo plano: primero el **temario** (temas y capítulos, visible enseguida) y después, pieza a pieza, la **lección** de cada capítulo, sus
**ejercicios** y el **examen** del tema si lo pediste. «Tus cursos» muestra el progreso («12 de 43») y cada pieza se puede leer en cuanto está lista. Una generación a la vez por usuario.

- **Con tu material:** las lecciones se apoyan en fragmentos de los documentos que elegiste y citan «Fuente n» con la página; lo que el modelo añade por su cuenta va marcado como «Complemento».
- **Pausa y «Reanudar»:** si llegas a tu tope de gasto mensual o el proveedor falla, el curso se **pausa** con el motivo y no gasta más. Pulsa **Reanudar** (una vez por minuto como máximo) y sigue
  solo por lo que falta: lo ya generado no se vuelve a pedir ni a pagar. Si una pieza no sale bien dos veces, el curso termina en pausa y «Reanudar» reintenta solo esas.
- **Menores:** el temario valora de nuevo si el tema es apto para escolares, el material y lo generado pasan por el filtro local y, si algo no es apropiado, se ve «Contenido inapropiado. Consulta con tu adulto.».
- **Exportar:** `GET /api/courses/{id}/export` devuelve el curso en JSON (sin nombres de documento ni datos personales).
- **Resultados:** la pantalla «Resultados» (enlace en el inicio) te cuenta de un vistazo cuántas veces te ayudó la IA este mes, cuánto llevas gastado y cuántos cursos tienes listos. Un **medidor** compara tu gasto con tu tope y lo dice con palabras («Vas bien», «Cerca del tope», «Tope alcanzado»); un reparto por tarea (lecciones, tutor, ejercicios…) enseña en qué se fue; si eres adulto con menores, ves a cada uno con su medidor; y cada curso es una **tarjeta con su planta**, su barra de piezas hechas, el estado y los errores o pendientes escritos. Un menor ve sus cursos y las veces que la IA le ayudó, **sin importes ni topes**. El enlace «Volver al inicio» es ahora un botón con flecha (en «Nuevo curso» dice «Cancelar»).
- Todo el contenido lleva el aviso «Generado por IA». Las lecciones se muestran con formato, pero nunca como HTML: enlaces, imágenes y marcado raro salen como texto.
- Coste orientativo: de 0,1 a 0,5 USD por curso con modelos baratos, 10 a 25 minutos de generación. La calidad con un modelo real solo la puedes medir tú con tus claves.

| Variable | Para qué |
|---|---|
| `APP_AI_TASKS_OUTLINE_*` · `_LESSON_*` · `_ASSESSMENT_*` | Modelo de cada paso: temario, lecciones, ejercicios y exámenes (ya existían desde F02) |

Valores de ingeniería sin variable de entorno (`app.generation.*`): 2 hilos, sondeo cada 3 s, máximo 150 piezas por curso, enfriamiento de «Reanudar» de 60 s.

## Estudiar un curso (F06)

Desde «Tus cursos» entras en un curso: el temario muestra tu avance, qué lecciones tienes hechas y la mejor nota de cada examen. Mientras el curso se genera puedes estudiar lo que ya está listo.

- **Lección:** se lee con formato y la marcas con **«Marcar como hecha»** (se puede desmarcar); hay enlaces a la anterior y a la siguiente. El porcentaje del curso son las lecciones hechas más los exámenes superados.
- **Ejercicios:** escribes tu respuesta y, al enviarla, ves la **solución y la explicación** (antes no salen del servidor). Marcas cómo te fue («Lo he acertado», «Casi», «No») y, si quieres, pulsas **«Corregir con IA»**: te da un
  veredicto y un comentario, con el aviso de que puede equivocarse. Puedes repetir el ejercicio las veces que quieras.
- **Examen por tema:** lo empiezas, eliges una opción por pregunta y lo entregas; el servidor lo corrige sin IA y te enseña la nota, tu respuesta, la correcta y la explicación. Con el **50 %** o más el tema cuenta como superado.
  Se puede repetir sin límite (cuenta la mejor nota) y el historial te dice qué fallaste.
- **Límites por usuario:** 120 envíos por hora entre ejercicios y exámenes, 20 correcciones de IA al día (y una a la vez) y los 200 últimos intentos de cada pieza. «Corregir con IA» usa el modelo de `ASSESSMENT` (el mismo de los
  ejercicios) y cuenta en tu tope mensual; no hay variables nuevas.
- **Menores:** su respuesta pasa por el filtro de contenido antes de enviarse a la IA (si coincide, se ve «Contenido inapropiado. Consulta con tu adulto.» y se puede autocorregir); su tutor verá los intentos en su panel (F08).
- Lo que escribes (respuestas, intentos) no se guarda en logs ni en la auditoría.

## Tutor IA por lección (F07)

Debajo de cada lección ya generada hay un chat para preguntar tus dudas. El tutor conoce la lección, su capítulo y el resumen de tu curso; si el curso tiene **material propio**, busca fragmentos relevantes y los cita como «Fuente n (página)»;
y si fallaste ejercicios de ese capítulo, sabe cuáles (solo el enunciado y que los fallaste, **nunca** tu respuesta ni las soluciones). Explica, da pistas y ejemplos, **no resuelve exámenes ni ejercicios por ti** y, si la pregunta no es del curso, te redirige.

- **Conversación:** se guarda por lección (hasta 400 mensajes). Un adulto puede **vaciarla**; la de un menor no se borra porque su adulto tutor puede verla (su panel llega con F08) y el menor lo ve avisado en el chat.
- **Límites por usuario:** 30 mensajes por hora y 150 al día (cuentan también los bloqueados y los fallidos) y una respuesta a la vez. Usa la tarea `TUTOR` (`APP_AI_TASKS_TUTOR_*`: un modelo medio o barato) y cuenta en tu tope mensual y en «Resultados».
  Si se alcanza el tope o el proveedor falla, ves un mensaje claro en el chat y tu pregunta sigue en la caja.
- **Menores:** su pregunta, los fragmentos del material y la respuesta del tutor pasan por el filtro de contenido; si salta, ven «Contenido inapropiado. Consulta con tu adulto.» (o un texto fijo) y queda en el hilo para su tutor.
- Lo que se dice en el chat no se guarda en logs ni en la auditoría. Sin variables nuevas: los límites son valores de ingeniería (`app.tutor.*`).

## Panel del adulto tutor (F08)

En «Tus alumnos», cada menor (también uno dado de baja) tiene un enlace **Ver actividad** que abre su panel, **solo de lectura**: nada de lo que ves se puede cambiar desde ahí.

- **Avisos:** lo que el filtro de menores ha parado, lo más reciente primero: preguntas bloqueadas al tutor, respuestas del tutor sustituidas, temas de curso rechazados, respuestas de ejercicio que no se enviaron a corregir y piezas no generadas por contenido bloqueado.
  Sin correos: se ven al entrar.
- **Cursos:** estado y avance de cada curso del alumno y, dentro, el avance por tema, las notas de examen y sus intentos: lo que respondió en cada ejercicio (con su autocorrección y el veredicto de la IA) y las preguntas de examen que falló.
  No se abre el texto de las lecciones, ejercicios ni exámenes generados.
- **Conversaciones:** los hilos del alumno con el tutor IA, incluidas las preguntas bloqueadas y las respuestas filtradas, marcadas como tales.
- **Consumo de IA:** lo que ha gastado el alumno este mes frente a su tope personal y cómo se reparte por tarea (sin tokens).
- **Transparencia:** cada vez que consultas intentos, conversaciones o avisos de un alumno queda anotado en la auditoría (quién, a quién y qué pantalla, nunca el contenido). El menor ve en su pantalla principal que su adulto tutor puede ver sus cursos, respuestas, intentos y conversaciones.
- Solo ves a tus alumnos; cualquier otro responde como si no existiera. Sin variables nuevas ni llamadas a la IA.

## Editar y compartir un curso (F09)

En la página de un curso, **Editar curso** abre su editor. Puedes **renombrar, reordenar (con los botones de subir y bajar), quitar y añadir** temas y capítulos, **editar el texto de una lección** (con vista previa) y **regenerar** una pieza con IA.

- **Lo nuevo lo escribe la IA:** al añadir un tema o un capítulo, el curso vuelve a la cola y se genera **solo lo nuevo**, con las mismas reglas de gasto de siempre. Añadir y regenerar cuentan para un límite de 20 por hora y para tu tope mensual. Mientras el curso se prepara no se puede editar.
- **Quitar o regenerar borra** el progreso, los intentos y la conversación con el tutor de esas piezas (te lo avisa y pide confirmación). Si editas una lección, sus ejercicios y el examen **se quedan como estaban** y salen marcados «la lección cambió»; tú decides si regenerarlos.
- **Versión:** cada edición guardada sube el número de versión del curso.
- **Compartir:** en la misma pantalla creas un **código** (caduca a los 7 días, o entre 1 y 90; hasta 5 activos por curso; vale para varias personas). Se te enseña **una sola vez** (solo se guarda un resumen cifrado) y puedes **revocarlo** cuando quieras. Solo se comparten cursos terminados.
- **Tengo un código:** quien lo introduce (adulto o menor) recibe **su propia copia**, que puede editar sin afectar al original. La copia **no lleva** tu material subido, ni las fuentes de las lecciones, ni tu progreso, intentos, conversaciones ni ningún dato tuyo. Un código que no vale (mal escrito, caducado o revocado) responde siempre igual,
  y hay un límite de intentos por usuario.
- **Menores:** lo que escriben al editar y toda la copia que canjean pasan por el filtro de contenido; si salta, no se guarda ni se crea nada y su adulto tutor lo ve en los avisos de su panel.

## Borrar cursos (F13)

En **Tus cursos** (inicio), **Seleccionar** deja marcar varios cursos y **Borrar (n)** los borra de una vez; también hay **Borrar curso** en la página de cada curso. Antes de borrar se abre un aviso que lista los cursos, dice qué se pierde y qué se conserva, y enlaza a **exportar** cada curso por si lo quieres guardar.

- **Es definitivo** (no hay papelera): se van las lecciones, ejercicios, intentos, conversaciones con el tutor, ilustraciones y códigos de compartir del curso. **Se conserva** tu gasto de IA, tu material subido y las copias que otras personas ya hicieron de tu curso.
- **No se borra un curso que se está preparando** (en cola o generándose): espera a que termine. Si seleccionas varios y alguno está ocupado, no se borra ninguno.
- **Solo los adultos borran.** Los menores no ven la opción (su adulto tutor ve su actividad) y el tutor no puede borrar los cursos de su menor.

## Panel del propietario (F14)

Una pantalla de administración, **`/propietario`**, con las estadísticas de la aplicación y algunas acciones sobre las cuentas. Solo la ve el **propietario**: la cuenta de adulto con el correo verificado que coincide con la variable `OWNER_EMAIL` del backend. Sin la variable no hay propietario ni panel (la app funciona igual), y el propietario nunca se elige desde la web ni se guarda en la base de datos: para cambiarlo se cambia la variable y se reinicia el backend. Cualquier otra persona recibe «no encontrado».

- **Gasto de IA:** hoy, mes y rango (7, 30 o 90 días), proyección a fin de mes, % del tope global, coste por día, por modelo, proveedor, tarea y usuario, errores, bloqueos, duración media y p95, coste por curso y por usuario activo, y la tabla de **precios vigentes** con su fecha de verificación (`verified-on` en `ai-pricing.properties`; sin ella, «sin fecha»). Es el coste real pagado a los proveedores; el benchmark sale aparte.
- **Usuarios y conexiones:** altas por día, adultos y menores, cuentas por estado, activos en 1, 7 y 30 días (con al menos un inicio de sesión), sesiones abiertas, inicios de sesión correctos, fallidos y bloqueados. No se guarda IP útil ni geolocalización, así que no hay mapas.
- **Uso y salud:** cursos por estado y por día, piezas, lecciones, ejercicios, exámenes, preguntas al tutor, material, ilustraciones, códigos de compartir y cursos borrados; colas, fallos, errores de IA de las últimas 24 horas, bloqueos de contenido y tamaño de la base de datos.
- **Acciones:** **desactivar** o **reactivar** una cuenta, **revocar sus sesiones** y fijar su **tope mensual de IA** (0 a 500 USD) o volver al de por defecto. Cada acción pide confirmación y queda en la auditoría con ids. El propietario no puede desactivarse ni revocarse a sí mismo.
- **Privacidad:** el panel **nunca** enseña contenido (cursos, temas, lecciones, respuestas, conversaciones, documentos). De un **menor** solo muestra su identificador de acceso, estado, fechas, gasto, tope y su tutor; puede desactivarlo, pero no ver lo que escribe. Los adultos aparecen con su nombre y correo.
- **Límites:** 120 consultas y 60 acciones por hora; nada se refresca solo (botón «Actualizar»). Las gráficas son SVG propios, cada una con su tabla debajo.
- **Activarlo:** pon tu correo en `OWNER_EMAIL` (en tu `.env`, que no se versiona), regístrate y verifica ese correo, y reinicia el backend. Antes de abrir la aplicación a Internet hacen falta HTTPS y la cookie `Secure` (`sdd/BACKLOG.md`, D1–D6 y F14-1): el panel es lo más sensible de la app.

## Ilustraciones de portada con IA (F12)

Al crear un curso, un adulto puede activar **«Con ilustraciones»** (apagado por defecto): cada **tema** recibe una ilustración de portada generada por IA, que sale en su tarjeta del curso y en la cabecera de sus lecciones con el rótulo «Ilustración generada por IA» y su texto alternativo. Es para **ambientar, no para enseñar hechos** (las imágenes por IA inventan detalles): contrástalo siempre con tu material.

- **Coste:** el asistente enseña el precio por imagen y el máximo posible (precio × máximo de temas) antes de aceptar; es una estimación y cuenta para tu tope mensual de IA. Con calidad baja o media suele ser de céntimos por curso.
- **Segundo plano:** el curso se puede estudiar sin esperar; las imágenes llegan después, una a una. Si una falla o el proveedor la rechaza, se ve la planta de siempre y no pasa nada.
- **Editar:** en la edición del curso, por tema, puedes **quitar** la ilustración o **volver a generarla** (cuenta para tu gasto y tiene límites).
- **Compartir:** la copia por código lleva las ilustraciones ya hechas, pero no genera nuevas ni cobra a quien canjea. Los **menores no** generan ni reciben ilustraciones.
- **Activarlo:** hace falta un proveedor de imagen de pago: define `APP_AI_TASKS_IMAGE_PROVIDER` y `APP_AI_TASKS_IMAGE_MODEL` (OpenAI o Google) con su clave y su precio por imagen en `ai-pricing.properties`. Sin ello, las ilustraciones quedan desactivadas y la app funciona igual.
- **Elegir modelo:** `make benchmark-images` pide una imagen por tema a los modelos de `APP_AI_BENCHMARK_IMAGE_MODELS` y deja en `sdd/benchmarks/` un informe y una página para compararlas. **Gasta dinero real** (tope `APP_AI_BENCHMARK_IMAGE_BUDGET_USD`): solo lo lanzas tú.

## La interfaz «Mundo que crece» (F11)

Es la interfaz definitiva (sustituye a «Mapa», de F10, que queda en el historial de Git y en `sdd/design/F10/`). Cada curso del Inicio es una **planta** que crece con tu avance (semilla, brote, árbol, árbol con frutos; la fase siempre se dice también con texto), y el curso es un **camino por un paisaje**: las lecciones son piedras, los exámenes de tema son puertas, los temas son colinas y un personaje marca «tú estás aquí». Mismas funciones y mismas reglas; cambian los tokens (`frontend/src/styles/tokens.css`), las ilustraciones (`frontend/src/world/`) y el trazo del camino (`frontend/src/map/roadPath.ts`). En la barra de navegación hay un botón **«Dejarlo por hoy»**: te lleva al Inicio (tus plantas y tu progreso) y deja cerrado todo lo que tenías abierto (el panel del tutor, diálogos y formularios), sin cerrar la sesión. En las lecciones, el tutor es un botón flotante «Pregunta al tutor» que abre un panel (no hace falta ir al final). Los prototipos de referencia están en `sdd/design/F10/` y el contrato en `sdd/specs/US-F11-piel-mundo-que-crece.md`. Accesibilidad: contraste AA comprobado por un test sobre los colores (claro y noche), foco visible, objetivos táctiles de 44 px y sin animaciones si el sistema pide menos movimiento; las fuentes viajan dentro de la propia aplicación.

## Tests

Todos se ejecutan en contenedores, contra la BD efímera del perfil `test`:

```bash
make test-backend    # JUnit + Spring Boot Test (SMTP en memoria, sin correo real)
make test-frontend   # Vitest
make test-env        # política del entorno: puertos, privilegios, secretos, healthchecks
```

Los tests nunca llaman a APIs de pago ni envían correo real.

## Más información

- Especificación: `sdd/PROJECT.md`, `sdd/features/` y `sdd/specs/`
- Arquitectura y decisiones: `ARCHITECTURE.md`
- Proceso de trabajo: `sdd/SDD_PROCESS.md`
