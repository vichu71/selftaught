# F10 · Rediseño del frontend («Mapa») — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F10-rediseno-frontend.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 66) y `PROJECT.md`. Prototipos: `sdd/design/F10/`. **El backend y la base de datos no cambian**: F10 solo toca `frontend/`, el test de entorno y la documentación.

## ART-001 · Stack

Vue 3 + TypeScript, Pinia, Vue Router y Vitest siguen igual. Dependencias nuevas (todas con versión **exacta**, fijada y revisada con `npm audit` en la FASE 6):

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Primitivas accesibles | `reka-ui` | Diálogo, pestañas y menú con teclado, foco y ARIA resueltos y sin estilo propio: se visten con nuestros tokens. Sustituye a `confirm()` y a botones sueltos. |
| Iconos | `lucide-vue-next` | SVG como componentes Vue (sin fuente de iconos, sin petición externa); se importa solo cada icono usado (el empaquetado descarta el resto). |
| Tipografías | `@fontsource-variable/bricolage-grotesque`, `@fontsource-variable/newsreader`, `@fontsource/space-mono` | Autoalojadas dentro del paquete (la política `default-src 'self'` no cambia), `font-display: swap`, solo los pesos y los subconjuntos latinos usados. |
| Mapa | SVG propio en un componente Vue, **sin librería de gráficos** | Es una línea con paradas: una función pura calcula posiciones y el componente las pinta con elementos Vue (nunca cadenas ni `v-html`). |
| Estilos | CSS propio con variables (tokens) y clases por componente | Sin Tailwind ni framework de CSS: la piel se cambia en un fichero. |

Sin cambios en backend, API, Docker ni nginx (las fuentes salen del mismo origen; la política de seguridad ya las permite). Se **amplía a propósito** el test de entorno `scripts/tests/environment.test.mjs` (hoy exige exactamente `marked`, `pinia`, `vue`, `vue-router`), con el motivo en el propio test.

## ART-002 · Modelo de datos

**Ninguno.** No hay migración. El mapa y el Inicio se calculan en el cliente con lo que ya devuelven:
- `GET /api/courses` y `GET /api/study/progress` (lista y porcentaje por curso);
- `GET /api/courses/{id}` (`CourseDetail`: temas → capítulos → `PieceRef` con `status` y `outdated`, estado del curso, `stopReason`, `version`, `copy`);
- `GET /api/courses/{id}/study` (`StudyOverview`: lecciones hechas, exámenes con mejor nota y si están superados).

Sin cambios en tipos de `client.ts` salvo lo que ya existe.

## ART-003 · Endpoints

**Ninguno nuevo ni modificado.** Contratos HTTP intactos (AC-17); los tests del backend no se tocan. Si en la FASE 6 aparece un dato que falta para el Inicio («lo último que estudié»), se **anota en `BACKLOG.md`** y se resuelve en el cliente (ADR-67), no se añade un endpoint.

## ART-004 · Estructura de componentes

```
App.vue
├─ layouts/AppShell.vue            marco con navegación        AC-3
│   ├─ NavBar.vue                  cabecera (≥ 768 px) y barra inferior (móvil), aria-current
│   ├─ Breadcrumb.vue              migas / «volver»
│   └─ <RouterView/>
└─ layouts/AuthShell.vue           marco reducido de acceso    AC-3
ui/ (componentes base, solo presentación; nada de datos de negocio)               AC-1, AC-2, AC-16
├─ UiButton.vue  UiTag.vue  UiCard.vue  UiField.vue (etiqueta + error)  UiNotice.vue (info|atención|error)
├─ UiProgress.vue (barra y segmentos, role=progressbar)   UiTabs.vue (Reka)   UiDialog.vue (Reka, confirmación)
├─ UiState.vue                     carga / vacío / error homogéneos
└─ UiIcon.vue                      envoltorio de Lucide con tamaño y etiqueta
map/                                                                               AC-4, AC-5, AC-6, AC-14
├─ CourseMap.vue                   SVG + lista equivalente; recibe CourseDetail + StudyOverview
├─ MapStation.vue                  una parada (círculo/cuadrado, estado, foco, enlace)
├─ MapLegend.vue
└─ layoutMap.ts                    función pura: (temas, estudio, modo) → paradas, tramos y alto
views/ (las existentes, rediseñadas con ui/ y map/)                                AC-7, AC-8
├─ HomeView.vue   → ContinueCard.vue (sigue donde lo dejaste), CoursesPanel.vue (líneas con progreso), StudentsPanel.vue
├─ CourseStatusView.vue → CourseMap.vue + ficha «Lo que acordamos» + avance
├─ PieceView.vue   → LessonReader.vue (lectura, AC-9), ExercisePractice.vue, ExamTake.vue, TutorChat.vue (AC-10)
├─ CourseEditView.vue → pestañas Temario | Compartir (UiTabs) con LessonEditor.vue y ShareCodes.vue
└─ Login, Register, VerifyEmail, ForgotPassword, ResetPassword (AuthShell), Material, NewCourse, Results, Attempts,
   Redeem, GuardianStudent/Course/Thread
styles/ tokens.css, base.css, fonts.css, motion.css
```

Cobertura: AC-1 `tokens.css`; AC-2 `ui/`; AC-3 `layouts/`; AC-4 a AC-6 `map/`; AC-7 Inicio; AC-8 todas las vistas; AC-9 `LessonReader`; AC-10 `TutorChat`/`HomeView`; AC-11 prueba de seguridad ampliada; AC-12 dependencias y test de entorno; AC-13 test de contraste sobre `tokens.css`; AC-14 pasada en navegador + `layoutMap` en modo estrecho; AC-15 `motion.css`; AC-16 `UiState`; AC-17 toda la suite.

### Comportamiento del mapa (`layoutMap`)
- **Entrada:** temas en orden, sus capítulos con lección y ejercicios, el examen del tema, y los hechos/superados de `StudyOverview`. **Salida:** paradas `{id, kind: lesson|exam, topicId, label, state, x, y, route}`, tramos de la línea y alto total.
- **Paradas:** una por lección (círculo) y por examen de tema (cuadrado). Los ejercicios cuelgan de su lección (no son parada): la parada muestra «ejercicios» como dato secundario.
- **Estado de una parada:** `done` (lección hecha o examen superado), `now` (la primera lección **lista y sin hacer**, o el examen listo cuyo tema ya está completo), `todo` (el resto), más marcas independientes: `queued` (pieza `PENDING`), `failed` (`FAILED`), `outdated` ("la lección cambió"). Hay un único `now` por curso; si el curso se está generando y la primera pieza aún no existe, no hay `now` y el mapa lo dice con texto.
- **Colocación:** modo ancho = una **zona por tema**, apiladas en vertical, con sus paradas en fila y la línea girando a 45° al cambiar de fila; modo estrecho (< 640 px) = una sola columna vertical. Así el ancho nunca desborda y un curso de 150 piezas crece solo en alto. Cada zona se puede plegar; los temas lejanos de `now` empiezan plegados cuando hay más de 40 paradas.
- **Accesibilidad:** el `<svg>` va con `role="img"` y un `aria-label` con el resumen; **debajo** va la lista `<ol>` real (la que ya existe como `CourseOutline`, reescrita) con las mismas paradas, enlaces y estados en texto. Tabulación por la lista; la parada `now` lleva `aria-current="step"`. Las etiquetas de las paradas son texto Vue (se escapa solo), nunca HTML.

## ART-005 · Estructura de carpetas

```
frontend/src/
├─ styles/            tokens.css (colores, espacios, radios, bordes, sombras, tipografías, claro y oscuro)
│                     base.css (reseteo, foco visible, enlaces, lectura) · fonts.css (@font-face locales) · motion.css (reduced-motion)
├─ ui/                componentes base (ART-004)
├─ layouts/           AppShell.vue, AuthShell.vue, NavBar.vue, Breadcrumb.vue
├─ map/               CourseMap.vue, MapStation.vue, MapLegend.vue, layoutMap.ts
├─ components/        los actuales, rediseñados; nuevos: ContinueCard.vue, LessonReader.vue
├─ views/             las actuales
├─ api/ stores/ router/    sin cambios de contrato
└─ assets/fonts/      (si el empaquetador no las toma de node_modules)
frontend/tests/
├─ ui/ map/ layouts/  pruebas de los componentes nuevos y de layoutMap
├─ views/ components/ las existentes, actualizadas
└─ design/            contrast.spec.ts (pares de color de tokens.css, claro y oscuro), tokens.spec.ts (nada de colores a mano)
sdd/design/F10/       prototipos de referencia
```

## ART-006 · Wireframes

Inicio (ancho):
```
┌ Study Lab ─ Mapa · Mi material · Resultados ─────────────── (A) Ana ┐
│ Estás [aquí].  Ana, llevas 5 de 12 paradas de Historia antigua.      │
│ ┌ LÍNEA 1 · HISTORIA ANTIGUA ────────────────────────┐ ┌ SIGUIENTE ┐│
│ │ ●──●──■   ◉──●──■   ○──○──■     (zonas por tema)     │ │ El Nilo…  ││
│ │ tema I     tema II   tema III                        │ │ [Entrar →]││
│ └──────────────────────────────────────────────────────┘ ├───────────┤│
│  (otras líneas: lista con barra de progreso)             │ ¿Otra     ││
│                                                          │ línea?    ││
│                                                          ├───────────┤│
│                                                          │ Mis menores││
└──────────────────────────────────────────────────────────┴───────────┘
```
Móvil: cabecera mínima, "Estás aquí", mapa vertical (una columna), tarjeta «siguiente parada» y barra inferior `Mapa | Material | Resultados`. Curso, lección con tutor, edición y compartir: según los prototipos `MapaCurso`, `MapaLeccion`, `MapaEdicion` y `MapaMovil`. En la lección el tutor es un panel lateral en ancho y una hoja inferior en móvil.

## ART-007 · ADR (se copiarán a `ARCHITECTURE.md` en la FASE 5)

### ADR-67: El Inicio y el mapa se calculan en el cliente con los datos que ya hay; "sigue donde lo dejaste" = la primera lección lista sin hacer del curso terminado con más avance
- Motivo: la lista de cursos ya llega ordenada y `StudyOverview` dice qué está hecho; no hace falta endpoint ni migración. Mantener el backend intacto hace que F10 sea reversible y que sus tests no se muevan.
- Alternativas descartadas: guardar la última lección vista (columna y endpoint nuevos); calcularla en el servidor.
- Revisitar si: el usuario quiere de verdad "lo último que estudié" entre varios cursos (entonces, un campo `lastStudiedAt` en la lista de cursos).

### ADR-68: El mapa es SVG propio con una función de colocación pura y una lista equivalente debajo
- Motivo: es una línea con pocas formas; una función pura es testeable sin navegador y el SVG con elementos Vue escapa texto por construcción (AC-11). La lista debajo da teclado y lector de pantalla sin trabajo extra (AC-6).
- Alternativas descartadas: librería de grafos o de diagramas (peso y superficie de seguridad sin necesidad); canvas (no accesible); componer el SVG como cadena (riesgo de inyección).
- Revisitar si: aparecen ramas o prerrequisitos entre lecciones (hoy el curso es lineal).

### ADR-69: Tokens CSS en un fichero y componentes base propios; Reka UI solo para lo que lleva comportamiento
- Motivo: la identidad es nuestra y debe poder cambiar de piel (la variante «Mundo» sigue en el radar); el foco, el teclado y ARIA de diálogos y pestañas son difíciles de acertar, así que se delegan.
- Alternativas descartadas: un kit con estilo propio (Vuetify, PrimeVue: se nota que es de serie, justo lo que el usuario quería evitar); Tailwind (valores repartidos por las plantillas); todo a mano (accesibilidad de diálogos a medias).
- Revisitar si: Reka UI deja de mantenerse o pesa demasiado en el empaquetado.

### ADR-70: Fuentes autoalojadas y sin recursos externos
- Motivo: la política `default-src 'self'` y la privacidad del menor (ninguna petición a terceros desde la app). Se sirven desde el mismo origen con `font-display: swap`, solo latín y los pesos usados.
- Alternativas descartadas: Google Fonts por enlace (viola la política y filtra la IP); fuentes del sistema (pierde la identidad).
- Revisitar si: el peso de las fuentes se nota en conexiones lentas (entonces, menos pesos o una sola familia variable).

### ADR-71: Contraste AA como prueba automática sobre los tokens, claro y oscuro
- Motivo: el estilo neo-brutalista usa mucho color plano y es fácil dejar pares ilegibles (amarillo con blanco); un test que lee `tokens.css` y calcula las razones impide que un cambio de paleta rompa la accesibilidad sin que nadie lo vea.
- Alternativas descartadas: revisión manual; herramienta externa en integración continua.
- Revisitar si: se cambia de piel (el test se reutiliza tal cual).

### ADR-72: Los tests de estructura se actualizan a propósito; los `data-testid` se conservan salvo que su elemento desaparezca
- Motivo: reducir el diff de pruebas y asegurar que ninguna regla de F01–F09 (aviso al menor, visor Markdown, mensaje sin categoría, códigos que se ven una vez) pierde su prueba al mover el HTML.
- Alternativas descartadas: reescribir los tests desde cero (riesgo de perder casos borde); congelar la estructura antigua (impide el rediseño).
- Revisitar si: el número de pruebas que cambian en una vista supera lo razonable (entonces, dividir la vista en entregas).

## Decisiones pendientes de tu respuesta
1. **Modo oscuro:** propongo mantener el automático con una paleta oscura propia (más trabajo de contraste). Alternativa: solo claro en esta entrega.
2. **Dependencias:** `reka-ui` y `lucide-vue-next` son dos dependencias nuevas con su superficie de actualización; las considero justificadas.
3. **Ejercicios en el mapa:** propongo que no sean parada propia (cuelgan de su lección) para no duplicar el número de paradas.
