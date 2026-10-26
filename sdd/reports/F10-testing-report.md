## Testing Report — F10 Rediseño del frontend «Mapa» — 2026-10-26

### Resumen
- Tests del frontend: **590 pasados, 0 fallidos** (47 ficheros; 116 más que antes de F10). Tests de entorno: **46/46**. Backend: sin cambios (F10 no lo toca).
- Cobertura del frontend: **97,0 % de líneas** (95,6 % de sentencias, 89,7 % de ramas). Lo nuevo: `layoutMap.ts` 98,5 %, `CourseMap.vue` 94,9 %, `ui/` ~100 %, `layouts/` 100 % de líneas.
- `vue-tsc` + `vite build` pasan. `npm audit` (completo y solo producción): **0 vulnerabilidades**. Fuentes servidas como ficheros propios (5 `.woff2`, 197 kB en total); la única origen de red de la aplicación es la propia.
- Verificado con el stack aislado levantado (`studylab-f03`: nginx → backend → BD, servidor de IA simulado por HTTP, tres cuentas y tres cursos) y en el navegador a 1280 y 390 px, adulto y menor.

### Resultados por criterio de aceptación

| AC | Descripción | Prueba | Estado |
|----|-------------|--------|--------|
| AC-1 | Tokens en un único fichero; ningún color a mano | `tokens.spec` (contrato de tokens, sin `#hex`/`rgb()`/`hsl()` fuera de `tokens.css`) | ✅ |
| AC-2 | Componentes base y primitivas Reka UI; nada de `confirm()` | `ui.spec` (22), `Redesign.spec` (diálogo en vez de `confirm`) | ✅ (ver nota UiCard) |
| AC-3 | Marco común, sección activa con `aria-current`, migas, marco de acceso | `shell.spec` (11), pasada en navegador | ✅ |
| AC-4 | Mapa del curso: paradas, estados, «tú estás aquí», sin SQL ni endpoint nuevos | `layoutMap.spec` (21), `CourseMap.spec` (11), `Redesign.spec` | ✅ |
| AC-5 | Estados: cola, generando, pausa, fallida, «la lección cambió», terminado, copia | `layoutMap.spec`, `CourseMap.spec`, `CourseProgress.spec` | ✅ |
| AC-6 | Lista equivalente, paradas enfocables, orden del curso | `CourseMap.spec` (`ol`, `aria-current="step"`, enlaces) | ✅ |
| AC-7 | Inicio: «sigue donde lo dejaste», líneas con progreso, menores, vacío | `Redesign.spec` (6), pasada en navegador | ✅ |
| AC-8 | Todas las vistas rediseñadas, misma funcionalidad | 590 tests de las vistas existentes (actualizados donde cambió la estructura) | ✅ con matiz (ver «Issues») |
| AC-9 | Lectura de lecciones con ancho y tipografía de lectura; solo `MarkdownContent` | `Redesign.spec` (lectura + entradas hostiles), `MarkdownContent.spec` | ✅ |
| AC-10 | Aviso al menor, tutor, mensaje sin categoría | `HomeView.spec`, `TutorChat.spec`, `Redesign.spec`, pasada con la cuenta del menor | ✅ |
| AC-11 | Sin `v-html`, sin SVG como cadena, sin recursos externos, política de nginx cerrada | `security.spec` (+3), entorno (F10), red del navegador | ✅ |
| AC-12 | Fuentes y dependencias mínimas, versión exacta, test de entorno actualizado | entorno (F10 + F05–F09), `npm audit` | ✅ |
| AC-13 | Contraste AA en claro y oscuro; foco; objetivos de 44 px | `contrast.spec` (18 pares × 2 temas, más 3 comprobaciones de contrato), `tokens.spec`, comprobación en navegador | ✅ (ver «Issues»: enlaces) |
| AC-14 | Móvil: sin desplazamiento horizontal, mapa vertical, barra inferior | Pasada a 390 px (`scrollWidth` = 390 en 6 pantallas) | ✅ a 390 px; 360 y 430 no se midieron aparte |
| AC-15 | Movimiento solo con `no-preference` | `tokens.spec` (motion) | ✅ |
| AC-16 | Carga, vacío y error homogéneos, sin códigos del backend | `ui.spec` (`UiState`), `CoursesPanel.spec` | ✅ en Inicio; el resto de vistas conserva sus mensajes propios |
| AC-17 | Sin regresiones; ningún test borrado | Suite completa | ✅ |

### Casos borde verificados

| Caso borde | Prueba | Estado |
|------------|--------|--------|
| Curso de 80 paradas: dentro del ancho, sin solapes, solo crece en alto | `layoutMap.spec` | ✅ |
| Un solo tema y capítulo; temario vacío | `layoutMap.spec`, `CourseMap.spec` | ✅ |
| Temas plegados (y por defecto con más de 40 paradas) | `layoutMap.spec`, `CourseMap.spec` | ✅ |
| Títulos con `<img onerror>` o `<script>`: texto, también dentro del SVG | `CourseMap.spec`, `Redesign.spec`, `CourseProgress.spec` | ✅ |
| Curso en cola o generándose: sin «ahora» engañoso | `layoutMap.spec`, `CourseMap.spec` | ✅ |
| Curso terminado | `layoutMap.spec`, `CourseMap.spec` | ✅ |
| Fallo al cargar el estudio: el Inicio sigue funcionando | `Redesign.spec` | ✅ |
| Cursos no terminados o al 100 %: no se ofrecen en «sigue donde lo dejaste» | `Redesign.spec` | ✅ |
| Menor frente a adulto (aviso, sin panel de menores) | `HomeView.spec`, `Redesign.spec`, navegador | ✅ |
| Zoom 200 %, lector de pantalla real, 360/430 px, `prefers-reduced-motion` en el navegador | — | ⚠️ no probado en navegador (solo por pruebas del CSS) |

### Revisión de seguridad (skill `security-checklist`)

Alcance: solo cambia el frontend; no hay endpoints, permisos, autenticación ni entrada de usuario nuevos.
- **Bien:** ningún `v-html`/`innerHTML`/`{@html}`/`DOMParser` (prueba automática); el SVG se construye con elementos Vue y el texto de la IA o del usuario se escapa; `marked` sigue importado solo en `MarkdownContent`; sin `localStorage`; sin secretos ni URLs absolutas; ninguna petición a terceros (el navegador solo habló con el propio origen y cargó solo las fuentes usadas); política `default-src 'self'` intacta (sin `script-src` ampliado); `npm audit` limpio; el cierre de sesión y la sesión en cookie `HttpOnly` no cambian (el botón se movió a la barra de navegación y usa el mismo store).
- **Menores:** el aviso de que el adulto ve respuestas, intentos y conversaciones sigue en Inicio y en el chat del tutor (probado y visto con la cuenta del menor); el mensaje «Contenido inapropiado…» no cambia.
- **No se puede comprobar desde aquí:** cabeceras y HTTPS en producción (sigue en el BACKLOG, D1–D6).
- `llm-integration-review`: no aplica (no hay llamadas nuevas a IA).

### Issues encontrados (y qué se hizo)
Corregidos durante la pasada visual:
1. En la tarjeta «Siguiente parada» el botón era negro sobre negro (texto invisible): se le dio fondo claro.
2. Las barras de progreso de «Tus cursos» salían minúsculas: ahora ocupan la fila.
3. En el mapa, los nombres se solapaban y se cortaban (ancho) o se salían por la izquierda (móvil): ahora se parten en dos líneas en ancho y van a la derecha de la parada en móvil, con la etiqueta «tú estás aquí» al lado.
4. Los tramos entre temas eran diagonales y atravesaban los títulos y nombres: ahora son tramos de metro en ángulo recto, que salen hacia un lado.
5. La barra inferior del móvil partía «Mi material» en dos líneas: ahora no se parte.
6. «Sigue donde lo dejaste» ofrecía el primer curso de la lista aunque otro tuviera más avance: ahora elige el de más avance sin llegar al 100 % (ADR-67 actualizado).
7. Enlaces que actúan como controles (volver, lista de cursos, menores) medían 22–25 px: ahora 44 px.
8. `UiCard` no lo usaba nadie: se eliminó (queda la clase `.card` compartida).

Pendiente o con matiz (anotado en el BACKLOG):
- **AC-8 con matiz:** las vistas se rediseñaron sobre todo por las clases compartidas de `components.css` y los marcos; los componentes `UiField`/`UiButton` están construidos y probados pero las vistas existentes siguen usando los elementos y clases anteriores (`button.primary`, `label`/`input`). Migrarlas una a una cambiaría muchas pruebas sin cambiar lo que se ve.
- **Pantalla de edición:** sigue siendo densa (muchos botones por fila); mejora por pestañas, no por contenido.
- **Contraste medido, no medido el resultado visual real:** el test calcula razones sobre los tokens; no se ha revisado color a color con una herramienta de accesibilidad en el navegador.
- 360 px y 430 px, zoom 200 % y teclado completo (acceso → examen) no se recorrieron a mano; sí 390 px y 1280 px.
- Los detalles de maquetación del navegador de la app (panel oculto) se comprobaron con capturas del lienzo emulado y con medidas del DOM.

### Cómo se verificó con el stack
`studylab-f03` (proyecto aislado, fichero de variables ficticio, servidor de IA simulado, puerto solo en `127.0.0.1:18080`). Datos: una cuenta adulta con dos cursos (uno con 2 lecciones hechas), un menor con un curso. El stack del usuario (`studylab-*`) no se tocó.
