## US-F11 · Piel «Mundo que crece»

### Como persona que usa Study Lab (alumno, adulto o adulto tutor)
### Quiero ver mis cursos como plantas que crecen y recorrerlos como un camino por un paisaje, con una identidad visual cálida y redondeada
### Para que estudiar se sienta como hacer crecer algo, sepa siempre dónde estoy y qué toca después, y el dueño del proyecto pueda comparar esta piel con «Mapa» sin perder ninguna garantía de seguridad ni de accesibilidad

Parte de F10 (ya en `main`): mismas funciones, mismas rutas, mismos datos y mismos contratos. F11 es **solo de frontend** y vive en su propia rama; en ella «Mundo» **sustituye** a «Mapa». `main` conserva «Mapa» hasta que el usuario decida cuál se queda. Prototipo de referencia del Inicio: `sdd/design/F10/MundoC.dc.html`; las demás pantallas se prototipan en el lienzo antes de la FASE 4.

Decisiones del usuario (antes de la FASE 1): alcance **todo ilustrado** (plantas en el Inicio **y** el mapa del curso como camino por un paisaje); proceso **ciclo SDD completo como F11**.

Supuestos míos (a confirmar): «Mundo» reemplaza a «Mapa» en esta rama (sin selector de piel); **cuatro fases de planta** (semilla 0 %, brote 1–39 %, árbol 40–99 %, árbol con frutos 100 %), calculadas en el cliente con el porcentaje que ya llega; se mantiene el modo oscuro automático (paisaje nocturno); el nombre «Study Lab» y una **mascota sencilla** (una forma redonda con cara, la del prototipo) siguen siendo provisionales; las ilustraciones son **SVG propios dentro del paquete**, sin recursos externos; tipografías autoalojadas por elegir (titular redondeado como Fredoka, lectura legible) con la política de F10 (solo latino, `font-display: swap`).

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Piel por tokens:** «Mundo» es un juego de tokens nuevo en `tokens.css` (colores en claro y oscuro, radios, relieve suave, tipografías) y de estilos de `ui/` y `components.css`; ninguna vista lleva colores, radios ni tamaños a mano (la prueba de F10 se mantiene). Cambiar de piel sigue siendo cambiar tokens y componentes base. |
| AC-2 | **Identidad visual:** paleta de cielo, hierba, sol y un acento cálido; formas redondeadas y relieve suave (no sombras duras ni bordes de 3 px); titular con tipografía redondeada y lectura cómoda; los avisos, estados y botones conservan todos sus textos y roles. |
| AC-3 | **Plantas en el Inicio:** cada curso se muestra como una planta en un paisaje, en una de cuatro fases según su porcentaje (0 % semilla, 1–39 % brote, 40–99 % árbol, 100 % árbol con frutos), con el título, el estado y el avance en texto. Un curso sin porcentaje (en cola o generándose) muestra una semilla con «Preparándose» y sin avance inventado. La fase siempre se dice también con texto, no solo con la ilustración. |
| AC-4 | **«Sigue donde lo dejaste»** y el resto del Inicio conservan su comportamiento (curso con más avance sin llegar al 100 %, crear curso, «Tengo un código», menores con avisos, estado vacío que invita a crear el primero). La tarjeta se integra en el paisaje sin perder su texto ni su enlace. |
| AC-5 | **Mapa del curso como camino:** el curso se dibuja como un camino por un paisaje: cada tema es una zona del paisaje, cada lección una piedra o parada (círculo) y cada examen de tema una puerta o hito (cuadrado); lo hecho se ve «crecido» o coloreado, y «tú estás aquí» lo marca un personaje o mojón. Mismas paradas, mismo orden y mismos estados que en F10 (hecho, ahora, por venir, en cola, fallida, «la lección cambió»); los ejercicios siguen colgando de su lección. |
| AC-6 | **Misma accesibilidad del mapa:** `role="img"` con resumen, lista equivalente debajo con enlaces, `aria-current="step"` en la parada actual, orden de tabulación del curso, estados dichos con texto y forma además del color, temas plegables. El SVG se construye con elementos Vue, nunca con cadenas ni `v-html`. |
| AC-7 | **Colocación probada sin navegador:** la función de colocación (nueva o adaptada) sigue siendo pura y probada: paradas dentro del ancho y sin solapes con 80 paradas, columna única en estrecho, temas plegados, un solo «ahora»; el camino por zonas no cruza nombres ni títulos. |
| AC-8 | **Ilustraciones propias y ligeras:** plantas por fase, paisaje, mascota y personaje del mapa son SVG del paquete (componentes Vue, sin imágenes externas), con título accesible o marcados como decorativos; el peso añadido al empaquetado queda acotado (se documenta en el informe). |
| AC-9 | **Todas las vistas heredan la piel:** acceso, material, asistente de creación, curso, lección, ejercicios, examen, resultados, intentos, edición y compartir, canje de código y panel del adulto se ven coherentes con «Mundo», con la misma funcionalidad y sin regresiones de texto ni de estructura accesible. |
| AC-10 | **Lectura de lecciones:** se mantiene la comodidad de F10 (ancho de 60–75 caracteres, tamaño y altura de línea, citas y fuentes diferenciadas, aviso «generado por IA») con la tipografía de lectura de «Mundo»; el Markdown solo con `MarkdownContent`. |
| AC-11 | **Marco y navegación:** cabecera y barra inferior móvil con la identidad de «Mundo» y la mascota como marca; `aria-current`, migas y enlace para saltar al contenido siguen igual. |
| AC-12 | **Contraste y foco:** todos los pares de color de «Mundo» pasan AA (4,5:1 texto, 3:1 elementos) en claro y en oscuro, comprobados por el test sobre los tokens (con los pares nuevos que haga falta, como el texto sobre el cielo o la hierba); foco visible; objetivos táctiles de 44 px. |
| AC-13 | **Móvil real:** sin desplazamiento horizontal a 360, 390 y 430 px; el camino pasa a vertical en estrecho; las plantas del Inicio se apilan; la barra inferior no tapa contenido. |
| AC-14 | **Movimiento:** si hay animación (la planta que «se mece», el sol), es breve, opcional y solo con `prefers-reduced-motion: no-preference`; nada parpadea ni se repite en bucle sin guarda. |
| AC-15 | **Seguridad del frontend:** ni `v-html` ni `innerHTML`; ningún recurso de terceros (ilustraciones y fuentes incluidas); política de nginx `default-src 'self'` intacta; texto de la IA o del usuario siempre como texto, también dentro de los SVG; ningún token en el almacenamiento del navegador. |
| AC-16 | **Dependencias:** ninguna librería nueva salvo las tipografías autoalojadas que se elijan, con versión exacta; se quitan las de «Mapa» que dejen de usarse; el test de entorno se actualiza a propósito con el motivo y `npm audit` queda sin vulnerabilidades. |
| AC-17 | **Sin regresiones:** los tests existentes de F01–F10 pasan (se actualizan a propósito los que dependían del aspecto o de ids que cambien; ninguno se borra para que pase); los contratos HTTP y el backend no cambian. |

### Casos borde
- Un curso en 0 %, en 1 %, en 39 %, en 40 %, en 99 % y en 100 %: la fase correcta en cada frontera; un curso con porcentaje ausente.
- Varios cursos con la misma fase: el Inicio sigue legible con 1, 3 y 20 cursos (la cuadrícula crece en alto, nunca en ancho).
- Un curso de 80 paradas: el camino sigue legible, se pliega por temas y no bloquea la interfaz.
- Un solo tema y un solo capítulo; un curso sin ninguna lección hecha; uno con todo hecho (árbol con frutos y mapa terminado).
- Títulos muy largos, sin espacios, con emojis o con escritura de derecha a izquierda dentro de las ilustraciones y del camino: se parten o se recortan sin desbordar.
- Título con HTML o scripts: se muestra como texto también en el SVG.
- Modo oscuro: el paisaje cambia a una versión nocturna legible; el menor y el adulto ven los mismos avisos.
- Zoom del navegador al 200 % y fuentes sin cargar: legible con la fuente de reserva.
- Usuario menor frente a adulto: nada se decide por ocultar en la interfaz; el aviso de visibilidad del adulto sigue visible.
- Navegador sin soporte de alguna propiedad moderna: se degrada a una versión sencilla pero usable.

### Casos borde añadidos al implementar
- Un curso al 100 % y la cuenta del menor con esta piel se cubren por pruebas, no se vieron en el navegador.
- Con pocas paradas por tema la colina deja espacio vacío a la derecha (sin decoración añadida).
- Tras reconstruir el stack el navegador puede mostrar el `index.html` antiguo hasta recargar (BACKLOG F11-6).
- Fuera de alcance real: 360/430 px, zoom 200 % y teclado completo sin recorrer a mano.

### Fuera de alcance (v1)
- Cambios de funcionalidad, de API o de reglas de negocio; el backend no se toca.
- Selector de piel por usuario y convivir con «Mapa» en la misma rama (si el usuario lo pide, es otra feature).
- Animaciones elaboradas, sonidos y gamificación (rachas, puntos, logros, tienda de plantas).
- Ilustración por curso a medida (todas las plantas son las cuatro fases genéricas) y personalización del paisaje.
- Internacionalización, modo claro/oscuro elegible, nombre y logo definitivos.

### Definición de hecho
- Todos los AC con un test (de componente, de seguridad, de contraste o de entorno); casos borde con test; los tests de F01–F10 pasan y los actualizados llevan su motivo.
- Pasada visual real en el navegador a 360, 390, 768 y 1280 px, claro y oscuro, con el stack aislado y un servidor de IA simulado (adulto y menor), más el recorrido con teclado; comparación lado a lado con «Mapa» en el informe.
- Contraste AA comprobado automáticamente; foco visible; `prefers-reduced-motion` respetado.
- `security-checklist` pasada (sin `v-html`, sin recursos externos, sin tokens en el navegador); `llm-integration-review` no aplica.
- Documentación al día (README, ARCHITECTURE con los ADR nuevos, CLAUDE con las reglas de la piel, BACKLOG); los prototipos finales en `sdd/design/F11/`; el informe dice qué piel recomienda el resultado y qué falta para que «Mundo» pueda sustituir a «Mapa» en `main`.
