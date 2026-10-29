## US-F17 · Alegría y colorido en toda la app

> FASE 1. Acordado con el usuario el 29-10-2026: «el menú está soso, le damos alegría y colorido» y, preguntado por el alcance, **«toda la app con más color»**. Las decisiones D1–D10 son propuestas mías a confirmar.

### Como persona que usa Study Lab (adulto o menor)
### Quiero que la app tenga color, gestos y personalidad: que cada zona se reconozca de un vistazo y que dé gusto usarla
### Para saber siempre dónde estoy, encontrar lo que busco sin leer y que estudiar se sienta como un juego tranquilo, no como un formulario

Contexto: tras F11 (piel «Mundo que crece») y F15–F16 la app tiene cielo, hierba, plantas y tarjetas, pero la **barra de navegación** es plana: cuatro botones iguales, con borde oscuro, sin color propio, y el nombre del usuario suelto. El resto de pantallas usa un único acento (el naranja del botón primario), así que Inicio, Material, Resultados, Curso y Lección se parecen demasiado. Pantallas como Nuevo curso, Tengo un código, Curso, Lección, Login y el panel del tutor siguen además con el estilo anterior a F16 (F16-3). Todo lo que hace falta ya existe (tokens, componentes de `ui/`, `Mascot`, `PlantIllustration`); es **solo de frontend**.

### Decisiones propuestas

| # | Decisión | Propuesta |
|---|----------|-----------|
| D1 | **Un color por zona** | Cuatro acentos de sección en `tokens.css`, cada uno con su tono de relleno, su tono de texto/borde y su par oscuro: **Mapa** (verde), **Mi material** (azul), **Resultados** (amarillo/ámbar), **Propietario** (violeta). El naranja actual sigue siendo el color de la acción principal (botones primarios) y no se usa como color de zona. |
| D2 | **Barra superior** | Fondo con degradado suave de cielo (de `--color-sky-*`), sin la raya negra dura; cada enlace lleva su icono dentro de una **burbuja de color**. El enlace activo va **relleno** con el color de su zona y con una marca de forma (subrayado grueso o pastilla) además del color; los inactivos van claros y cogen su color al pasar el ratón o al enfocarlos. Sigue con `aria-current="page"`. |
| D3 | **Marca y mascota** | La carita de «Study Lab» hace un gesto corto (inclinarse o guiñar) al pasar el ratón o al enfocarla, **solo con `prefers-reduced-motion: no-preference`**. El logo recupera color propio (no un rectángulo amarillo plano): mismas formas, pero con el degradado y la sombra de los tokens. |
| D4 | **Usuario y salida** | «victor huecas» pasa a «¡Hola, victor!» (primer nombre, como texto) dentro de un chip de color suave. «Dejarlo por hoy» y «Cerrar sesión» siguen siendo botones, más discretos que los de navegación, para que no compitan con ellos. Conservan `data-testid` y sus nombres accesibles. |
| D5 | **Barra inferior en móvil** | Los mismos colores y burbujas, con iconos algo mayores y la etiqueta debajo; el elemento activo se destaca con relleno y forma. Nunca tapada por el contenido (`elementFromPoint`) ni con desbordamiento horizontal a 360–430 px. |
| D6 | **Acento de sección en las páginas** | Cada vista usa el color de su zona en una **franja o cabecera de color** y en el relieve de sus tarjetas: Inicio y Curso/Lección en verde, Mi material en azul, Resultados en ámbar, Propietario en violeta. Los títulos de las tarjetas llevan una pequeña burbuja con icono en el color de la zona. |
| D7 | **Pantallas pendientes (F16-3)** | Nuevo curso, Tengo un código, Curso, Lección, Login/Registro y el panel del tutor pasan a los componentes de `ui/` de F15–F16 (`ActionCard`, `Disclosure`, `EmptyNote`, `StatCard`, `BackLink`…) y al acento de su zona, sin cambiar rutas, API ni reglas. |
| D8 | **Botones y etiquetas** | Variantes de botón secundario y de etiqueta (`UiTag`) por zona y por estado (éxito, aviso, error), siempre con icono o texto además del color. El botón primario no cambia de significado. |
| D9 | **Alegría pequeña y medida** | Detalles que no estorban: la planta de un curso termina con un destello breve al llegar al 100 %, un «¡Hecho!» con confeti discreto al completar una lección y un saludo distinto por franja del día en el Inicio. Todo movimiento queda **dentro de `prefers-reduced-motion: no-preference`** y desaparece sin perder información. |
| D10 | **Lo que no cambia** | Rutas, API, backend, reglas de privacidad de menores, avisos legales (`guardian-notice`, `material-minor-notice`, «generado por IA»), los `data-testid` y roles de F10–F16, tipografías (Fredoka y Newsreader), `default-src 'self'` y la ausencia de librerías nuevas. |

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Tokens de zona:** `tokens.css` define para Mapa, Material, Resultados y Propietario un relleno, un texto/borde y su variante oscura; ninguna vista ni otro CSS lleva `#hex`, `rgb()` ni `hsl()` (el test de F10 sigue en verde). |
| AC-2 | **Contraste:** todo par de color nuevo (texto sobre relleno de zona, icono sobre burbuja, chip de saludo, etiquetas) está en `tests/design/contrast.spec.ts` y cumple AA (4,5:1 texto y 3:1 elementos) en claro y en oscuro. |
| AC-3 | **Barra superior:** cada enlace tiene icono en burbuja de color; el activo va relleno con el color de su zona y con una marca de forma, no solo de color; `aria-current="page"` solo en el activo; «Propietario» solo para el propietario; se conservan `nav-map`, `nav-material`, `nav-results`, `nav-owner`, `nav-user`, `rest`, `logout` y `nav-mascot`. |
| AC-4 | **Usuario:** muestra «¡Hola, {primer nombre}!» como texto plano; con nombre vacío, largo o con HTML se ve entero o recortado sin romper el diseño ni interpretar el marcado. |
| AC-5 | **Marca y movimiento:** el gesto de la mascota y cualquier otra animación solo existen bajo `prefers-reduced-motion: no-preference` (un test lee el CSS); con la preferencia de reducir movimiento no hay animación y todo sigue visible. |
| AC-6 | **Móvil:** a 360–430 px la barra inferior muestra los mismos destinos con sus colores, con objetivos de al menos 44 px, sin desbordamiento horizontal y sin que el contenido la tape (comprobado con `elementFromPoint` sobre cada enlace). |
| AC-7 | **Acento por página:** Inicio, Mi material, Resultados, Propietario, Curso y Lección aplican el color de su zona en la cabecera y en los títulos de tarjeta; el acento sale de la ruta y de los tokens, sin estilos a mano por vista. |
| AC-8 | **Pantallas pendientes:** Nuevo curso, Tengo un código, Curso, Lección, Login/Registro y el panel del tutor usan los componentes de `ui/` y conservan **todos** sus `data-testid`, textos de privacidad y flujos; los tests de F04–F09 que dependen de ellas siguen en verde (se actualizan solo los que comprueban marcado o frases que cambian a propósito). |
| AC-9 | **Estados con forma:** etiquetas y botones de éxito, aviso, error y bloqueo dicen su estado con icono o texto, nunca solo con color (un test recorre los componentes de `ui/`). |
| AC-10 | **Alegría medida:** el destello de planta al 100 %, el «¡Hecho!» al completar una lección y el saludo por franja del día existen y se prueban; ninguno se repite sin parar ni oculta información, y el texto del saludo no depende de la hora del servidor para un menor ni enseña datos personales. |
| AC-11 | **Texto plano y privacidad:** nombres de personas, cursos, documentos y temas se pintan siempre como texto (nada de `v-html`), también hostiles; un menor no ve ningún elemento nuevo que enseñe dinero, topes o datos de otros usuarios. |
| AC-12 | **Sin regresiones:** backend, rutas y API intactos; los tests de F01–F16 que dependen del frontend siguen en verde; el test de entorno de dependencias no cambia (sin librerías nuevas). |

### Casos borde
- Nombre con apóstrofo, tilde, HTML, vacío o de más de 60 caracteres: el saludo se parte o recorta sin romper la barra.
- Menor, adulto sin menores, tutor y propietario: cada uno ve solo sus destinos (el menor, sin «Propietario» ni dinero).
- Modo noche: todos los acentos de zona tienen su variante oscura y pasan contraste.
- Preferencia de reducir movimiento activa: ningún destello, confeti ni gesto de mascota; todo lo demás igual.
- Zoom del texto al 200 % y ventana estrecha: la barra no desborda y los botones se apilan o pasan a la barra inferior.
- Teclado: orden de tabulación coherente, foco visible en todos los botones de color, «Dejarlo por hoy» sigue montando la vista de nuevo.
- Navegar a una ruta sin zona propia (error, login): usa el acento neutro y no rompe la barra.
- Navegador sin soporte de degradados o de `color-mix`: cae a un color liso legible.

### Fuera de alcance (v1)
- Cambios de backend, de la lógica de cursos, IA, estudio o material.
- Nuevas tipografías, nuevos temas completos o librerías de iconos, animaciones o confeti.
- Ilustraciones nuevas por tema (F12) o personalización del color por usuario.
- Sonidos y vibración.

### Definición de hecho
- Todos los AC y casos borde con test de frontend (incluidos contraste, movimiento, privacidad del menor y entradas hostiles), y los tests de las pantallas migradas actualizados solo donde cambia el marcado a propósito.
- Pasada en el navegador en escritorio y en móvil (360–430 px), en claro y en noche, como menor, tutor y propietario, con `elementFromPoint` sobre todos los enlaces; capturas revisadas pantalla a pantalla (Inicio, Mi material, Resultados, Propietario, Nuevo curso, Tengo un código, Curso, Lección, Login y panel del tutor).
- `security-checklist` pasada (solo frontend, pero toca datos de menores y texto de usuario); `llm-integration-review` no aplica.
- ADR nuevo en `ARCHITECTURE.md`; README, CLAUDE y BACKLOG al día (se cierra F16-3); informe en `sdd/reports/`.
