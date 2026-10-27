## US-F15 · Pulido de «Resultados» y botón «Volver» con flecha

> FASE 1. Acordado con el usuario el 27-10-2026 («si encaja»): medidor del tope, tarjetas de cifras, cursos como tarjetas de jardín, gasto por tarea, bloque de menores y un botón con flecha en lugar del enlace subrayado. Lo demás (D1–D9) son propuestas mías a confirmar.

### Como persona que usa Study Lab (adulto o menor)
### Quiero que la pantalla de Resultados se entienda de un vistazo y que volver al inicio sea un botón reconocible
### Para que no tenga que leer tres párrafos de texto para saber cuánto he gastado o cómo van mis cursos, y no me parezca una página de documento

Contexto: `ResultsView` enseña hoy tres bloques de texto («Este mes», «Tus cursos» y un total) con todo escrito en frases. El enlace «Volver al inicio» es un texto azul subrayado suelto, repetido en cinco pantallas (Resultados, Curso, Mi material, Tengo un código y Nuevo curso; en esta última con `data-testid="cancel-home"`). Los datos ya existen: `/api/results` (piezas hechas, con error y pendientes, llamadas e importe por curso; un menor no recibe importes) y `/api/ai/usage` (mes, llamadas, gasto y tope propios; para un adulto, también el uso de sus menores). El servidor ya sabe repartir el gasto de un usuario por tarea (`UsageRepository.userMonthByTask`, F08), pero solo lo enseña en el panel del tutor.

### Decisiones propuestas

| # | Decisión | Propuesta |
|---|----------|-----------|
| D1 | Botón «Volver» | Componente nuevo `src/ui/BackLink.vue`: un enlace con aspecto de **botón secundario** (borde y radio de `ui/`), icono ← de Lucide y texto «Inicio», 44 px de alto, sin subrayado. Lleva `aria-label="Volver al inicio"`. Reutiliza los tokens y componentes de F10/F11; sin colores nuevos. |
| D2 | Dónde se usa | En las **cinco** pantallas, en el mismo sitio (esquina de la cabecera), con un único componente. Conservan sus `data-testid` (`back-home` y `cancel-home`) y su destino `/`. En «Nuevo curso» el texto es «Cancelar» (es lo que hace), con la misma forma. |
| D3 | Medidor del tope (solo adultos) | Una **barra** con el gasto del mes sobre su tope («0,50 de 1,00 USD»), un porcentaje y un **estado en palabras con icono**: «Vas bien» (< 80 %), «Cerca del tope» (80–99 %) y «Tope alcanzado» (≥ 100 %). La barra nunca pasa del 100 %. Un menor **no ve importes ni topes**: ve solo cuántas veces le ayudó la IA. Se conserva el aviso de `limit-reached`. |
| D4 | Tarjetas de cifras | Tres tarjetas con icono: **veces que la IA ayudó** este mes, **gasto del mes** (solo adultos) y **cursos listos** (de los que tiene). Reutilizan `StatCard` (que hoy vive en `src/owner/`). |
| D5 | Cursos como tarjetas de jardín | Cada curso es una tarjeta con su **planta** (`PlantIllustration`, fase según `done/total`; sin piezas, semilla «Preparándose»), el título como **enlace claro sin subrayado**, la etiqueta de estado de siempre, una barra «42 de 42 piezas», y debajo, llamadas e importe (solo adultos). Los errores y pendientes se dicen con **palabras** («2 con error», «3 pendientes»). Tarjetas en rejilla que se apila en móvil. |
| D6 | Gasto por tarea | Un bloque con barras horizontales (`BarList`): **Temario, Lecciones, Ejercicios y exámenes, Tutor, Material, Asistente e Ilustraciones**. Para un adulto, en USD; para un menor, en número de veces (nunca importes). Para eso se añade un endpoint nuevo, `GET /api/ai/usage/tasks` (`{month, tasks:[{task, calls, spendUsd?}]}`), con la consulta que ya existe (`userMonthByTask`): **un endpoint nuevo y ninguna consulta nueva**; no toca el contrato de `/api/ai/usage`. Es el único cambio de backend. |
| D7 | «Tus peques» (solo adultos con menores) | Un bloque con cada menor: nombre, veces que usó la IA, gasto y su tope con la misma barra y estado que D3. Sale de `usage.minors`, que el servidor ya envía a los adultos. Con más de 0 menores solo. |
| D8 | Tono | Frases cortas y amables en lugar de jerga («326 llamadas a la IA» → «La IA te ha ayudado 326 veces este mes»). Los textos de los tests cambian donde cambia el texto a propósito. |
| D9 | Lo que no cambia | Rutas, `data-testid` existentes de Resultados (`results-view`, `month`, `limit-reached`, `results-error`, `results-empty`, `result-{id}`, `result-link-{id}`, `result-failed-{id}`, `result-cost-{id}`, `results-total`, `back-home`), el carácter de solo lectura, las reglas de privacidad de menores y los tokens. Sin librerías nuevas. |

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **BackLink:** `BackLink` es un enlace a `/` con aspecto de botón (flecha ← y texto), sin subrayado, 44 px de alto, nombre accesible «Volver al inicio» y foco visible. Lo usan las cinco pantallas con sus `data-testid` y su destino de antes; en «Nuevo curso» dice «Cancelar». |
| AC-2 | **Medidor (adulto):** muestra gasto y tope, un porcentaje y el estado en palabras con icono («Vas bien», «Cerca del tope», «Tope alcanzado») según < 80 %, 80–99 % y ≥ 100 %; la barra no pasa del 100 %; con tope 0 o sin tope no hay división por cero. `limit-reached` sigue saliendo cuando corresponde. |
| AC-3 | **Menor:** no ve ningún importe ni tope en ninguna parte de la pantalla (ni en el medidor, las tarjetas, las tarjetas de curso ni el gasto por tarea); ve veces que la IA le ayudó y sus cursos. Un test lo recorre buscando «USD» y cifras de dinero. |
| AC-4 | **Tarjetas de cifras:** veces que la IA ayudó, gasto del mes (solo adulto) y cursos listos, con los valores correctos; sin NaN con datos vacíos. |
| AC-5 | **Cursos:** cada curso es una tarjeta con su planta (semilla sin piezas, brote 1–39 %, árbol 40–99 %, árbol con frutos 100 %, la misma regla de `plantStage`), título como enlace a su página, estado en palabras, barra «x de y piezas», llamadas e importe (adulto) y los errores y pendientes en palabras. Mantiene los `data-testid` de D9. |
| AC-6 | **Gasto por tarea:** barras ordenadas de más a menos con su etiqueta en español; en USD para el adulto y en veces para el menor. `GET /api/ai/usage/tasks` devuelve el gasto por tarea de **quien llama** (nunca de otro usuario, sin parámetros); para un menor, sin `spendUsd`. Un test de backend lo comprueba, también que exige sesión y que `GET /api/ai/usage` no cambia. |
| AC-7 | **Tus peques:** un adulto con menores ve a cada uno con sus veces, su gasto y su tope y el estado en palabras; un adulto sin menores y un menor no ven el bloque. |
| AC-8 | **Texto plano:** títulos de curso y nombres de menores salen siempre como texto (nada de `v-html`), también hostiles; los títulos largos se parten sin desbordar. |
| AC-9 | **Estados:** cargando, error («No se pudieron cargar…») y vacío («Todavía no tienes cursos») con `UiState`, sin códigos del backend; si falla solo el uso (`/api/ai/usage`) los cursos se siguen viendo, y al revés. |
| AC-10 | **Diseño y accesibilidad:** colores solo de `tokens.css` (los pares nuevos, si los hay, entran en el test de contraste); un estado nunca solo con color; objetivos de 44 px; movimiento solo dentro de `prefers-reduced-motion: no-preference`; sin desbordamiento horizontal a 360–430 px. |
| AC-11 | **Sin regresiones:** los tests de F10 a F14 que dependen de estas pantallas siguen en verde (los que comprueban el texto del enlace o frases de Resultados se actualizan a propósito); no cambian rutas, API existente ni reglas de privacidad. |

### Casos borde
- Curso en cola o generándose (`total` 0): semilla «Preparándose», sin barra de piezas y sin porcentaje.
- Curso `FAILED` o `PAUSED`: su estado en palabras y los errores visibles; la planta sigue la fase de sus piezas hechas.
- Gasto mayor que el tope (puede pasar por el último cálculo): barra al 100 % y «Tope alcanzado».
- Tope personal fijado por el propietario (F14): el medidor usa el tope real que devuelve el servidor.
- Usuario sin llamadas este mes: «La IA todavía no te ha ayudado este mes», sin bloque de gasto por tarea (mensaje vacío).
- Adulto con menores sin gasto: cada menor aparece con 0 veces.
- Título de curso muy largo o con HTML: se parte y se ve como texto.
- `/api/ai/usage` o `/api/results` falla por separado: la otra mitad de la pantalla se sigue viendo.

### Fuera de alcance (v1)
- Gráficas por día o histórico de meses; exportar datos; filtros.
- Cambios en la pantalla de Inicio, el panel del propietario o el panel del adulto tutor (F08), ni nuevos temas de color.
- Más cambios de backend que el endpoint `GET /api/ai/usage/tasks`.
- Cambiar el texto o la forma de otros enlaces de la app.

### Definición de hecho
- Todos los AC y casos borde con test (frontend y, para el endpoint de tareas, backend); el test de privacidad del menor recorre la pantalla entera.
- Pasada en el navegador en escritorio y en móvil (360–430 px) con un adulto con menores y con un menor; `elementFromPoint` sobre los enlaces y la barra de navegación, y sin desbordamiento horizontal.
- `security-checklist` pasada (es un cambio pequeño de backend que toca datos de gasto); `llm-integration-review` no aplica (no llama a modelos).
- ADR nuevos en `ARCHITECTURE.md` (endpoint de gasto por tarea y componentes compartidos); README, CLAUDE y BACKLOG al día (F14-11 se cierra); informe en `sdd/reports/`.
