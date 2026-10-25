## Ciclo SDD completado — US-F02

- Feature: **F02 · Capa de IA y control de gasto**
- Rama: `feature/F02-capa-ia-control-gasto`
- Tests: **372/372 pasados** (backend 276 · entorno 25 · frontend 71) + 4 de Ollama real, opt-in, ejecutados aparte (4/4)
- Cobertura: backend 94,3 % de líneas (78,6 % de ramas)
- Verificado además por HTTP real (servidor simulado), con Ollama real y con el servicio `benchmark` de Compose de punta a punta.
  **Ninguna llamada a una API de pago.**

### Cambios respecto al plan original
- **El tope mensual avisa (80 % y 100 %) pero no corta** (decisión del usuario: sus cuentas de proveedor ya tienen tope). El corte es opcional
  (`AI_BUDGET_MODE=BLOCK`). Se actualizaron `PROJECT.md` §3 y §5, la regla de `CLAUDE.md` y la skill `llm-integration-review`.
- **Capa propia con adaptadores HTTP en lugar de Spring AI** (ADR-18). Sin dependencias nuevas.
- **Ollama del contenedor** para embeddings (ADR-25); el modelo se descarga con `make pull-embedding-model`.
- **Tercer tema del benchmark: cocina básica**, elegido por defecto y cambiable en `benchmark/topics.properties`.
- **Respuestas facturadas pero vacías** se contabilizan (ADR-28): un modelo con razonamiento que agota el límite pensando ya no se registra a coste 0.
- **Benchmark que sigue si un modelo falla**, en lugar de abortar y perder el informe.
- **`Lease.renew()`** para generaciones largas y **`https://` obligatorio** en producción para proveedores de pago.
- **Errores de IA con estado HTTP** y mensaje genérico en `GlobalExceptionHandler`.
- **Nombre de tarea `ASSESSMENT`** (ejercicios y exámenes) en lugar de `EXERCISES_EXAM`.
- **No se implementó** el punto de enganche para filtrar contenido de menores que la especificación prometía: se diseñará con F04.
- Timeouts **por proveedor**, no por tarea. `ai-pricing.properties` queda **vacío** hasta verificar precios reales.

### Próximos pasos sugeridos
1. **Antes de F04–F07:** tope de gasto **por usuario**, empezando por los menores (A1, 🟠), y límite de frecuencia en los endpoints de generación (A2).
2. **Run real del benchmark** con las claves del usuario (A8): elegir modelos por tarea, rellenar `ai-pricing.properties` con precios verificados,
   confirmar la cabecera `x-goog-api-key` de Gemini y los campos de OpenAI, y revisar los límites de salida con modelos que razonan.
3. **Antes de desplegar:** los puntos D1–D6 de F01 y la evaluación de la skill de auditoría de Cloudflare (ya anotada en el backlog).
4. **Siguiente feature:** **F03 — Material propio (subida e indexado RAG)**; depende de F01 y F02. Usará los embeddings de Ollama y la columna de
   vectores en BD, y la revisión con `llm-integration-review` incluirá por fin "el material de un usuario nunca se recupera para otro".
