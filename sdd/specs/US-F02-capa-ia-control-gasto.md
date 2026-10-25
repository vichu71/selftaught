# US-F02 · Capa de IA y control de gasto

> Historia refinada (FASE 1 de SDD) tras las decisiones del usuario. Los tests citan estos AC.

### Como adulto responsable del gasto (y como el resto de features que usan IA)
### Quiero acceder a los modelos mediante una capa intercambiable, con el gasto medido y vigilado
### Para que cambiar de proveedor sea solo configuración y el gasto nunca se desborde en silencio

## Decisiones del usuario (25-10-2026)

- Proveedores reales: **Anthropic, OpenAI y Google (Gemini)**; **Ollama** para embeddings, el del **contenedor** (así la misma imagen
  de Docker sirve en producción). Las claves solo irán en su `.env`.
- Tope de gasto: **solo avisar**. Sus cuentas de proveedor ya tienen tope, así que el corte dentro de la app es opcional (modo `BLOCK`, apagado).
- Mes natural en hora de Madrid, precios en USD, aviso al 80 % (sin correo), cerrojo de generación en BD con caducidad de 20 min
  (solo para generaciones de curso), sin guardar prompts ni respuestas.
- Benchmark: el arnés se entrega en F02 y el run real se hace con el usuario, con un tope propio de unos 5 USD. Temas: inglés de
  2º de la ESO, inteligencia artificial y **cocina básica** (cambiable en `benchmark/topics.properties`).
- Capa propia con adaptadores HTTP en lugar de Spring AI (ADR-18).

## Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | Interfaz única: las features piden una operación por **tarea** (`OUTLINE`, `LESSON`, `ASSESSMENT`, `TUTOR`, `EMBEDDING`) sin saber qué proveedor hay detrás. Nada fuera de `ai/` usa un adaptador de proveedor. |
| AC-2 | Proveedor, modelo, límite de salida y timeout se configuran **por tarea**. Ningún nombre de modelo está escrito en el código. |
| AC-3 | Proveedor **falso** determinista: predeterminado en `dev`, el único posible en tests y **prohibido en producción**. Ningún test llama a una API de pago. |
| AC-4 | Adaptadores de Anthropic, OpenAI y Gemini para chat y de Ollama para embeddings, tras el mismo puerto `AiProvider`. |
| AC-5 | Claves solo por variables de entorno. Si una tarea usa un proveedor sin clave, el arranque falla nombrando la variable (nunca el valor). La clave de un proveedor que nadie usa no se exige. |
| AC-6 | Cada intento queda registrado en `ai_usage` (tarea, proveedor, modelo, tokens, coste, usuario, curso, resultado, duración), de solo añadir y **sin contenido**. |
| AC-7 | El coste sale de una tabla de precios por modelo. Un modelo usado sin precio impide arrancar. |
| AC-8 | Tope mensual (USD, mes natural de Madrid). Modo `WARN` (predeterminado): se avisa y se sigue. Modo `BLOCK` (opcional): se rechazan las llamadas al alcanzarlo o si su peor caso lo superaría. Un proveedor real exige un tope explícito. |
| AC-9 | Aviso al 80 % y al 100 %: una sola vez por mes y umbral, como evento de auditoría y fila en `ai_budget_alert`. |
| AC-10 | Límite de tokens de salida por llamada: la petición puede bajarlo, nunca subirlo; la configuración no puede superar un máximo duro. |
| AC-11 | Una sola generación concurrente por usuario con cerrojo en BD y caducidad; se libera siempre; un lease antiguo no libera el cerrojo de otro; una generación larga lo **renueva** (`Lease.renew()`) para que no caduque con ella viva. |
| AC-12 | Reintentos acotados: máximo 2, solo ante timeout, 429 transitorio y 5xx. Nunca ante clave, petición, modelo inexistente, respuesta inválida o **cuota agotada del proveedor**. |
| AC-13 | Los errores que ve la feature son genéricos: nunca el mensaje del proveedor (puede repetir el prompt) ni la clave. |
| AC-14 | Embeddings con Ollama: un vector por texto, de la dimensión configurada, coste 0; error claro si falta Ollama o el modelo. |
| AC-15 | `GET /api/ai/usage`: cada usuario ve lo suyo, el adulto además lo de sus menores y el estado del tope global; el menor solo su número de llamadas. |
| AC-16 | Instrucciones y datos se separan por construcción (`PromptAssembler`): los datos van en un mensaje aparte, delimitados y escapados. |
| AC-17 | Benchmark reproducible con tope propio, informe Markdown, coste por curso tipo y hoja de valoración manual. Imposible de ejecutar en el arranque normal. Un modelo que falla se anota en el informe y el run sigue con los demás. |
| AC-18 | Auditoría: `AI_BUDGET_WARNING`, `AI_BUDGET_BLOCKED` y `AI_GENERATION_DENIED`, sin secretos ni contenido. |

## Contrato

**API interna (Java, paquete `es.vicai.studylab.ai`)**
```
AiGateway.complete(AiRequest) → AiResponse(text, inputTokens, outputTokens, tokensEstimated, costUsd, finishReason, provider, model, requestId)
AiGateway.embed(userId, textos) → EmbeddingResult(vectors, inputTokens)
AiRequest.of(task, userId, plantilla).data(etiqueta, texto).course(id).maxOutputTokens(n)     // userId obligatorio
GenerationLock.acquire(userId) → Lease (AutoCloseable)   // GenerationBusyException si ya hay una generación
Errores: AiException(kind) con BUDGET_BLOCKED · PROVIDER_UNAVAILABLE · PROVIDER_QUOTA · PROVIDER_AUTH · PROVIDER_REJECTED ·
         INVALID_RESPONSE · INPUT_TOO_LARGE · USER_NOT_ALLOWED · CONFIG
Puerto AiProvider: chat(ChatCall) · embed(EmbedCall); ProviderException(kind, retryable) con TIMEOUT · RATE_LIMITED ·
         SERVER_ERROR · UNAVAILABLE (reintentables) y QUOTA_EXCEEDED · AUTH · BAD_REQUEST · MODEL_NOT_FOUND · INVALID_RESPONSE
```

**Configuración**
```
app.ai.tasks.<tarea>.{provider, model, max-output-tokens, timeout}      # env: APP_AI_TASKS_OUTLINE_PROVIDER, …
app.ai.providers.{anthropic,openai,gemini}.api-key                       # env: ANTHROPIC_API_KEY, OPENAI_API_KEY, GEMINI_API_KEY
app.ai.providers.ollama.base-url                                         # env: OLLAMA_BASE_URL (por defecto http://ollama:11434)
app.ai.pricing[proveedor/modelo].{input-per-million, output-per-million} # fichero ai-pricing.properties, con fecha de verificación
app.ai.budget.{monthly-limit-usd, warn-at-percent=80, mode=WARN|BLOCK}   # env: AI_MONTHLY_LIMIT_USD, AI_BUDGET_MODE
app.ai.{hard-max-output-tokens, max-input-chars, retry.max-retries=2, retry.backoff, generation-lock-ttl=20m, embedding.dimensions}
```

**Endpoint** `GET /api/ai/usage` (con sesión)
```
adulto → {month, you:{calls, spendUsd}, budget:{percent, level: OK|WARNING|OVER}, minors:[{id, displayName, calls, spendUsd}]}
menor  → {month, you:{calls}}
```

**Datos (migración V2):** `ai_usage` (solo se añade, sin contenido), `ai_budget_alert` (PK mes + umbral), `ai_generation_lock` (PK usuario).

## Cambios respecto a lo especificado (FASE 8, 25-10-2026)

- **Respuesta facturada pero vacía.** Los adaptadores devuelven el resultado con su uso aunque no haya texto; el gateway lo contabiliza (ADR-28).
  `AiException` lleva `costUsd()` cuando el proveedor ya cobró una llamada que falla. Antes se registraba con coste 0.
- **`GENERATION_BUSY`** se añade a los tipos de `AiException` y todos los tipos tienen estado HTTP (`AI_<TIPO>`) en `GlobalExceptionHandler`.
- **`https://` obligatorio** en producción para la URL base de un proveedor de pago.
- **`Lease.renew()`** y **modelo fallido sin abortar el benchmark** (ver AC-11 y AC-17). Se añadieron tests de Ollama real (opt-in, `OLLAMA_LIVE_URL`).
- **No se implementó** el *punto de enganche* para filtrar contenido de menores que figuraba en "Fuera de alcance": se diseñará con F04, que es
  quien define el filtro. Hasta entonces no hay hook en la capa.
- Los timeouts son **por proveedor** (el mayor de las tareas que lo usan), no por tarea.

## Ampliación A1 (25-10-2026): tope de gasto por usuario

| # | Criterio |
|---|----------|
| AC-19 | Cada usuario tiene un tope mensual propio (mes natural de Madrid): menor `AI_USER_LIMIT_MINOR_USD` (2,00), adulto `AI_USER_LIMIT_ADULT_USD` (10,00). Debe ser mayor que 0 o el arranque falla nombrando la variable. |
| AC-20 | Corta **siempre**, también en modo `WARN`: se rechaza (`USER_LIMIT`, 429, `AI_USER_LIMIT`) si el gasto del usuario más el peor caso de la llamada supera su tope, sin llegar al proveedor. Fila `BLOCKED` y auditoría `AI_USER_LIMIT_REACHED` sin importes. El gasto de un usuario no cuenta contra otro y se reinicia cada mes. |
| AC-21 | Los embeddings locales (coste 0) no se bloquean. |
| AC-22 | `GET /api/ai/usage`: el adulto ve `limitUsd` y `limitReached` propios y de cada menor suyo; el menor solo ve `limitReached` (nunca importes ni topes). |

## Casos borde cubiertos (ver tests)

Sin conteo de tokens (se estima y se marca) · respuesta truncada o vacía · clave inválida y cuota agotada del proveedor (sin reintento) ·
cambio de mes (límites del mes de Madrid) · usuario dado de baja o inexistente · prompt demasiado grande · plantilla inexistente ·
dato que intenta cerrar la sección de datos · carrera de varias generaciones simultáneas · cerrojo huérfano tras una caída ·
dimensión de vector inesperada · benchmark que alcanza su propio tope.

## Fuera de alcance (v1)

Facturación, cuotas por plan, panel gráfico de costes, streaming, function calling, API batch y caché de prompts del proveedor
(se valoran en F05), filtrado de contenido para menores (F04/F07; el punto de enganche se diseñará con F04), guardar contenido, aviso por
correo del 80 %, multimoneda, chat con Ollama, y la columna de vectores en BD (F03).

## Definición de hecho

- Los 18 AC tienen test y pasan en contenedor, con cobertura ≥ 80 %. Ningún test llama a una API de pago.
- Verificado con el stack levantado y el proveedor falso.
- Run real del benchmark con las claves del usuario, que además confirma los formatos de API aún no verificados (OpenAI y Gemini).
- `README`, `ARCHITECTURE.md` y `CLAUDE.md` actualizados. `security-checklist` y `llm-integration-review` pasadas e informadas.
