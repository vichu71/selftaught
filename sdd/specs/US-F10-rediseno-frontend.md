## US-F10 · Rediseño del frontend («Mapa»)

### Como persona que usa Study Lab (alumno, adulto o adulto tutor)
### Quiero una interfaz con identidad propia, en la que cada curso se vea como un mapa por el que avanzo y que se use bien en el móvil
### Para que estudiar sea claro y apetecible, sepa siempre dónde estoy y cuál es el siguiente paso, y no perdamos ni una garantía de seguridad por el camino

Dirección aprobada en la FASE 0: **«Mapa»** (línea de metro, neo-brutalista), prototipos en `sdd/design/F10/`. La variante «Mundo que crece» queda fuera, pero la piel debe poder cambiarse (AC-1).

Supuestos míos (a confirmar): se mantiene el modo oscuro automático que ya existe (`prefers-color-scheme`), con una paleta oscura propia de la dirección Mapa; el nombre «Study Lab» y un logo sencillo propio siguen siendo provisionales, sin mascota; no hay animaciones salvo transiciones breves que se apagan con `prefers-reduced-motion`; el backend no cambia (todo el mapa se dibuja con lo que ya devuelve la API).

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Tokens y piel:** todo color, espacio, radio, borde, sombra y tipografía sale de variables CSS definidas en un único fichero de tokens; ninguna vista ni componente lleva colores o tamaños escritos a mano. Cambiar de piel (p. ej. a «Mundo») es cambiar tokens y componentes base, no las vistas. |
| AC-2 | **Componentes base:** botón (primario, secundario, peligro), etiqueta, tarjeta, campo con etiqueta y error, aviso (info, atención, error), diálogo de confirmación, pestañas y barra de progreso. Los que llevan comportamiento (diálogo, pestañas, menú) usan las primitivas accesibles de Reka UI; se usan en lugar de `confirm()`/`alert()` del navegador y de los botones sueltos actuales. |
| AC-3 | **Marco común:** cabecera con marca, «Mapa», «Mi material», «Resultados» y cuenta (con «Cerrar sesión»); en móvil, barra inferior; la sección activa se marca visualmente **y** con `aria-current`; migas de pan o «volver» en las pantallas interiores. Las pantallas de acceso (login, registro, verificación, recuperación) usan un marco reducido sin navegación. |
| AC-4 | **Mapa del curso:** un componente dibuja un curso como una línea con una parada por lección (círculo) y por examen de tema (cuadrado), agrupadas por tema, con tres estados (hecho, ahora, por venir) y «tú estás aquí» en la siguiente lección pendiente. Se alimenta solo de datos que ya devuelven los endpoints del curso y de estudio (sin SQL ni endpoint nuevos). Tocar o activar una parada abre su pieza. |
| AC-5 | **Estados del mapa:** curso en cola o generándose (piezas aún sin hacer, sin «tú estás aquí» engañoso), pausado con motivo y «Reanudar», con piezas fallidas, con piezas marcadas «la lección cambió» (F09), curso terminado (todo hecho) y curso copiado. El estado nunca se indica solo con color: también con forma, texto o icono. |
| AC-6 | **Accesibilidad del mapa:** el mismo contenido existe como lista ordenada para lector de pantalla y teclado (el dibujo es decorativo o equivalente); cada parada es enfocable con nombre, tipo y estado; el orden de tabulación sigue el del curso. |
| AC-7 | **Inicio:** saludo, bloque «Sigue donde lo dejaste» (la siguiente lección del curso con actividad más reciente), crear curso, «Tengo un código», lista de cursos con progreso y, para el adulto tutor, sus menores con los avisos nuevos. Sin cursos, un estado vacío que invita a crear el primero. |
| AC-8 | **Todas las vistas rediseñadas:** acceso, material, asistente de creación, estado del curso, lección, ejercicios, examen y su resultado, intentos, resultados, edición del curso, canje de código y panel del adulto (alumno, curso, hilo). Misma funcionalidad y mismos textos de negocio; solo cambia la presentación y la navegación. |
| AC-9 | **Lectura de lecciones:** ancho de línea de 60–75 caracteres, tipografía y altura de línea de lectura, citas y fuentes diferenciadas, aviso «generado por IA» visible. El Markdown se sigue pintando **solo** con `MarkdownContent` y sus pruebas con entradas hostiles siguen pasando. |
| AC-10 | **Tutor y menores:** el chat del tutor conserva el aviso de que el adulto tutor puede ver la conversación, el contador de preguntas restantes, los estados de bloqueo y el mensaje «Contenido inapropiado. Consulta con tu adulto.» sin categoría. El aviso de `HomeView` al menor sigue nombrando respuestas, intentos y conversaciones. |
| AC-11 | **Seguridad del frontend:** ni `v-html` ni `innerHTML` con datos del usuario o de la IA (lo comprueba la prueba de seguridad existente, ampliada a los componentes nuevos); el SVG del mapa se construye con elementos Vue, nunca con cadenas; ningún token en `localStorage`; ningún script, fuente o imagen externos; la política de seguridad del servidor web sigue siendo `default-src 'self'` (las fuentes van en el propio paquete). |
| AC-12 | **Tipografías y dependencias:** Bricolage Grotesque, Newsreader y Space Mono autoalojadas, con `font-display: swap` y solo los pesos usados; dependencias nuevas mínimas y con versión exacta (Reka UI, iconos); el test de entorno se actualiza **a propósito** con la lista nueva y el motivo; `npm audit` revisado y documentado. |
| AC-13 | **Contraste y foco:** todos los pares de color de texto y de elementos de interfaz cumplen AA (4,5:1 y 3:1) en claro y en oscuro, comprobado por un test automático sobre los tokens; foco visible en todo elemento interactivo; objetivos táctiles de al menos 44 × 44 px. |
| AC-14 | **Móvil real:** sin desplazamiento horizontal a 360, 390 y 430 px; el mapa pasa a vertical en móvil; la barra inferior no tapa contenido ni el teclado; formularios con tipo de teclado adecuado. |
| AC-15 | **Movimiento:** cualquier transición o animación es breve y se desactiva con `prefers-reduced-motion`; nada parpadea ni se mueve en bucle. |
| AC-16 | **Estados de carga, vacío y error** homogéneos en todas las vistas (mismo componente y tono), sin enseñar códigos del backend ni mensajes técnicos. |
| AC-17 | **Sin regresiones:** los 40 ficheros de test del frontend y los de entorno pasan; los que dependían de la estructura antigua se **actualizan** con el motivo, ninguno se borra para que pase; los contratos HTTP no cambian (los tests del backend no se tocan). |

### Casos borde
- Un curso con 150 piezas (el máximo): el mapa sigue legible (se pliega por temas, con desplazamiento dentro del propio mapa) y no bloquea la interfaz.
- Un curso con un solo tema y un solo capítulo; un curso sin ninguna lección hecha; uno con todo hecho.
- Títulos muy largos o con caracteres raros (emojis, escritura de derecha a izquierda, palabras sin espacios): se recortan o se parten sin romper el diseño ni desbordar.
- Texto de la IA o del usuario con HTML o scripts en un título de tema: se muestra como texto, también dentro del SVG.
- Zoom del navegador al 200 % y tamaño de letra grande: sin pérdida de contenido ni solapes.
- Sin JavaScript de fuentes o con las fuentes sin cargar: el texto es legible con la fuente de reserva.
- Usuario menor frente a adulto: el menor no ve opciones que no tiene (vaciar hilo, panel del adulto); el adulto tutor ve sus menores; nada de esto se decide solo por ocultar en la interfaz (el backend sigue siendo quien manda).
- Sesión caducada a mitad de uso: se vuelve al acceso como hasta ahora, sin perder el diseño.
- Navegación con teclado y con lector de pantalla por todo el flujo: acceso → inicio → curso → lección → ejercicio → examen.
- Navegador sin soporte de alguna propiedad CSS moderna: el diseño se degrada a una versión sencilla pero usable.

### Casos borde añadidos al implementar
- Un curso al que le faltan lecciones por generar: el mapa muestra «En cola» sin inventar una parada «ahora»; con una lección lista pero sin estudiar sale «Lista».
- Varios cursos en marcha: «sigue donde lo dejaste» elige el de más avance sin llegar al 100 %.
- Los ejercicios no son parada: se abren desde su lección en la lista y quedan «en cola» mientras no estén generados.
- Fuera de alcance real: migrar cada vista a `UiField`/`UiButton` (las vistas siguen con las clases compartidas), 360/430 px y zoom 200 % en navegador (sin recorrer a mano).

### Fuera de alcance (v1)
- Cambios de funcionalidad, de API o de reglas de negocio; el backend no se toca.
- La variante «Mundo que crece», ilustraciones propias y mascota.
- Modo claro/oscuro seleccionable por el usuario (solo el automático del sistema).
- Internacionalización (la app sigue en español), panel de administración, aplicación nativa o instalable (PWA).
- Animaciones elaboradas, sonidos y gamificación (rachas, puntos, logros).
- Nombre y logo definitivos.

### Definición de hecho
- Todos los AC con un test (de componente, de seguridad, de contraste o de entorno); casos borde con test; los tests existentes actualizados sin borrar ninguno.
- Pasada visual real en el navegador a 360, 390, 768 y 1280 px, claro y oscuro, con el stack levantado y un servidor de IA simulado; recorrido completo con teclado.
- Contraste AA comprobado automáticamente; foco visible; `prefers-reduced-motion` respetado.
- `security-checklist` pasada (el frontend no pinta datos de usuario ni de IA como HTML, sin recursos externos, sin tokens en el navegador); `llm-integration-review` no aplica (no hay llamadas nuevas a IA).
- Documentación al día (README, ARCHITECTURE con los ADR nuevos, CLAUDE con las reglas de F10, BACKLOG); el sistema de diseño queda descrito (tokens y componentes base) para poder cambiar de piel.
