## Testing Report — F11 Piel «Mundo que crece» — 2026-10-26

### Resumen
- Tests del frontend: **659 pasados, 0 fallidos** (52 ficheros; 69 más que en F10). Tests de entorno: **47/47**. Backend: sin cambios.
- Cobertura del frontend: **97,1 % de líneas** (95,8 % de sentencias, 90,1 % de ramas). Lo nuevo: `plantStage.ts`, `roadPath.ts`, `Landscape`, `Mascot`, `Wanderer`, `ContinueCard` y `CoursesPanel` al 100 %; `MapStation.vue` 100 % de líneas.
- `vue-tsc` + `vite build` pasan; `npm audit` sin vulnerabilidades. Una sola fuente en la red (`fredoka-latin-wght-normal.woff2`) y todo el tráfico, del propio origen; 0 etiquetas `<img>` en las pantallas.
- Verificado con el stack aislado (`studylab-f03`: nginx → backend → BD, IA simulada, cuentas de adulto y menor, dos cursos con avance real) en el navegador a 1280 px y 390 px, en claro y en oscuro.

### Resultados por criterio de aceptación

| AC | Descripción | Prueba | Estado |
|----|-------------|--------|--------|
| AC-1 | Piel por tokens, sin colores a mano | `tokens.spec` (contrato ampliado, sin `#hex`/`rgb()` fuera de `tokens.css`) | ✅ |
| AC-2 | Identidad: cielo, hierba, formas redondeadas, relieve suave | Pasada visual (capturas del Inicio, curso y lección) | ✅ |
| AC-3 | Plantas en el Inicio, fase en texto, semilla «Preparándose» sin porcentaje | `plantStage.spec` (12), `Garden.spec` (8), pasada visual (semilla y árbol) | ✅ (brote y fruto, por tests; no se vio un curso al 100 % en el navegador) |
| AC-4 | «Sigue donde lo dejaste» y resto del Inicio intactos | `Redesign.spec` (F10), pasada visual | ✅ |
| AC-5 | Camino por un paisaje: colinas, piedras, puertas, recorrido, personaje | `CourseRoad.spec` (7), `CourseMap.spec`, pasada visual | ✅ |
| AC-6 | Misma accesibilidad del mapa | `CourseMap.spec` (11, sin cambios de contrato) | ✅ |
| AC-7 | Colocación y camino puros, sin salirse del ancho | `layoutMap.spec` (21), `roadPath.spec` (9), `CourseRoad.spec` (80 paradas) | ✅ |
| AC-8 | Ilustraciones propias, ligeras, accesibles | `world.spec` (13), prueba de seguridad ampliada | ✅ (peso: una fuente de ~60 kB, ilustraciones inline) |
| AC-9 | Todas las vistas heredan la piel | Suite de vistas (sin cambios de contrato) y pasada visual de 6 pantallas | ✅ con matiz (ver «Issues») |
| AC-10 | Lectura de lecciones cómoda | Pasada visual (lección con tutor) y `Redesign.spec` | ✅ |
| AC-11 | Marco y navegación con la mascota | `world.spec` (mascota), `shell.spec`, pasada visual | ✅ |
| AC-12 | Contraste AA claro y noche, foco, 44 px | `contrast.spec` (pares nuevos sobre cielo y hierba), medición en navegador | ✅ (ver «Issues»: enlaces) |
| AC-13 | Móvil sin desplazamiento horizontal, camino vertical | `scrollWidth` = 390 en 6 pantallas; capturas | ✅ a 390 px; 360 y 430 no medidos |
| AC-14 | Movimiento solo con `no-preference` | `tokens.spec` (motion) | ✅ (esta piel no añade animaciones) |
| AC-15 | Seguridad del frontend | `security.spec` (+1), entorno, red del navegador | ✅ |
| AC-16 | Dependencias | Entorno (F11), `npm audit` | ✅ (Fredoka dentro; Bricolage y Space Mono fuera) |
| AC-17 | Sin regresiones | Suite completa; ningún test borrado ni debilitado | ✅ |

### Casos borde verificados

| Caso borde | Prueba | Estado |
|------------|--------|--------|
| 0, 1, 39, 40, 99, 100 % y ausente | `plantStage.spec` | ✅ |
| 1, 3 y 20 cursos en el Inicio | `Garden.spec` (20), navegador (2) | ✅ |
| Curso de 80 paradas | `CourseRoad.spec`, `roadPath.spec` | ✅ |
| Un solo tema y capítulo; sin lecciones hechas; todo hecho | `layoutMap.spec`, `CourseMap.spec` | ✅ |
| Título con HTML: texto también en el SVG y en la planta | `CourseMap.spec`, `Garden.spec`, `world.spec` | ✅ |
| Modo oscuro | `contrast.spec` (noche más oscura), navegador (camino y Inicio) | ✅ |
| Menor frente a adulto | Suite de F10 (sin cambios) | ✅ (no se repasó visualmente con la cuenta del menor) |
| Zoom 200 %, 360/430 px, `prefers-reduced-motion` en el navegador | — | ⚠️ no probado en navegador |

### Revisión de seguridad (skill `security-checklist`)
Alcance: solo frontend; sin endpoints, permisos, autenticación ni entrada nueva.
- **Bien:** sin `v-html`/`innerHTML`; ilustraciones como plantillas Vue (la prueba comprueba que `world/` y `map/` no usan `<image>`, `foreignObject`, `xlink:href` ni imágenes incrustadas); el texto de la IA o del usuario se escapa también dentro del SVG; ninguna petición a terceros; política `default-src 'self'` intacta; `npm audit` limpio; sin almacenamiento de tokens.
- **No se puede comprobar desde aquí:** cabeceras y HTTPS en producción (BACKLOG D1–D6). `llm-integration-review` no aplica.

### Comparación con «Mapa» (lado a lado, mismas pantallas y mismos datos)

| | Mapa (F10, `main`) | Mundo (F11, esta rama) |
|---|---|---|
| Sensación | Atrevida, gráfica, «cómic»: bordes gruesos y sombras duras | Cálida y suave: cielo, colinas y relieve ligero |
| Inicio | Lista de cursos con barra de progreso | **Plantas que crecen**: el avance se ve sin leer |
| Curso | Línea de metro por zonas | **Camino por colinas** con personaje; más «lugar», menos «diagrama» |
| Lectura de lecciones | Serifa sobre blanco | Igual, con titulares redondeados |
| Móvil y oscuro | Bien | Bien; el oscuro es un paisaje nocturno (más atmosférico) |
| Coste | Más contraste visual, más «ruidosa» | Más ilustración que mantener (cuatro plantas, paisaje, mascota) |
| Riesgo | Parece «de otro estilo» si se mezcla con contenido infantil | Si el usuario quiere sobriedad, puede sentirse infantil |

Recomendación: con menores en la familia y el objetivo de que se note el avance, **«Mundo»** cumple mejor la idea de «hacer crecer algo»; «Mapa» es más sobrio para adultos. Ambas comparten tokens, `ui/` y `layoutMap`, así que elegir una no bloquea cambiar después.

### Issues encontrados (y qué se hizo)
Corregidos durante la pasada visual:
1. La plantita de «Siguiente parada» ocupaba toda la tarjeta (un `width: 100%` general ganaba al tamaño propio): ahora cada contexto fija su tamaño.
2. El personaje de «tú estás aquí» quedaba diminuto y cortado por arriba en la primera zona: ahora es mayor y está junto a la parada, sobre el camino.
3. Las plantas eran pequeñas para ser el protagonista del Inicio: ahora 9 rem y tarjetas más anchas.
4. Los enlaces de las tarjetas de planta y de la lista de resultados medían 25–27 px: ahora 44 px.
5. El navegador conservaba el `index.html` antiguo tras reconstruir y mostraba la hoja de estilos vieja: se resolvió recargando; **conviene que nginx no cachee `index.html`** (BACKLOG F11-6).

Pendiente o con matiz (BACKLOG):
- Hereda los pendientes de F10 (migrar vistas a `ui/`, pantalla de edición densa, 360/430 px y zoom a mano, contraste con herramienta en el navegador).
- Las colinas de cada tema dejan espacio vacío a la derecha con pocas paradas; no se rellenó con decoración (árboles, flores) para no recargar.
- No se vio en el navegador un curso al 100 % (árbol con frutos), ni la cuenta del menor con esta piel.

### Cómo se verificó con el stack
`studylab-f03` (proyecto aislado, variables ficticias, IA simulada, puerto solo en `127.0.0.1:18080`), cuenta adulta con dos cursos (uno con 8 lecciones hechas) y un menor con otro. El stack del usuario (`studylab-*`) no se tocó.
