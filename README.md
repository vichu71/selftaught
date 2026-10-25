# Study Lab (nombre provisional)

Web de ayuda al estudio con IA: dices qué quieres aprender y la IA te monta el curso. Proyecto familiar
con base multiusuario; es un laboratorio, no formación reglada.

> Estado: **F01** (base técnica, cuentas y roles) y **F02** (capa de IA y control de gasto) terminadas, con el primer benchmark real hecho
> (`sdd/benchmarks/`), el tope de gasto por usuario (ADR-29), **F03** (material propio, ADR-30 a 36), **F04** (asistente de creación de curso, ADR-37 a 41)
> y **F05** (generación del curso, ADR-42 a 48). Pendientes: `sdd/BACKLOG.md`.

## Stack

- Frontend: Vue 3 + TypeScript (Vite, Pinia, Vue Router), servido por nginx
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
- **Resultados:** la pantalla «Resultados» (enlace en el inicio) muestra por curso las piezas hechas, con error y pendientes, las llamadas y el coste, y tu gasto del mes frente a tu tope. Un menor ve sus cursos y piezas, sin importes.
- Todo el contenido lleva el aviso «Generado por IA». Las lecciones se muestran con formato, pero nunca como HTML: enlaces, imágenes y marcado raro salen como texto.
- Coste orientativo: de 0,1 a 0,5 USD por curso con modelos baratos, 10 a 25 minutos de generación. La calidad con un modelo real solo la puedes medir tú con tus claves.

| Variable | Para qué |
|---|---|
| `APP_AI_TASKS_OUTLINE_*` · `_LESSON_*` · `_ASSESSMENT_*` | Modelo de cada paso: temario, lecciones, ejercicios y exámenes (ya existían desde F02) |

Valores de ingeniería sin variable de entorno (`app.generation.*`): 2 hilos, sondeo cada 3 s, máximo 150 piezas por curso, enfriamiento de «Reanudar» de 60 s.

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
