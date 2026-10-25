## Ciclo SDD completado — US-F06

- Feature: **F06 · Estudio: lecciones, ejercicios, exámenes y progreso**
- Rama: `feature/F06-estudio`
- Tests: **1 201/1 201 pasados** (backend 858 · frontend 306 · entorno 37) + 4 de Ollama real opt-in de F02, omitidos
- Cobertura: backend 94,1 % de líneas (82,9 % de ramas); paquete `study` 95,7 %
- Verificado además con el stack levantado (nginx → backend → PostgreSQL → Ollama real → servidor de IA simulado por HTTP): 50 comprobaciones, y una pasada visual en el navegador integrado (móvil). **Ninguna llamada
  a una API de pago.**

### Cambios respecto al plan original
- **Un ADR nuevo, ADR-54:** los envíos por hora se cuentan sobre los intentos conservados; el máximo por hora no debe superar al de intentos por pieza (F6-6: validarlo al arrancar).
- **Cambia la pieza de F05** (ADR-50): la lección trae `done` y `navigation`; los ejercicios y el examen ya no llevan solución ni opción correcta. `ExerciseList` y `ExamView` se sustituyen por los componentes de estudio.
- El historial devuelve `kind` además de `attempts`; el avance se calcula en el servidor y «Tus cursos» lo pide aparte (`GET /api/study/progress`).
- La corrección con IA reutiliza `ASSESSMENT` (sin tarea ni variable nueva) y `ModelJson` pasa a ser público para que `study` lo use.
- Cierra A2 para F06 (límites por usuario en la BD) y deja los datos listos para el panel del tutor (F08).
- Dos tests de la FASE 4 se corrigieron al implementar (el límite por hora con la poda de intentos y el doble clic en «Corregir con IA»).

### Próximos pasos sugeridos
1. **Probar con tu modelo real:** cuántas veces «Corregir con IA» acierta y cuánto cuesta (F6-1); solo lo puedes medir tú con tus claves.
2. **Pequeñas mejoras:** validar al arrancar el invariante de configuración (F6-6) y decidir si la exportación del curso debe llevar las soluciones (F6-3).
3. **Pendientes de seguridad y despliegue:** A2 en la subida de material (F3-6) y en F07, D1 a D6, PDFBox (F3-4), filtro de menores en otros idiomas (F4-3).
4. **Siguiente feature:** **F07 — Tutor IA por lección** (depende de F02, F06 y F03). Usará `AiGateway` con la tarea `TUTOR`, el filtro de menores y los datos de estudio; sin `GenerationLock`.
