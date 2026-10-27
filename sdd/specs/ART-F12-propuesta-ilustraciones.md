# F12 · Ilustraciones de portada generadas por IA — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F12-ilustraciones-ia.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 77), `ART-F05` (cola), `ART-F09` (edición y copia) y las reglas de F02. **Es la primera feature que toca backend y frontend desde F09**; no cambia ningún contrato HTTP existente salvo añadir campos opcionales.

## ART-001 · Stack

Sin librerías nuevas. La imagen del proveedor se valida y re-codifica con `javax.imageio` (JDK); el frontend usa `<img>` del mismo origen.

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Imagen | `AiProvider.image(...)` (nueva llamada) con adaptadores OpenAI (`/v1/images/generations`, `b64_json`, PNG) y Google (modelos de imagen vía `generateContent` con `responseModalities: IMAGE`) | Los dos proveedores que ya tenemos para texto; Anthropic y Ollama no ofrecen imagen (error de configuración). Los formatos se prueban **según la documentación**; la confirmación definitiva es el benchmark (regla de F02). |
| Proceso de imagen | `ImageProcessor` con `javax.imageio` | Sin dependencias: decodifica PNG/JPEG, acota, reduce a 768 px y re-codifica a JPEG (quita metadatos). WebP no se admite (el JDK no lo lee): se pide PNG. |
| Almacenamiento | `bytea` en PostgreSQL | ≤ 300 kB por imagen, ~1–2 MB por curso; sin sistema de ficheros ni almacén de objetos (misma línea que el original del material, ADR-30). |
| Cola | Hilo de fondo propio (`IllustrationWorkerLoop`) con el `GenerationLock` de F02 | Las ilustraciones no son piezas del curso: no tocan `course_piece`, ni el tope de 150, ni el progreso, ni `course.status`. |
| Frontend | Vue 3 + `<img>` + `PlantIllustration` como respaldo | Misma interfaz de F11; política `img-src 'self'` ya vigente. |

Variables nuevas, **todas opcionales** (sin ellas las ilustraciones quedan desactivadas y la app arranca igual): `APP_AI_TASKS_IMAGE_PROVIDER`, `APP_AI_TASKS_IMAGE_MODEL`. El precio por imagen y los límites van en `ai-pricing.properties` y `application.properties` (sin variable de entorno), como el resto de valores de ingeniería.

## ART-002 · Modelo de datos (`V10__illustrations.sql`)

```
course            + illustrations boolean NOT NULL DEFAULT false      elección del usuario al crear el curso; gobierna solo la GENERACIÓN (la copia nace en false)
ai_usage          task CHECK ampliado con 'IMAGE'                      (se recrea el CHECK con todas las tareas vigentes)
course_action     kind CHECK ampliado con 'AI_IMAGE'                   cuenta las imágenes generadas por hora y usuario

course_image      id uuid PK
                  course_id, owner_id, topic_id          FK compuesta (course_id, owner_id) → course ON DELETE CASCADE;  (topic_id, owner_id) → course_topic ON DELETE CASCADE
                  status varchar(8) NOT NULL DEFAULT 'PENDING' CHECK (PENDING | DONE | FAILED | BLOCKED | REMOVED)
                  error_kind varchar(30)                  tipo de error, nunca texto del proveedor ni del modelo
                  attempts smallint NOT NULL DEFAULT 0,  regenerations smallint NOT NULL DEFAULT 0 CHECK (regenerations <= 20)
                  alt_text varchar(200)                    determinista: «Ilustración generada por IA sobre «título»»
                  mime varchar(16), size_bytes integer CHECK (size_bytes <= 307200), content bytea   solo en DONE
                  created_at, generated_at
                  UNIQUE (topic_id, owner_id)              una imagen por tema · índice (course_id, owner_id, status) · índice parcial de PENDING para la cola
```

- `REMOVED` **conserva la fila** (sin bytes) para que el hilo no vuelva a encolar un tema cuya imagen quitó el propietario; regenerar la devuelve a `PENDING`.
- `GRANT SELECT, INSERT, UPDATE, DELETE ON course_image TO studylab_app`. Toda consulta lleva `owner_id` en el mismo `WHERE`, salvo la de la cola, que reclama con `FOR UPDATE SKIP LOCKED` y comprueba el propietario después (como `claimNext` de F05).
- Lo que **no** se guarda: el prompt, el título como parte de un registro, mensajes del proveedor.

## ART-003 · Endpoints

Todos exigen sesión; los que cambian datos, CSRF. Ajeno o inexistente → 404 igual. Los errores salen por `GlobalExceptionHandler` con mensaje seguro.

```
GET    /api/illustrations/offer                          → {available, pricePerImageUsd, maxTopics}  — available = proveedor configurado Y usuario adulto; el menor siempre false
POST   /api/courses            {intakeId, answers, illustrations?}   → (existente) illustrations=true solo si available; si no, 400 ILLUSTRATIONS_UNAVAILABLE
GET    /api/courses/{c}        → (existente) + illustrations y, por tema, image: {status, alt}  (nada si no hay fila o está REMOVED; nunca bytes ni prompt)
GET    /api/courses/{c}/topics/{t}/image                 → 200 image/jpeg · Cache-Control: private, max-age=3600 · ETag · X-Content-Type-Options: nosniff · inline; sin imagen DONE → 404
POST   /api/courses/{c}/topics/{t}/image/regenerate      → 202 detalle del curso · el curso no puede estar generándose (409 COURSE_BUSY) · 429 IMAGE_RATE_LIMITED / REGENERATION_LIMIT · cuenta para AI_EDIT (20/h)
DELETE /api/courses/{c}/topics/{t}/image                 → 200 detalle · idempotente · 409 COURSE_BUSY si se genera · pasa a REMOVED
GET    /api/courses/{c}/pieces/{p}   → (existente) + topicId e image {status, alt} del tema, para pintar la cabecera de la lección (solo lecciones)
```

Códigos nuevos: `ILLUSTRATIONS_UNAVAILABLE` (400), `IMAGE_RATE_LIMITED` y `REGENERATION_LIMIT` (429, mensaje sin cifras internas). Sin endpoint de «generar ahora»: la cola es el único camino a la llamada de pago.

## ART-004 · Estructura de componentes

### Backend

```
ai/provider/   AiProvider (+ ImageResult image(ImageCall)) · ImageCall(model, prompt, size, quality, timeout) · ImageResult(bytes, mime) ·
               OpenAiProvider / GeminiProvider (implementan) · AnthropicProvider / OllamaProvider (Unsupported) · FakeProvider (PNG 128×128 válido; fallos a petición)
ai/            AiGateway.generateImage(AiImageRequest) → AiImageResponse(bytes, mime, costUsd)  · usa withRetries y BudgetService como el chat; registra ai_usage tarea IMAGE
               AiTask.IMAGE · AiProperties: image-pricing[proveedor/modelo].per-image y tasks.image.* · AiSettingsValidator: IMAGE opcional; configurada sin precio → no arranca
               ProviderErrorKind.MODERATION → AiErrorKind.MODERATED (negativa del proveedor: no es un error técnico y no se reintenta)
ai/benchmark/  ImageBenchmarkRunner · ImageBenchmarkLauncher (perfil benchmark-images)
illustrations/ NUEVO paquete                                                                                              AC-3..AC-16
├─ IllustrationProperties       calidad, tamaño pedido, máximos de bytes/píxeles del proveedor, lado y peso finales, JPEG, límites por hora y regeneraciones por tema
├─ ImagePromptBuilder           plantilla prompts/illustration.txt + motivo saneado (sin caracteres de control, longitud acotada) · devuelve vacío si no pasa el saneado
├─ ImageProcessor               bytes del proveedor → JPEG ≤ 768 px y ≤ 300 kB, o ImageRejected (tipo por contenido, bombas de descompresión, 1×1, cabeceras mentirosas)
├─ ImageRepository              toda consulta con owner_id · enqueueMissing · claimNext · saveDone/saveFailed/saveBlocked · remove/regenerate · info por tema
├─ IllustrationGenerator        processPending(): enqueueMissing → reclamar → GenerationLock + renew → presupuesto → ImagePromptBuilder → AiGateway → ImageProcessor → guardar
├─ IllustrationWorkerLoop       hilo de fondo (app.illustrations.worker.enabled=false en los tests; processPending() síncrono)
├─ IllustrationService          offer, quitar, regenerar (FOR UPDATE del curso, COURSE_BUSY, límites en BD), imagen para servir
├─ IllustrationController       GET offer · GET/POST/DELETE de la imagen de un tema
└─ IllustrationException        códigos y mensajes seguros
course/        CourseService/CourseController: illustrations en el alta (adulto + proveedor) · CourseDetail: illustrations
generation/    CourseContentService/Controller: image por tema en el detalle (vía una lectura pública de illustrations) y topicId en la pieza
share/         CourseCopier: copia course_image DONE (ids nuevos) cuando quien canjea es adulto; la copia nace con illustrations=false; menor: sin imágenes
audit          COURSE_IMAGE_GENERATED / _FAILED / _BLOCKED / _REMOVED (solo ids, fuera de transacción)
```

Cobertura: AC-1 `ai/`; AC-2 `AiGateway` + pricing; AC-3 `IllustrationController.offer` + `CourseService`; AC-4 y AC-6 `IllustrationGenerator`; AC-5 `ImagePromptBuilder`; AC-7 `ImageProcessor`; AC-8 `AiErrorKind.MODERATED`; AC-9 `ImageRepository`; AC-10 y AC-11 controladores; AC-12 frontend; AC-13 `IllustrationService`; AC-14 `CourseCopier`; AC-15 `AccessGuard`/servicio; AC-16 pruebas de logs y de arquitectura; AC-17 proveedor falso y `ImageBenchmarkRunner`.

### Reglas de arquitectura (pruebas nuevas)
- `ImageArchitectureTest`: `illustrations/` **solo** accede a `ai.AiGateway`, `ai.lock` y `safety`; no importa adaptadores (`ai.provider`), no escribe nombres de modelo ni hosts, no usa `java.io.File`/`java.nio.file`, no construye HTML ni escribe en `course_piece`; toda consulta sobre `course_image` lleva `owner_id` (salvo `claimNext`).
- `AiArchitectureTest` (existente): `IMAGE` pasa por `AiGateway`; `generation/` no importa `illustrations/` salvo la lectura pública del detalle.

### Comportamiento de la cola
1. `processPending()` empieza con `enqueueMissing`: inserta una fila `PENDING` por cada tema de un curso `READY` con `illustrations = true` **que no tenga fila** (cubre cursos nuevos y temas añadidos por F09; no recrea `REMOVED` ni reintenta `FAILED`/`BLOCKED`).
2. Reclama la siguiente `PENDING` (`SKIP LOCKED`) de un curso `READY` cuyo usuario está activo; toma el `GenerationLock` del usuario (si hay otra generación suya, la salta) y lo renueva antes de la llamada; si `renew()` da `false`, la deja `PENDING` y no escribe nada.
3. Comprueba el tope y los límites **antes** de llamar; sin presupuesto no hay llamada y la fila sigue `PENDING`.
4. Construye el prompt; si no pasa el saneado, la fila queda `BLOCKED` sin llamar.
5. Llama por `AiGateway` (reintento transitorio acotado del gateway); `MODERATED` → `BLOCKED`; fallo → `FAILED`; imagen inválida → `FAILED` (ya contabilizada).
6. Procesa con `ImageProcessor` y guarda `DONE` con su texto alternativo; audita con ids.
Un fallo de una imagen **nunca** cambia `course.status` ni pausa el curso.

## ART-005 · Estructura de carpetas

```
backend/src/main/java/es/vicai/studylab/
├─ ai/provider/ImageCall, ImageResult + métodos image(...) · ai/AiImageRequest, AiImageResponse · ai/benchmark/ImageBenchmarkRunner, ImageBenchmarkLauncher
├─ illustrations/    (paquete nuevo, ver ART-004)
backend/src/main/resources/
├─ db/migration/V10__illustrations.sql · prompts/illustration.txt · benchmark/image-topics.properties (3 temas) · ai-pricing.properties (+ image-pricing) 
backend/src/test/java/…/illustrations/   ImageProcessorTest, ImagePromptBuilderTest, IllustrationGeneratorTest, IllustrationFlowTest (HTTP), ImageArchitectureTest, IllustrationLogsTest, CourseCopierImagesTest
frontend/src/
├─ api/client.ts   (+ getIllustrationOffer, createCourse(illustrations), removeTopicImage, regenerateTopicImage, topicImagePath)
├─ components/TopicCover.vue · IllustrationToggle.vue · (CourseMap, PieceView, NewCourseView y CourseEditView lo usan)
frontend/tests/    components/TopicCover.spec.ts, IllustrationToggle.spec.ts · views/NewCourseView (interruptor), CourseEdit (quitar/regenerar), PieceView (cabecera)
scripts/ · Makefile (benchmark-images) · compose.yaml (servicio benchmark-images, perfil benchmark-images) · .env.example (variables opcionales)
sdd/benchmarks/  (informe e index.html del benchmark de imágenes; los genera el usuario)
```

## ART-006 · Wireframes

Último paso del asistente (solo adultos con proveedor de imagen):
```
┌ Resumen de tu curso ───────────────────────────────────────────┐
│ Tema: Historia antigua · 6 temas · con examen en cada uno       │
│ ┌────────────────────────────────────────────────────────────┐ │
│ │ [ ] Con ilustraciones (una por tema)                        │ │
│ │     Coste estimado: ≈ 0,07 USD (6 imágenes). Es una         │ │
│ │     estimación; cuenta para tu tope mensual.                │ │
│ └────────────────────────────────────────────────────────────┘ │
│ [Aceptar y crear el curso]                                      │
└─────────────────────────────────────────────────────────────────┘
```
Tarjeta de tema (curso) con portada:
```
┌ TEMA 2 · 2 DE 3 ──────────────────────────────┐
│ ┌──────────┐  Egipto            [67 %] [Plegar] │
│ │ ilustra- │  ───────────────────────────────── │
│ │ ción IA  │  Cap. 2.1 …   Hecha   Ejercicios    │
│ └──────────┘  Cap. 2.2 …   Por venir             │
│  Ilustración generada por IA                      │
└──────────────────────────────────────────────────┘
```
Sin imagen: la planta de F11 y, si está pendiente, «Preparándose…». En la edición del curso, por tema: «Quitar ilustración» y «Volver a generar» (confirmación; dice que cuenta para el gasto).

## ART-007 · ADR (se copiarán a `ARCHITECTURE.md` en la FASE 5)

### ADR-78: La imagen es una tercera modalidad de `AiProvider`/`AiGateway`, con precio por imagen en un mapa propio
- Motivo: el gateway ya da tope de gasto, avisos, registro, reintentos acotados y cerrojo; una imagen cobra por unidad y no por token, así que `image-pricing` (por `proveedor/modelo`) evita tocar el registro `Price` que usan todos los demás. Una llamada que el proveedor cobra se contabiliza siempre.
- Alternativas descartadas: un cliente de imágenes aparte (duplica presupuesto y registro); meter el precio por imagen en `Price` (rompe construcciones y pruebas existentes).
- Revisitar si: un proveedor de imagen cobra por tokens (entonces, mismo mecanismo que el texto).

### ADR-79: Las ilustraciones viven fuera de `course_piece`, con su tabla y su hilo, y el curso no las espera
- Motivo: no son contenido que estudiar; así no tocan el tope de 150, el progreso (ADR-49) ni `course.status`, y un fallo, un tope o una pausa de imágenes nunca pausa un curso. El hilo **descubre** lo que falta (`enqueueMissing`) en vez de que `generation/` lo encole: cubre cursos nuevos y temas añadidos con IA (F09) sin acoplar paquetes. `REMOVED` conserva la fila para que no se vuelva a encolar.
- Alternativas descartadas: una pieza `IMAGE` en la cola de F05 (contaminaría contadores, estados y pausas); generar durante la creación del curso (bloquea y encarece los fallos).
- Revisitar si: se quieren imágenes por lección o varias por tema.

### ADR-80: La imagen del proveedor es entrada no fiable y se re-codifica
- Motivo: viene de un tercero y se guarda y se sirve a un navegador. Se decide su tipo por contenido, se acotan bytes y píxeles antes de decodificar (bombas de descompresión), se descartan 1×1 y cabeceras mentirosas, se reduce a 768 px y se re-codifica a JPEG sin metadatos (quita EXIF y C2PA; la etiqueta «generada por IA» es de la interfaz). Sin dependencias: `javax.imageio`. WebP no se admite y se pide PNG.
- Alternativas descartadas: guardar los bytes tal cual (metadatos y ficheros hostiles); una librería de imágenes (superficie nueva).
- Revisitar si: un proveedor solo ofrece WebP.

### ADR-81: El prompt es plantilla + motivo saneado, y el texto alternativo es determinista
- Motivo: las APIs de imagen no separan instrucciones y datos; el único dato es el motivo (título y resumen del tema), así que se sanea, se acota y se rodea de una plantilla fija con el estilo común, y si no pasa, no hay llamada. El modelo de texto no ve la imagen, por lo que un alt «descriptivo» sería inventado: se usa «Ilustración generada por IA sobre «título»».
- Alternativas descartadas: dejar al modelo de texto escribir el prompt (superficie de inyección y un coste más); alt text generado.
- Revisitar si: se usa un modelo con visión para describir la imagen.

### ADR-82: Cinco estados, límites en la BD y copia pasiva
- Motivo: `PENDING`, `DONE`, `FAILED`, `BLOCKED` (negativa del proveedor, sin reintento) y `REMOVED`. Ni `FAILED` ni `BLOCKED` se reintentan solos (coste); regenerar es explícito, limitado (20 ediciones con IA por hora de F09, 3 regeneraciones por tema, imágenes por hora) y contado en la BD. La **copia** por código lleva las imágenes `DONE` pero nace con `illustrations = false`: así nunca se cobra una generación al que canjea sin pedirla. Un menor no genera ni recibe imágenes (no se pueden filtrar con `ContentGuard`).
- Alternativas descartadas: reintentos automáticos (gasto imprevisible); copiar el interruptor (cobraría al canjeador).
- Revisitar si: se filtran imágenes (moderación propia) y se abre a menores.

### ADR-83: El benchmark de imágenes es un perfil aparte que solo lanza el usuario
- Motivo: gasta dinero real y necesita claves; como el de texto (ADR-26), vive en un servicio de Compose de un solo uso (`make benchmark-images`) con tope en USD, 3 temas fijos y los modelos que indique `APP_AI_BENCHMARK_IMAGE_MODELS`; deja el informe y un `index.html` estático con las imágenes lado a lado y su coste real para compararlas a ojo. Nunca se ejecuta desde un test ni en el arranque.
- Alternativas descartadas: ampliar el benchmark de texto (otro formato y otro coste); medir la calidad automáticamente (no es fiable para imágenes).
- Revisitar si: se quiere puntuar con un modelo con visión.

## Decisiones pendientes de tu respuesta
1. **`REMOVED` como fila:** guardo la fila sin bytes para que no se vuelva a encolar; la alternativa (borrar y marcar en el curso) es más complicada.
2. **Copia pasiva:** la copia no genera imágenes aunque el original las tuviera; si quieres que el que canjea pueda activarlas, sería una opción nueva.
3. **Orden de entrega:** propongo primero backend completo con proveedor falso, luego benchmark y adaptadores reales, y por último la interfaz; así puedes lanzar el benchmark antes de ver nada visual.
