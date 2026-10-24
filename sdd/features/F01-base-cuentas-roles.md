# F01 — Base técnica, cuentas y roles

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

Montar el esqueleto del proyecto (frontend Vue + TS, backend Spring Boot, PostgreSQL con pgvector,
todo en Docker Compose) y el sistema de cuentas.

Los usuarios se registran e inician sesión con correo y contraseña. Hay dos roles: alumno y tutor
(adulto). Un menor siempre tiene al menos un tutor asociado; un adulto puede ser alumno y tutor a
la vez. **El adulto es quien da de alta al menor** desde su propia cuenta (nombre de usuario y contraseña
creados por el adulto; el menor no se registra por su cuenta ni necesita correo propio). Al crearlo
queda vinculado automáticamente a ese adulto como tutor. El adulto puede restablecer la contraseña
del menor y dar de baja su cuenta.

Requisitos clave: guardar los mínimos datos personales; límite de intentos de login; contraseñas con
hash seguro; sesión con token; secretos solo por variables de entorno (la app no arranca si faltan);
puertos de BD y backend no expuestos al exterior; diseño responsive desde el principio.

**Docker (requisito central, ver sección 4b de PROJECT.md):** el entregable de F01 incluye Docker
Compose con frontend, backend, PostgreSQL + pgvector y Ollama; perfiles dev/test/prod; healthchecks;
`.env.example`; scripts estándar (up, down, logs, test-backend, test-frontend, seed, reset-dev-db);
tests ejecutables en contenedor; puertos solo en 127.0.0.1; sin `privileged` ni `docker.sock`. Debe
poder levantarse desde cero con un solo comando y comprobar su salud con otro.

Incluye la pantalla vacía de "inicio" tras el login y la comprobación de permisos básica que
reutilizarán las demás features (cada dato pertenece a un usuario).

Fuera de alcance: login social, recuperación de contraseña por correo (valorar si es imprescindible),
panel de administración.
