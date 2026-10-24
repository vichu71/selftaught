# BACKLOG — pendientes que no son una feature nueva

> Lo que quedó fuera de F01 a propósito o apareció en la revisión. Origen: `sdd/reports/F01-testing-report.md`.
> Cuando una feature toque la zona, o al desplegar, se revisan estos puntos.

## Antes de desplegar (bloqueantes)

| # | Qué | Por qué | Dónde |
|---|-----|---------|-------|
| D1 | 🟠 Configurar `real_ip` en nginx (`set_real_ip_from` + `real_ip_header X-Forwarded-For`) | Tras Docker Desktop o un proxy de TLS todos los clientes comparten IP: 20 fallos de login bloquearían a todos 15 min | `frontend/nginx.conf` |
| D2 | 🟠 Pasar `X-Forwarded-Proto` del proxy de TLS y añadir HSTS | Si no, la cookie de sesión no lleva `Secure` | `frontend/nginx.conf`, `AuthController` |
| D3 | HTTPS con Caddy en el perfil `prod` | Decidido: se hace al desplegar | `compose.yaml` |
| D4 | Probar el envío real por SMTP (Gmail, puerto 587, contraseña de aplicación) | Solo se probó con GreenMail y Mailpit | `.env` del usuario |
| D5 | Límites de memoria de los contenedores (`mem_limit`) | Argon2 usa ~19 MiB por login concurrente | `compose.yaml` |
| D6 | Comprobar desde otro equipo que `:8080`, `:8081`, `:5432` y `:8025` no se ven por la LAN | No se puede comprobar desde el código | firewall del host |

## Mejoras (🟡)

| # | Qué | Nota |
|---|-----|------|
| M1 | Cuota de correo **por destinatario** (hoy solo por IP) | Rotando IPs se puede inundar de correos a una víctima |
| M2 | Envío de correo asíncrono | Hoy se puede enumerar cuentas por tiempo de respuesta en `forgot-password` / `resend` |
| M3 | Purga programada de `login_attempt`; definir retención de `audit_event` | Se guardan IP indefinidamente |
| M4 | Caducidad de sesión por inactividad y "cerrar todas mis sesiones" | Hoy son 7 días fijos |
| M5 | Rechazar contraseñas comunes | Hoy solo se exige longitud (10–128) |
| M6 | Recarga en caliente en el perfil `dev` | `PROJECT.md` §4b la pide; no se hizo en F01 |
| M7 | Planificar la migración de Spring Boot 3.5 a 4.x | No verificado hasta cuándo recibe parches la rama 3.5 |
| M8 | Instalar `make` o ofrecer scripts `.ps1` equivalentes | El host de desarrollo es Windows sin `make` |
| M9 | Test de arranque del `Makefile` en CI | `make health` y `make down` no se ejecutaron tal cual |

## Decisiones de producto aplazadas (de `PROJECT.md` §11)

Nombre definitivo del producto · benchmark de modelos (F02) · filtrado de contenido para menores (F04/F07) ·
ejercicios con solución comprobable (F06) · borrado definitivo de cuentas por RGPD.

## Efectos de decisiones de F02 en features posteriores

- **F07 (tutor IA):** su ficha dice "si se alcanza el tope, muestra un mensaje". Con el modo `WARN` (decisión del usuario, 25-10-2026) la
  app no corta al llegar al tope; solo en modo `BLOCK` el tutor recibiría `BUDGET_BLOCKED`. Revisar la ficha al llegar a F07.
- **F04–F07:** usar `GenerationLock` solo en generaciones de curso; el chat del tutor no lo toma (ADR-20).

## Para el cierre de F02 (antes de desplegar)

- **Evaluar `cloudflare/security-audit-skill`** (auditoría de seguridad multi-fase con agentes y verificación independiente). Decidido el
  25-10-2026: se deja para el cierre. Antes de instalarla, leer su `SKILL.md` y sus validadores (es una skill de terceros: trae
  instrucciones para el agente y scripts de Node), y ejecutarla una sola vez sobre **todo** el proyecto, no por feature: gasta bastantes
  tokens. La clase de ataque de IA/LLM es la que más aporta a F02. Requiere sandbox sin red para ejecutar código del objetivo; sin él,
  solo deja pistas "por validar".
