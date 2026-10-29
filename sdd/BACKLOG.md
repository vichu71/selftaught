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

## Anotado en F09 (edición y compartir)

| # | Qué | Nota |
|---|-----|------|
| F9-1 | 🟡 Los ejercicios y las preguntas de examen solo se regeneran con IA: no se pueden editar a mano | Fuera de v1 |
| F9-2 | 🟡 Avisar al propietario o a quien copió de que hay una versión nueva del original | Fuera de v1; `origin_id` y `origin_version` ya se guardan |
| F9-3 | 🟡 El propietario no ve quién ha canjeado su código, solo cuántas veces | Decisión de privacidad |
| F9-4 | 🟡 Reordenar es con botones (sin arrastrar y soltar) | Mejora de interfaz (F10) |
| F9-5 | 🟡 Un adulto que pasa una copia a su menor | Fuera de v1 (borrar un curso entero llegó con F13) |
| F9-6 | 🟡 Sin moderación del contenido compartido: un adulto puede compartir algo que él mismo editó | Fuera de v1; el filtro solo protege a los menores |
| F9-7 | 🟡 La lista local del filtro de menores es corta y en español: ahora protege también las copias (F4-3, F5-5, F7-2, F8-6) | Segunda capa pendiente |
| F9-8 | 🟡 El panel del adulto no muestra los códigos que crea su menor ni cuándo alguien canjea uno de sus cursos | Los eventos `COURSE_SHARE_CREATED` y `COURSE_SHARE_REDEEMED` ya están en la auditoría; decidir en F10 si se enseñan |
| F9-9 | 🟡 Las ediciones que no usan IA (renombrar, reordenar, editar el texto) no tienen límite de frecuencia: son de un usuario autenticado sobre su propio curso y cada una sube la versión | Añadir un límite por usuario si se abusa |

## Anotado en F10 (rediseño del frontend)

| # | Qué | Nota |
|---|-----|------|
| F10-1 | 🟡 «Sigue donde lo dejaste» usa el curso con más avance (sin llegar al 100 %), no el último estudiado de verdad | ADR-67; si molesta, `lastStudiedAt` en la lista de cursos (cambio de backend) |
| F10-2 | 🟡 Reordenar sigue siendo con botones (sin arrastrar y soltar) | Cierra F9-4 solo en la presentación; arrastrar queda fuera de v1 |
| F10-3 | 🟡 El panel del adulto sigue sin mostrar códigos creados por su menor ni canjes (F9-8) | F10 no cambia datos; decidir si se enseñan en otra feature |
| F10-4 | 🟡 Sin modo claro/oscuro elegible: solo el automático del sistema | Fuera de v1 |
| F10-5 | 🟡 Nombre y logo siguen siendo provisionales; sin mascota ni ilustraciones | La variante «Mundo que crece» (`sdd/design/F10/MundoC.dc.html`) queda en el radar |
| F10-6 | 🟡 La comprobación visual (móvil, zoom 200 %, teclado) es manual; no hay capturas de referencia automáticas | Valorar pruebas visuales si el diseño crece |
| F10-7 | 🟡 Mapa lineal: no soporta ramas ni prerrequisitos entre lecciones | ADR-68; revisitar si el curso deja de ser lineal |
| F10-8 | 🟡 Las vistas siguen con las clases compartidas (`button.primary`, `label`/`input`) en lugar de `UiField`/`UiButton` | Migrarlas una a una cambia muchas pruebas sin cambiar lo que se ve; hacerlo si se cambia de piel |
| F10-9 | 🟡 La pantalla de edición del curso sigue densa (muchos botones por fila) | Mejora de contenido, no de marco |
| F10-10 | 🟡 Sin recorrer a mano en el navegador: 360 y 430 px, zoom 200 %, recorrido con teclado de acceso a examen y lector de pantalla | Pasada manual o prueba visual automática |
| F10-11 | 🟡 El contraste se comprueba sobre los tokens, no con una herramienta de accesibilidad sobre las pantallas | Valorar axe u otra en el navegador |

## Anotado en F11 (piel «Mundo que crece»)

| # | Qué | Nota |
|---|-----|------|
| F11-1 | ✅ Decidido (26-10-2026): «Mundo» es la interfaz definitiva y sustituye a «Mapa»; no hay selector de piel | Si algún día conviven, tokens por tema de datos y pruebas para las dos (ADR-73) |
| F11-2 | 🟡 Las cuatro plantas son genéricas: no hay ilustración por curso ni paisaje personalizable | Fuera de v1 |
| F11-3 | 🟡 Mascota y personaje sencillos, nombre y logo provisionales | Valorar ilustración profesional si «Mundo» se queda |
| F11-4 | 🟡 Las pantallas no se prototiparon antes (decisión del usuario); el informe las compara con «Mapa» en el navegador | Riesgo de rehacer ilustraciones |
| F11-5 | 🟡 Heredan los pendientes de F10 (F10-8 a F10-11: migrar vistas a `ui/`, pantalla de edición densa, 360/430 px y zoom a mano, contraste con herramienta en el navegador) | |
| F11-6 | 🟠 nginx puede dejar que el navegador conserve el `index.html` antiguo tras un despliegue y mostrar la hoja de estilos vieja | Añadir `Cache-Control: no-cache` para `index.html` (y caché larga solo para `/assets/*` con hash); anotar con D1–D6 |
| F11-7 | 🟡 Las colinas dejan espacio vacío a la derecha con pocas paradas | Decoración (árboles, flores) si se quiere más «paisaje» |

## Anotado en F12 (ilustraciones de portada con IA)

| # | Qué | Nota |
|---|-----|------|
| F12-1 | 🟠 El modelo de imagen por defecto no está medido: hasta que el usuario lance `make benchmark-images` con sus claves no hay un modelo recomendado ni un coste real por curso | Lo decide el benchmark (ADR-83); anotar el resultado y su fecha en `ai-pricing.properties` |
| F12-2 | 🟡 Los formatos de las llamadas de imagen (OpenAI y Google) se prueban solo **según la documentación**, no contra el servicio real | Se confirman con el benchmark de imágenes |
| F12-3 | 🟡 Una imagen por tema; ni por lección, ni varias, ni diagramas SVG o Mermaid, ni imágenes reales de Wikimedia | Fuera de v1; el análisis previo recomienda diagramas para ciencias y matemáticas |
| F12-4 | 🟡 Los menores no ven ilustraciones y su copia de un curso compartido no las lleva: una imagen no se puede filtrar con `ContentGuard` | Se abre si hay moderación de imágenes propia (ADR-82) |
| F12-5 | 🟡 El texto alternativo es genérico («Ilustración generada por IA sobre «título»») porque el modelo de texto no ve la imagen | Un modelo con visión podría describirla (ADR-81) |
| F12-6 | 🟡 La estimación de coste usa el máximo de temas posible, no el número real (lo decide el servidor al crear la ficha) | Mostrarlo exacto exigiría conocer la ficha antes de aceptar |
| F12-7 | 🟡 La exportación JSON no incluye las ilustraciones | Binario; fuera de v1 |
| F12-8 | 🟡 Una instrucción maliciosa dentro del título de un tema no se detecta; la defensa es la estructura del prompt y la moderación del proveedor | ADR-81 |
| F12-9 | 🟡 Quien canjea una copia no puede activar las ilustraciones para los temas que no las tengan | Sería una opción nueva con su coste a la vista |

## Anotado en F13 (borrar cursos)

| # | Qué | Nota |
|---|-----|------|
| F13-1 | 🟡 Sin papelera ni «deshacer»: el borrado es definitivo; la red de seguridad es el aviso y la exportación previa | ADR-84; papelera de 7–30 días si se pierden cursos por error |
| F13-2 | 🟡 Los menores no pueden borrar sus cursos y el tutor adulto tampoco los de su menor: si se acumulan, no hay forma de limpiarlos | ADR-86; opciones: archivar para el menor o borrado por el tutor con auditoría |
| F13-3 | 🟡 El borrado de cursos no tiene límite de frecuencia | ADR-84: no gasta ni toca la IA |
| F13-4 | 🟡 Quien canjeó un código no se entera de que el original desapareció (su copia sigue) | Fuera de v1 |
| F13-5 | 🟡 La fila de `course_intake` se queda vacía para siempre (cuenta para el límite de consultas) | ADR-85; limpiar al pasar el límite a su propio contador |
| F13-6 | 🟡 `IllustrationGeneratorTest.ac4_…` falló una vez en una pasada completa del backend y pasó suelto y al repetir | Posible contaminación entre clases por la BD compartida (como las que F12 ya corrigió); vigilar |

## Anotado en F14 (panel del propietario)

| # | Qué | Nota |
|---|-----|------|
| F14-1 | 🟠 El panel es el endpoint más sensible de la app: antes de abrirla a Internet hacen falta HTTPS y la cookie `Secure` (D2, D3) y comprobar los puertos (D6) | Mismo trabajo que D1–D6; sin ello, la sesión del propietario viaja en claro |
| F14-2 | 🟡 Un solo propietario y sin 2FA | ADR-87; varios propietarios o roles intermedios exigirían una tabla de roles con auditoría y 2FA |
| F14-3 | 🟡 Cambiar `OWNER_EMAIL` exige reiniciar el backend (se lee al arrancar) | Es intencionado: solo quien controla el despliegue decide quién es propietario |
| F14-4 | 🟡 «Activo» = al menos un inicio de sesión en la ventana: una sesión larga sin volver a entrar no cuenta | D7; si molesta, registrar la última actividad por sesión |
| F14-5 | 🟡 La lista de usuarios calcula gasto y actividad sobre todas las cuentas para poder ordenar | ADR-89; válido para cientos de cuentas; con miles, resumen diario precalculado |
| F14-6 | 🟡 Sin métricas del servidor (CPU, memoria, latencia HTTP), alertas por correo o push, exportar a CSV ni comparar periodos | Fuera de v1; las métricas del servidor necesitarían un sistema aparte |
| F14-7 | 🟡 El propietario no puede borrar cuentas, moderar contenido ni cambiar topes globales o precios desde la pantalla | ADR-91; siguen en la configuración |
| F14-8 | 🟡 Decidir si el aviso al menor de F08 («tu adulto ve tus respuestas, intentos y conversaciones») debe nombrar también que el propietario ve cifras de cuenta y gasto | Hoy solo lo dice el README; el propietario nunca ve contenido |
| F14-9 | 🟡 Los topes por usuario fijados desde el panel no avisan al usuario del cambio | Solo se enteran al llegar al tope (mensaje de siempre) |
| F14-10 | 🟡 Un no propietario que envía a `PUT /api/owner/users/{id}/limit` un cuerpo que no es JSON recibe 400 en vez de 404 (Spring lo rechaza antes de llegar a la guardia) | Revela, casi sin valor, que la ruta existe; se arreglaría leyendo el cuerpo como texto dentro del controlador |
| F14-11 | ✅ **Hecho en F15 (ADR-92 y 93):** pulido de la pantalla **Resultados** (medidor del tope, tarjetas de cifras, cursos como tarjetas de jardín, gasto por tarea) y un `BackLink` con flecha en lugar del enlace subrayado «Volver al inicio» de 5 pantallas | Acordado con el usuario el 27-10-2026: se hace después de F14, en rama aparte, reutilizando `StatCard`, `BarList` y los colores de F14 |

## Anotado en F15 (Resultados y botón «Volver»)

| # | Qué | Nota |
|---|-----|------|
| F15-1 | 🟡 El gasto por tarea es solo el del mes en curso | ADR-92; un parámetro de mes validado en el servidor si hace falta ver meses anteriores |
| F15-2 | ✅ **Hecho en F16 (ADR-94):** «Tus peques» no enlazaba con el panel del adulto tutor de cada menor (F08) | Un enlace a `/alumnos/{id}` por menor sería barato; queda fuera de v1 |
| F15-3 | ✅ **Hecho en F16 (ADR-94) para Inicio y Mi material:** solo se pulía Resultados; el resto de pantallas siguen con su estilo de antes | Se haría pantalla a pantalla con los mismos componentes de `ui/` |
| F15-4 | 🟡 `LineChart` y `DataTable` siguen en `owner/`; si Resultados necesita una gráfica diaria habría que subirlos | ADR-93 |

## Anotado en F16 (Inicio y Mi material)

| # | Qué | Nota |
|---|-----|------|
| F16-1 | 🟡 Se sube un fichero cada vez (arrastrando varios, se coge el primero y se avisa) | ADR-94; subir varios a la vez cambiaría el almacén de material y la cola de procesado |
| F16-2 | 🟡 Sin vista previa, renombrar, buscar ni ordenar documentos | Fuera de v1 |
| F16-3 | ✅ **Hecho en F17 (ADR-95 a 97) para la cabecera y los colores; el rediseño a fondo de cada pantalla sigue en F17-3:** siguen con el estilo de antes las pantallas Nuevo curso, Tengo un código, Curso, Lección, Login y el panel del tutor | Se harían pantalla a pantalla con los mismos componentes de `ui/` |

## Anotado en F17 (alegría y colorido)

| # | Qué | Nota |
|---|-----|------|
| F17-1 | 🟡 No hay un color o tema elegible por usuario ni un tema de alto contraste | Fuera de v1; los tokens de zona lo permitirían (ADR-95, ADR-96) |
| F17-2 | 🟡 El destello, el confeti y el gesto de la mascota son los mismos para todos | ADR-97; una celebración por curso o por usuario sería otra feature |
| F17-3 | 🟡 Las pantallas internas (Nuevo curso, Editar curso, Intentos, panel del tutor) usan la cabecera y los tonos de zona, pero no se han rediseñado a fondo | Se mejorarían pantalla a pantalla con los componentes de `ui/` |
