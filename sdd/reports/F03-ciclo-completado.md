## Ciclo SDD completado — US-F03

- Feature: **F03 · Material propio (subida e indexado RAG)**
- Rama: `feature/F03-material-propio-rag`
- Tests: **575/575 pasados** (backend 440 · frontend 107 · entorno 28) + 4 de Ollama real opt-in de F02, omitidos
- Cobertura: backend 93,4 % de líneas (79,7 % de ramas)
- Verificado además con el stack levantado (nginx → backend → pgvector → Ollama real): 4 subidas válidas, rechazos, bomba ZIP y XXE, permisos entre usuarios y
  búsqueda correcta con embeddings reales. **Ninguna llamada a una API de pago.**

### Cambios respecto al plan original
- **Biblioteca del usuario**, no vinculada a un curso (ADR-30); límites 10 MB y 20 documentos.
- **Los menores pueden subir** con aviso a su tutor; el filtrado de contenido (A3) pasa a ser condición antes de usar su texto con un modelo de pago.
- **Búsqueda solo interna** (`MaterialSearch`), sin endpoint ni pantalla.
- **Una única dependencia nueva, PDFBox**; el DOCX se lee con ZIP + StAX del JDK (ADR-31). Cola en BD con el original solo hasta procesarlo (ADR-32), búsqueda exacta
  filtrada por propietario (ADR-33), el texto es dato y no instrucción (ADR-34), borrado real (ADR-35).
- **ADR-36 (nuevo):** Spring MVC no registra cuerpos de respuesta, porque con DEBUG volcaba los nombres de los documentos al log.
- Dos fallos reales cazados por los tests: la auditoría del 403 de borrado se perdía con la transacción, y `pageCount` salía como 0 en lugar de ausente.

### Próximos pasos sugeridos
1. **Antes de abrir generación a menores o de desplegar:** límite de frecuencia por usuario (A2 / F3-6) y filtrado de contenido para menores (A3 / F3-1).
2. **Ver la pantalla en un navegador real** y, si se activa `frontend-design`, una pasada de diseño sobre Inicio y «Mi material».
3. **Actualizar PDFBox** a la última 3.0.x (F3-4) y decidir si la extracción de PDF merece un proceso aislado (F3-7).
4. **Siguiente feature:** **F04 — Asistente de creación de curso** (depende de F01 y F02; usará el material de F03 en F05). Es donde se diseña el filtrado de contenido de menores.
