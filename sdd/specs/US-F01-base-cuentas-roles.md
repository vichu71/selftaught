# US-F01 · Base técnica, cuentas y roles

> Historia refinada (FASE 1 de SDD) tras las decisiones del usuario. Los tests citan estos AC.

### Como adulto (alumno y tutor a la vez) o como menor alumno
### Quiero registrarme / iniciar sesión, y que el adulto dé de alta y gestione a sus menores
### Para que cada persona acceda solo a sus datos y el entorno completo arranque con un solo comando en Docker

## Decisiones del usuario (24-10-2026)

- Nombre visible libre y repetible; **identificador de acceso** del menor generado por el sistema
  (único global, con sufijo corto si ya existe).
- Baja = **desactivación** (nunca borrado) + **auditoría** de solo añadir.
- Alta de adultos **siempre con correo verificado** y **recuperación de contraseña por correo**.
- Datos del menor mínimos: nombre visible, identificador de acceso y contraseña (sin fecha de nacimiento).
- Registro de adultos abierto. El adulto puede reactivar a un menor dado de baja.
- HTTPS (Caddy) se resuelve al desplegar, no en F01. Correo real por SMTP vía variables de entorno
  (`SPRING_MAIL_*`, `MAIL_FROM`, `MAIL_ENABLED`); Mailpit en `dev`.

## Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | Un adulto se registra con correo y contraseña (10–128). Hash Argon2id. La cuenta queda `PENDING_VERIFICATION`. |
| AC-1b | Verificación por enlace de un solo uso (24 h) con reenvío. Sin verificar no se puede iniciar sesión. |
| AC-2 | Login con correo y contraseña → token de sesión (cookie HttpOnly, SameSite=Strict). Sin sesión válida, ningún endpoint protegido responde. Logout la revoca. |
| AC-3 | 5 fallos seguidos por cuenta (o 20 por IP) en 15 min bloquean el login (429). El mensaje no revela si la cuenta existe. |
| AC-3b | Recuperación por correo: enlace de un solo uso (1 h), respuesta idéntica exista o no el correo; al cambiarla se cierran todas las sesiones. |
| AC-4 | El adulto da de alta a un menor (nombre visible + contraseña); el sistema genera el identificador de acceso. Sin correo. |
| AC-5 | El menor queda vinculado automáticamente a ese adulto como tutor (uno por menor en v1). |
| AC-6 | El menor inicia sesión con su identificador y contraseña. No existe registro autónomo de menores. |
| AC-7 | El adulto restablece la contraseña de sus menores, los desactiva y los reactiva. Nunca los de otro adulto (404). |
| AC-8 | Un adulto es alumno y tutor con la misma cuenta; un menor solo es alumno. |
| AC-9 | Pantalla de inicio vacía tras el login, responsive. Adulto: lista de sus menores. Menor: aviso de que su tutor ve su actividad. |
| AC-10 | Comprobación de permisos reutilizable: denegado por defecto, tutor solo sobre sus menores, CSRF en peticiones autenticadas. |
| AC-11 | Si falta un secreto obligatorio, el backend no arranca (el error nombra la propiedad, nunca el valor). |
| AC-12 | `docker compose up -d --build` levanta frontend, backend, PostgreSQL + pgvector y Ollama, con healthchecks y `depends_on` sano. |
| AC-13 | Perfiles `dev`, `test`, `prod` y `.env.example` documentado sin secretos reales. |
| AC-14 | Scripts estándar: `up`, `down`, `logs`, `health`, `test-backend`, `test-frontend`, `test-env`, `seed`, `reset-dev-db`. Tests en contenedor. |
| AC-15 | Puertos solo en `127.0.0.1` (en prod solo el frontend), sin `privileged` ni `docker.sock`, procesos no root, volúmenes con nombre. |
| AC-16 | Datos personales mínimos; sin contraseñas, tokens ni datos personales en logs. |
| AC-17 | Auditoría de solo añadir (trigger + rol sin UPDATE/DELETE) de registros, logins, bloqueos, altas, reseteos, bajas y accesos denegados; sin secretos. |
| AC-18 | Envío de correo por SMTP configurado por entorno; en producción el correo no puede desactivarse y falta de SMTP impide arrancar. |

## Contrato HTTP fijado por los tests

- Sesión en la cookie `STUDYLAB_SESSION`; el login también entrega `XSRF-TOKEN` (cabecera `X-XSRF-TOKEN`).
- CSRF obligatorio en peticiones autenticadas que modifican datos; los endpoints públicos de `/api/auth` no lo piden.
- Públicos: `register`, `verify-email`, `resend-verification`, `login`, `forgot-password`, `reset-password`.
- Autenticados: `GET /api/me`, `POST /api/auth/logout`. Solo adultos: `/api/tutor/students…`.
- Errores: 400 validación · 401 sin sesión/credenciales · 403 rol insuficiente o sin CSRF · 404 recurso ajeno o inexistente
  (misma respuesta) · 409 nombre duplicado · 429 bloqueo.
- Enlaces de correo: `{APP_PUBLIC_URL}/verify-email?token=…` y `{APP_PUBLIC_URL}/reset-password?token=…`.
- Tokens (correo y sesión) y identificadores de intento se guardan hasheados.

## Casos borde cubiertos (ver tests)

Correo duplicado, formato y longitudes inválidas, campos nulos, mass assignment (`kind`, `status`, `id`),
token caducado / reutilizado / manipulado / de otro propósito, sesión caducada, doble envío del alta,
nombre reutilizable tras una baja, identificador reservado tras una baja, accesos cruzados entre familias,
menor llamando a endpoints de tutor, ids de adulto usados como alumno, XSS en nombres, arranque sin secretos.

## Fuera de alcance (v1)

Login social, panel de administración y rol admin global, 2FA, un menor con varios tutores, borrado definitivo
de cuentas, pantalla de consulta de la auditoría, HTTPS/Caddy (se hace al desplegar), llamadas a IA (F02).

## Definición de hecho

- Los 18 AC tienen test y pasan, ejecutados en contenedor; cobertura ≥ 80 %.
- Entorno levantable desde cero con un comando y comprobable con otro.
- `README.md`, `ARCHITECTURE.md` y "Comandos útiles" de `CLAUDE.md` actualizados.
- `security-checklist` pasada y documentada en el informe de la FASE 7.
