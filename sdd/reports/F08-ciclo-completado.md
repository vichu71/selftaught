## Ciclo SDD completado — US-F08

- Feature: **F08 · Panel del adulto tutor**
- Rama: `feature/F08-panel-tutor` (sin push ni fusión hasta que lo pidas)
- Tests: **1 481/1 481 pasados** (backend 1 051 · frontend 389 · entorno 41) + 4 de Ollama real opt-in de F02, omitidos
- Cobertura: backend 94,3 % de líneas (83,8 % de ramas); paquete `guardian` 96,6 %
- Verificado además con el stack levantado (nginx → backend → PostgreSQL → servidor de IA simulado por HTTP): 35 comprobaciones, y una pasada por el navegador integrado (escritorio y 375 px, por el contenido de la página, sin capturas).
  **Ninguna llamada a una API de pago.** Informe: `sdd/reports/F08-testing-report.md`.

### Cambios respecto al plan original
- **Ninguna tabla nueva**, como estaba previsto: `V8` solo añade el índice `audit_event_actor_idx`. Sin variables, dependencias, tareas ni plantillas de IA nuevas.
- **ADR-59 ampliado:** el formato de los mensajes del hilo lo comparten el alumno y el panel (`TutorViews`) y `StudyService` gana `overviewFor` y `courseSummaries` (los endpoints del alumno no cambian).
- **AC-10:** el aviso al menor ya existía en su pantalla principal; solo se cambió el texto para nombrar respuestas e intentos.
- **Comprobado:** un alumno desactivado se puede consultar sin cambios (`isTutorOf` no mira el estado).
- En la pasada por el navegador se corrigió cómo se muestra un gasto pequeño («menos de 0,01 USD») y el plural de «llamada».
- Cierra **F4-4**, **F6-4** y **F7-4**. Nuevos pendientes menores: **F8-1 a F8-8** (los dos últimos, F8-7 y F8-8, salen de la revisión de seguridad).

### Próximos pasos sugeridos
1. **Probar con tu modelo real:** que el tutor guíe sin resolver (F7-1, F7-3), la corrección con IA (F6-1) y la calidad y el coste de los cursos; solo lo puedes medir tú con tus claves.
2. **Siguiente feature:** **F09 — edición de temario y compartir por código** (depende de F05 y F06), la última del mapa de la v1.
3. **Después, F10 (mejora del frontend):** diseño visual, navegación común, pantalla principal con «continuar donde lo dejé», accesibilidad y pruebas en móvil real. Todavía no existe su ficha.
4. **Antes de desplegar:** HTTPS y proxy con IP real (D1 a D6), límite de frecuencia en la subida de material (F3-6), PDFBox (F3-4) y el filtro de menores en otros idiomas y con segunda capa (F4-3, F5-5, F7-2, F8-6).
5. **Pequeñas mejoras:** F7-6 (que vaciar el chat no reinicie el límite del tutor), F6-6 reescrito (la regla actual del backlog es demasiado estricta) y F8-7 y F8-8.
