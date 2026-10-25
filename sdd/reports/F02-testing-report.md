## Testing Report — F02 · Capa de IA y control de gasto — 25-10-2026

Rama `feature/F02-capa-ia-control-gasto`. Todo se ejecutó con Docker, con variables de entorno **ficticias** (el `.env` del usuario no se leyó
ni se escribió) y **sin ninguna llamada a una API de pago**.

### Resumen
- Tests: backend **276** (0 fallos, 4 omitidos) · entorno **25** · frontend **71** (sin cambios en F02) = **372** ✅
- Los 4 omitidos son `OllamaLiveTest` (opt-in): ejecutados aparte contra el Ollama del contenedor, **4/4** ✅
- Cobertura del backend: **94,3 %** de líneas · 78,6 % de ramas (JaCoCo). Objetivo ≥ 80 % de líneas cumplido. Por paquete de `ai/`: budget 100 %,
  lock 100 %, benchmark 95 %, provider 93 %, prompt 93 %.
- Verificaciones **fuera de los tests** (ver más abajo): adaptadores por HTTP real contra un servidor simulado, Ollama real con un modelo real,
  el servicio `benchmark` de Compose de punta a punta y el endpoint `/api/ai/usage` sobre el stack levantado.

### Resultados por criterio de aceptación

| AC | Descripción | Tests principales | Estado |
|----|-------------|-------------------|--------|
| AC-1 | Interfaz única por tarea; nadie fuera de `ai/` usa un adaptador | `AiGatewayTest`, `AiArchitectureTest` | ✅ |
| AC-2 | Proveedor, modelo, límite y timeout por tarea; sin modelos en el código | `AiGatewayTest`, `AiArchitectureTest` | ✅ |
| AC-3 | Proveedor falso: predeterminado en dev, único en tests, prohibido en prod | `AiGatewayTest`, `AiStartupSettingsTest` | ✅ |
| AC-4 | Adaptadores de Anthropic, OpenAI, Gemini y Ollama | `*ProviderTest`, `ProviderRegistryTest`, `OllamaLiveTest` | ✅ (formatos: ver "No verificado") |
| AC-5 | Claves solo por entorno; el arranque falla nombrando la variable | `AiStartupSettingsTest` | ✅ |
| AC-6 | Registro de cada intento, de solo añadir y sin contenido | `AiGatewayTest`, `AiSchemaTest`, `AiArchitectureTest` | ✅ |
| AC-7 | Precios por modelo; sin precio no arranca | `AiGatewayTest`, `AiStartupSettingsTest` | ✅ |
| AC-8 | Tope mensual: `WARN` por defecto, `BLOCK` opcional | `AiBudgetWarnTest`, `AiBudgetBlockModeTest` | ✅ |
| AC-9 | Aviso al 80 % y al 100 %, una vez por mes y umbral | `AiBudgetWarnTest`, `AiSchemaTest` | ✅ |
| AC-10 | Límite de salida: se puede bajar, nunca subir | `AiGatewayTest`, `AiStartupSettingsTest` | ✅ |
| AC-11 | Una generación concurrente por usuario (+ renovación del cerrojo) | `GenerationLockTest` | ✅ |
| AC-12 | Reintentos acotados; nunca ante cuota agotada | `AiGatewayTest`, `*ProviderTest` | ✅ |
| AC-13 | Errores genéricos: ni mensaje del proveedor ni claves | `AiGatewayTest`, `AiExceptionHandlingTest`, `*ProviderTest` | ✅ |
| AC-14 | Embeddings con Ollama, dimensión validada | `AiGatewayTest`, `OllamaProviderTest`, `OllamaLiveTest` | ✅ |
| AC-15 | `GET /api/ai/usage` con permisos por rol y por tutela | `AiUsageEndpointTest` + HTTP real | ✅ |
| AC-16 | Instrucciones y datos separados por construcción | `PromptAssemblerTest` | ✅ |
| AC-17 | Benchmark reproducible, con tope propio y que no tumba el run por un modelo | `BenchmarkRunnerTest`, `NoBenchmarkInNormalRunTest` + Docker | ✅ |
| AC-18 | Auditoría de avisos, bloqueos y generaciones denegadas | `AiBudget*Test`, `GenerationLockTest` | ✅ |

### Casos borde verificados
Sin conteo de tokens (estimado y marcado) · respuesta truncada · **respuesta vacía pero facturada** (se contabiliza) · clave inválida · cuota
agotada del proveedor (sin reintento) · cambio de mes y mes de Madrid · usuario dado de baja o inexistente · prompt demasiado grande · plantilla
inexistente o fuera de la carpeta · dato que intenta cerrar la sección de datos · carrera de 8 generaciones simultáneas · cerrojo huérfano ·
cerrojo que caduca con la generación viva · dimensión de vector inesperada · benchmark que alcanza su tope · benchmark con un modelo que falla ·
URL `http://` de un proveedor de pago en producción.

### Verificaciones fuera de los tests

1. **Adaptadores por HTTP real** (servidor simulado en la red de Docker, cliente JDK real, tres proveedores y cinco modelos de mentira): el
   servidor validó lo que llegó por el cable y respondió **"SIN ANOMALIAS"** — autenticación por cabecera, la clave nunca en la URL, instrucciones y
   datos en campos separados, sin `temperature` ni `max_tokens` (OpenAI usa `max_completion_tokens`). Las cuentas cuadran a mano (Anthropic 0,00486;
   OpenAI 0,00246; Gemini 0,001275 sumando razonamiento). Un modelo con tope de gasto recibió **una sola** petición.
2. **Ollama real** (`nomic-embed-text`, 274 MB): respuesta de `/api/embed` exactamente como espera el adaptador, 768 dimensiones, y modelo
   inexistente = `404` con `{"error": …}`. Confirmó dos puntos que su documentación no cubría.
3. **Servicio `benchmark` de Compose** de punta a punta: arranca con la migración V2, ejecuta las llamadas, escribe informe y salidas, y sigue
   si un modelo falla.
4. **Stack levantado**: 5 servicios sanos, migraciones V1 y V2 aplicadas, y `/api/ai/usage` correcto para adulto, menor y sin sesión.

### Revisión `llm-integration-review`

| Punto | Estado |
|---|---|
| Toda llamada pasa por la capa intercambiable | **OK** (`AiArchitectureTest`) |
| Modelo elegido por tarea desde configuración | **OK** |
| Se registra tarea, modelo, tokens, coste, usuario y curso | **OK** (curso opcional hasta F05) |
| Tope mensual con aviso al 80/100 % y límite de salida | **OK** (corte opcional, apagado por decisión del usuario) |
| Una generación concurrente por usuario, cerrojo real | **OK** la pieza (con renovación); su **uso** llega con F05 |
| Reintentos acotados, sin bucles | **OK** |
| Nunca se regenera lo ya generado | **NO APLICA** hasta F05 |
| Tests con proveedor falso, ninguno llama a una API de pago | **OK** |
| Clave solo por entorno; si falta, falla | **OK** |
| Datos del usuario separados de las instrucciones | **OK** |
| El material de un usuario no se recupera para otro | **NO APLICA** (F03) |
| Salida validada contra un esquema antes de guardarla | **NO APLICA** aún: `AiResponse.text` llega sin validar; lo hace F05 |
| Prompts sin datos personales | **OK** en la capa: solo recibe un id para contabilizar |
| Filtrado de temas y respuestas para menores | **NO APLICA** (F04/F07) |
| Tono por edad, tutor que no resuelve exámenes, aviso "generado por IA" | **NO APLICA** (F04–F07) |
| Punto de enganche para el filtrado (prometido en la especificación) | **FALLA parcial**: no se implementó (ver Issues) |

### Revisión `security-checklist`

**Lo que ya está bien:** `/api/ai/usage` exige sesión y filtra por tutela (verificado por HTTP real) · las claves solo van por entorno y en
cabeceras, nunca en la URL ni en un `toString` ni en un log (hay tests) · SQL parametrizado, sin concatenar entrada · el id de la plantilla y el
modelo de Gemini no pueden alterar la ruta · los errores no llevan el mensaje del proveedor (puede repetir el prompt) · `ai_usage` y la auditoría
son de solo añadir · Ollama no se publica fuera de la red de Compose y el benchmark no publica puertos · sin dependencias nuevas.

**Corregido durante esta revisión:**
- 🟠 El cerrojo caducaba a los 20 min aunque la generación siguiera viva → `Lease.renew()`.
- 🟠 Nada impedía configurar un proveedor de pago con `http://` (clave en claro) → obligatorio `https://` en producción.
- 🟡 Una `AiException` que saliera por un endpoint caía en un 500 genérico → cada tipo tiene su estado y un mensaje seguro.

**Pendiente, por gravedad:**
- 🟠 **No hay límite de gasto por usuario.** En modo `WARN` cualquier usuario, también un menor, podría consumir todo el presupuesto antes de que nadie
  lo note: el aviso llega después, y el único freno duro es el tope de la cuenta del proveedor (decisión del usuario). *Propuesta:* tope mensual por
  usuario, empezando por los menores, antes de que F04–F07 abran endpoints de generación.
- 🟡 Los futuros endpoints de generación necesitarán además un **límite de frecuencia** (el cerrojo limita la concurrencia, no el volumen).
- 🟡 El porcentaje del tope **global** lo ve cualquier adulto: con varias familias dejaría ver su nivel de actividad.
- 🟡 La separación de instrucciones y datos protege la estructura del prompt, **no** impide por sí sola la inyección de significado: la salida debe
  validarse (F05) y el tutor revisarse (F07).
- 🟡 El contenedor `benchmark` lleva las credenciales del propietario de la BD (para migrar al arrancar) además de las claves de proveedor.
- 🟡 El timeout es por proveedor (el mayor de sus tareas), no por tarea.
- 🟡 `greenmail` (solo tests) va por la 2.1.3.
- **No se puede comprobar desde el código:** los límites reales de las cuentas de OpenAI, Anthropic y Google, que son el único tope duro mientras se use `WARN`.

### Issues encontrados (y corregidos salvo indicación)
1. **Pérdida de coste en respuestas facturadas pero vacías.** Un modelo con razonamiento que agota `max_tokens` pensando devolvía un error y se
   registraba con coste 0, aunque el proveedor cobra esos tokens. Ahora el adaptador devuelve el uso, el gateway lo contabiliza y la excepción lleva
   el coste. Es el caso más probable con los modelos nuevos.
2. **Un modelo que falla abortaba todo el benchmark** y se perdía el informe. Ahora se anota, se omite el resto de ese modelo y el run sigue.
3. Los tres puntos de seguridad de arriba.
4. Errores de mis propios tests durante la implementación (un código HTTP no estándar, un patrón que confundía un proveedor con un modelo, una
   fila de gasto previo leída como la bloqueada, y la opción "sin servidor web" puesta donde rompía MockMvc), sin impacto en la lógica.
5. **No corregido:** no existe el *punto de enganche* para filtrar contenido de menores que la especificación prometía. Se decidirá en F04 con el
   diseño del filtro; hasta entonces la especificación no debe darlo por hecho.

### No verificado
- **OpenAI y Gemini contra su API real.** Solo contra un servidor simulado y mi lectura de su documentación (la de OpenAI no estuvo accesible). Lo
  confirma el run del benchmark: en concreto la cabecera `x-goog-api-key` de Gemini y los campos de OpenAI.
- **Modelos con razonamiento y los límites de salida por defecto** (4 000 y 6 000 tokens): si el modelo gasta el límite pensando, la respuesta sale
  vacía y con `LENGTH`. El benchmark lo revelará y quizá haya que subir los límites de `app.ai.tasks.*.max-output-tokens`.
- **Precios reales:** `ai-pricing.properties` está vacío a propósito; se rellenan al elegir modelos, verificándolos en la página de cada proveedor.
- **`make benchmark` y `make pull-embedding-model` tal cual:** no hay `make` en este Windows. Se ejecutaron los comandos `docker compose` equivalentes.
- **Un run real** con tus claves.
- El stack de desarrollo conserva en su volumen de BD datos de **gasto ficticio** de estas pruebas (≈ 0,8 USD simulados).
