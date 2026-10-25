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
| F3-1 | 🟠 Filtrado de contenido para menores **antes** de enviar texto de su material a un modelo de pago | Condición de ADR-34; la capa local ya existe (`ContentGuard`, F04). **F05 lo aplica a los fragmentos (ADR-46), verificado en su FASE 7**; falta F07 (tutor) |
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
| F4-4 | ✅ **Se cierra en F08 (ADR-60):** los temas rechazados salen en los avisos del panel del adulto | Hecho y verificado en F08 (informe: `sdd/reports/F08-testing-report.md`) |
| F4-5 | 🟠 Medir con un modelo real barato cuántas veces el JSON del asistente llega mal formado y ajustar la plantilla. Si un menor recibe JSON inservible, el tema se acepta sin la valoración del modelo: F05 debe volver a comprobarlo antes de generar | ✅ La parte de F05 queda cubierta por la valoración `appropriate` del temario (ADR-47, verificado). Medir el JSON con un modelo real lo hace el usuario (APP_AI_TASKS_INTAKE_*) |
| F4-6 | 🟡 Anotado en F05: el tutor aún no puede subir el tope de un menor (F4-2) y un curso de un menor con modelos caros puede quedarse en pausa por `BUDGET` | ADR-44 |

## Anotado en F05 (generación del curso)

| # | Qué | Nota |
|---|-----|------|
| F5-1 | 🟠 Medir con los modelos reales del usuario cuántas piezas fallan (JSON de ejercicios y exámenes, citas) y cuánto cuesta un curso; ajustar plantillas y límites de salida | Solo lo puede hacer el usuario con sus claves; el stack se verificó con un modelo simulado |
| F5-2 | 🟡 Enlaces `http(s)` en las lecciones: su texto lo escribe el modelo. Para menores, pintar solo el texto o mostrar el dominio | `MarkdownContent.vue`; hoy se permiten con `rel` seguro |
| F5-3 | 🟡 Dos hilos de generación: con más de dos personas a la vez, el resto espera en cola | `app.generation.worker.threads` |
| F5-4 | 🟡 No hay forma de borrar un curso ni de purgar sus piezas | El gasto queda en `ai_usage` |
| F5-6 | ✅ Hecho: indicador de actividad en la pantalla del curso («Comprobado hace N s» con rueda animada) y aviso «esperando a que termine tu otro curso» (`waitingForOther` en el detalle) | Cambio pequeño tras la primera prueba con modelos reales |
| F5-5 | 🟡 Segunda capa de filtro para menores sobre cada lección (valoración del modelo o moderación) y otros idiomas | Junto con F4-3; decidir en F07/F08 |

## Anotado en F06 (estudio)

| # | Qué | Nota |
|---|-----|------|
| F6-1 | 🟠 Medir con tu modelo real cuántas veces «Corregir con IA» acierta o se equivoca, y cuánto cuesta | Solo lo puede hacer el usuario; se verifica con un modelo simulado |
| F6-2 | 🟡 «Una corrección de IA a la vez por usuario» está en memoria: con varias instancias del backend debe pasar a la BD (el tutor de F07 ya lo hace así: ADR-55) | ADR-52 |
| F6-3 | 🟡 La exportación del curso lleva soluciones y respuestas correctas: si se usa antes de examinarse, rompe la autoevaluación | ADR-50 |
| F6-4 | ✅ **Se cierra en F08 (ADR-59):** el adulto ve los intentos de su menor (respuestas, notas y preguntas falladas) | Hecho y verificado en F08 (informe: `sdd/reports/F08-testing-report.md`) |
| F6-6 | 🟡 Validar al arrancar que `app.study.max-submissions-per-hour` no supere a `max-attempts-per-piece` (la poda de intentos dejaría sin efecto el límite por hora) | Hoy solo está documentado en `StudyProperties` |
| F6-5 | Más tipos de pregunta (verdadero/falso, huecos, respuesta abierta en examen) y motor de cálculo para matemáticas y ciencias | v2; F05 solo genera test |

## Anotado en F07 (tutor IA)

| # | Qué | Nota |
|---|-----|------|
| F7-1 | 🟠 Medir con tu modelo real la calidad pedagógica del tutor: que guíe sin resolver ejercicios, que redirija lo que no es del curso, el tono según la edad y cuánto cuesta una conversación | Solo lo puede hacer el usuario con sus claves; las instrucciones fijas son hoy la única garantía |
| F7-2 | 🟡 El filtro de menores solo cubre español (F4-3) y es una lista corta: no hay una valoración del modelo sobre cada pregunta ni respuesta | Segunda capa a decidir en F08 junto con F5-5 |
| F7-3 | 🟡 Un alumno que pegue un enunciado de examen y pida la solución: el tutor no tiene soluciones ni exámenes, pero podría razonarla; las instrucciones piden pistas | Medirlo con F7-1 |
| F7-4 | ✅ **Se cierra en F08 (ADR-59 y 60):** el adulto lee las conversaciones, los bloqueos y los filtrados de su menor | Hecho y verificado en F08 (informe: `sdd/reports/F08-testing-report.md`) |
| F7-5 | 🟡 Streaming de la respuesta, voz, imágenes y memoria entre cursos | Fuera de v1 (ADR-58) |
| F7-6 | 🟡 Un adulto que vacía una conversación borra también las preguntas que cuentan para el límite por hora y por día: puede preguntar más de 30 por hora vaciando entre medias | Solo adultos (el menor no puede vaciar) y el tope mensual del usuario sigue cortando; arreglo posible: contar las llamadas `TUTOR` de `ai_usage` o una tabla de eventos |

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
| A2 | Límite de **frecuencia** por usuario: ✅ hecho para el asistente de F04 (ADR-41) , para «Reanudar» de F05 (60 s por curso, ADR-44) , los envíos y las correcciones de IA de F06 (ADR-53) y los mensajes del tutor de F07 (ADR-55); falta en la subida de material (F3-6) | 🟡 F3-6 |
| A3 | ✅ **Hecho en F04 (ADR-39):** `safety.ContentGuard` es el punto de enganche reutilizable. Falta usarlo en F05 (lecciones) y F07 (tutor) antes de enviar texto de un menor a un modelo de pago | ✅ |
| A4 | Usar `GenerationLock` en F05 llamando a `Lease.renew()` entre llamadas de IA; validar la salida del modelo contra un esquema antes de guardarla | ✅ **Hecho en F05 (ADR-42 y 43):** cerrojo renovado antes de cada llamada y cuatro validadores de salida; verificado en el stack |
| A5 | Timeout **por tarea** (hoy es el mayor de las tareas de cada proveedor) | 🟡 |
| A6 | Que solo los adultos con rol "global" vean el porcentaje del tope global, si la app se abre a más de una familia | 🟡 |
| A7 | Quitar las credenciales de propietario de la BD del contenedor `benchmark` (que no migre al arrancar) | 🟡 |
| A8 | **Parcial (25-10-2026):** run real hecho (27 llamadas, 0,208 USD): adaptadores de Anthropic, OpenAI y Gemini (incl. `x-goog-api-key`) confirmados; límite de `ASSESSMENT` subido a 10 000 (Haiku se truncó a 6 000); valoración en `sdd/benchmarks/valoracion.md`. **Falta:** contrastar el gasto con los paneles, confirmar el precio de OpenAI, valoración manual del usuario, pedir "sin LaTeX" en las plantillas y salida estructurada en F05 | al cerrar F02 |
| A9 | Actualizar `greenmail` (solo tests) y revisar versiones de dependencias | 🟡 |

## Anotado en F08 (panel del adulto tutor)

| # | Qué | Nota |
|---|-----|------|
| F8-1 | 🟡 El menor no sabe **cuándo** lo ha mirado su adulto: solo sabe que puede hacerlo | La consulta ya queda en la auditoría (`GUARDIAN_VIEW`); mostrarla al menor sería posterior (ADR-61) |
| F8-2 | 🟡 Los avisos salen de lo guardado: si se purgan las consultas bloqueadas (F4-1) o se borra un curso, desaparecen | Decidir la retención junto con F4-1 (ADR-60) |
| F8-3 | 🟡 Sin histórico completo de intentos: se recortan a 20 por ejercicio y 200 en total, y sin paginación ni filtros | Paginar si algún alumno los supera de forma habitual |
| F8-4 | 🟡 Sin notificaciones (correo), informes en PDF ni control del tiempo de uso | Fuera de alcance de v1 |
| F8-5 | 🟡 Un adulto no puede cambiar el tope de gasto de un alumno desde el panel | Hoy es configuración (A1, ADR-62) |
| F8-6 | 🟡 Segunda capa del filtro de menores y otros idiomas (F4-3, F5-5, F7-2) | Sigue pendiente; el panel solo muestra lo que el filtro actual ya detecta |
| F8-7 | 🟡 Sin límite de frecuencia en las lecturas del panel (un adulto autenticado puede pedirlas en bucle) y el contador de avisos del resumen se acota a 1 000 | Son consultas de solo lectura sobre datos acotados; si se abusa, límite por usuario en la BD como en F04 a F07 |
| F8-8 | 🟡 Cada intento de un adulto sobre un alumno ajeno añade un `ACCESS_DENIED` a la auditoría (F01): un bucle la haría crecer | Valorar agrupar o limitar esos eventos al desplegar |
