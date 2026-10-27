## US-F13 · Borrar cursos

> FASE 1. D1, D7 y D8 decididas por el usuario (27-10-2026); el resto son propuestas mías a confirmar.

### Como propietario de un curso
### Quiero borrar los cursos que ya no necesito
### Para que mi lista no se llene de cursos viejos, sin perder el registro de lo que gastó la IA ni afectar a nadie más

Contexto: hoy no existe ninguna forma de borrar un curso (F09 lo dejó fuera). El esquema ya cascada casi todo desde `course` (`ON DELETE CASCADE` en temas, capítulos, piezas, progreso, intentos de ejercicio y examen, hilos del tutor, códigos de compartir, enlaces a documentos e imágenes); `course.origin_id` ya es `ON DELETE SET NULL`; `ai_usage.course_id` no tiene clave foránea. Faltan: el permiso `DELETE` del rol de la aplicación sobre `course`/`course_piece`, y decidir qué hacer con `course_intake`.

### Decisiones propuestas

| # | Decisión | Propuesta |
|---|----------|-----------|
| D1 ✅ (usuario) | Borrado definitivo o papelera | **Definitivo**, con diálogo de confirmación que dice qué se pierde (nº de lecciones, intentos, conversaciones) y ofrece **exportar el curso antes** (la exportación JSON ya existe). Papelera exigiría un `deleted_at` filtrado en *todas* las consultas de F05–F12 y un purgado; es mucho riesgo para un problema de «acumulación». |
| D2 | Qué se lleva | Todo lo del curso: temas, capítulos, piezas, progreso, intentos, hilos del tutor, imágenes, códigos de compartir (los códigos dejan de valer: 404) y enlaces a documentos. **No** se borran los documentos de «Mi material». |
| D3 | Uso de IA | `ai_usage` **se conserva** (solo se añade; el tope mensual y el consumo del usuario no bajan). Quedan filas con un `course_id` que ya no existe; la UI no las muestra por curso. |
| D4 | Copias (`origin_id`) | Las copias **sobreviven** e independientes (`origin_id` pasa a nulo, ya es `SET NULL`); quien las canjeó no se entera ni pierde nada. Los códigos del curso borrado dejan de funcionar. |
| D5 | Consulta del asistente (`course_intake`) | Se **conserva la fila** (cuenta para el límite de consultas por hora: si no, crear-y-borrar lo esquivaría), pero se **vacían** el tema, las preguntas y los documentos elegidos. Los avisos del panel del adulto por temas *rechazados* (`BLOCKED`) no se tocan. |
| D6 | Curso generándose | **409 `COURSE_BUSY`** si está `PENDING_GENERATION` o `GENERATING`. Una ilustración en vuelo **no** bloquea (el guardado del trabajador ya exige la fila `PENDING`, ADR-82): termina sin escribir nada y su coste se contabiliza. Se permite en `READY`, `PAUSED` y `FAILED`. La fila del curso se bloquea (`FOR UPDATE`) igual que en F09. |
| D7 ✅ (usuario) | Menores | Propuesta: **el menor no borra** (403 `CHILD_CANNOT_DELETE`): su adulto tutor ve intentos y conversaciones (F01/F08) y un clic las destruiría. Y el adulto tutor **tampoco** borra los cursos de su menor (regla fijada). Consecuencia: los cursos del menor solo los gestiona hoy la cuenta del menor; si se acumulan, queda como pendiente. Alternativa: el menor puede borrar, y el borrado queda en la auditoría y como aviso en el panel del adulto. |
| D8 ✅ (usuario) | Alcance de la interfaz | **Selección múltiple** en la lista de cursos con un solo diálogo, y también «Borrar curso» desde la pantalla de un curso. En el servidor: `POST /api/courses/delete` con `{ids:[…]}` (1–50, sin repetidos), **todo o nada** en una transacción: si uno es ajeno o inexistente → 404 y no se borra nada; si uno está ocupado → 409 `COURSE_BUSY` y no se borra nada. `DELETE /api/courses/{id}` es el caso de uno. |

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Borrar:** `DELETE /api/courses/{id}` por el propietario adulto elimina el curso y todo lo que depende de él (D2) en **una sola transacción**; responde 204. Después el curso, sus piezas, intentos e imágenes responden 404. |
| AC-2 | **Permisos:** un curso ajeno o inexistente responde 404 igual (también el del menor visto por su adulto tutor). Exige sesión y CSRF. |
| AC-3 | **Menores (D7):** el menor recibe 403 y no cambia nada (si se confirma la propuesta). |
| AC-4 | **Estado:** en cola o generándose → 409 `COURSE_BUSY` y no cambia nada; en `READY`, `PAUSED` y `FAILED` se borra. Un borrado concurrente con una edición o con el trabajador no deja datos a medias. |
| AC-5 | **Gasto:** las filas de `ai_usage` del curso siguen ahí tras borrar; el consumo mensual del usuario no cambia. |
| AC-6 | **Copias y códigos:** las copias hechas a partir del curso siguen intactas con `origin_id` nulo; sus códigos de compartir dejan de valer (404, igual que uno inexistente); el contenido de las copias no cambia. |
| AC-7 | **Consulta del asistente (D5):** su fila sigue (cuenta para el límite por hora) pero sin tema, preguntas ni documentos; el material de «Mi material» no se toca. |
| AC-8 | **Auditoría y logs:** evento `COURSE_DELETED` con ids (usuario y curso), nunca el título ni contenido; ni logs ni auditoría llevan el título del curso. |
| AC-9 | **Aislamiento:** borrar un curso no toca ningún dato de otro curso ni de otro usuario (test que cuenta las filas antes y después). |
| AC-10 | **Interfaz:** botón «Borrar curso» en la pantalla del curso (y en la lista si se elige D8), diálogo de Reka UI (nada de `confirm()`) con el título como **texto**, lo que se pierde y qué se conserva (gasto, copias de otros), enlace a exportar antes, botón de cancelar con el foco inicial; estados de carga y error sin códigos del backend; tras borrar vuelve a la lista sin el curso. Sin `v-html`. `COURSE_BUSY` se explica con un mensaje claro. |
| AC-12 | **Borrado múltiple:** `POST /api/courses/delete` con 1–50 ids distintos (400 si hay 0, más de 50, repetidos o mal formados) borra todos o ninguno (404/409 de AC-2 y AC-4 si falla uno); el diálogo dice cuántos cursos y lo que se pierde, lista sus títulos como texto y permite deseleccionar; tras borrar, la lista se refresca. |
| AC-11 | **Borrado idempotente en la UI:** un doble clic no produce un error visible (el segundo 404 se trata como «ya borrado»). |

### Casos borde
- Curso con una ilustración en vuelo (`PENDING` con la llamada en curso): se borra igual; el trabajador no escribe nada ni falla, y su coste queda en `ai_usage`. Las imágenes `DONE`, `FAILED`, `BLOCKED` o `REMOVED` se borran con el curso.
- Curso en `PAUSED` con piezas `PENDING`: se puede borrar; no vuelve a la cola.
- Curso cuyo original se borró y que luego se borra él: sin problema (`origin_id` ya nulo).
- Borrar un curso con códigos activos y ya canjeados: las copias siguen.
- Intento de borrar un id mal formado: 404 igual que uno inexistente.
- Borrar dos veces el mismo curso: el segundo 404.
- Una generación o ilustración en vuelo que termina justo después de borrar: no escribe nada ni falla de forma visible; su coste se contabiliza igual (regla de F02).
- Usuario desactivado: no puede borrar (no tiene sesión).

### Fuera de alcance (v1)
- Papelera y «deshacer» (si D1 se confirma), archivar sin borrar, borrado programado o automático de cursos antiguos.
- Borrar cuentas, documentos de «Mi material» o el historial de gasto.
- Que el adulto tutor borre o archive los cursos de su menor.
- Aviso a quien canjeó un código de que el original desapareció.

### Definición de hecho
- Todos los AC y casos borde con test (backend y frontend); un test cuenta filas antes y después y comprueba que solo desaparece lo del curso y que `ai_usage` se conserva.
- Probado con el **rol real de la aplicación** (no solo el dueño de las migraciones): falta hoy el `GRANT DELETE`.
- Verificado con el stack aislado (proyecto `studylab-f03`, variables ficticias; nunca el `.env` real): dos usuarios, un menor, una copia, un curso generándose y una pasada en el navegador.
- `security-checklist` pasada; ADR nuevos en `ARCHITECTURE.md`; README, CLAUDE y BACKLOG al día; informe en `sdd/reports/`.
