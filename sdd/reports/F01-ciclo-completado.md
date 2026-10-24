## Ciclo SDD completado — US-F01

- Feature: **F01 · Base técnica, cuentas y roles**
- Rama: `feature/F01-base-cuentas-roles`
- Tests: **211/211 pasados** (backend 121 · frontend 71 · entorno 19)
- Cobertura: backend 94,5 % de líneas (82,9 % de ramas) · frontend 97,6 %
- Verificado además con el stack real (5 servicios sanos) y en el navegador integrado (escritorio y móvil 375 px).

### Cambios respecto al plan original
- **JDBC en lugar de JPA** (ADR-13).
- **Recuperación de contraseña por correo, verificación del correo y envío SMTP entraron en F01** (decisión del usuario; estaban "fuera de alcance, valorar").
- **Baja = desactivación + auditoría inmutable**; identificador de acceso del menor generado por el sistema.
- **Cuota de correo por IP** (10 envíos / 15 min), que no estaba especificada.
- **Perfil `dev` sin recarga en caliente** (desviación de `PROJECT.md` §4b, ADR-10).
- **Comprobación de secretos como listener** de `ApplicationPreparedEvent` (ADR-17), no como `EnvironmentPostProcessor`.
- **Vistas sin carga diferida** (ADR-14).
- **HTTPS (Caddy) aplazado** al despliegue, por decisión del usuario.
- **Bug detectado y corregido en la FASE 7:** la cookie `XSRF-TOKEN` se borraba tras la primera petición autenticada (ADR-15).
- Tres ajustes a los tests de la FASE 4 al escribir el código (runners sin healthcheck, `Makefile` con variables, GreenMail con autenticación abierta).
- Dependencias actualizadas en la revisión de seguridad: Spring Boot 3.5.16, BouncyCastle 1.86, Vitest 4 / Vite 7.

### Próximos pasos sugeridos
1. **Antes de desplegar:** `real_ip` y `X-Forwarded-Proto` en nginx, HTTPS con Caddy, probar el SMTP real, límites de memoria
   y comprobar desde otro equipo que los puertos no se ven por la LAN (`sdd/BACKLOG.md`, D1–D6).
2. **Deuda técnica menor:** cuota de correo por destinatario, purga de `login_attempt`, envío asíncrono, caducidad de sesión
   por inactividad, contraseñas comunes (M1–M9).
3. **Siguiente feature:** **F02 — Capa de IA y control de gasto** (depende solo de F01). Incluye el mini benchmark de modelos y
   el control mensual de gasto; al terminarla usar también la skill `llm-integration-review`.
