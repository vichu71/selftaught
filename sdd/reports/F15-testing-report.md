# F15 — Informe de pruebas (Resultados y botón «Volver»)

## Resultado
- Backend: suite completa **1487/1487** (4 omitidos), sin el intermitente de siempre en esta pasada. 10 son de `AiUsageTasksTest` (el endpoint nuevo) y los 10 de `AiUsageEndpointTest` siguen intactos.
- Frontend: **894/894** y `vue-tsc` sin errores (tras los últimos ajustes de la pasada visual se repitió la suite completa).
- Entorno (`scripts/tests`): **57/57**.
- Verificación con el stack (proyecto aislado `studylab-f15`, variables ficticias fuera del repositorio, correo de Mailpit, IA de desarrollo): **14/14** por HTTP real, con una adulta con un menor, otro adulto y el menor.
- Pasada en el navegador (escritorio 1280 px y móvil 390 y 360 px), como adulta y como menor: ver abajo.
- Ningún test llama a una API de pago. No se tocó el stack real ni el `.env` real.

## Resultados por criterio de aceptación

| AC | Descripción | Test | Estado |
|----|-------------|------|--------|
| AC-1 | `BackLink`: enlace a `/` con aspecto de botón, flecha, 44 px, sin subrayado, nombre accesible; lo usan las cinco pantallas con sus `data-testid`; en «Nuevo curso» dice «Cancelar» | `BackLink.spec`, `ResultsView.spec`, navegador | ✅ |
| AC-2 | Medidor del tope: gasto y tope, porcentaje, estado en palabras e icono (< 80 %, 80–99 %, ≥ 100 %), sin pasar del 100 % ni dividir por cero | `TopeMeter.spec`, `ResultsView.spec` | ✅ |
| AC-3 | Un menor no ve importes ni topes en ninguna parte de la pantalla | `ResultsView.spec`, `usagetasks`, script de verificación, navegador | ✅ |
| AC-4 | Tarjetas: veces que la IA ayudó, gasto (solo adulto), cursos listos; sin NaN; «1 vez» en singular | `ResultsView.spec` | ✅ |
| AC-5 | Cursos como tarjetas con planta, título como enlace, estado, barra, errores y pendientes en palabras | `CourseCard.spec`, `ResultsView.spec` | ✅ |
| AC-6 | `GET /api/ai/usage/tasks`: de más a menos, solo de quien llama, un menor sin `spendUsd`; `/api/ai/usage` no cambia | `AiUsageTasksTest` (10), script (14) | ✅ |
| AC-7 | «Tus peques» solo para un adulto con menores, con su medidor | `ResultsView.spec`, navegador | ✅ |
| AC-8 | Títulos y nombres como texto, también hostiles; los títulos largos se parten | `CourseCard.spec`, `ResultsView.spec`, navegador | ✅ |
| AC-9 | Estados de carga, error y vacío; cada petición falla por separado | `ResultsView.spec` | ✅ |
| AC-10 | Colores solo de `tokens.css`, estado nunca solo con color, 44 px, sin desbordamiento a 360–430 px | `tests/design/results.spec`, navegador | ✅ |
| AC-11 | Sin regresiones: F10–F14 en verde, rutas y API existente intactas | suite completa | ✅ |

## Casos borde verificados
- Curso en cola o generándose (`total` 0): semilla «Preparándose», sin barra ni porcentaje; curso `PAUSED` con errores y pendientes escritos.
- Gasto mayor que el tope o `limitReached`: barra al 100 % y «Tope alcanzado»; tope 0 o ausente, sin división por cero.
- Usuario sin llamadas: «La IA todavía no te ha ayudado este mes» y sin bloque de gasto por tarea.
- Llamadas de otro mes no cuentan; una llamada fallida suma su coste y no cuenta como llamada.
- Menor sin importes ni en el mensaje de límite alcanzado (para él dice «máximo»).
- Fallos independientes de `getResults`, `getUsage` y `getUsageTasks`.

## Verificación con el stack (14/14)
Anónimo 401; la adulta ve sus tareas de más a menos con importes y veces correctos (el mes anterior no cuenta); el menor recibe solo `task` y `calls` (ni «spend», ni «usd», ni «limit»); otro adulto recibe una lista vacía, sin nada de Ana ni de su menor; leer no cambia `ai_usage`; `/api/ai/usage` mantiene las claves exactas del adulto y del menor y la adulta sigue viendo a su menor con su tope; `/api/results` del menor sin importes y el de la adulta con ellos; los logs del backend no llevan el nombre del menor ni los correos.

## Pasada visual
Escritorio: «Resultados» con el botón «← Inicio» arriba a la derecha, tarjetas de cifras, medidor con «Vas bien», gasto por tarea con barras, «Tus peques» con el medidor del menor y los cursos como tarjetas con su planta (semilla, brote, árbol, árbol con frutos), barra de piezas y «2 con error · 3 pendientes» escrito.
Móvil (390 y 360 px): sin desbordamiento horizontal, las tarjetas se apilan, el título largo de un curso se parte, objetivos de 44 px y el botón «Inicio» y los enlaces de la barra inferior son pulsables (`elementFromPoint`). Como menor: tres veces, gasto por tarea en veces y ningún importe, tope ni medidor.
Cuatro de las cinco pantallas del botón (Resultados, Mi material, Tengo un código y Nuevo curso) se comprobaron en el navegador (texto, destino, 44 px, sin subrayado, pulsable); la quinta (Curso) la cubre el test que lee la fuente.

**Corregido en esta pasada (frontend):**
- El gasto por tarea salía dentro de una tarjeta que ya llevaba su propio marco (doble borde): ahora es una sola.
- «Tus peques» repetía el nombre del menor en la etiqueta del medidor: ahora «Tope de este mes».
- «1 veces»: ahora «1 vez» (`times()` en `ui/format.ts`, con su test).

## Revisión de seguridad (security-checklist)
Bien: el endpoint nuevo exige sesión y no tiene parámetros, así que no hay IDOR posible (el usuario sale siempre de la sesión); un menor no recibe nunca importes (el campo se omite y un test y la verificación con el stack lo comprueban); `GET /api/ai/usage` no cambia de contrato; es solo lectura, con una consulta ya existente, parametrizada y filtrada por usuario, sin SQL nuevo ni migración; las tareas son los nombres de una enumeración, sin nada del proveedor ni del contenido; el frontend pinta nombres y títulos como texto, sin `v-html`, y no añade dependencias ni colores nuevos.
Pendiente / decisiones aceptadas:
- 🟡 El gasto por tarea es solo el del mes en curso (F15-1).
- 🟡 «Tus peques» no enlaza con el panel del tutor de cada menor (F15-2).
- No comprobable desde aquí: HTTPS, cabeceras y puertos del despliegue real (siguen en `sdd/BACKLOG.md`, D1–D6 y F14-1).

## Revisión de la integración con IA
No aplica: no llama a ningún modelo. Solo lee `ai_usage` con una consulta que ya existía.

## Cambios de la FASE 7
- `ResultsView.vue`: el gasto por tarea sin tarjeta propia, la etiqueta del medidor de cada menor y el singular de «veces».
- `ui/format.ts`: `times()`; `ResultsView.spec`: test del singular.
- Ninguno en backend.

## Issues encontrados
- Ninguno nuevo. El intermitente `IllustrationGeneratorTest` (F13-6) no falló en esta pasada.
