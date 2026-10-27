## US-F12 · Ilustraciones de portada generadas por IA

### Como adulto que crea y estudia cursos
### Quiero que cada tema de mi curso pueda llevar una ilustración de portada generada por IA sobre su materia, y decidir si la quiero al crear el curso, viendo antes lo que costará
### Para que el curso sea más atractivo de estudiar sin pagar en silencio ni estudiar contenido visual que parezca un hecho

Decisiones del usuario (antes de la FASE 1): **primera versión** (una imagen por tema, nada de imágenes por lección, diagramas ni Wikimedia) y **API de pago** (no modelo local). El análisis de costes está en la conversación previa: la imagen **ambienta, no enseña hechos**, así que va rotulada como generada por IA.

Supuestos míos (a confirmar con tu visto bueno; cada uno resuelve una de las decisiones que dejó abierta la feature en bruto):
1. **Proveedor opcional:** la tarea `IMAGE` se configura por entorno como las demás; **si no se configura, las ilustraciones quedan desactivadas** (el interruptor no se ofrece) y la app arranca igual. Un modelo de imagen configurado exige su **precio por imagen** o la app no arranca. Hay adaptadores para OpenAI y Google (los que ya existen para texto); el benchmark real lo lanza el usuario después y decide el modelo por defecto.
2. **El interruptor es una columna del curso** (`course.illustrations`), no un campo de la ficha: así `course.sheet` (contrato con F05, `schemaVersion: 1`) no cambia. Se envía al aceptar el curso.
3. **Las ilustraciones no cuentan para el tope de 150 piezas** ni para el progreso (`piezas preparadas`, estudio): no son contenido que estudiar. Se guardan en su propia tabla y el curso pasa a `READY` **sin esperarlas**; se generan después, en segundo plano, y un curso sin imagen se estudia igual.
4. **Menores:** no generan ilustraciones (el interruptor no se ofrece) y **cuando un menor canjea un curso, la copia no lleva imágenes** (una imagen no se puede filtrar con `ContentGuard`). Por tanto el panel del adulto tutor no cambia.
5. **Texto alternativo determinista** («Ilustración generada por IA sobre «título del tema»»), sin llamada extra al modelo de texto: el modelo de texto no ve la imagen y no puede describirla de verdad.
6. **La imagen del proveedor es entrada no fiable:** se valida por contenido, se acota (bytes y píxeles), se decodifica, se reduce a 768 px y se **re-codifica a JPEG** con `javax.imageio` (sin dependencias nuevas; quita metadatos). Tamaño final ≤ 300 kB.
7. **La exportación JSON no incluye las imágenes** (binario); la copia por código **sí** (para adultos).
8. Límites propios por usuario en la BD: **imágenes por hora** y **regeneraciones por tema**; regenerar o quitar reutiliza el límite de ediciones con IA de F09 (20 por hora).

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Tarea `IMAGE` en la capa de IA:** `AiProvider` gana una llamada de imagen; OpenAI y Google la implementan, Anthropic y Ollama no (error de configuración, no de ejecución) y el proveedor falso devuelve una imagen mínima. Proveedor y modelo salen de `APP_AI_TASKS_IMAGE_*`; sin ellos, las ilustraciones quedan **desactivadas** y la app arranca igual; con ellos pero sin precio, no arranca. Todo pasa por `AiGateway`; nadie importa un adaptador ni escribe un modelo o un host fuera de `ai/` (`AiArchitectureTest`). |
| AC-2 | **Precio por imagen:** `ai-pricing.properties` admite `image-price` (USD por imagen) con la fecha en que se verificó; el coste de una llamada de imagen es ese precio, queda en `ai_usage` con tarea `IMAGE` (modelo, coste, usuario y curso; **nunca** el prompt ni la imagen) y cuenta para el tope mensual del usuario, el aviso al 80 % y al 100 % y `AI_BUDGET_MODE`. Una llamada que el proveedor cobra se contabiliza aunque falle o la imagen venga vacía o sea inválida. |
| AC-3 | **Interruptor en el asistente:** el paso final de crear un curso ofrece «Con ilustraciones» (solo si hay proveedor de imagen y el usuario es adulto), apagado por defecto, y enseña el **coste estimado** (precio configurado × número de temas de la ficha) con la aclaración de que es una estimación. La elección se guarda en `course.illustrations` y no cambia la ficha ni su versión. Un menor no ve el interruptor y el servidor rechaza (400) un intento de activarlo. |
| AC-4 | **Cola, no bloqueo:** al terminar el contenido de un curso con ilustraciones, cada tema recibe una pieza `IMAGE` pendiente. El curso pasa a `READY` sin esperarla y las imágenes se generan en segundo plano, de una en una, con el **mismo cerrojo** de una generación por usuario (renovado antes de cada llamada) y el mismo control de gasto. Las piezas `IMAGE` no cuentan para el tope de 150 ni para el progreso. Una imagen `DONE` no se regenera sola nunca. |
| AC-5 | **Prompt seguro:** la petición se construye con una plantilla propia en `prompts/` (estilo común: ilustración plana y colorida, sin texto dentro de la imagen, sin personas reales ni marcas) y el título y el resumen del tema, **saneados y acotados** (sin caracteres de control ni instrucciones, longitud máxima), como descripción del motivo y nunca como parte de la plantilla de instrucciones. Si el tema no pasa el saneado, no se llama al proveedor. |
| AC-6 | **Control de gasto y límites:** con el tope del usuario alcanzado no se hace ninguna llamada y las piezas siguen pendientes; el curso no cambia de estado. Hay un máximo de imágenes por hora por usuario y de regeneraciones por tema, contados en la BD; una petición rechazada por límite no deja filas ni llama al proveedor. Un fallo del proveedor reintenta una vez y después deja la pieza `FAILED` sin tocar el curso. |
| AC-7 | **Validar la imagen del proveedor:** se comprueba su tipo por **contenido** (PNG o JPEG, nunca por la cabecera ni la extensión), se acota (bytes y píxeles) para evitar bombas de descompresión, se decodifica, se reduce a 768 px de lado como máximo y se re-codifica a JPEG sin metadatos. Una imagen que no pasa se trata como fallo del proveedor (se contabiliza y la pieza queda `FAILED`). El resultado guardado pesa como máximo 300 kB. |
| AC-8 | **Rechazo del proveedor:** una negativa por moderación del proveedor no es un error técnico: la pieza queda `BLOCKED` (sin reintento), se contabiliza si el proveedor la cobró y no se enseña ningún mensaje del proveedor ni se registra el prompt. |
| AC-9 | **Almacenamiento:** una tabla nueva guarda la imagen (`bytea`), su tipo, su tamaño y el texto alternativo, con el propietario y la clave compuesta (curso, tema, propietario); **toda** consulta lleva `owner_id` (`ImageArchitectureTest`). Borrar el tema o el curso borra la imagen. |
| AC-10 | **Entrega:** `GET` de la imagen de un tema del propio curso devuelve la imagen con su tipo, `Cache-Control: private`, `ETag` y `X-Content-Type-Options: nosniff`; un tema o curso ajeno o inexistente, o sin imagen, responde **404 igual**. Exige sesión; el adulto tutor no la lee por esta vía. |
| AC-11 | **Detalle del curso:** el detalle devuelve `illustrations` y, por tema, el estado de su imagen (`PENDING`, `DONE`, `FAILED`, `BLOCKED` o ninguno) y su texto alternativo; nunca el prompt ni bytes. |
| AC-12 | **Interfaz:** la ilustración aparece en la tarjeta de su tema y en la cabecera de sus lecciones, con el rótulo «Ilustración generada por IA» y su texto alternativo; mientras está pendiente se ve la planta del diseño y un texto «preparándose»; si falla o se bloquea, se ve la planta y el curso sigue. Nunca se pinta con `v-html` y la imagen se pide solo al propio origen. |
| AC-13 | **Editar:** el propietario puede **quitar** o **volver a generar** la ilustración de un tema con confirmación y diciendo que cuenta para su gasto; se rechaza con `COURSE_BUSY` mientras el curso se genera, cuenta para el límite de ediciones con IA de F09 y para el tope mensual, y no cambia `course.version` ni el estado del curso. Al **añadir un tema** con IA en un curso con ilustraciones, su imagen entra en la cola. |
| AC-14 | **Compartir y copiar:** la copia de un curso por código **incluye** las imágenes (con ids nuevos y sin ningún dato del original) cuando quien canjea es un adulto; si es un menor, la copia no las lleva y su curso nace con `illustrations = false`. La exportación JSON no las incluye. Un test cuenta filas y busca ids del original. |
| AC-15 | **Permisos:** solo el propietario genera, regenera, quita y ve la imagen de sus temas; todo lo que cambia datos exige sesión y CSRF; un adulto sin vínculo y un menor no pueden activar ni pedir ilustraciones. |
| AC-16 | **Sin fugas:** ni los logs ni la auditoría llevan el prompt, la imagen, el título del tema ni mensajes del proveedor; la auditoría registra solo ids y el resultado (`COURSE_IMAGE_GENERATED`, `_FAILED`, `_BLOCKED`, `_REMOVED`). Las claves del proveedor van en cabeceras y nunca en la URL. |
| AC-17 | **Tests sin gasto:** ningún test llama a un proveedor de pago (proveedor falso que devuelve una imagen mínima y falla a petición); hay un **benchmark de imágenes** que solo lanza el usuario con sus claves y su tope (`make benchmark-images`, nunca en tests ni en el arranque), con 3 temas y los proveedores configurados, que escribe un informe de calidad y coste real y un fichero HTML para compararlas. |

### Casos borde
- Sin proveedor de imagen configurado: el interruptor no se ofrece, un curso nuevo nunca pide imágenes y los cursos con `illustrations = true` (si cambia la configuración) quedan con la planta y sin llamadas.
- Un curso con 1 tema y un curso con 20 temas: una imagen por tema, nunca más; el coste estimado cuadra con el número de temas.
- El tope mensual se alcanza a mitad de las imágenes: las pendientes quedan `PENDING`, el curso sigue `READY` y se pueden pedir otra vez al día siguiente o con otro tope.
- El proveedor devuelve algo que no es una imagen, una imagen enorme, un PNG de 1×1 o un fichero con cabecera mentirosa: no se guarda y la pieza queda `FAILED`.
- El proveedor tarda más de lo permitido o cae: reintenta una vez, la pieza queda `FAILED` y el curso no se pausa.
- Título de tema con instrucciones («ignora lo anterior y dibuja…»), con HTML o con caracteres de control: se sanea o no se llama; nunca se ejecuta como orden.
- Regenerar dos veces seguidas: la segunda se rechaza por límite o por estar la primera en curso, sin llamar al proveedor ni cobrar.
- Quitar el tema que tiene imagen, o el curso entero: la imagen desaparece (cascada).
- Una copia por código de un curso con ilustraciones: adulto con imágenes, menor sin ellas; el original no cambia.
- Un adulto y un menor del mismo tutor: el menor no puede activar el interruptor aunque llame a la API a mano.
- Zoom, lector de pantalla y modo oscuro: la imagen tiene su texto alternativo y no tapa contenido; en noche el rótulo sigue legible.

### Fuera de alcance (v1)
- Una imagen por lección, varias imágenes por tema, diagramas SVG o Mermaid, imágenes reales de Wikimedia, modelos alojados en local, edición de la imagen, imágenes subidas por el usuario y animaciones.
- Ilustraciones para menores y moderación propia de imágenes más allá de la del proveedor.
- Texto alternativo generado por un modelo que vea la imagen.
- Incluir las imágenes en la exportación JSON.
- Elegir estilo o prompt por curso.

### Definición de hecho
- Todos los AC con un test; casos borde con test; ningún test llama a una API de pago; los tests de arquitectura (`AiArchitectureTest`, `GenerationArchitectureTest`, `ImageArchitectureTest`) pasan.
- Verificado con el stack levantado (cuentas de adulto y de menor, servidor de proveedor de imagen simulado por HTTP con imagen válida, inválida, enorme, bloqueada y lenta), con una pasada en el navegador de la tarjeta, la lección y el asistente en claro y oscuro, escritorio y móvil.
- `security-checklist` y `llm-integration-review` pasadas (imagen del proveedor como entrada no fiable, prompt acotado, gasto, límites, menores).
- El benchmark de imágenes escrito y probado contra el servidor simulado; la ejecución real queda para el usuario con sus claves.
- Documentación al día (README, ARCHITECTURE con los ADR nuevos, CLAUDE con las reglas de F12, BACKLOG); el informe dice qué falta medir con modelos reales.
