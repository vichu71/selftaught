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

## Anotado en F03 (material propio)

| # | Qué | Nota |
|---|-----|------|
| F3-1 | 🟠 Filtrado de contenido para menores **antes** de enviar texto de su material a un modelo de pago | Condición de ADR-34; la capa local ya existe (`ContentGuard`, F04). **F05 lo aplica a los fragmentos (ADR-46), pendiente de verificar en su FASE 7**; falta F07 (tutor) |
| F3-2 | Reindexado cuando cambie el modelo de embeddings | Hoy los fragmentos antiguos quedan sin uso |
| F3-3 | OCR para documentos escaneados, y más formatos (enlaces, `.doc`) | Fuera de alcance v1 |
| F3-4 | Actualizar PDFBox a la última 3.0.x (la 3.0.7 corrige CVE-2026-23907, que no afecta a este código) y revisar sus avisos al actualizar | Única librería nueva de F03 |
| F3-6 | 🟠 Límite de frecuencia por usuario en la subida (A2): el ciclo subir-borrar mantiene ocupado al hilo de procesado | Antes de desplegar |
| F3-7 | Un PDF patológico puede seguir consumiendo CPU y memoria tras agotar el tiempo máximo (PDFBox no siempre atiende la interrupción); sin `mem_limit` (D5) | Aislar la extracción en un proceso si se publica |
| F3-5 | Índice vectorial (HNSW) si un usuario supera ≈ 100 000 fragmentos | ADR-33 |

## Anotado en F04 (asistente de creación de curso)

| # | Qué | Nota |
|---|-----|------|
| F4-1 | Retención de `course_intake`: guardan el texto del tema (también los bloqueados, para el tutor). Fijar cuándo se purgan las consultas caducadas | ADR-37 |
| F4-2 | Desbloqueo de un tema concreto por el tutor, y revisión periódica de `blocked-terms.txt` | Hoy un tema escolar legítimo bloqueado solo remite al adulto (ADR-39) |
| F4-3 | El filtro local solo cubre español; un menor puede pedir lo mismo en otro idioma (lo frena la valoración del modelo) | ADR-39 |
| F4-4 | Pantalla del tutor con los intentos bloqueados de sus menores | Llega con F08; el dato ya se guarda |
| F4-5 | 🟠 Medir con un modelo real barato cuántas veces el JSON del asistente llega mal formado y ajustar la plantilla. Si un menor recibe JSON inservible, el tema se acepta sin la valoración del modelo: F05 debe volver a comprobarlo antes de generar | La parte de F05 queda cubierta por la valoración `appropriate` del temario (ADR-47). Medir el JSON con un modelo real lo hace el usuario (APP_AI_TASKS_INTAKE_*) |
| F4-6 | 🟡 Anotado en F05: el tutor aún no puede subir el tope de un menor (F4-2) y un curso de un menor con modelos caros puede quedarse en pausa por `BUDGET` | ADR-44 |

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

## Pendientes de F02 (de la revisión de la FASE 7)

| # | Qué | Prioridad |
|---|-----|-----------|
| A1 | ✅ **Hecho (25-10-2026, ADR-29):** tope de gasto por usuario (menor 2 USD, adulto 10 USD al mes), corta siempre. Pendiente menor: que el tutor pueda ajustar el tope de cada menor desde la app | ✅ |
| A2 | Límite de **frecuencia** por usuario: ✅ hecho para el asistente de F04 (ADR-41) y para «Reanudar» de F05 (60 s por curso, ADR-44); falta en la subida de material (F3-6) y en los endpoints de F06–F07 | 🟡 F06–F07 |
| A3 | ✅ **Hecho en F04 (ADR-39):** `safety.ContentGuard` es el punto de enganche reutilizable. Falta usarlo en F05 (lecciones) y F07 (tutor) antes de enviar texto de un menor a un modelo de pago | ✅ |
| A4 | Usar `GenerationLock` en F05 llamando a `Lease.renew()` entre llamadas de IA; validar la salida del modelo contra un esquema antes de guardarla | ✅ diseñado en F05 (ADR-42 y 43); se marca hecho al cerrar su FASE 7 |
| A5 | Timeout **por tarea** (hoy es el mayor de las tareas de cada proveedor) | 🟡 |
| A6 | Que solo los adultos con rol "global" vean el porcentaje del tope global, si la app se abre a más de una familia | 🟡 |
| A7 | Quitar las credenciales de propietario de la BD del contenedor `benchmark` (que no migre al arrancar) | 🟡 |
| A8 | **Parcial (25-10-2026):** run real hecho (27 llamadas, 0,208 USD): adaptadores de Anthropic, OpenAI y Gemini (incl. `x-goog-api-key`) confirmados; límite de `ASSESSMENT` subido a 10 000 (Haiku se truncó a 6 000); valoración en `sdd/benchmarks/valoracion.md`. **Falta:** contrastar el gasto con los paneles, confirmar el precio de OpenAI, valoración manual del usuario, pedir "sin LaTeX" en las plantillas y salida estructurada en F05 | al cerrar F02 |
| A9 | Actualizar `greenmail` (solo tests) y revisar versiones de dependencias | 🟡 |
