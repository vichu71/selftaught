# Ciclo SDD completado — US-F14 · Panel del propietario

- **Feature:** pantalla de administración `/propietario` con las estadísticas de la app (gasto de IA, usuarios y conexiones, uso, salud) y acciones sobre las cuentas (desactivar, reactivar, revocar sesiones y fijar el tope mensual de IA).
- **Rama:** `feature/US-F14-panel-propietario` (sin fusionar en `main`, sin push).
- **Commits:** `3d2d5d1` (FASE 1–4), `229cf6d` (FASE 5), `59647fd` (FASE 6), `8e38630` (FASE 7) y el de esta FASE 8.
- **Contrato y diseño:** `sdd/specs/US-F14-panel-propietario.md`, `sdd/specs/ART-F14-propuesta-panel-propietario.md`, ADR-87 a 91 en `ARCHITECTURE.md`.

## Tests
- Backend: 1477 tests; 119 son de F14 y pasan todos. En la suite completa falló 1, el intermitente anterior a F14 (`IllustrationGeneratorTest.ac4_…`, F13-6), que suelto pasa 23/23.
- Frontend: 833/833 y `vue-tsc` limpio. Entorno: 57/57.
- Verificación con el stack aislado (`studylab-f14`): 46/46 por HTTP real. Pasada en el navegador en escritorio y móvil (390 y 360 px).
- Cobertura: los criterios AC-1 a AC-15 y los casos borde tienen test (tabla en `F14-testing-report.md`). No se midió un porcentaje de líneas.

## Qué se hizo
- Backend: paquete `owner/` (guardia, límite de frecuencia, informes agregados en SQL, lista de usuarios y acciones con auditoría), migración `V12` (`user_limit_override`, `owner_action` e índice de auditoría) y 11 endpoints bajo `/api/owner`. `BudgetService` lee el tope personalizado; `/api/me` y el login llevan `owner: true` solo para el propietario; los precios llevan una fecha de verificación opcional.
- Frontend: `OwnerView` con cuatro pestañas, gráficas SVG propias con su tabla equivalente, lista de usuarios con diálogos de confirmación, enlace y ruta solo para el propietario y colores nuevos en `tokens.css`.
- Documentación: `ARCHITECTURE.md`, `CLAUDE.md`, `README.md`, `sdd/BACKLOG.md` (F14-1 a F14-12).

## Cambios respecto al plan
- **404 uniforme:** la guardia lanza la excepción de «recurso no encontrado» del propio Spring, para que el panel no se distinga de una ruta inventada. Un anónimo recibe 401 y una escritura sin CSRF 403, como en cualquier ruta.
- **Mes de Madrid:** el mes del panel es el natural de Madrid, el mismo que los topes; los días y rangos van en UTC.
- **Precios:** la fecha de verificación pasa de comentarios a la clave opcional `verified-on`; el precio de OpenAI no confirmado sale «sin fecha».
- **`/api/owner/prices`:** los campos son `models` e `images` (con `text` chocaba con la lista de campos prohibidos).
- **Pasada visual:** se añadió el margen lateral y tarjetas, se acortó la tabla de inicios de sesión, y se arregló la barra inferior de móvil. Esa barra **ya quedaba tapada por el contenido** y ningún enlace se podía pulsar (error existente corregido).
- `UiDialog` gana `keepOpen` y `confirmDisabled` (sin cambiar su comportamiento por defecto).

## Decisiones aceptadas (ver BACKLOG F14-1 a F14-12)
- Un solo propietario y sin 2FA; cambiar `OWNER_EMAIL` exige reiniciar el backend.
- «Activo» = al menos un inicio de sesión en la ventana; la lista calcula sobre todas las cuentas (válido para una base familiar).
- Sin métricas del servidor (CPU, memoria, latencia) ni alertas.
- El propietario no borra cuentas, no modera contenido y no cambia topes globales ni precios desde la pantalla.
- Fugas mínimas: JSON roto de un no propietario → 400 (F14-10) y una pequeña carrera en el límite de frecuencia (F14-12).

## Siguientes pasos
1. Decidir si se fusiona la rama en `main` (no se ha hecho). Antes de desplegar: aplicar `V12` y definir `OWNER_EMAIL` en el `.env` real (lo haces tú).
2. **Antes de abrir la app a Internet:** HTTPS y cookie `Secure` (D1–D3, D6, F14-1): el panel es lo más sensible de la app.
3. Pulido de la pantalla **Resultados** y un `BackLink` con flecha en lugar del enlace subrayado de 5 pantallas (F14-11, acordado con el usuario), reutilizando `StatCard`, `BarList` y los colores de F14.
4. Medir con modelos reales (F4-5, F5-1, F6-1, F7-1, F12-1) sigue pendiente del usuario.
5. Los volúmenes `studylab-f14_db-data` y `studylab-f14_ollama-data` siguen en Docker; borrarlos lo decide el usuario.
