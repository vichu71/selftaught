## Testing Report — F01 · Base técnica, cuentas y roles — 24-10-2026

Rama `feature/F01-base-cuentas-roles`. Todo se ejecutó con Docker (sin instalar nada en el host).
Las pruebas con el stack real usaron un fichero de variables **ficticio**; el `.env` del usuario no se leyó ni se escribió.

### Resumen
- Tests totales: **211** (backend 121 · frontend 71 · entorno 19)
- Tests pasados: **211** ✅
- Tests fallidos: **0** ❌
- Cobertura: backend **94,5 %** de líneas y 82,9 % de ramas (JaCoCo) · frontend **97,6 %** (V8). Objetivo ≥ 80 % cumplido.
- Prueba de humo con el stack real (nginx → backend → PostgreSQL → Mailpit): flujo completo OK.
- Interfaz comprobada en el navegador integrado (escritorio y móvil 375 px).

### Resultados por criterio de aceptación

| AC | Descripción | Tests principales | Estado |
|----|-------------|-------------------|--------|
| AC-1 | Registro de adulto, Argon2id, cuenta pendiente | `RegistrationAndVerificationTest`, `RegisterView.spec` | ✅ |
| AC-1b | Verificación por enlace (24 h, un solo uso) y reenvío | `RegistrationAndVerificationTest`, `VerifyEmailView.spec` | ✅ |
| AC-2 | Login, cookie HttpOnly/SameSite, logout | `LoginAndSessionTest`, `CsrfCookieFlowTest`, `LoginView.spec` | ✅ |
| AC-3 | Bloqueo tras 5 fallos (cuenta) / 20 (IP) | `LoginLockoutTest` | ✅ |
| AC-3b | Recuperación de contraseña por correo | `PasswordRecoveryTest`, `PasswordRecoveryViews.spec` | ✅ |
| AC-4 | Alta de menor con identificador generado | `StudentManagementTest`, `StudentsPanel.spec` | ✅ |
| AC-5 | Vínculo automático con el tutor | `StudentManagementTest`, `SchemaTest` | ✅ |
| AC-6 | Login del menor | `LoginAndSessionTest`, `LoginView.spec` | ✅ |
| AC-7 | Reset, baja y reactivación (nunca de menores ajenos) | `StudentManagementTest` | ✅ |
| AC-8 | Roles derivados del tipo de cuenta | `MeEndpointTest`, `auth.spec` | ✅ |
| AC-9 | Inicio vacío, responsive, aviso al menor | `HomeView.spec`, navegador integrado | ✅ |
| AC-10 | Permisos comunes, denegado por defecto, CSRF | `AuthorizationMatrixTest`, `CsrfCookieFlowTest`, `router.spec` | ✅ |
| AC-11 | Sin secreto obligatorio la app no arranca | `StartupSecretsTest`, `environment.test` | ✅ |
| AC-12 | Un comando levanta el entorno, healthchecks | stack real: 5/5 servicios `healthy`; `SchemaTest` | ✅ |
| AC-13 | Perfiles dev/test/prod y `.env.example` | `environment.test` | ✅ |
| AC-14 | Scripts estándar, tests en contenedor | `environment.test`; `seed.sh` probado | ✅ (ver "No verificado") |
| AC-15 | Puertos en 127.0.0.1, sin privileged/docker.sock, no root | `environment.test`; `docker compose ps` | ✅ |
| AC-16 | Datos mínimos, sin secretos en logs | `MeEndpointTest`, `AuditTrailTest` | ✅ |
| AC-17 | Auditoría de solo añadir sin secretos | `AuditTrailTest`, `SchemaTest` | ✅ |
| AC-18 | SMTP por entorno; en prod no se desactiva | `StartupSecretsTest` | ✅ (envío real: ver abajo) |

### Casos borde verificados

| Caso borde | Test | Estado |
|------------|------|--------|
| Correo duplicado (misma respuesta, sin segunda cuenta) | `edge_duplicateEmailLooksIdentical…` | ✅ |
| Campos nulos, longitudes y formatos inválidos | `invalidRegistrations`, `edge_invalidStudentFields…` | ✅ |
| Mass assignment (`kind`, `status`, `id`) | `edge_registerIgnoresPrivilegedFields`, `edge_createIgnoresPrivilegedFields` | ✅ |
| Token caducado, reutilizado, manipulado o de otro propósito | `RegistrationAndVerificationTest`, `PasswordRecoveryTest` | ✅ |
| Sesión caducada / manipulada / revocada | `LoginAndSessionTest` | ✅ |
| Doble envío del alta de menor | `edge_duplicateDisplayName…`, `StudentsPanel.spec` | ✅ |
| Identificador reservado tras una baja | `ac7_deactivatedMinorsLoginIdStaysReserved` | ✅ |
| Menor ajeno / inexistente / id de adulto (404 idéntico) | `ac7_anotherAdultCannot…`, `ac7_anAdultIdIsNever…` | ✅ |
| Menor llamando a endpoints de tutor | `ac10_aMinorCannotUseTutorEndpoints` | ✅ |
| XSS en nombres | `StudentsPanel.spec`, `security.spec` | ✅ |
| Arranque sin secretos / SMTP incompleto / prod sin correo | `StartupSecretsTest` | ✅ |
| Cookie XSRF-TOKEN que desaparecía tras la primera petición | `CsrfCookieFlowTest` | ✅ (ver Issues) |

### Revisión de seguridad (skill `security-checklist`)

**Mapa de endpoints:** 6 públicos en `/api/auth` (CSRF exento), `/actuator/health` (solo `status`), `/api/me` y
`/api/auth/logout` (sesión), y 5 en `/api/tutor/students…` (rol adulto **y** comprobación de tutela con `AccessGuard`).
Todo lo demás está denegado por defecto. No hay datos compartidos entre grupos, endpoints de admin ni de sync en F01.

**Lo que ya está bien:**
- Autorización: denegado por defecto, IDOR cubierto (404 idéntico para ajeno e inexistente), roles derivados del tipo de cuenta.
- Contraseñas con Argon2id (≥ 10 caracteres), sesiones opacas hasheadas y revocables, CSRF con cookie real, respuestas genéricas anti-enumeración.
- SQL siempre parametrizado (las únicas concatenaciones son constantes: `UserRepository.java:39`, `:44`); sin `v-html`; sin tokens en el almacenamiento del navegador.
- Secretos solo por entorno (Compose los exige con `${VAR:?}`), `.env` ignorado, ningún secreto versionado, rol de BD sin DDL y auditoría inmutable (trigger + permisos).
- Despliegue: verificado en vivo que los puertos escuchan en `127.0.0.1` (Ollama ni se publica); errores sin trazas; cabeceras CSP, nosniff, DENY y Referrer-Policy; `Server: nginx` sin versión (corregido).
- Dependencias de producción: `npm audit --omit=dev` = 0.

**Corregido durante esta revisión:** Spring Boot 3.5.6 → 3.5.16 y BouncyCastle 1.80 → 1.86; `server_tokens off`;
Vitest 4.1.12 / Vite 7.3.7 (`npm audit`: 7 avisos de desarrollo → **0**; nunca llegaron a la imagen final).

**Problemas pendientes por gravedad:**
- 🟠 **Los límites por IP pueden convertirse en un corte para todos.** Verificado: detrás de nginx la IP registrada es `172.21.0.1` (la del proxy) para todos los clientes. Con 20 fallos desde "esa IP", el login se bloquea para todos durante 15 min. Con Caddy ocurrirá lo mismo. `frontend/nginx.conf:21` (`X-Forwarded-For $remote_addr`). *Arreglo al desplegar:* `set_real_ip_from` + `real_ip_header` en nginx.
- 🟠 **La cookie de sesión no llevará `Secure` detrás de HTTPS.** `nginx.conf:22` sobrescribe `X-Forwarded-Proto` con `$scheme` (http), y `AuthController` solo marca `Secure` si la petición es segura. *Arreglo al desplegar:* pasar el protocolo real del proxy de TLS; añadir HSTS. (HTTPS está aplazado por decisión del usuario.)
- 🟡 **Bombardeo de correos a una víctima:** la cuota (10 envíos/15 min) es por IP, no por destinatario; rotando IPs se pueden enviar muchos "restablece tu contraseña". *Propuesta:* enfriamiento por destinatario.
- 🟡 **Enumeración por tiempo en `forgot-password`/`resend`:** si la cuenta existe se envía un SMTP (más lento) y si no, no. *Propuesta:* envío asíncrono.
- 🟡 **Bloqueo por cuenta como arma:** quien conozca un correo puede dejar esa cuenta 15 min sin entrar con 5 intentos. Es el compromiso habitual; se mitiga con el bloqueo temporal.
- 🟡 **Retención:** `login_attempt` (con IP) no se purga y la auditoría guarda IP indefinidamente. *Propuesta:* purga programada de `login_attempt`; definir retención de auditoría.
- 🟡 **Argon2 usa ~19 MiB por login concurrente** y los contenedores no tienen límite de memoria. *Propuesta:* `mem_limit` al desplegar.
- 🟡 La IP es falseable con `X-Forwarded-For` en el puerto de depuración `:8081` (verificado; solo `127.0.0.1` y solo en `dev`). Sin caducidad por inactividad (7 días fijos) y sin comprobación de contraseñas comunes.
- 🟡 Spring Boot 3.5 es una rama antigua: hay una 4.x en publicación (4.2.0-RC1). No he verificado hasta cuándo recibe parches; conviene planificar la migración.

**No puedo comprobarlo desde el código:** que el host no exponga los puertos en la LAN y el firewall. Para comprobarlo,
desde otro equipo de la red: `curl -m 5 http://<IP-del-PC>:8080/` y `:8081`, `:5432`, `:8025` (los tres últimos deben fallar).
Tampoco he probado el envío real por Gmail (ver abajo).

### Issues encontrados
1. **Bug real, corregido:** la cookie `XSRF-TOKEN` se borraba tras la primera petición autenticada, así que en el navegador
   cualquier acción de escritura daba 403. Los tests de MockMvc no lo detectaron (su `csrf()` se salta la cookie y además
   cambia el repositorio CSRF del contexto compartido). Lo encontró la prueba de humo con el stack real. Causa y arreglo en
   ADR-15; verificado después en el navegador integrado (recarga → `GET /api/me` → `POST …/reactivate` = 204).
2. `make seed` llamaba a un script inexistente: creado (`scripts/seed.sh`) y probado dos veces (idempotente) y sin contraseña (rechaza).
3. Tres ajustes a mis propios tests durante la FASE 6 (runners sin healthcheck, Makefile con variables, GreenMail con autenticación abierta) y uno
   en esta fase (el test de regresión CSRF va en su propia clase con contexto aislado).
4. `npm install` falla con npm 10.9 al resolver Vitest 4 (error interno de npm); el lockfile se regeneró con npm 11 y el Dockerfile sigue usando `npm ci`, que funciona.

### No verificado
- **Envío real por SMTP (Gmail):** solo se probó con GreenMail y Mailpit. Falta probarlo con credenciales reales (contraseña de aplicación).
- **HTTPS / Caddy y el despliegue en producción:** aplazado por decisión del usuario; ver los dos 🟠 de arriba.
- **`make`:** no está instalado en este Windows. Se ejecutaron los comandos `docker compose` equivalentes y `scripts/seed.sh`/`run-env-tests.sh` con `sh`.
  `make health`, `make down` y `make reset-dev-db` no se ejecutaron tal cual (el último es destructivo y pide confirmación).
- **Recarga en caliente en `dev`:** no implementada en F01 (el frontend de dev es el mismo nginx de producción).
- **Dispositivo móvil real:** se comprobó con emulación a 375 px (sin scroll horizontal, controles ≥ 44 px), no en un teléfono.
- **Modelo de Ollama:** solo se comprobó que el servicio arranca sano; no hay modelo hasta F03.
