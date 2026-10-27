# F15 · Pulido de «Resultados» y botón «Volver» — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F15-resultados-backlink.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 91) y `PROJECT.md`. Sin IA, sin tareas ni plantillas nuevas, **sin librerías nuevas** y sin migración.

## Ajustes a la FASE 1 que salen de leer el código

| Ajuste | Motivo | Efecto |
|--------|--------|--------|
| El gasto por tarea va en un **endpoint nuevo** (`GET /api/ai/usage/tasks`) y no como campo de `/api/ai/usage` | `AiUsageEndpointTest` fija con `containsExactlyInAnyOrder` las claves de `you` (también la de un menor: solo `calls` y `limitReached`). Un campo nuevo rompería ese contrato de privacidad y sus tests; un endpoint aparte lo deja intacto y además aísla los fallos (AC-9) | Ya reflejado en D6, AC-6 y «Fuera de alcance» de la FASE 1 |
| `StatCard` y `BarList` (y `format.ts`) **suben de `src/owner/` a `src/ui/`** | Resultados los reutiliza: que una pantalla de todos importe de la carpeta del propietario sería un acoplamiento raro | Se mueven con `git mv`, se renombran sus clases `owner-*` a `ui-*` y se actualizan los imports de F14 y de sus tests; `LineChart` y `DataTable` se quedan (solo los usa el panel) |
| El **medidor** no es un componente de F14 | Hace falta una barra con estado en palabras | `results/TopeMeter.vue`, sobre `UiProgress` (que ya trae `role="progressbar"`) |

## ART-001 · Stack

Sin cambios: Vue 3 + TypeScript, Spring Boot (JdbcClient), Lucide para los iconos (`ArrowLeft`, `CircleCheck`, `TriangleAlert`, `OctagonX`, ya en el paquete). El único cambio de backend es una lectura nueva de solo lectura en `AiUsageController`.

## ART-002 · Modelo de datos

**Ninguno.** Ni migración, ni tabla, ni índice, ni permiso. El endpoint nuevo usa `UsageRepository.userMonthByTask(userId)` (F08): gasto del usuario en el mes de Madrid repartido por tarea, de más a menos, solo las que usó; `calls` cuenta las llamadas con éxito.

## ART-003 · API

```
GET /api/ai/usage/tasks      sesión → 200 { month: "2026-10", tasks: [{ task, calls, spendUsd? }] }
```
- El usuario es **siempre el de la sesión** (no hay parámetros ni ids: no hay IDOR que probar). Anónimo → 401 como cualquier ruta.
- Adulto: `spendUsd` en cada tarea. **Menor: sin `spendUsd`** (nunca importes), solo `task` y `calls`; el campo se omite con `@JsonInclude(NON_NULL)`.
- `tasks` vacío si no ha usado la IA este mes. Las tareas son el nombre de `AiTask` (`OUTLINE`, `LESSON`, `ASSESSMENT`, `TUTOR`, `EMBEDDING`, `INTAKE`, `IMAGE`); el cliente las traduce a palabras.
- Sin límite de frecuencia nuevo: es una lectura indexada (`ai_usage_user_idx`) de un solo usuario.
- Sin tocar: `GET /api/ai/usage` (sus claves siguen siendo las de F02/F08), `GET /api/results` y el resto.

Cambio en el cliente (`api/client.ts`): `Usage` gana `minors?: { id; displayName; calls; spendUsd; limitUsd; limitReached }[]` (el servidor ya lo envía a los adultos; hoy el tipo no lo declaraba) y nace `UsageTasks` + `api.getUsageTasks()`.

## ART-004 · Estructura de componentes (frontend)

```
ui/BackLink.vue          RouterLink a «/» con forma de botón secundario: icono ←, texto («Inicio» por defecto), aria-label propio
                         props: to='/', label='Inicio', ariaLabel='Volver al inicio'; los data-testid pasan por atributos        AC-1
ui/StatCard.vue          (movido de owner/) cifra + etiqueta + estado en palabras con icono                                       AC-4
ui/BarList.vue           (movido de owner/) reparto en barras con su etiqueta y su valor escritos                                 AC-6
ui/format.ts             (movido de owner/) usd, num, percent, dateOf…
results/TopeMeter.vue    props: label, spendUsd, limitUsd, reached → UiProgress + porcentaje + estado («Vas bien» < 80 %,
                         «Cerca del tope» 80–99 %, «Tope alcanzado» ≥ 100 %) con icono; tope 0 o ausente sin división por cero   AC-2, AC-7
results/CourseCard.vue   props: course, showMoney → PlantIllustration (fase por done/total) + título (RouterLink, sin subrayado) +
                         etiqueta de estado + barra «x de y piezas» + llamadas e importe + errores y pendientes en palabras        AC-5
results/taskLabels.ts    OUTLINE «Temario» · LESSON «Lecciones» · ASSESSMENT «Ejercicios y exámenes» · TUTOR «Tutor» ·
                         EMBEDDING «Material» · INTAKE «Asistente» · IMAGE «Ilustraciones» (una tarea desconocida sale tal cual como texto)
views/ResultsView.vue    compone: cabecera (h1 + BackLink), estados, tarjetas de cifras, medidor, gasto por tarea, «Tus peques» y cursos
views/{CourseStatusView,MaterialView,RedeemView,NewCourseView}.vue   cambian su enlace por <BackLink> (en «Nuevo curso», label «Cancelar»)
```
- Las tres peticiones (`getResults`, `getUsage`, `getUsageTasks`) van en paralelo y **cada una falla por separado**: si falta una, su bloque dice «No se pudo cargar» y los demás se ven (AC-9). Un menor no recibe `minors` ni importes.
- El porcentaje de la planta de un curso sale de `done/total` (sin piezas → sin porcentaje → semilla «Preparándose»), con `plantStage` de F11; la lista equivalente ya es la tarjeta (todo está escrito).
- Textos amables: «La IA te ha ayudado N veces este mes», «Llevas gastados X de tu tope de Y», «Todavía no tienes cursos…». Los menores leen «La IA te ha ayudado N veces» y nunca importes ni topes.
- CSS: clases `ui-stat*`, `ui-bars*`, `back-link`, `result-grid`, `result-card`, `tope-meter*` en `components.css`, con tokens (`--color-stat-ok|warn|bad`, de F14, ya en el test de contraste); objetivo táctil `var(--size-tap)`; sin animaciones (solo la anchura de la barra, dentro de `prefers-reduced-motion: no-preference`); la rejilla se apila en móvil.

## ART-005 · Estructura de carpetas

```
backend/src/main/java/es/vicai/studylab/ai/AiUsageController.java      + GET /api/ai/usage/tasks (record TaskUse, UsageTasks)
backend/src/test/java/…/ai/AiUsageTasksTest.java                       (nuevo) endpoint nuevo
frontend/src/ui/{BackLink.vue, StatCard.vue, BarList.vue, format.ts}
frontend/src/results/{TopeMeter.vue, CourseCard.vue, taskLabels.ts}
frontend/tests/ui/BackLink.spec.ts · tests/results/{TopeMeter,CourseCard}.spec.ts · tests/views/ResultsView.spec.ts (ampliado)
```

## ART-006 · Wireframe

```
┌──────────────────────────────────────────────────────────────────────┐
│  Resultados                                          [ ← Inicio ]     │
├──────────────────────────────────────────────────────────────────────┤
│ ┌ La IA te ha ┐ ┌ Gasto del mes ┐ ┌ Cursos listos ┐                    │
│ │ ayudado     │ │  0,50 USD     │ │      3        │                    │
│ │ 326 veces   │ │               │ │               │                    │
│ └─────────────┘ └───────────────┘ └───────────────┘                    │
│ ┌ Tu tope de este mes ────────────────────────────────────────────┐   │
│ │ ████████░░░░░░░░░░░░░░░░░░  0,50 de 1,00 USD · 50 %             │   │
│ │ ✔ Vas bien                                                       │   │
│ └──────────────────────────────────────────────────────────────────┘   │
│ ┌ En qué te ha ayudado ───────┐  ┌ Tus peques ─────────────────────┐   │
│ │ Lecciones   ██████████ 0,40 │  │ Hugo  ████░░ 0,25 de 2,00 ✔     │   │
│ │ Tutor       ████░░░░░░ 0,10 │  │       Vas bien · 12 veces       │   │
│ └─────────────────────────────┘  └─────────────────────────────────┘   │
│ Tus cursos                                                             │
│ ┌───────────────┐ ┌───────────────┐                                    │
│ │      🌳       │ │      🌱       │                                    │
│ │ Curso de alemán│ │ La célula    │   título = enlace claro             │
│ │ [Listo]       │ │ [En pausa]    │                                    │
│ │ ████ 42 de 42 │ │ ██░░ 8 de 20  │                                    │
│ │ 50 llamadas · │ │ 2 con error · │                                    │
│ │ 0,09 USD      │ │ 3 pendientes  │                                    │
│ └───────────────┘ └───────────────┘                                    │
│ Total de los cursos: 0,09 USD                                          │
└──────────────────────────────────────────────────────────────────────┘
Menor: sin «Gasto del mes», sin medidor, sin «Tus peques» ni importes; el reparto por tarea en veces.
```

## ART-007 · ADR

### ADR-92: El gasto por tarea del propio usuario es un endpoint aparte
- Motivo: `GET /api/ai/usage` tiene su contrato de privacidad fijado por test (claves exactas de `you`, también la del menor). Añadirle un campo cambiaría ese contrato y arrastraría sus tests; un endpoint de solo lectura (`/api/ai/usage/tasks`) con la consulta que ya existe desde F08 deja lo anterior intacto, y deja que la pantalla se siga viendo si una de las dos lecturas falla. El usuario sale siempre de la sesión: no hay parámetros ni ids que validar, y un menor nunca recibe importes.
- Alternativas descartadas: ampliar `you` con `byTask` (rompe el contrato y sus tests); que el cliente pida el panel del tutor (no es suyo y un menor no tiene acceso); calcularlo en el cliente a partir de otro dato (no existe).
- Revisitar si: hace falta el reparto por tarea de varios meses (entonces un parámetro de mes validado en el servidor).

### ADR-93: Los componentes de cifras (`StatCard`, `BarList`) y el botón «Volver» son de `ui/`
- Motivo: dos pantallas ya los usan (el panel del propietario y Resultados) y los componentes genéricos viven en `src/ui/` desde F10. Moverlos evita que Resultados dependa de la carpeta del propietario y deja un único sitio con el estilo (clases `ui-*`, tokens). `BackLink` sustituye cinco enlaces subrayados sueltos por un botón único, con `data-testid` y destino iguales, para cambiar su aspecto en un solo lugar. `LineChart` y `DataTable` se quedan en `owner/` porque solo el panel los usa.
- Alternativas descartadas: importar de `owner/` desde Resultados (acopla pantallas no relacionadas); copiar los componentes (dos sitios que cambian a la vez); una carpeta nueva `charts/` (otra categoría para dos ficheros).
- Revisitar si: un tercer sitio necesita `LineChart` o `DataTable` (entonces suben también).
