# Study Lab (nombre provisional)

Web de ayuda al estudio con IA: dices qué quieres aprender y la IA te monta el curso. Proyecto familiar
con base multiusuario; es un laboratorio, no formación reglada.

> Estado: **F01 en curso** (base técnica, cuentas y roles). Los comandos de abajo están disponibles
> cuando termine la FASE 6 de F01; hasta entonces solo existen los tests.

## Stack

- Frontend: Vue 3 + TypeScript (Vite, Pinia, Vue Router), servido por nginx
- Backend: Spring Boot 3 (Java 21), Spring Security, Spring Data JPA, Flyway
- Base de datos: PostgreSQL + pgvector
- Embeddings: Ollama (contenedor; el modelo se instala en F03)
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
| `make seed` | Carga datos de ejemplo en desarrollo |
| `make reset-dev-db` | Borra y recrea la BD de **desarrollo** (pide confirmación) |

El `Makefile` es un envoltorio fino de `docker compose`: si no tienes `make` (p. ej. en Windows),
ejecuta el comando `docker compose` equivalente que aparece en cada objetivo.

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
