# F11 · Piel «Mundo que crece» — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F11-piel-mundo-que-crece.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 72) y la propuesta de F10 (`ART-F10-propuesta-rediseno.md`). **Solo frontend**: sin migraciones, endpoints ni cambios en `backend/`. Prototipo de referencia del Inicio: `sdd/design/F10/MundoC.dc.html` (las demás pantallas se diseñan directamente en código; el informe de la FASE 7 las compara con «Mapa» en el navegador).

## ART-001 · Stack

Todo el stack de F10 sigue (Vue 3 + TS, Pinia, Vue Router, Vitest, Reka UI, Lucide). Cambios en dependencias, con versión exacta y revisión de `npm audit`:

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Titular redondeado | `@fontsource-variable/fredoka` (nuevo) | Identidad «Mundo»; variable, un solo fichero latino autoalojado. Sustituye a Bricolage Grotesque y a Space Mono (las etiquetas pasan a Fredoka). |
| Lectura de lecciones | `@fontsource-variable/newsreader` (se mantiene) | Ya probada en F10 para leer; la identidad redondeada va en titulares y botones, no en el texto largo. |
| Ilustraciones | Componentes Vue con SVG propio, sin librería ni imágenes | Se colorean con clases que leen tokens (cumple «colores solo en `tokens.css`», cambia solo con el modo oscuro). |
| Camino | Función pura `roadPath` (curva suave por las paradas) + el SVG de `CourseMap` | Sin librería de gráficos (ADR-68 sigue valiendo). |

Se **retiran** `@fontsource-variable/bricolage-grotesque` y `@fontsource/space-mono`. Lista final: `@fontsource-variable/fredoka`, `@fontsource-variable/newsreader`, `lucide-vue-next`, `marked`, `pinia`, `reka-ui`, `vue`, `vue-router`. El test de entorno (`FRONTEND_DEPS`) se actualiza a propósito, con el motivo.

## ART-002 · Modelo de datos
**Ninguno.** La fase de la planta sale del porcentaje que ya devuelve `GET /api/study/progress` (`study.percentByCourse`).

## ART-003 · Endpoints
**Ninguno nuevo ni modificado.** Contratos HTTP intactos (AC-17).

## ART-004 · Estructura de componentes

```
styles/tokens.css      NUEVO juego de tokens «Mundo» (mismos NOMBRES del contrato de F10 + los del paisaje) · fonts.css · base.css · components.css · motion.css   AC-1, AC-2, AC-12, AC-14
world/ (ilustración, solo presentación)                                                                                        AC-3, AC-8
├─ plantStage.ts       función pura: porcentaje → 'seed' | 'sprout' | 'tree' | 'fruit' (fronteras 0 / 1–39 / 40–99 / 100; sin porcentaje = 'seed')
├─ PlantIllustration.vue   las cuatro fases en SVG (clases con tokens: --color-leaf, --color-trunk, --color-fruit, --color-soil)
├─ Landscape.vue       cielo, sol, nubes y colinas (decorativo, aria-hidden); versión nocturna por tokens
├─ Mascot.vue          la mascota (marca, estados vacíos); título accesible o decorativa
└─ Wanderer.vue        el personaje de «tú estás aquí» en el camino
map/                                                                                                                           AC-5, AC-6, AC-7
├─ layoutMap.ts        sin cambios de contrato (paradas, estados, zonas, plegado); constantes de espacio retocadas si el camino lo pide
├─ roadPath.ts         NUEVO función pura: puntos → camino suave (curvas, sin diagonales que crucen nombres); testeada sin navegador
├─ CourseMap.vue       zonas como colinas, camino curvo, paradas, lista equivalente (mismo contrato de ids y roles que F10)
├─ MapStation.vue      piedra (círculo) y puerta (cuadrado, con `data-kind`), nombre, «tú estás aquí» con Wanderer
└─ MapLegend.vue
components/            CoursesPanel.vue → cada curso con su PlantIllustration (`plant-<id>` con `data-stage`), mismos testids y barra de progreso;
                       ContinueCard.vue → restilada en el paisaje (mismo texto y enlace)
layouts/               NavBar.vue (mascota como marca), AppShell.vue, AuthShell.vue (paisaje de fondo), Breadcrumb.vue
ui/                    UiButton, UiTag, UiNotice, UiProgress, UiState, UiField, UiTabs, UiDialog: mismo API, otra forma
views/                 las mismas, sin cambios de estructura salvo lo que pida la piel
```

Cobertura: AC-1/2 `tokens.css`; AC-3 `plantStage` + `PlantIllustration` + `CoursesPanel`; AC-4 `HomeView`/`ContinueCard`; AC-5 a AC-7 `CourseMap`/`roadPath`/`layoutMap`; AC-8 `world/`; AC-9 y AC-10 las vistas y `LessonReader`; AC-11 `layouts/`; AC-12 prueba de contraste (pares nuevos); AC-13 pasada en navegador y `layoutMap` estrecho; AC-14 `motion.css`; AC-15 prueba de seguridad ampliada; AC-16 test de entorno; AC-17 toda la suite.

### Comportamiento
- **Fase de la planta (`plantStage`):** `undefined`/`null` → semilla («Preparándose»); `0` → semilla; `1..39` → brote; `40..99` → árbol; `100` → árbol con frutos. Función pura y total (valores fuera de rango se acotan). El texto de la fase («Semilla», «Brote», «Árbol», «Árbol con frutos») va siempre junto a la ilustración.
- **Camino (`roadPath`):** recibe las posiciones que ya calcula `layoutMap` (y los desvíos hacia un lado en los cambios de fila, como en F10) y devuelve un `d` de SVG con esquinas redondeadas. En estrecho, curva suave en una columna. No usa el DOM.
- **Zonas:** cada tema es una colina (rectángulo redondeado con relieve suave) con su título al lado contrario de la línea, como en F10, para no cruzarla.
- **Paradas:** lección = piedra redonda (`circle[data-kind="lesson"]`), examen = puerta (`rect[data-kind="exam"]`); estado por clase (`--done` crecida/coloreada, `--now`, `--todo`), más texto en la lista. «Tú estás aquí» con `Wanderer` y la etiqueta de texto.
- **Accesibilidad:** igual que F10 (`role="img"` con resumen, lista equivalente, `aria-current="step"`, plegado). Las ilustraciones decorativas llevan `aria-hidden`; las que informan, un título.

## ART-005 · Estructura de carpetas

```
frontend/src/
├─ styles/            tokens.css (NUEVO juego «Mundo»), fonts.css (Fredoka + Newsreader), base.css, components.css, motion.css
├─ world/             plantStage.ts, PlantIllustration.vue, Landscape.vue, Mascot.vue, Wanderer.vue
├─ map/               layoutMap.ts, roadPath.ts, CourseMap.vue, MapStation.vue, MapLegend.vue
├─ ui/ layouts/ components/ views/    los de F10, con la piel nueva
frontend/tests/
├─ world/             plantStage.spec.ts, world.spec.ts (ilustraciones: título/aria, sin HTML crudo)
├─ map/               roadPath.spec.ts (+ los de F10, actualizados)
├─ design/            contrast.spec.ts (pares nuevos), tokens.spec.ts (fuentes nuevas; sin colores a mano)
sdd/design/F11/       prototipos de referencia de «Mundo» (el de Inicio de F10 se copia aquí)
```

## ART-006 · Wireframes

Inicio (ancho), sobre el paisaje (cielo arriba, colinas abajo):
```
┌ (mascota) Study Lab ─ Mapa · Mi material · Resultados ──────── Ana ┐   ☀
│ Mira cuánto ha crecido tu mundo            ☁        ☁              │
│ ┌ SIGUIENTE PARADA ────────────┐                                   │
│ │ Capítulo 2.1 · Historia ant. │   🌱 semilla   🌿 brote   🌳 árbol  │   (ilustraciones SVG, sin emoji real)
│ │ [Seguir leyendo →]           │   Inglés       Fracciones  Historia │
│ └──────────────────────────────┘   preparándose  8 %       38 %     │
│ ───────────── colinas ───────────────────────────────────────────── │
│ Tu espacio · Mis menores (tarjetas redondeadas)                     │
└─────────────────────────────────────────────────────────────────────┘
```
Curso, en estrecho (una columna): colina por tema; el camino serpentea entre piedras y puertas, nombres a la derecha; el personaje marca «tú estás aquí»; barra inferior redondeada. Lección, edición, resultados y panel: mismas estructuras de F10 con la piel nueva (tarjetas redondeadas, relieve suave).

## ART-007 · ADR (se copiarán a `ARCHITECTURE.md` en la FASE 5)

### ADR-73: «Mundo» sustituye a «Mapa» en su rama y conserva los nombres de los tokens
- Motivo: así las vistas, los componentes y casi todos los tests de F10 se reutilizan; solo cambian los valores y lo que el diseño pide. El usuario compara dos ramas, no dos pieles dentro de una.
- Alternativas descartadas: selector de piel (doble mantenimiento, decisión de producto y de almacenamiento que nadie pidió); una rama que copie las vistas (se desincroniza).
- Revisitar si: se decide que convivan (entonces, tokens por tema de datos y pruebas para ambas).

### ADR-74: La fase de la planta es una función pura del porcentaje, y siempre se dice con texto
- Motivo: es lo único que la ilustración sabe del curso; una función total y probada en sus fronteras (0, 1, 39, 40, 99, 100, ausente) evita sorpresas, y el texto asegura que no depende de ver la ilustración ni el color.
- Alternativas descartadas: guardar la fase en el servidor (no hace falta); decidirla dentro del componente (no se prueba sin montar).
- Revisitar si: se quieren más fases o fases por tipo de curso.

### ADR-75: Las ilustraciones son componentes SVG coloreados por clases con tokens
- Motivo: la regla «colores solo en `tokens.css`» (F10) debe seguir valiendo para las ilustraciones, y así el modo oscuro y un cambio de piel las alcanzan sin tocarlas; el SVG se escribe como plantilla Vue, nunca como cadena.
- Alternativas descartadas: imágenes SVG/PNG en ficheros (colores fuera de los tokens, una petición más por imagen); atributos `fill` con colores fijos (rompen la prueba y el modo oscuro).
- Revisitar si: el número de ilustraciones crece tanto que convenga un sistema de iconos.

### ADR-76: El camino se curva con una función pura sobre la colocación existente
- Motivo: la posición de las paradas ya está resuelta y probada (`layoutMap`); curvar el trazo es presentación, y una función pura de puntos a `d` se prueba sin navegador (sin solapes, sin salir del ancho). El contrato accesible no cambia.
- Alternativas descartadas: una colocación nueva para el paisaje (duplica lógica y pruebas); una librería de rutas (peso y superficie sin necesidad).
- Revisitar si: aparecen ramas o prerrequisitos entre lecciones (ADR-68).

### ADR-77: Fredoka para titulares y etiquetas, Newsreader para leer; se retiran Bricolage y Space Mono
- Motivo: identidad redondeada sin perder la lectura cómoda; menos peso total (se quitan tres ficheros de fuente y se añade uno).
- Alternativas descartadas: una sola familia redondeada también para leer (cansa en lecciones largas); mantener las tres de F10 (peso y dos identidades mezcladas).
- Revisitar si: la lectura de lecciones se queja de la tipografía (probar otra de lectura redondeada).

## Decisiones pendientes de tu respuesta
1. **Tipografías:** propongo Fredoka (titulares y etiquetas) y mantener Newsreader para leer. Alternativa: otra redondeada para leer (más cohesión, menos cómoda en textos largos).
2. **Fases de planta:** cuatro, con las fronteras 0 / 1–39 / 40–99 / 100. Alternativa: tres (sin «árbol con frutos»; el 100 % sería solo árbol).
3. **Prototipos:** como pediste, no se prototipan antes; las pantallas se diseñan en código y el informe de la FASE 7 las compara con «Mapa» en el navegador. El riesgo es rehacer ilustraciones si no te gustan: se mitiga entregando primero Inicio y camino y revisándolos contigo antes de tocar el resto.
