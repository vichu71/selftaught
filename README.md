# Study Lab (nombre provisional)

Web de ayuda al estudio con IA: dices qué quieres aprender y la IA te monta el curso. Proyecto familiar
con base multiusuario; es un laboratorio, no formación reglada.

> Estado: **F01** (base técnica, cuentas y roles) y **F02** (capa de IA y control de gasto) terminadas. Pendiente del run real del benchmark
> con tus claves (ver `sdd/BACKLOG.md`). El tope de gasto por usuario ya está hecho (ADR-29).

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
