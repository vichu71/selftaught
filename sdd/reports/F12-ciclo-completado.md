## Ciclo SDD completado — US-F12

- Feature: Ilustraciones de portada generadas por IA (una imagen por tema, API de pago, interruptor «Con ilustraciones» apagado por defecto)
- Rama: `feature/F12-ilustraciones-ia` (sin fusionar en `main`)
- Tests: backend 1323/1323 (4 omitidos), frontend 713/713, entorno 54/54; verificación con el stack y servidor de imágenes simulado 25/25
- Cobertura: no se ha vuelto a medir en esta feature (la última cifra del frontend sigue siendo la de F11, 97,1 %)
- Cambios respecto al plan original:
  - El guardado del resultado solo escribe sobre filas `PENDING` (una imagen quitada mientras se generaba reaparecía): detalle añadido a ADR-82.
  - `nosniff` lo añade Spring Security; la imagen ya no lo repite. El `nosniff, nosniff` global (nginx + Spring) viene de F01 y queda para el despliegue.
  - Los tests de F12 aíslan su cola (sin filas ni cupo de otros tests) y `ac6` ya no depende del orden de los ids.
  - Ninguna decisión técnica nueva: ADR-78 a 83 siguen valiendo.
- Próximos pasos sugeridos: el usuario lanza `make benchmark-images` con sus claves (BACKLOG F12-1 y F12-2), se añade el precio verificado del modelo elegido a `ai-pricing.properties` y se ponen `APP_AI_TASKS_IMAGE_PROVIDER` y `APP_AI_TASKS_IMAGE_MODEL` en su `.env`; después reconstruir su stack. Mejoras v2 en el BACKLOG (F12-3 a F12-9): diagramas para ciencias y matemáticas, moderación de imágenes para abrirlas a menores, texto alternativo con un modelo de visión, coste exacto en el asistente.
- Lo que solo puede decidir el usuario: qué modelo de imagen usar por calidad y precio, y si las portadas aportan en el uso diario.
