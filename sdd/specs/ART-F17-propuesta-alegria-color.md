# F17 · Alegría y colorido en toda la app — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F17-alegria-y-color.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 94) y `PROJECT.md`. **Solo frontend**: sin backend, sin migración, sin tareas de IA y **sin librerías nuevas** (confeti y destellos son CSS).

## Ajustes a la FASE 1 que salen de leer el código

| Ajuste | Motivo | Efecto |
|--------|--------|--------|
| La **zona** se calcula por la **ruta** con una función pura (`layouts/zone.ts`), no con `meta` en el router | `NavBar` ya deriva su sección de `route.path`; una sola función compartida por la barra, el marco y los tests evita dos fuentes de verdad y no toca `router/index.ts` | D1/D6: Mapa = Inicio, Nuevo curso, Curso, Lección, Intentos, Editar, Código y paneles del tutor; Material, Resultados y Propietario, la suya; Login, registro y demás, **neutra** |
| El color de zona llega a las páginas como **variables CSS** (`--zone-fill`, `--zone-on`, `--zone-ink`) puestas por `AppShell` en `.shell[data-zone]` | Así ninguna vista lleva estilos por zona: tarjetas, cabeceras y etiquetas leen la variable; cambiar un acento es cambiar un token | D6, AC-7 |
| «Mapa» pasa de naranja a **verde** en la barra; el **naranja** queda solo para el botón primario | Hoy el enlace activo y el botón primario son del mismo naranja y no se distinguen; con un color por zona el primario es la única «llamada a la acción» | D1; ningún botón primario cambia de color |
| El **saludo por franja del día** usa el reloj del navegador y solo cambia la palabra («Buenos días», «Buenas tardes», «Buenas noches») | Es un detalle sin datos nuevos, no hay petición ni hora del servidor; nunca nombra nada más que el primer nombre ya visible | D9, AC-10 |
| El **confeti** son 12 elementos `<span>` decorativos (`aria-hidden`) con animación CSS de una sola pasada | Sin librería (ADR-90) y sin `canvas`; el texto «¡Hecho!» siempre sale, también sin movimiento | D9, AC-5, AC-10 |
| La burbuja del **icono** usa el relleno de su zona; el **texto** de la zona (títulos, etiquetas) usa su tono `ink` (4,5:1) | Un relleno vivo no sirve como color de texto; separar `fill`, `on` e `ink` es lo que hace pasar el contraste en claro y en noche | AC-2 |

## ART-001 · Stack

Sin cambios: Vue 3 + TypeScript, Reka UI, Lucide (iconos ya en el paquete: `Map`, `BookOpenText`, `BarChart3`, `ShieldCheck`, `Sparkles`, `PartyPopper`, `Sun`, `Moon`, `UserRound`), `Mascot` y `PlantIllustration`. El test de entorno de dependencias **no cambia**.

## ART-002 · Modelo de datos y API

**Ninguno.** Sin endpoints, migraciones ni estado nuevo en el servidor.

## ART-003 · Tokens (`tokens.css`, único sitio con colores)

Doce tokens por esquema (claro y noche), tres por zona. Valores propuestos con su contraste ya calculado (todos pasan; se fijan en `contrast.spec.ts` en la FASE 4):

| Zona | `--color-zone-*` relleno | `--color-on-zone-*` texto sobre relleno | `--color-zone-*-ink` texto sobre superficie |
|------|--------------------------|------------------------------------------|---------------------------------------------|
| Mapa (verde) | claro `#3fae6a` · noche `#4cc38a` | `#0b2a18` (5,5:1) · `#0b1623` (8,2:1) | `#166534` (7,1:1) · `#6ee7a8` (9,0:1) |
| Material (azul) | `#7db7ff` · `#8cb8ff` | `#0b1d3a` (8,1:1) · `#0b1623` (9,0:1) | `#1d4ed8` (6,7:1) · `#8cb8ff` (6,9:1) |
| Resultados (ámbar) | `#f5b82e` · `#ffd966` | `#3b2300` (8,3:1) · `#0b1623` (13,3:1) | `#92400e` (7,1:1) · `#fcd34d` (9,6:1) |
| Propietario (violeta) | `#b79cff` · `#c4a5ff` | `#1e0f4d` (7,5:1) · `#0b1623` (8,8:1) | `#6d28d9` (7,1:1) · `#c4a5ff` (6,7:1) |

Los contrastes de `ink` están medidos sobre la superficie (`--color-surface`); sobre `--color-paper` y `--color-sky` quedan entre 5,5:1 y 11,2:1 (también cubiertos por el test). Sin tokens de degradado nuevos: el degradado de la barra y del logo mezcla `--color-sky`, `--color-paper`, `--color-accent` y `--color-now` (ya existentes) con `color-mix`, y cae a color liso si el navegador no lo soporta. La variante «neutra» usa los tokens actuales (`--color-primary` y `--color-ink`).

## ART-004 · Estructura de componentes (frontend)

```
layouts/zone.ts          zoneOf(path): 'map' | 'material' | 'results' | 'owner' | 'neutral' (pura, con tests)          AC-3, AC-7
layouts/greeting.ts      firstName(displayName, max = 20): texto sin HTML, recortado con «…», vacío → ''; daypart(date)  AC-4, AC-10
layouts/AppShell.vue     pone data-zone="{zoneOf(route.path)}" en .shell                                                  AC-7
layouts/NavBar.vue       enlaces con burbuja de icono (<span class="nav-bubble">), data-zone por enlace, chip de saludo,
                         «Dejarlo por hoy» y «Cerrar sesión» como botones discretos; mismos data-testid                     AC-3, AC-4, AC-6
world/Mascot.vue         clase .mascot--wave y gesto CSS al :hover y :focus-visible del logo (solo no-preference)         AC-5
ui/PageHeader.vue        cabecera de página: burbuja de icono de zona + <h1> (slot) + acciones (slot); franja de color      AC-7
ui/UiTag.vue             variantes por estado con icono (ok, warn, bad, info) y por zona; nunca solo color                 AC-9
ui/Celebration.vue       «¡Hecho!» con role="status" + confeti decorativo (12 <span aria-hidden>), una pasada             AC-10
world/PlantIllustration  clase .plant--bloom (destello de una pasada al llegar a la fase 100)                              AC-10
views/*                  PageHeader en Inicio, Mi material, Resultados, Propietario, Curso, Lección, Nuevo curso, Código,
                         Login/Registro y paneles del tutor; sin cambiar data-testid, rutas ni flujos                      AC-8
```
- **CSS** (`components.css`): `.shell[data-zone='map'] { --zone-fill: var(--color-zone-map); --zone-on: var(--color-on-zone-map); --zone-ink: var(--color-zone-map-ink); }` y análogos; `.nav-link[data-zone=…]` fija las mismas variables para su burbuja; `.ui-card { border-top: 6px solid var(--zone-fill) }` (la forma, no solo el color, marca la zona). `nav-link.is-active` = relleno de zona + `box-shadow` inferior grueso + `aria-current`. Barra inferior móvil: mismos enlaces, burbuja de 2,25 rem, etiqueta debajo y el activo relleno.
- **Movimiento:** todo `@keyframes` (gesto de mascota, destello, confeti, saludo) dentro de `@media (prefers-reduced-motion: no-preference)`; fuera de él los elementos existen pero estáticos. Un test lee `components.css` y `motion.css` y comprueba que no hay `animation:` ni `transition:` fuera de ese bloque.
- **Pantallas migradas (D7):** Nuevo curso, Tengo un código, Curso, Lección, Login/Registro y panel del tutor pasan a `PageHeader`, `ActionCard`, `Disclosure`, `EmptyNote`, `StatCard` y `BackLink`; los formularios usan `UiField`. Se conservan todos los `data-testid`, los textos de privacidad y los avisos legales.
- **Privacidad:** el saludo y la barra nunca muestran dinero, topes ni datos de otros usuarios; un menor no recibe ningún elemento nuevo con esa información. Nombres de personas, cursos, documentos y temas, siempre como texto.

## ART-005 · Estructura de carpetas

```
frontend/src/layouts/{zone.ts, greeting.ts (nuevos), AppShell.vue, NavBar.vue (cambian)}
frontend/src/ui/{PageHeader.vue, Celebration.vue (nuevos), UiTag.vue (cambia)}
frontend/src/world/{Mascot.vue, PlantIllustration.vue (cambian)}
frontend/src/styles/{tokens.css, components.css, motion.css (cambian)}
frontend/src/views/*.vue (los listados en ART-004)
frontend/tests/{layouts/zone,layouts/greeting,layouts/NavBarF17,ui/PageHeader,ui/Celebration,ui/UiTagF17,design/f17-tokens,design/f17-motion,views/ZonasF17}.spec.ts
frontend/tests/design/contrast.spec.ts (ampliado con las 12 parejas ×2 esquemas)
```

## ART-006 · Wireframes

```
BARRA SUPERIOR (escritorio)
┌─ degradado cielo ─────────────────────────────────────────────────────────────────────┐
│ ( :) Study Lab )  (🗺 Mapa ▰▰▰)  (📖 Mi material)  (📊 Resultados)  (🛡 Propietario)        │
│   logo con degradado   activo: relleno VERDE + sombra gruesa abajo      cada burbuja con su color │
│                                         ¡Hola, victor!  (🌙 Dejarlo por hoy)  (⎋ Salir)    │
└────────────────────────────────────────────────────────────────────────────────────────┘

BARRA INFERIOR (móvil, 360–430 px)
┌────────────────────────────────────────────┐
│  🗺 Mapa   📖 Material   📊 Resultados   🛡  │  ← burbujas de color; el activo, relleno + forma
└────────────────────────────────────────────┘

PÁGINA CON ZONA (Mi material, azul)
┌────────────────────────────────────────────┐
│ ▓▓ franja azul ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ (📖)  Mi material                [← Inicio] │
│ ┌─────────────────────────────────────────┐ │
│ ═ borde superior azul de 6 px ═════════════ │
│ │ ...                                      │ │
└────────────────────────────────────────────┘

LECCIÓN HECHA
┌ ¡Hecho! 🎉  (confeti 1 vez; con «reducir movimiento», solo el texto) ┐
```

## ART-007 · Plan de pruebas (para la FASE 4)

| Test | Cubre |
|------|-------|
| `layouts/zone.spec` | `zoneOf` para todas las rutas (incluida `/login` neutra y una ruta desconocida) · AC-3, AC-7 |
| `layouts/greeting.spec` | primer nombre, vacío, 60 caracteres, HTML, apóstrofo; `daypart` en los límites · AC-4, AC-10 |
| `layouts/NavBarF17.spec` | burbujas, `aria-current` solo en el activo, «Propietario» solo para el propietario, testids, chip de saludo como texto · AC-3, AC-4 |
| `ui/PageHeader`, `ui/UiTagF17`, `ui/Celebration` | roles, estado con icono o texto, confeti `aria-hidden`, texto «¡Hecho!» siempre · AC-9, AC-10 |
| `design/f17-tokens` | 12 tokens ×2 esquemas, sin `#hex` fuera de `tokens.css` · AC-1 |
| `design/contrast.spec` (ampliado) | texto sobre relleno, `ink` sobre superficie/paper/sky, 3:1 de elementos, en claro y noche · AC-2 |
| `design/f17-motion` | todo movimiento dentro de `prefers-reduced-motion: no-preference`; objetivos con `--size-tap` · AC-5, AC-6 |
| `views/ZonasF17` | cada vista pinta su zona y su `PageHeader`; Nuevo curso, Código, Curso, Lección, Login y panel del tutor conservan sus testids; un menor no ve dinero; entradas hostiles como texto · AC-7, AC-8, AC-11 |
| Existentes F01–F16 | siguen en verde; se actualizan solo los que comprueban marcado o frases que cambian a propósito · AC-12 |

## ART-008 · Orden de trabajo y riesgos

1. Tokens y contraste → 2. `zone.ts`, `greeting.ts` y `AppShell` → 3. `NavBar` y barra móvil → 4. `PageHeader`, `UiTag`, `Celebration`, `PlantIllustration` → 5. páginas ya pulidas (Inicio, Material, Resultados, Propietario) → 6. páginas pendientes → 7. pasada en el navegador.
- **Riesgo de alcance:** diez pantallas. Cada paso deja la app funcionando; si hace falta, F17 se entrega en dos commits (barra y tokens; pantallas).
- **Riesgo de regresión:** muchos tests de vistas fijan marcado; se actualizan solo los que cambian de forma a propósito y se anota cuáles en el informe.
- **Riesgo visual:** la pasada en el navegador es obligatoria (F10, F14 y F15 encontraron fallos que los tests de DOM no ven: solapes, barra inferior tapada, desbordes a 390 px).

## ART-009 · ADR propuestos (entran en `ARCHITECTURE.md` en la FASE 5)

- **ADR-95: el color de zona es una variable CSS puesta por el marco desde la ruta.** Motivo: una sola fuente de verdad (`zoneOf`) para barra, páginas y tests; las vistas no llevan estilos por zona. Alternativas descartadas: `meta.zone` en cada ruta (dos fuentes que se desincronizan), clases por vista (estilos duplicados). Revisitar si: una pantalla debe mostrar dos zonas a la vez.
- **ADR-96: tres tonos por zona (`fill`, `on`, `ink`) y el estado nunca solo con color.** Motivo: el relleno vivo no es legible como texto; separar tonos permite AA en claro y noche, y la forma (borde grueso, icono, texto) lleva el significado. Alternativas descartadas: un solo color por zona (falla el contraste), colores a mano por componente (rompe el test de tokens). Revisitar si: se añaden más zonas o un tema de alto contraste.
- **ADR-97: la alegría es CSS, de una pasada y solo con movimiento permitido.** Motivo: confeti, destello y gesto de mascota no deben añadir dependencias ni ocultar información; sin movimiento, el texto sigue. Alternativas descartadas: librería de confeti o Lottie (dependencia y `canvas` inaccesible), animaciones continuas (distraen al estudiar). Revisitar si: se quiere personalizar la celebración por usuario.
