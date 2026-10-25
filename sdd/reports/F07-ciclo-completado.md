## Ciclo SDD completado — US-F07

- Feature: **F07 · Tutor IA por lección**
- Rama: `feature/F07-tutor-ia` (sin push ni fusión hasta que lo pidas)
- Tests: **1 352/1 352 pasados** (backend 975 · frontend 338 · entorno 39) + 4 de Ollama real opt-in de F02, omitidos
- Cobertura: backend 94,2 % de líneas (83,6 % de ramas); paquete `tutor` 95,9 %
- Verificado además con el stack levantado (nginx → backend → PostgreSQL → servidor de IA simulado por HTTP): 34 comprobaciones, y una pasada visual en el navegador integrado (escritorio y móvil). **Ninguna llamada a una API de pago.**
  Informe: `sdd/reports/F07-testing-report.md`.

### Cambios respecto al plan original
- **ADR-55 ampliado:** el tope de conservación por lección pasa de 200 a **400 mensajes** (≥ 2 × el límite diario), porque los límites se cuentan sobre lo conservado. Nuevo pendiente **F7-6**: vaciar la conversación reinicia el contador (solo adultos; el tope mensual sigue cortando).
- Los tests de retención pasan a su propia clase (`TutorRetentionTest`) y el pool de la BD de pruebas baja a 4 conexiones: con más de unos 12 contextos de Spring en caché se llenaba la BD.
- En desarrollo la tarea `TUTOR` apunta al proveedor falso: para probarla con un proveedor real hay que fijar `APP_AI_TASKS_TUTOR_PROVIDER` y `APP_AI_TASKS_TUTOR_MODEL`.
- Sin dependencias, variables ni tareas de IA nuevas; plantilla `prompts/tutor.txt`, migración `V7__tutor.sql`.

### Próximos pasos sugeridos
1. **Probar con tu modelo real:** que el tutor guíe sin resolver ejercicios, que redirija lo que no es del curso, el tono según la edad y el coste de una conversación (F7-1, F7-3); solo lo puedes medir tú con tus claves.
2. **Pequeñas mejoras:** F7-6 (límite que no se reinicie al vaciar) y F6-6 (validar al arrancar los invariantes de configuración, ahora también `max-stored ≥ 2 × max-per-day`).
3. **Pendientes de seguridad y despliegue:** A2 en la subida de material (F3-6), D1 a D6, PDFBox (F3-4), filtro de menores en otros idiomas (F4-3, F7-2).
4. **Siguiente feature:** **F08 — panel del adulto tutor** (verá la actividad de su menor, incluidos los bloqueos y filtrados del tutor: F7-4).
