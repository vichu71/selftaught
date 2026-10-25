## Ciclo SDD completado — US-F05

- Feature: **F05 · Generación del curso**
- Rama: `feature/F05-generacion-curso`
- Tests: **990/990 pasados** (backend 739 · frontend 216 · entorno 35) + 4 de Ollama real opt-in de F02, omitidos
- Cobertura: backend 93,8 % de líneas (82,0 % de ramas); paquete `generation` 93,3 %
- Verificado además con el stack levantado (nginx → backend → PostgreSQL → Ollama real → servidor de IA simulado por HTTP): 59 comprobaciones, más consultas a la BD, la auditoría y los logs, y una pasada
  visual en el navegador integrado (móvil). **Ninguna llamada a una API de pago.**

### Cambios respecto al plan original
- **Un ADR nuevo, ADR-48:** los ejercicios esperan a su lección y el reclamo de la cola respeta el cerrojo vigente (y al arrancar se liberan los cerrojos de los cursos interrumpidos).
- **`GET /api/courses/{id}` lo sirve ahora `generation`** (detalle con temario, progreso, coste y motivo de la pausa); el POST y el listado de F04 no cambian de forma. `course/` sigue sin poder leer material.
- Respuestas cortadas por el límite de salida, respuestas vacías ya cobradas y entradas demasiado grandes cuentan como intento fallido de la pieza, no como caída del proveedor.
- Se registra también `COURSE_GENERATION_STARTED` (AC-15), que faltaba en la primera versión.
- El visor de Markdown (`marked` 18.1.0, solo para leer tokens) es la única dependencia nueva; `npm audit` sin vulnerabilidades.
- Tres tests de la FASE 4 se corrigieron al implementar (cerrojo con un texto en una columna `uuid`, y comprobaciones del visor que miraban el HTML como cadena en vez de los atributos de los elementos).
- **Cierra A4, la parte de F05 de F3-1 y la de F05 de F4-5**; aporta el límite de frecuencia de «Reanudar» a A2.

### Próximos pasos sugeridos
1. **Probar con tus modelos reales** (`APP_AI_TASKS_OUTLINE_*`, `_LESSON_*`, `_ASSESSMENT_*` en tu `.env`): cuántas piezas fallan y cuánto cuesta un curso (F5-1). Es lo único de F05 que no está medido.
2. **Antes de abrir la generación a menores sin supervisión:** decidir una segunda capa de filtro sobre cada lección y otros idiomas (F5-5 y F4-3), y qué hacer con los enlaces del visor (F5-2).
3. **Pendientes de seguridad y despliegue:** A2 en la subida de material (F3-6) y en F06–F07, D1 a D6, PDFBox (F3-4).
4. **Siguiente feature:** **F06 — Estudio: lecciones, ejercicios, exámenes y progreso** (depende de F05). Los ejercicios y exámenes ya guardan su solución y su explicación con estructura para poder corregirlos.
