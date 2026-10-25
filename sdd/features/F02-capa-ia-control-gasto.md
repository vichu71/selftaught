# F02 — Capa de IA y control de gasto

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

Una capa de acceso a modelos de IA que el resto de la app usa sin saber qué proveedor hay detrás.
Debe permitir cambiar de proveedor y modelo **por tarea** (ficha/temario, lecciones, ejercicios y
exámenes, tutor, embeddings) solo con configuración.

Control de gasto, obligatorio: registrar cada llamada (tarea, modelo, tokens de entrada/salida,
coste estimado, usuario, curso); tope de gasto mensual configurable que corta las llamadas cuando se
alcanza y avisa antes (p. ej. al 80 %); límite de tokens de salida por llamada; una sola
generación concurrente por usuario; reintentos acotados ante errores del proveedor.

Embeddings por Ollama en local. Soporte para un proveedor falso (mock) usado en todos los tests y
en desarrollo sin coste.

Incluye un **mini benchmark** reproducible: mismos prompts con 3 temas de prueba muy distintos en
3-4 proveedores, con informe de calidad (valoración manual simple) y coste real por curso, para
decidir los modelos por defecto.

Seguridad: claves de API solo en variables de entorno del backend; si falta la clave del proveedor
activo, el arranque o la operación falla (nunca valor por defecto). Los prompts no incluyen datos
personales del usuario más allá de lo necesario.

Fuera de alcance: facturación, cuotas por plan, panel gráfico de costes (basta consulta o endpoint).

---

## Actualización tras la implementación (FASE 8, 25-10-2026)

Estado: **hecha y probada** (276 tests de backend, 94 % de cobertura). Especificación refinada: `sdd/specs/US-F02-capa-ia-control-gasto.md`;
informe: `sdd/reports/F02-testing-report.md`; pendientes: `sdd/BACKLOG.md`.

**Cambios de alcance decididos con el usuario durante el ciclo**
- El tope mensual **avisa al 80 % y al 100 % pero no corta** (la descripción original decía "corta las llamadas cuando se alcanza"): sus cuentas de
  proveedor ya tienen tope. El corte queda como opción (`AI_BUDGET_MODE=BLOCK`, apagada). Se actualizó `PROJECT.md` §3 y §5.
- Proveedores: Anthropic, OpenAI y Gemini para chat, y Ollama **del contenedor** para embeddings (la misma imagen sirve en producción).
- Capa propia con adaptadores HTTP en lugar de Spring AI. Los prompts y las respuestas **no se guardan**.

**Casos borde que no estaban en la ficha y aparecieron al implementar**
- Un modelo con razonamiento que agota el límite pensando devuelve una respuesta vacía **que el proveedor sí cobra**: se contabiliza.
- Un tope de gasto del proveedor llega como 400, o como 429 **sin** `retry-after` (Anthropic) o `insufficient_quota` (OpenAI): no se reintenta.
- El cerrojo de generación caducaba con la generación viva: se añadió la renovación.
- Un proveedor de pago configurado con `http://` enviaría la clave en claro: se exige `https://` en producción.
- Un modelo del benchmark que falla no debe tumbar todo el run.

**Fuera de alcance real (documentado, no hecho)**
Tope de gasto **por usuario** (🟠, A1 del backlog), límite de frecuencia por usuario, el punto de enganche para filtrar contenido de menores,
timeout por tarea, y el run real del benchmark (requiere las claves del usuario). Detalle en `sdd/BACKLOG.md`.
