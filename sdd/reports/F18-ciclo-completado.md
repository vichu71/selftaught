# F18 · Material del curso — ciclo SDD completado (FASE 8)

Fecha de cierre: 2026-10-29. Rama `feature/F18-material-del-curso`.

## Qué se entrega

El material de un curso se sube **al crearlo**: en «Nuevo curso», con «Sí», aparece la zona «Añade los apuntes de este curso» (la misma subida de Mi material) y el documento se marca solo cuando termina de procesarse; los ya subidos siguen disponibles. La pantalla del curso gana la tarjeta **Material de este curso** (sustituye a la línea «Documentos: …») y **Mi material** dice en qué cursos se usa cada documento (o «Sin usar todavía») con un endpoint nuevo `GET /api/courses/material-usage`. Borrar un documento usado avisa de que los cursos ya creados no cambian. La **biblioteca del usuario** (F03) sigue por debajo: no hay migración ni cambios de privacidad.

## Fases

| Fase | Resultado | Commit |
|------|-----------|--------|
| 1–4 | Historia (10 AC), propuesta (ADR-98 y 99) y tests que fallaban (backend y frontend) | `7acc58c` |
| 5 | ARCHITECTURE, README, CLAUDE y BACKLOG (con F19) | `4910487` |
| 6 | Código (backend y frontend) | `fb39869` |
| 7–8 | Informe de pruebas y este cierre | ver `git log` |

## Decisiones que cambiaron respecto a la propuesta

- `material.upload` pasa a **devolver el id** del documento (cadena vacía si falla); los motivos de error de un documento se mueven a `material/reasons.ts` para compartirlos entre Mi material y el asistente.
- La espera del asistente es **local a la vista** (no entra en el almacén del curso): solo hace falta mientras el asistente está abierto.
- Mientras se confirma el borrado, las acciones de la tarjeta pasan a una fila propia (corrección vista solo en el navegador).

## Cifras

backend 1495 / 1495 (4 omitidos) · frontend 1127 / 1127 · compilación y `vue-tsc` limpios.

## Lecciones

- Un contrato de API fijado por tests (`DocumentView`) no se amplía: el dato nuevo va en un endpoint aparte (ADR-98), como en F15.
- La relación curso–documento ya existía desde F04 (`course_document`); **mirar el modelo antes de proponer una migración** ahorró una migración y un contrato.
- Un layout en rejilla con contenido que cambia de tamaño (la confirmación de borrado) se rompe solo en el navegador: se probó el estado de confirmación, no solo el estado normal.

## Pendiente

BACKLOG F18-1 a F18-3 y **F19**: imágenes y fotos de apuntes (visión u OCR por `AiGateway`, precio por imagen, entrada no fiable, filtrado para menores), pedida por el usuario.
