# F14 — Informe de pruebas (panel del propietario)

## Resultado
- Backend: suite completa **1477 tests, 1 fallo**: `IllustrationGeneratorTest.ac4_…` (línea 89), el intermitente ya anotado (F13-6). Repetido suelto pasa **23/23** (BUILD SUCCESS). 119 de los tests son de F14 (`owner/`) y pasan todos.
- Frontend: **833/833** y `vue-tsc` sin errores (se repitió la suite completa tras los últimos ajustes de la pasada visual).
- Entorno (`scripts/tests`): **57/57** (3 nuevos de F14).
- Verificación con el stack (proyecto aislado `studylab-f14`, variables ficticias fuera del repositorio, `OWNER_EMAIL` de prueba, puertos remapeados, correo de Mailpit, IA de desarrollo): **46/46** por HTTP real, con cookies y CSRF reales y el rol real de la aplicación.
- Pasada en el navegador (escritorio y móvil a 390 y 360 px): enlace solo para el propietario, cuatro pestañas, gráficas con su tabla, diálogos con validación del tope, sin desbordamiento horizontal en ninguna pestaña y objetivos táctiles de 44 px.
- Ningún test llama a una API de pago. No se tocó el stack real ni el `.env` real.

## Resultados por criterio de aceptación

| AC | Descripción | Test | Estado |
|----|-------------|------|--------|
| AC-1 | Propietario por `OWNER_EMAIL` (mayúsculas y espacios), solo adulto activo; sin variable no hay panel; el rol sale del correo en cada petición | `OwnerAccessTest`, `OwnerDisabledTest` | ✅ |
| AC-2 | Sesión y guardia primero; no propietario → mismo 404 que una ruta inexistente; anónimo 401; sin CSRF 403; `ACCESS_DENIED` del adulto | `OwnerAccessTest`, `OwnerArchitectureTest` | ✅ |
| AC-3 | Cifras exactas de gasto, cuentas, uso y salud; rangos 7/30/90 (400 si no); benchmark aparte; instalación vacía sin divisiones por cero | `OwnerAiStatsTest`, `OwnerAccountsStatsTest`, `OwnerUsageStatsTest`, `OwnerHealthTest` | ✅ |
| AC-4 | Sin contenido; de un menor solo los campos permitidos | `OwnerNoContentTest`, `OwnerArchitectureTest` | ✅ |
| AC-5 | Lista paginada, filtros, orden y límites (400 si no) | `OwnerUsersTest` | ✅ |
| AC-6 | Desactivar y reactivar (idempotente, no a uno mismo, revoca sesiones, pendiente → 400) | `OwnerActionsTest` | ✅ |
| AC-7 | Tope por usuario 0–500 con dos decimales; manda sobre la configuración y sigue cortando | `OwnerActionsTest`, `OwnerLimitEnforcementTest`, `OwnerSchemaTest` | ✅ |
| AC-8 | Revocar sesiones | `OwnerActionsTest` | ✅ |
| AC-9 | Auditoría `OWNER_*` con ids y sin datos personales; rechazos sin evento; transacción atómica; logs sin correos | `OwnerActionsTest`, `OwnerArchitectureTest` | ✅ |
| AC-10 | 120 consultas y 60 acciones por hora por propietario (429), poda, índices | `OwnerRateLimitTest`, `OwnerAccountsStatsTest`, `OwnerSchemaTest` | ✅ |
| AC-11 | Tabla de precios con `verified-on`; sin fecha → «sin fecha» y marcado | `OwnerAiStatsTest`, `OwnerView.spec` | ✅ |
| AC-12 | Interfaz: enlace y ruta solo del propietario, pestañas, diálogos de Reka UI, estados, texto plano | `OwnerView.spec`, `layouts/owner.spec`, `charts.spec` | ✅ |
| AC-13 | Colores solo en `tokens.css` con contraste AA en claro y noche; 44 px; móvil | `tests/design/owner.spec`, pasada en el navegador | ✅ |
| AC-14 | `OWNER_EMAIL` opcional, sin valor por defecto | `OwnerDisabledTest`, `environment.test.mjs` | ✅ |
| AC-15 | El panel solo lee salvo las acciones; no cambia `ai_usage`, cursos ni piezas | `OwnerAiStatsTest`, `OwnerUsageStatsTest`, `OwnerActionsTest`, `OwnerArchitectureTest` | ✅ |

## Casos borde verificados
- Variable ausente o con otro correo; correo cambiado → el rol se pierde en la siguiente petición; cuenta sin verificar o desactivada, sin panel.
- Propietario sobre sí mismo (desactivar o revocar → 400 `CANNOT_TARGET_SELF`); tope propio permitido.
- Id inexistente o mal formado → 404; tope inválido (−1, 500,01, 1,234, texto, booleano, sin cuerpo) → 400 sin tocar nada; 0 y 500 válidos.
- Tope por debajo de lo gastado: corta ya y el diálogo lo avisa.
- Desactivar a un tutor no desactiva a sus menores; repetir una acción no duplica la auditoría.
- Un fallo al auditar deshace el cambio (restricción temporal en `audit_event`).
- `ai_usage` vacía, un modelo sin llamadas en el rango, proyección con menos de 3 días.
- Hostile (`<img onerror>`) en nombres, modelos y proveedores: siempre texto.

## Verificación con el stack (46/46)
Propietario, otro adulto y un menor con nombre secreto; gasto sembrado (incluido el benchmark); migración V12 aplicada y permisos mínimos del rol de la aplicación (`DELETE` sobre `user_limit_override`, nada de `UPDATE` sobre `owner_action`, ni `DELETE` sobre `ai_usage`). `owner:true` solo en el login y en `/api/me` del propietario. Anónimo 401, adulto y menor 404 con la misma respuesta que una ruta inventada, sin CSRF 403. Las cifras (llamadas, coste del rango, activos, sesiones abiertas, cursos) coinciden con la base de datos; el benchmark no suma; los precios traen sus fechas y el no confirmado sale sin fecha. Ningún informe lleva el tema de un curso ni el nombre del menor; de un menor, solo identificador, tutor y tope. Desactivar (204, cae la sesión y el login), reactivar, revocar sesiones y fijar o quitar el tope; el propio usuario y el tutor ven el tope real. Auditoría `OWNER_*` sin correos ni nombres; logs del backend sin correos ni contenido; la consulta 121.ª de la hora responde 429.

Nota de método: la comprobación de «salud» del script es débil (acepta tanto errores de IA recientes como ninguno); la cubren los tests de integración (`OwnerHealthTest`). El volumen del proyecto `studylab-f14` queda en Docker (el stack se paró sin `-v`): borrarlo lo decide el usuario.

## Pasada visual
Escritorio (1280 px): enlace «Propietario» en el menú, tarjetas de cifras con el estado del tope en palabras («Dentro del tope»), gráfica diaria con «Máximo» y su tabla, reparto por modelo, proveedor, tarea y usuario (el menor con su identificador), lista de usuarios y diálogo del tope con validación («Escribe un número de 0 a 500…», botón desactivado con −1).
Móvil (390 y 360 px): sin desbordamiento horizontal en las cuatro pestañas, tablas con scroll dentro de su caja, pestañas que se parten en líneas, 44 px de objetivo.

**Corregido en esta pasada (frontend):**
- La vista no tenía margen lateral (faltaba `page wide`) y las gráficas y tablas se mezclaban con las colinas del fondo: ahora cada bloque es una tarjeta.
- La tabla de inicios de sesión mostraba 30 filas de ceros: ahora solo los días con actividad.
- En móvil, el cuarto enlace de la barra inferior desbordaba (1 enlace más): los enlaces reparten el ancho y apilan icono y texto.
- **Error existente, no de F14:** en móvil la barra de navegación inferior quedaba **tapada por el contenido** (mismo `z-index`, el contenido se pintaba encima y ningún enlace se podía pulsar en cuanto la página tenía scroll). Pasa por delante con un `z-index` propio (`.shell > .nav-bar`). Se comprobó que los cuatro enlaces son pulsables en 360 y 390 px.

## Revisión de seguridad (security-checklist)
Bien: los 11 endpoints empiezan por `OwnerGuard.require` y por el límite de frecuencia (lo comprueba un test de arquitectura); el propietario se calcula en cada petición con el correo de la sesión (cuenta activa, es decir, verificada) contra `OWNER_EMAIL`, sin columna ni caché, y quien no lo es recibe el mismo 404 que una ruta inventada, de modo que no se revela que el panel existe; el correo no se puede cambiar desde la aplicación (no hay cambio de correo) ni obtener sin verificarlo; SQL parametrizado, ordenación y rangos como listas cerradas, página y tamaño acotados; un tope se valida en el backend (0–500, dos decimales) y sigue cortando; acciones en una transacción con la fila bloqueada y la auditoría dentro, solo con ids; de un menor nunca sale el nombre ni el correo, y ninguna respuesta lleva contenido (lo prueban un test de arquitectura y otro que recorre el JSON); nada de `v-html`, `confirm()` ni dependencias nuevas; `OWNER_EMAIL` solo vive en el entorno del backend y se documenta sin valor; las respuestas llevan las cabeceras de caché de Spring Security.
Pendiente / decisiones aceptadas:
- 🟠 **Antes de abrir la app a Internet:** HTTPS y cookie `Secure` (D1–D3, D6, F14-1); el panel es lo más sensible de la app.
- 🟡 Un solo propietario y sin 2FA (F14-2).
- 🟡 Un cuerpo que no es JSON enviado por un no propietario a `PUT …/limit` da 400 en vez de 404 (F14-10).
- 🟡 El contador del límite de frecuencia cuenta y luego escribe sin bloqueo: dos peticiones simultáneas podrían pasar una más de las 120 (sin consecuencias: solo hay un propietario y no hay coste).
- No comprobable desde aquí: HTTPS, cabeceras y puertos del despliegue real (siguen en `sdd/BACKLOG.md`).

## Revisión de la integración con IA
No aplica: el paquete no llama a ningún modelo (lo comprueba `OwnerArchitectureTest`). Sí afecta al control de gasto: `BudgetService` lee el tope personalizado antes de cada llamada y el tope sigue cortando siempre (`OwnerLimitEnforcementTest`).

## Cambios de la FASE 7
- `OwnerView.vue`: clase `page wide`, tabla de inicios de sesión solo con actividad.
- `components.css`: tarjetas del panel, pestañas que se parten, barra inferior de móvil y `z-index` de la barra de navegación.
- `environment.test.mjs`: la comprobación de `OWNER_EMAIL` lee la configuración sin interpolar.
- Ninguno en backend.

## Issues encontrados
- Intermitente (ya existente): `IllustrationGeneratorTest.ac4_…` falló una vez en la suite completa y pasa suelto (F13-6).
- Barra de navegación inferior tapada en móvil (ya existente, corregida).
