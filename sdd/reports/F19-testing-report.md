# F19 · Fotos de apuntes — informe de pruebas (FASE 7)

Fecha: 2026-10-29. Todo en Docker Compose con un fichero de variables ficticio fuera del repositorio; ningún test llama a una API de pago.

## Resultados
| Suite | Resultado |
|---|---|
| Backend (`backend-test`, BD efímera) | 1616 tests, 0 fallos (4 omitidos: `OllamaLiveTest`, necesita un Ollama real) |
| Frontend (`frontend-test`) | 1173 tests en 99 ficheros, 0 fallos |
| Entorno (`run-env-tests.sh`) | 57 tests, 0 fallos |

## Qué encontró la ejecución (y cómo se resolvió)
- Una foto con el cerrojo del usuario ocupado se reclamaba en bucle dentro de la misma ronda (el test tardó 1200 s): `processPending` ya no la reclama otra vez hasta la siguiente ronda.
- Un SVG es UTF-8 válido y pasaba como `.txt`: el detector rechaza el texto que empieza por `<svg` o `<?xml`.
- Un PNG o JPEG con la cabecera intacta pero cortado se aceptaba al subir: `PhotoProcessor.inspect` comprueba que el fichero esté completo.
- `MaterialArchitectureTest` prohíbe mencionar `AiRequest` en `material/` (incluso en un comentario).
- Un test de la FASE 4 esperaba un `Short` para `attempts`; el driver devuelve `Integer`. Corregido el test.

## Revisiones
- `llm-integration-review`: sin fallos (lectura por `AiGateway`, datos separados de instrucciones, salida validada, cerrojo, tope y registro sin contenido). Limitación conocida: la imagen de un menor no se puede filtrar antes de enviarla (ADR-104: permiso del tutor + filtro de la salida).
- `security-checklist`: endpoints nuevos con sesión y CSRF; `photo-material` empieza por `AccessGuard.requireStudentOf`; validación en el backend; SQL parametrizado; límites por usuario en la BD; sin secretos en el frontend.

## Pendiente (BACKLOG F19-5 y F19-7)
- Los formatos de petición de OpenAI y Google solo están probados contra la documentación y servidores simulados.
- No hay modelo de lectura elegido ni precio en `ai-pricing.properties`; falta el benchmark de lectura.
- Prueba manual en el navegador con el stack levantado.
