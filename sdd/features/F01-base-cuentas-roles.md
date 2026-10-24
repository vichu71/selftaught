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

---

## Actualización tras la implementación (FASE 8, 25-10-2026)

Estado: **hecha y probada** (211 tests en verde). Especificación refinada: `sdd/specs/US-F01-base-cuentas-roles.md`;
informe: `sdd/reports/F01-testing-report.md`; pendientes: `sdd/BACKLOG.md`.

**Cambios de alcance decididos con el usuario durante el ciclo**
- La recuperación de contraseña **por correo entró en F01** (y con ella la verificación del correo y el envío SMTP).
- La baja de un menor es una **desactivación reversible** con **auditoría** de solo añadir (no hay borrado).
- El identificador de acceso del menor lo **genera el sistema** (el nombre visible puede repetirse).
- HTTPS (Caddy) se **aplaza al despliegue**; el perfil `dev` no tiene recarga en caliente.

**Casos borde que no estaban en la feature original y aparecieron al implementar**
- La cookie `XSRF-TOKEN` se borraba tras la primera petición autenticada (403 en el navegador). Corregido, con regresión.
- Cuota de correo por IP para que registro, reenvío y recuperación no sirvan para enviar correo masivo.
- Los límites por IP son inútiles (o bloquean a todos) si el proxy no pasa la IP real del cliente: se resuelve al desplegar.
- Doble envío del alta de un menor: se resuelve con el nombre visible único entre los menores activos del tutor.
- Un identificador reservado por un menor dado de baja no se reutiliza.

**Fuera de alcance real (documentado, no hecho)**
Purga de `login_attempt`, límite de correos por destinatario, caducidad de sesión por inactividad, comprobación de
contraseñas comunes, recarga en caliente en `dev`, envío real por Gmail sin probar. Detalle en `sdd/BACKLOG.md`.
