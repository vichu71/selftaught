---
name: llm-integration-review
description: Revisa código que llama a modelos de IA o embeddings (generación de cursos, tutor, RAG, prompts). Úsala al crear o cambiar cualquier integración con IA, o cuando se pregunte por coste, prompt injection o seguridad de menores en el uso de LLM.
---

Revisa el código de integración con IA (funciona sin git: lee los ficheros directamente) y responde
con una lista de lo comprobado y lo pendiente. Marca cada punto como OK, FALLA o NO APLICA.

## Coste y control
- Toda llamada pasa por la capa de proveedor intercambiable; nada llama a un SDK de proveedor
  directamente desde la lógica de negocio.
- El modelo se elige por tarea desde configuración, no está escrito en el código.
- Cada llamada registra tarea, modelo, tokens de entrada/salida, coste, usuario y curso.
- Existe tope de gasto mensual con corte automático y límite de tokens de salida por llamada.
- Una sola generación concurrente por usuario (cerrojo real, no solo en el frontend).
- Los reintentos están acotados; un fallo del proveedor no provoca bucles de llamadas.
- Nunca se regenera lo ya generado; hay caché o comprobación previa.
- Los tests usan el proveedor falso; ningún test llama a una API de pago.

## Seguridad y datos
- La clave del proveedor solo viene de variables de entorno del backend; si falta, la app o la
  operación falla (sin valor por defecto).
- El texto del usuario, del material subido y de las lecciones va al modelo como **datos**, en una
  sección separada de las instrucciones, nunca concatenado dentro del prompt de sistema.
- El material de un usuario nunca se recupera para otro: la búsqueda vectorial filtra por propietario.
- La salida del modelo se valida contra un esquema estricto antes de guardarla y nunca se renderiza
  como HTML sin sanitizar (sin `v-html`).
- Los prompts no incluyen datos personales innecesarios del usuario.

## Menores
- Hay filtrado de temas y respuestas inapropiadas en entrada y salida.
- El tono se adapta a la edad y el tutor IA no resuelve los exámenes por el alumno.
- El aviso "generado por IA" aparece en el contenido.
