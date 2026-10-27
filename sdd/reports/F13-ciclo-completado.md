# Ciclo SDD completado — US-F13 · Borrar cursos

- **Feature:** borrar cursos de forma definitiva (uno o varios), solo el adulto propietario.
- **Rama:** `feature/US-F13-borrar-cursos` (sin fusionar en `main`, sin push).
- **Commits:** `d566838` (FASE 1–4), `a03a21d` (FASE 5), `31a8af9` (FASE 6), `f9a045f` (FASE 7) y el de esta FASE 8.
- **Contrato y diseño:** `sdd/specs/US-F13-borrar-cursos.md`, `sdd/specs/ART-F13-propuesta-borrar-cursos.md`, ADR-84 a 86 en `ARCHITECTURE.md`.

## Tests
- Backend: 1358/1358 (4 omitidos); 35 son de F13 (comportamiento, casos borde y estructura).
- Frontend: 741/741 y el build con `vue-tsc` compila.
- Verificación con el stack aislado (`studylab-f13`): 34/34. Pasada visual en escritorio y móvil.
- Cobertura: todos los criterios AC-1 a AC-12 y los casos borde tienen test (tabla en `F13-testing-report.md`). No se midió un porcentaje de líneas.

## Qué se hizo
- Backend: paquete `coursedelete/` (`DELETE /api/courses/{id}` y `POST /api/courses/delete`, ambos 204), migración `V11` con el `GRANT DELETE` sobre `course`, borrado en una transacción con filas bloqueadas, todo o nada, auditoría `COURSE_DELETED` con ids.
- Frontend: selección múltiple en «Tus cursos», diálogo de confirmación (Reka UI, con enlace a exportar) y «Borrar curso» en la pantalla del curso.
- Documentación: `ARCHITECTURE.md`, `CLAUDE.md`, `README.md`, `sdd/BACKLOG.md`.

## Cambios respecto al plan
- D6 enmendada: una ilustración en vuelo no impide borrar (su guardado exige estado `PENDING`, así que no escribe sobre un curso borrado).
- La verificación con el stack usó el proyecto nuevo `studylab-f13` en lugar de `studylab-f03`, cuyo volumen tenía otras contraseñas ficticias y no se borró.
- El contenido de los cursos de la verificación se sembró por SQL: el modelo falso de desarrollo deja los cursos en `PAUSED`.
- Corregido en la pasada visual: «1 seleccionado» en singular.

## Decisiones aceptadas (ver BACKLOG F13-1 a F13-6)
- Borrado definitivo, sin papelera; red de seguridad: aviso y exportación previa.
- El menor no borra y el tutor tampoco borra por él.
- Sin límite de frecuencia (no gasta ni toca la IA).
- Un fallo intermitente de `IllustrationGeneratorTest` (anterior a F13) queda vigilado.

## Siguientes pasos
1. Revisar y decidir la fusión de la rama en `main` (no se ha hecho).
2. Antes de desplegar: aplicar `V11` (el rol de la app necesita el `GRANT`) y repasar los pendientes de despliegue de `sdd/BACKLOG.md` (D1–D6).
3. Si molesta en la práctica: papelera (F13-1) o forma de limpiar los cursos de un menor (F13-2).
4. El volumen `studylab-f13` sigue en Docker; borrarlo lo decide el usuario.
