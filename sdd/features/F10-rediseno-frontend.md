# F10 — Rediseño del frontend («Mapa»)

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

Las nueve features anteriores dejaron un frontend funcional pero sin identidad: pantallas sueltas, navegación por enlaces
y un aspecto genérico. F10 le da una **identidad visual propia y una navegación coherente**, sin cambiar lo que la app hace
ni ninguna regla de seguridad, de IA o de menores. Es la primera feature que es sobre todo de frontend: el backend solo se
toca si una pantalla necesita un dato que hoy no existe.

## Dirección elegida (prototipos aprobados)
Los prototipos están en `sdd/design/F10/` (ficheros del lienzo de diseño, `.dc.html`: su HTML y estilos sirven de referencia, pero no se abren solos en un navegador; el lienzo original es privado). Dirección **«Mapa»**,
neo-brutalista y juguetona:
- **Cada curso es una línea de metro**: las lecciones son paradas (círculos), los exámenes de tema son cuadrados, los temas son
  zonas. El progreso se ve como el trayecto hecho; «tú estás aquí» marca la siguiente parada. Hecho = verde menta, ahora = rosa,
  por venir = blanco.
- Estilo: color plano (amarillo, rosa, menta, lavanda), bordes negros de 3 px, sombras duras, etiquetas inclinadas, fondo de
  puntos. Titulares Bricolage Grotesque, lectura de lecciones en Newsreader, etiquetas en Space Mono.
- Cinco pantallas prototipadas: Inicio, Curso (línea completa), Lección con tutor, Editar y compartir, Inicio en móvil.
- **Alternativa en el radar (no se hace ahora): «Mundo que crece»** (`MundoC`): cada curso es una planta (semilla, brote, árbol) en un
  paisaje ilustrado. Si se quiere probar más adelante, los tokens de F10 deben permitir cambiar de piel sin rehacer las pantallas.

## Qué incluye
- **Sistema de diseño propio**: tokens CSS (color, espacio, radio, borde, sombra, tipografía) en un único sitio y componentes base
  (botón, etiqueta, tarjeta, campo, aviso, diálogo, pestañas). Primitivas accesibles sin estilo de **Reka UI** para lo que lleva
  comportamiento (diálogos de confirmación, menús, pestañas); iconos con una librería de iconos (Lucide). Tipografías
  **autoalojadas** (nada de CDN en producción).
- **Navegación común**: cabecera con marca, Mapa / Mi material / Resultados y cuenta; barra inferior en móvil; migas de pan;
  cada pantalla actual indica dónde está.
- **Mapa del curso** (componente nuevo): dibuja la línea de un curso a partir de los datos que ya devuelve la API (temas,
  capítulos, piezas, progreso). Accesible: alternativa de lista para lector de pantalla y teclado, y no depende solo del color.
  En móvil, vertical.
- **Pantalla de Inicio** con «sigue donde lo dejaste», creación de curso, código y, para el adulto, sus menores con los avisos.
- Rediseño de **todas** las vistas existentes con el sistema nuevo (acceso, material, asistente, estado del curso, lección,
  ejercicios y examen, resultados, intentos, edición, canje de código, panel del adulto). Misma funcionalidad.
- **Lectura de lecciones** pensada para leer: ancho de línea cómodo, tamaño y altura de línea, citas y fuentes; sigue pintándose
  **solo** con `MarkdownContent`.
- **Móvil de verdad**: probado a 360–430 px; objetivos táctiles de al menos 44 px.
- **Accesibilidad**: contraste AA comprobado en todos los pares de color, foco visible, orden de tabulación, etiquetas,
  `prefers-reduced-motion` respetado (animaciones opcionales y discretas).

## No cambia (reglas que siguen mandando)
- Nada de `v-html`; el Markdown solo con `MarkdownContent` (sus tests con entradas hostiles se mantienen).
- El aviso al menor de que su adulto ve respuestas, intentos y conversaciones sigue en `HomeView` y en el chat del tutor.
- El mensaje «Contenido inapropiado. Consulta con tu adulto.» sin categoría; el aviso «generado por IA» sigue visible.
- Los estados de carga y error no enseñan códigos del backend; los códigos de compartir se muestran una sola vez.
- Sesión en cookie HttpOnly; nada en `localStorage` que sea un token. No se añade ningún script externo ni analítica.
- Contratos HTTP intactos. Los `data-testid` y los tests actuales se **actualizan a propósito** si cambia la estructura; ninguna
  prueba se borra para que pase.
- Dependencias: hoy el entorno exige exactamente `marked`, `pinia`, `vue`, `vue-router`. F10 las amplía de forma explícita y
  mínima (Reka UI, iconos, tipografías) y actualiza ese test de entorno con el motivo; versiones exactas y revisión de `npm audit`.

## Decisiones que quedan para la FASE 1
- Con qué se dibuja el mapa (SVG generado por un componente Vue, sin librería de gráficos) y cómo se coloca la línea cuando un curso
  tiene muchos temas (150 piezas como máximo).
- Si el nombre y el logo siguen siendo provisionales (sí, salvo que el usuario decida otra cosa) y si hay mascota.
- Modo oscuro: fuera de la primera entrega salvo que salga barato desde los tokens.
- Cómo se prueba lo visual: tests de componentes, comprobación de contraste automática, y pasada manual en el navegador a varios
  anchos; capturas de referencia solo si se pueden mantener.

## Fuera de alcance
Cambios de funcionalidad o de API, internacionalización (la app sigue en español), un panel de administración, animaciones
complejas, ilustraciones de la variante «Mundo», modo claro/oscuro configurable y una app nativa.
