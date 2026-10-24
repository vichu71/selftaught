# CLAUDE.md — Study Lab (nombre provisional)

Web de ayuda al estudio con IA: el usuario dice qué quiere aprender y la IA monta el curso. Proyecto
familiar con base multiusuario. Es un laboratorio, no formación reglada.

## Cómo se trabaja aquí
- El proyecto se construye por features con SDD. Fuente de verdad: `sdd/PROJECT.md` (manda sobre
  cualquier suposición) y `sdd/features/`. Proceso: `sdd/SDD_PROCESS.md`.
- Para arrancar una feature: `/sdd-feature F01` (o F02...F09).
- No se escribe código antes de tener tests (FASE 4 antes que FASE 6). Si algo no está claro, se pregunta.
- Stack: Vue 3 + TypeScript, Spring Boot, PostgreSQL + pgvector, Docker Compose, embeddings con Ollama.

## Reglas que no se discuten
- Todo endpoint comprueba quién puede llamarlo y filtra los datos por propietario.
- Validación en el backend; SQL parametrizado; nada de `v-html` con datos de usuario o de IA.
- Secretos solo en variables de entorno del backend; si falta uno obligatorio, la app falla al arrancar.
- Toda llamada a IA pasa por la capa de proveedor, queda registrada (tokens y coste) y respeta el
  tope mensual y una sola generación concurrente por usuario. Los tests usan un proveedor falso.
- Usuario menor: datos mínimos, filtrado de contenido y adulto tutor que ve toda su actividad.
- Al terminar una feature que toque seguridad o IA: skills `security-checklist` y `llm-integration-review`.

## Docker
Todo se construye, arranca y prueba con Docker Compose (ver `sdd/PROJECT.md` sección 4b). No instales
dependencias en el host. Nunca montes `docker.sock`, uses `privileged` ni leas/escribas `.env`.
`down -v`, borrar volúmenes y `system prune` solo si el usuario lo pide.

## Herramientas
Ver `sdd/TOOLING.md` (skills, hooks y por qué no hace falta un plugin).

## Comandos útiles
Disponibles al terminar la FASE 6 de F01 (el `Makefile` envuelve `docker compose`; sin `make`, usa el comando
equivalente). Detalle en `README.md`.
- `make up` / `make down` / `make logs` / `make health`: levantar, parar, ver logs y estado del entorno.
- `make test-backend` · `make test-frontend` · `make test-env`: tests en contenedor (BD efímera del perfil `test`).
- `make seed` · `make reset-dev-db`: datos de ejemplo y reinicio de la BD de desarrollo (este último borra datos: solo si el usuario lo pide).
- Sin `make` en Windows, ejecutar los tests de backend con Maven en contenedor, p. ej.
  `docker run --rm -v "<ruta>\backend:/app" -v studylab-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn test`.

## Reglas de negocio de F01
- Contrato HTTP y criterios de aceptación: `sdd/specs/US-F01-base-cuentas-roles.md`. Arquitectura y ADR: `ARCHITECTURE.md`.
- Solo el adulto da de alta menores; el menor entra con un identificador generado por el sistema, sin correo.
- Baja de menor = desactivación (nunca borrado). Todo evento relevante va a `audit_event` (solo se añade, sin secretos).
- El adulto se registra con correo verificado y recupera la contraseña por correo. Respuestas genéricas: nunca revelar si una cuenta existe.
- Un menor ajeno o inexistente responde igual (404). Sesión en cookie HttpOnly; nada de tokens en `localStorage`.
- Variables obligatorias en `.env.example`; el `.env` real no se lee ni se escribe desde aquí.

## Lo que NO hacer en F01
Login social, panel de administración, 2FA, varios tutores por menor, borrado definitivo de cuentas, HTTPS/Caddy
(se resuelve al desplegar) y cualquier llamada a IA (llega en F02).
