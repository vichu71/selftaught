## Ciclo SDD completado — US-F04

- Feature: **F04 · Asistente de creación de curso**
- Rama: `feature/F04-asistente-creacion-curso`
- Tests: **793/793 pasados** (backend 604 · frontend 157 · entorno 32) + 4 de Ollama real opt-in de F02, omitidos
- Cobertura: backend 93,9 % de líneas (80,9 % de ramas)
- Verificado además con el stack levantado (nginx → backend → PostgreSQL → servidor de IA simulado por HTTP, con Ollama real para los documentos): 44 comprobaciones, y una pasada visual en el
  navegador integrado (móvil). **Ninguna llamada a una API de pago.**

### Cambios respecto al plan original
- **«Aceptar» guarda el curso pendiente de generar** (tabla `course` y enlace a documentos con claves compuestas); la generación llega con F05 (ADR-40).
- **La consulta del asistente vive en el servidor** con sus preguntas ya validadas; el cliente solo devuelve ids de opciones (ADR-37). **Una sola llamada** al modelo con salida JSON
  validada por un esquema estricto y degradación sin errores técnicos (ADR-38).
- **Filtro de menores en dos capas** y reutilizable (`safety.ContentGuard`, ADR-39): lista local de frases sin coste y valoración del modelo; mensaje «Contenido inapropiado. Consulta con tu adulto.»
  sin categoría; el intento queda guardado para el tutor. **Cierra A3**.
- **Límites por usuario contados en la BD** (30 consultas/h y 5 cursos pendientes, ADR-41). **Cierra A2** para el asistente.
- Tarea nueva `INTAKE` (amplía el CHECK de `ai_usage`) y gancho `respondWithSequence` del proveedor falso.
- Un hallazgo visual cambió el resumen: ahora muestra cada pregunta con su respuesta.

### Próximos pasos sugeridos
1. **Antes de F05:** medir con un modelo real barato cuántas veces falla el JSON del asistente (F4-5) y decidir cómo F05 comprueba el tema de un menor cuando la valoración del modelo faltó.
2. **Antes de abrir generación a menores:** aplicar `ContentGuard` al temario y a las lecciones (F05) y al tutor (F07), y el límite de frecuencia de la subida de material (A2/F3-6).
3. **Pantalla del tutor** con los intentos bloqueados de sus menores y retención de las consultas (F08, F4-1 y F4-4).
4. **Siguiente feature:** **F05 — Generación del curso** (depende de F02 y F04, y de F03 si hay material). Recoge los cursos `PENDING_GENERATION`, usa el cerrojo `GenerationLock` y valida la salida contra un
   esquema como hace `IntakeParser`.
