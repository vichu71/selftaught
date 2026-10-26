# F11 — Piel «Mundo que crece» (frontend ilustrado)

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md. Depende de F10.

F10 dejó el frontend con la dirección «Mapa» (línea de metro, neo-brutalista). El usuario la ve bien pero quiere **ver también la
opción C**, «Mundo que crece», para compararlas. F11 la implementa **en su propia rama** y como piel completa: misma
funcionalidad y mismas reglas de F01–F10, otra identidad visual. Alcance elegido: **todo ilustrado**.

## Idea (prototipo del Inicio en `sdd/design/F10/MundoC.dc.html`)
- **Cada curso es una planta en un paisaje**: sin empezar = semilla, con algo de avance = brote, bastante avance = árbol, terminado =
  árbol con frutos. La fase sale del porcentaje que ya calcula el servidor; no hay datos nuevos.
- **El mapa del curso es un camino por un paisaje** en vez de una línea de metro: cada parada es un lugar del camino (lección = círculo
  o piedra, examen del tema = cuadrado o puerta), cada tema es una zona del paisaje, lo hecho se ve «crecido» o coloreado, y
  «tú estás aquí» lo marca un personaje o un mojón. Sigue siendo un SVG hecho con elementos Vue y con su lista equivalente debajo.
- **Cielo y pradera**: paleta de cielo, hierba, sol y un acento cálido; formas redondeadas, relieve suave en vez de sombras duras;
  tipografía redondeada para titulares (por elegir y autoalojar). Una mascota sencilla como marca y en los estados vacíos.
- **Lección, edición, resultados, material, acceso, panel del adulto y móvil** heredan la piel; la lectura de lecciones mantiene su
  comodidad (ancho, tamaño, altura de línea) con la tipografía de lectura que se elija.

## Qué incluye
- Un **juego de tokens nuevo** (colores en claro y oscuro, radios, relieve, tipografías) y los **componentes base** de `ui/` adaptados,
  de modo que las vistas cambien de aspecto sin reescribirse. Las vistas que aún llevan clases de F10 se limpian lo necesario.
- Las **ilustraciones propias** (plantas por fase, paisaje, mascota, personaje del mapa) como SVG del paquete, sin recursos externos.
- El **componente de mapa** con su variante de paisaje (misma función de colocación o una nueva, siempre probada sin navegador) y las
  mismas paradas, estados y accesibilidad que en F10.
- Las **pantallas prototipadas** antes de la FASE 4 en el lienzo de diseño: Inicio, curso (camino), lección con tutor, edición y compartir,
  y móvil.

## Decisiones que quedan para la FASE 1
- **Reemplazar o convivir**: en esta rama «Mundo» sustituye a «Mapa». Si el usuario quiere que convivan con selector de piel, es otra
  decisión (dónde se guarda la elección, cómo se prueban las dos). Por defecto, **reemplaza** en la rama y `main` conserva «Mapa» hasta
  que el usuario decida cuál se queda.
- Cuántas fases de planta (3 o 4) y cómo se leen para quien no distingue colores (forma y texto además del color).
- Tipografías concretas (titular redondeado, lectura) y su peso en el paquete; modo oscuro del paisaje.
- Cómo se prueba lo visual: pruebas de componentes y de contraste sobre los tokens (se reutilizan), pruebas de la función de colocación
  y pasada manual en el navegador a varios anchos con el stack aislado.

## Reglas que siguen mandando (no cambian)
- Nada de `v-html`; el SVG se construye con elementos Vue y el texto de la IA o del usuario es siempre texto; el Markdown solo con
  `MarkdownContent`. Ningún recurso de terceros (ilustraciones y fuentes dentro del paquete); política `default-src 'self'` intacta.
- Aviso al menor de que su adulto ve respuestas, intentos y conversaciones; aviso «generado por IA»; mensaje sin categoría para menores;
  códigos de compartir que se ven una sola vez.
- Contraste AA comprobado por test sobre los tokens (claro y oscuro), foco visible, objetivos táctiles de 44 px, movimiento solo con
  `prefers-reduced-motion: no-preference`, móvil sin desplazamiento horizontal.
- Contratos HTTP intactos; sin cambios de backend. Los `data-testid` se conservan y los tests se actualizan a propósito, sin borrar ninguno.

## Fuera de alcance
Cambios de funcionalidad o de API, selector de piel por usuario (salvo que se decida en la FASE 1), animaciones complejas, sonidos,
gamificación (rachas, puntos, logros), internacionalización, modo claro/oscuro elegible y nombre y logo definitivos.
