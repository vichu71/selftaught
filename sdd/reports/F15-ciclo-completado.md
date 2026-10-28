# Ciclo SDD completado — US-F15 · Pulido de «Resultados» y botón «Volver»

- **Feature:** la pantalla Resultados pasa de tres párrafos de texto a un panel que se entiende de un vistazo (tarjetas de cifras, medidor del tope, gasto por tarea, «Tus peques» y cursos como tarjetas con su planta), y el enlace subrayado «Volver al inicio» de cinco pantallas pasa a ser un botón con flecha.
- **Rama:** `feature/F15-resultados-backlink` (sin fusionar en `main`, sin push).
- **Commits:** `5a09d92` (FASE 1–4), `16bee21` (FASE 5), `206eaee` (FASE 6), `9b20eec` (FASE 7) y el de esta FASE 8.
- **Contrato y diseño:** `sdd/specs/US-F15-resultados-backlink.md`, `sdd/specs/ART-F15-propuesta-resultados-backlink.md`, ADR-92 y 93 en `ARCHITECTURE.md`.

## Tests
- Backend: 1487/1487 (4 omitidos); 10 son de `AiUsageTasksTest` y `AiUsageEndpointTest` sigue intacto.
- Frontend: 894/894 y `vue-tsc` limpio. Entorno: 57/57.
- Verificación con el stack aislado (`studylab-f15`): 14/14 por HTTP real. Pasada en el navegador en escritorio y móvil (390 y 360 px), como adulta y como menor.
- Cobertura: los criterios AC-1 a AC-11 y los casos borde tienen test (tabla en `F15-testing-report.md`). No se midió un porcentaje de líneas.

## Qué se hizo
- Backend: `GET /api/ai/usage/tasks` en `AiUsageController` (gasto del mes de quien llama por tarea, con la consulta que ya existía; un menor recibe solo `task` y `calls`). Sin migración ni SQL nuevo; `GET /api/ai/usage` no cambia.
- Frontend: `ui/BackLink` (botón con flecha, 44 px, sin subrayado) en Resultados, Curso, Mi material, Tengo un código y Nuevo curso (que dice «Cancelar»), con sus `data-testid`; `StatCard`, `BarList` y `format.ts` suben de `owner/` a `ui/` con clases `ui-*`; `results/TopeMeter`, `results/CourseCard` y `results/taskLabels`; `ResultsView` reescrita con tres lecturas en paralelo que fallan por separado.
- Documentación: `ARCHITECTURE.md`, `CLAUDE.md`, `README.md`, `sdd/BACKLOG.md` (F14-11 cerrado, F15-1 a F15-4).

## Cambios respecto al plan
- El gasto por tarea es un **endpoint aparte** y no un campo de `/api/ai/usage`, porque un test fija las claves exactas de `you` (también la del menor). Se ajustó la FASE 1 antes de escribir los tests.
- `StatCard` y `BarList` se movieron a `ui/` con renombrado de clases (ADR-93), en vez de importarse desde `owner/`.
- Para un menor, el mensaje de límite alcanzado dice «máximo» y no «tope».
- La pasada visual quitó un doble marco del gasto por tarea, un nombre repetido en «Tus peques» y el «1 veces» (ahora `times()` da «1 vez»).
- En la FASE 7 del panel (F14) ya se había corregido la barra inferior de móvil tapada por el contenido, que también beneficia a estas pantallas.

## Decisiones aceptadas (ver BACKLOG F15-1 a F15-4)
- El gasto por tarea es solo el del mes en curso.
- «Tus peques» no enlaza con el panel del tutor de cada menor.
- Solo se pule Resultados: Inicio, Mi material y el resto de pantallas siguen con su estilo.
- `LineChart` y `DataTable` siguen en `owner/`.

## Siguientes pasos
1. Decidir la fusión de `feature/F15-resultados-backlink` en `main` (no se ha hecho).
2. Pulir otras pantallas con los mismos componentes de `ui/` si te gusta el resultado (Inicio y Mi material, F15-3); un enlace a `/alumnos/{id}` desde «Tus peques» es barato (F15-2).
3. Antes de abrir la app a Internet: HTTPS y cookie `Secure` (D1–D3, D6, F14-1).
4. Medir con modelos reales (F4-5, F5-1, F6-1, F7-1, F12-1) sigue pendiente del usuario.
5. Los volúmenes `studylab-f15_db-data` y `studylab-f15_ollama-data` (y los de F14) siguen en Docker; borrarlos lo decide el usuario.
