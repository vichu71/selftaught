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
