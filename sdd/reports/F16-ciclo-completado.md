# Ciclo SDD completado — US-F16 · Pulido de «Inicio» y «Mi material»

- **Feature:** Inicio y Mi material con el mismo aire que Resultados: tarjetas claras, botones reconocibles y estados que se entienden, en lugar de frases con enlaces subrayados y formularios sueltos. Solo frontend.
- **Rama:** `feature/F16-pulido-inicio-material` (sin fusionar en `main`, sin push).
- **Commits:** `091371f` (FASE 1–4), `07e6660` (FASE 5), `0c92e28` (FASE 6), `2ea38ca` (FASE 7) y el de esta FASE 8.
- **Contrato y diseño:** `sdd/specs/US-F16-pulido-inicio-material.md`, `sdd/specs/ART-F16-propuesta-pulido-inicio-material.md`, ADR-94 en `ARCHITECTURE.md`.

## Tests
- Frontend: 972/972 y `vue-tsc` limpio (los 911 anteriores en verde sin tocarlos y 61 nuevos). Entorno: 57/57. Backend sin cambios.
- Pasada en el navegador con el stack de demostración aislado: tutora con dos menores, adulto sin menores y menor, en escritorio y móvil (390 y 360 px), con subida real por arrastre, borrado con confirmación y alta plegable.
- Cobertura: los criterios AC-1 a AC-11 y los casos borde tienen test (tabla en `F16-testing-report.md`). No se midió un porcentaje de líneas.

## Qué se hizo
- `ui/`: `ActionCard` (atajo = enlace entero con icono, título y descripción), `Disclosure` (plegable accesible, con `v-show`) y `EmptyNote` (semilla o mascota con un mensaje amable).
- Inicio: «Tu espacio de estudio» son tres tarjetas-atajo; los menores son `StudentCard` con estado en palabras e icono; el alta va plegada en «Añadir un menor» (abierta si no hay menores); cursos y menores sin nada usan `EmptyNote`.
- Mi material: `DropZone` (la etiqueta del `<input type="file">` real; arrastrar y soltar entrega el primer fichero a la misma subida), documentos como tarjetas con icono por tipo y estado con icono (`MaterialList`, `MaterialStatus`), vacíos amables.
- Resultados: «Ver actividad» de cada menor en «Tus peques» (cierra F15-2).
- Documentación: `ARCHITECTURE.md`, `CLAUDE.md`, `README.md`, `sdd/BACKLOG.md` (F15-2 y F15-3 cerrados, F16-1 a F16-3).

## Cambios respecto al plan
- Los textos de estado de un documento no cambian (Subido, Procesando, Listo, Error); solo ganan icono, porque los tests de F03 ya los fijan.
- El formulario de alta se pliega con `v-show`, no con `v-if`: no se pierde lo escrito y el DOM de los tests de F01 sigue igual.
- Al arrastrar varios ficheros se sube el primero y se avisa en palabras.
- La pasada visual no encontró nada que corregir en el código.

## Decisiones aceptadas (ver BACKLOG F16-1 a F16-3)
- Se sube un fichero cada vez; sin vista previa, renombrar, buscar ni ordenar documentos.
- Siguen con el estilo anterior Nuevo curso, Tengo un código, Curso, Lección, Login y el panel del tutor.

## Siguientes pasos
1. Decidir la fusión de `feature/F16-pulido-inicio-material` en `main` (no se ha hecho).
2. Si te gusta el resultado, aplicar los mismos componentes al resto de pantallas (F16-3), pantalla a pantalla.
3. Antes de abrir la app a Internet: HTTPS y cookie `Secure` (D1–D3, D6, F14-1).
4. Medir con modelos reales (F4-5, F5-1, F6-1, F7-1, F12-1) sigue pendiente del usuario.
5. El entorno de demostración (`studylab-demo`) sigue levantado en http://127.0.0.1:18080; sus volúmenes, y los de F14 y F15, los borra el usuario cuando quiera.
