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
(se rellenan en la FASE 5 de F01)
