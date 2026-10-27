# F12 — Ilustraciones de portada generadas por IA (primera versión)

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md. Depende de F02 (capa de IA), F05 (cola de generación),
> F09 (edición y copia) y F11 (interfaz).

Los cursos ganan valor visual: cada **tema** del curso puede llevar una **ilustración de portada generada por IA** sobre su materia (el Nilo, una
célula, un volcán), con un estilo común a toda la aplicación. Decisiones del usuario: **primera versión** (una imagen por tema, no por lección) y
**API de pago** (no modelo local). Análisis de costes y riesgos hecho antes de la feature: la imagen es para **ambientar, no para enseñar hechos**
(las imágenes por IA inventan detalles), así que va siempre rotulada como generada por IA y con texto alternativo.

## Qué incluye
- **Una ilustración por tema**, generada **después** de que el tema tenga su contenido y **nunca bloqueando** el curso ni la lección: entra en la cola
  de F05 como una pieza más, con el mismo cerrojo de una generación por usuario, el mismo control de gasto y las mismas pausas (tope, proveedor caído).
- **Interruptor en el asistente de creación** («con ilustraciones: sí/no») que enseña el **coste estimado** antes de aceptar (precio configurado por
  imagen × número de temas). Apagado por defecto para menores hasta que se midan (decisión a confirmar en la FASE 1).
- **Tarea `IMAGE` en `AiGateway`** con su adaptador de proveedor de pago, su modelo por configuración, su precio **por imagen** (no por token) en
  `ai-pricing.properties` con la fecha en que se verificó, su registro de gasto (tarea, modelo, coste, usuario y curso; nunca el contenido) y los
  avisos al 80 % y al 100 %. Calidad baja o media por defecto, tamaño acotado y formato WebP.
- **Plantilla de petición** propia en `prompts/` con la guía de estilo común (ilustración plana y colorida, sin texto dentro de la imagen, sin personas
  reales ni marcas); el título y el resumen del tema van **como datos**, nunca dentro de la plantilla.
- **Texto alternativo** de cada imagen (breve, en español), generado por el modelo de texto y validado, para lectores de pantalla.
- **Almacenamiento y entrega:** la imagen se guarda en la base de datos (tabla nueva, con propietario) y se sirve por un endpoint que comprueba quién
  la pide; ajena o inexistente responde 404. La política `img-src 'self'` ya lo permite; sin recursos externos.
- **Interfaz:** la ilustración en la tarjeta del tema y en la cabecera de la lección, con el rótulo «Ilustración generada por IA», su texto
  alternativo y un estado de «preparándose» mientras no llega; si falla o se bloquea, se ve la planta del diseño actual y el curso sigue.
- **Editar:** el propietario puede **quitar** o **volver a generar** la ilustración de un tema (cuenta para el límite de ediciones con IA de F09 y para
  el tope mensual); la copia de un curso por código **incluye** las ilustraciones (son contenido, no datos del usuario).

## Reglas que siguen mandando
- Toda llamada pasa por `AiGateway`; ningún test llama a una API de pago (proveedor falso que devuelve una imagen mínima); un modelo nuevo exige precio
  y tarea en la configuración; una llamada que el proveedor cobra se contabiliza aunque falle o venga vacía; los límites por usuario viven en la BD.
- **Menores:** el texto del que sale la imagen pasa por `ContentGuard` antes de enviarse al proveedor; si salta, no se genera y se ve la planta. La
  moderación del proveedor se respeta (un rechazo no es un error técnico). Qué ve el adulto tutor de esto queda para la FASE 1.
- Nada de `v-html`; ningún recurso de terceros; el contenido de la imagen y su texto alternativo nunca van a logs ni a la auditoría; ningún mensaje del
  proveedor llega al usuario.
- Un tema **sin ilustración** (apagada, fallida o bloqueada) es un caso normal, no un error: el curso se estudia igual.

## Decisiones que quedan para la FASE 1
- Qué proveedor y modelo (se elige con un **benchmark real que lanza el usuario** con sus claves, como en F02: 3 proveedores, 3 temas, calidad y coste real;
  hasta entonces, un adaptador y un modelo por defecto configurables).
- Si el interruptor vive en la ficha del curso (`course.sheet`, que obligaría a subir su `schemaVersion`) o en una columna del curso.
- Si las ilustraciones **cuentan** para el tope de piezas por curso (150) o no.
- Qué hace la **copia de un curso** cuando quien canjea es un menor (las imágenes no se pueden filtrar por texto) y si la exportación JSON las incluye.
- Si el adulto tutor ve las ilustraciones de su menor y si una ilustración bloqueada genera un aviso en su panel.
- Límites por usuario (imágenes por hora y por curso) y tamaño máximo de la imagen guardada.

## Fuera de alcance (v1)
Una imagen por lección, diagramas SVG o Mermaid escritos por el modelo, imágenes reales de Wikimedia, modelos alojados en local, edición de la imagen,
imágenes subidas por el usuario, varias imágenes por tema, animaciones, y moderación propia de imágenes más allá de la del proveedor.
