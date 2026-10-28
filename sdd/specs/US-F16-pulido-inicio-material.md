## US-F16 · Pulido de «Inicio» y «Mi material»

> FASE 1. Acordado con el usuario el 28-10-2026 («pulir Inicio y Mi material»): mismo estilo que Resultados, con los componentes de `ui/` (F15-3), y enlazar «Tus peques» con el panel del tutor (F15-2). Lo demás (D1–D9) son propuestas mías a confirmar.

### Como persona que usa Study Lab (adulto o menor)
### Quiero que el Inicio y «Mi material» tengan el mismo aire que Resultados: tarjetas claras, botones reconocibles y estados que se entienden
### Para que no tenga que leer frases con enlaces subrayados ni formularios sueltos, y pueda subir un documento o dar de alta a un menor de un vistazo

Contexto: tras F15, Resultados tiene tarjetas, medidor y botón «Volver», pero **Inicio** y **Mi material** siguen con su estilo de antes. En Inicio, «Tu espacio de estudio» son tres frases con un enlace subrayado cada una; «Mis menores» muestra a cada menor en una fila con enlaces y botones sueltos y el formulario de alta siempre abierto debajo. En Mi material, subir un documento es un `<input type="file">` desnudo del navegador, la lista es una fila de texto con botones pequeños y el borrado se confirma en la misma fila. Todo lo que hace falta ya existe (componentes `ui/`, tokens, `PlantIllustration`, `Mascot`); no hay que tocar el backend.

### Decisiones propuestas

| # | Decisión | Propuesta |
|---|----------|-----------|
| D1 | «Tu espacio de estudio» (Inicio) | Las tres frases pasan a **tres tarjetas-atajo** que son el enlace entero: icono (Lucide), título y una línea de descripción. «Mi material», «Tengo un código» y «Resultados» (con la frase distinta para un menor, sin dinero). 44 px de alto como mínimo, en rejilla que se apila en móvil. Conservan `material-link`, `redeem-link` y `results-link` y sus destinos. |
| D2 | «Mis menores» (Inicio, solo tutores) | Cada menor es una **tarjeta**: nombre, etiqueta de estado en palabras con icono («Activo» / «De baja»), su identificador de acceso como texto y tres botones con forma de botón (`Ver actividad`, `Cambiar contraseña`, `Dar de baja` o `Reactivar`). El cambio de contraseña sigue en su sitio, con sus mensajes. Conservan todos los `data-testid` (`student-*`, `student-activity-*`, `reset-*`, `deactivate-*`, `reactivate-*`…). |
| D3 | Alta de un menor | El formulario sale de estar siempre abierto: se pliega en un bloque **«Añadir un menor»** (botón que lo abre y lo cierra, con `aria-expanded`). Con 0 menores empieza abierto. Mismos campos, validaciones, mensajes y `data-testid`; tras dar de alta con éxito se queda abierto el aviso del identificador generado. |
| D4 | Enlace desde «Tus peques» (Resultados, F15-2) | Cada menor de «Tus peques» lleva un botón **«Ver actividad»** a `/alumnos/{id}` (el panel del tutor de F08, que ya exige ser su tutor y responde 404 si no). Mismo estilo que en D2. |
| D5 | Subir un documento (Mi material) | Una **zona de subida** grande y con sentido: icono, «Arrastra un documento aquí o elige un fichero», tipos y tamaño máximo y un botón «Elegir fichero». El `<input type="file">` real sigue siendo el control (teclado y lector de pantalla), oculto visualmente pero accesible; arrastrar y soltar es una mejora que usa **la misma subida** (mismas comprobaciones, mismos errores). Estados: subiendo, error y listo, con palabras. Conserva `file-input`, `uploading` y `upload-error`. |
| D6 | Lista de documentos | Cada documento es una **tarjeta**: icono por tipo (PDF, Word, Texto), nombre como texto, tipo y tamaño, estado en palabras con icono (los textos de siempre —Subido, Procesando, Listo y Error— ahora con icono, y el motivo del error en una frase) y el botón «Borrar». El borrado **sigue pidiendo confirmación** (hoy en la misma fila con «¿Borrar este documento?»): se mantiene el flujo y los `data-testid` (`doc-*`, `delete-*`, `confirm-delete-*`, `cancel-delete-*`, `doc-status-*`, `doc-error-*`), solo cambia la forma. |
| D7 | Estados vacíos y tono | Sin documentos o sin cursos: una frase amable con la planta o la mascota de F11 en vez de una línea gris suelta. Textos cortos y cálidos; los avisos legales al menor (`guardian-notice`, `material-minor-notice`) **no cambian de texto ni de sitio**. |
| D8 | «Material de mis menores» | Mismo componente de tarjetas en solo lectura, con el nombre del menor como título; sin botones de borrar. |
| D9 | Lo que no cambia | Rutas, API, reglas de privacidad de menores, el aviso «Tu adulto tutor puede ver…», la subida (tipos, tamaño, procesado y sondeo), el borrado con confirmación y los tokens. Sin librerías nuevas y **sin cambios de backend**. |

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Atajos:** el Inicio enseña tres tarjetas-atajo que son enlaces completos (icono, título, descripción) a `/material`, `/codigo` y `/resultados`, con 44 px, sin subrayado, y conservan sus `data-testid`. Para un menor, «Resultados» se describe sin dinero. |
| AC-2 | **Menores (tutor):** cada menor es una tarjeta con su nombre, el estado en palabras con icono, su identificador de acceso y los botones `Ver actividad`, `Cambiar contraseña` y `Dar de baja`/`Reactivar`; las acciones siguen funcionando igual (mismos `data-testid`, mismos mensajes de éxito y error). Un menor no ve este bloque. |
| AC-3 | **Alta:** el formulario de alta está plegado en «Añadir un menor» (abierto si no hay ninguno), con `aria-expanded`; mismos campos, validaciones y mensajes; el identificador generado se enseña tras el alta. |
| AC-4 | **Tus peques → actividad:** en Resultados cada menor lleva un botón «Ver actividad» a `/alumnos/{id}`; un menor y un adulto sin menores no ven el bloque (como en F15). |
| AC-5 | **Zona de subida:** Mi material enseña una zona con icono, texto de ayuda, tipos y tamaño máximo y «Elegir fichero»; el `<input type="file">` real sigue siendo accesible con teclado y `<label>`; elegir un fichero y arrastrarlo y soltarlo llaman a la misma subida y dan los mismos errores. Estados «Subiendo…» y error en palabras. |
| AC-6 | **Documentos:** cada documento es una tarjeta con icono por tipo, nombre (texto), tipo y tamaño, estado en palabras con icono, motivo del error en una frase y «Borrar»; el borrado pide confirmación como hoy; los `data-testid` de F03 se conservan. |
| AC-7 | **Menores y material:** «Material de mis menores» usa las mismas tarjetas en solo lectura, sin «Borrar»; el aviso `material-minor-notice` y `guardian-notice` conservan texto y lugar. |
| AC-8 | **Vacíos y errores:** sin documentos y sin cursos hay una frase amable con ilustración; los errores de carga, subida y borrado salen en palabras, sin códigos del backend. |
| AC-9 | **Texto plano:** nombres de documentos, de cursos y de menores salen siempre como texto (nada de `v-html`), también hostiles; los nombres largos se parten sin desbordar. |
| AC-10 | **Diseño y accesibilidad:** colores solo de `tokens.css` (sin pares nuevos, o con su entrada en el test de contraste); un estado nunca solo con color; objetivos de 44 px; movimiento solo dentro de `prefers-reduced-motion: no-preference`; sin desbordamiento horizontal a 360–430 px; la zona de subida y los plegables se usan con teclado. |
| AC-11 | **Sin regresiones:** los tests de F01, F03, F08 y F15 que dependen de estas pantallas siguen en verde (los que comprueban la forma exacta del marcado o frases que cambian a propósito se actualizan); no cambian rutas, API ni reglas de privacidad. |

### Casos borde
- Tutor con 0 menores: «Añadir un menor» abierto y mensaje amable de «todavía no has dado de alta a nadie»; con varios menores, plegado.
- Menor de baja: etiqueta «De baja» y botón «Reactivar» (y sin «Dar de baja»).
- Documento en `ERROR` con cada motivo conocido (sin texto, protegido, dañado, demasiadas páginas, demasiado grande…) y con un motivo desconocido: una frase en palabras.
- Soltar un fichero que no es PDF, Word o texto, o más grande que el máximo, o dos ficheros a la vez: se avisa en palabras con los mismos errores que la subida normal y no se sube nada (o solo el primero válido, como hoy: un fichero cada vez).
- Soltar un fichero fuera de la zona o con el navegador sin arrastrar y soltar: nada se rompe; el botón «Elegir fichero» sigue funcionando.
- Subida en curso: la zona queda desactivada y dice «Subiendo…»; un segundo fichero espera.
- Nombre de documento o de menor con HTML o muy largo: texto plano y se parte.
- Un menor ve «Mi material» con su aviso y sin «Material de mis menores».

### Fuera de alcance (v1)
- Cambios de backend, de la lógica de subida, procesado o sondeo, o de los límites de tamaño y de documentos.
- Vista previa, renombrar, buscar o ordenar documentos; subir varios a la vez.
- Otras pantallas (Nuevo curso, Tengo un código, Curso, Lección, Login, panel del tutor).
- Nuevos temas de color o tipografías.

### Definición de hecho
- Todos los AC y casos borde con test de frontend (incluido el de arrastrar y soltar y el de teclado), y el test de privacidad del menor en Mi material.
- Pasada en el navegador en escritorio y en móvil (360–430 px), como tutor con menores, como adulto sin menores y como menor; `elementFromPoint` sobre los enlaces y la zona de subida, y sin desbordamiento horizontal.
- `security-checklist` pasada (es solo frontend, pero toca la subida de ficheros y datos de menores); `llm-integration-review` no aplica.
- ADR nuevo en `ARCHITECTURE.md`; README, CLAUDE y BACKLOG al día (F15-2 y F15-3 se cierran); informe en `sdd/reports/`.
