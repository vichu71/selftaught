## US-F08 · Panel del adulto tutor

### Como adulto con alumnos menores asociados (F01)
### Quiero ver, en solo lectura, cómo van mis alumnos: sus cursos y avances, sus intentos, sus conversaciones con el tutor IA, lo que el filtro de menores ha bloqueado y lo que gastan en IA
### Para que pueda acompañarlos, detectar problemas a tiempo y saber que su uso de la IA es seguro y razonable

Decisiones del usuario (FASE 1): el adulto abre **progreso, intentos y conversaciones** (no el texto de las lecciones generadas); hay una sección de **avisos** con lo bloqueado y filtrado, bien visible y sin correos; cada consulta de
conversaciones y respuestas queda **auditada** solo con ids; se muestra el **gasto del mes frente al tope del alumno** y su reparto por tarea, sin tokens.

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Lista de alumnos:** el adulto ve, en la pantalla de sus alumnos, un acceso al panel de cada uno (activos y desactivados, estos marcados). Un adulto sin alumnos no ve el acceso. |
| AC-2 | **Resumen de un alumno** (`GET /api/tutor/students/{id}/overview`): sus cursos (título, estado, avance en %, lecciones hechas y exámenes superados) y la fecha de su última actividad. Los datos son los del alumno, calculados igual que él los ve (F06). |
| AC-3 | **Detalle de un curso:** el avance por tema (lecciones hechas, examen superado, mejor y última nota) de un curso del alumno. No incluye el texto de lecciones, ejercicios ni exámenes. |
| AC-4 | **Intentos:** el adulto ve los intentos de ejercicio del alumno (enunciado, **su respuesta**, autocorrección y veredicto de la IA con su comentario) y los de examen (nota, fecha y preguntas falladas), por pieza y del más reciente al más antiguo. Es solo lectura. |
| AC-5 | **Conversaciones:** el adulto lee los hilos del alumno con el tutor IA, por lección, incluidos los mensajes `BLOCKED` y `FILTERED` marcados como tales. No ve los `PENDING` ni los `FAILED`. No puede escribir, borrar ni vaciar nada (el `DELETE` del menor sigue prohibido, `THREAD_PROTECTED`). |
| AC-6 | **Avisos:** una lista, del más reciente al más antiguo, con lo que el filtro ha parado: preguntas bloqueadas al tutor, respuestas del tutor filtradas, temas de curso rechazados, respuestas de ejercicios que no se enviaron a corregir y piezas no generadas por contenido bloqueado. Cada aviso lleva fecha, tipo y dónde ocurrió, y enlaza a la conversación o al curso. |
| AC-7 | **Consumo de IA:** el gasto del mes del alumno en USD frente a su tope personal y su reparto por tarea (temario, lecciones, ejercicios/exámenes, tutor, otros). Sin tokens ni modelos. |
| AC-8 | **Vínculo:** un adulto solo accede a alumnos con los que tiene vínculo activo. Un alumno de otro adulto, un adulto cualquiera, un id inexistente o mal formado responden **404 igual** (la comprobación `requireStudentOf` de F01 deja su rastro en la auditoría). Un alumno no accede a ningún endpoint del panel (403/404) y no hay ningún endpoint que modifique nada. |
| AC-9 | **Auditoría:** cada consulta de conversaciones, intentos y avisos de un alumno queda en `audit_event` (actor, alumno y pantalla), **nunca el contenido**. El resumen y el consumo no se auditan. |
| AC-10 | **Aviso al menor:** el alumno ve en la interfaz, de forma clara, que su adulto tutor puede ver su actividad. Ya existe en su pantalla principal y en el chat; el texto pasa a nombrar también sus **respuestas e intentos** (hoy dice cursos, progreso y conversaciones) y se mantiene un test que lo exige. |
| AC-11 | **Texto seguro:** todo lo que viene del alumno o del modelo (respuestas, conversaciones, comentarios, temas) se pinta como texto o con `MarkdownContent`; nunca como HTML. |
| AC-12 | **Estados claros:** carga, error y vacío (sin cursos, sin conversaciones, sin avisos) tienen mensaje propio y nunca enseñan códigos del backend. |
| AC-13 | **Sin fugas:** ni los logs ni la auditoría llevan respuestas, conversaciones, temas ni comentarios; las respuestas del panel no incluyen ids de otros usuarios ni la contraseña, el correo o el identificador de acceso del alumno más allá de su nombre. |

### Casos borde
- Un alumno **desactivado** se puede consultar (solo lectura); su actividad no cambia.
- Un alumno sin ninguna actividad: resumen vacío, sin avisos y sin error.
- Un alumno al que se le retiró el vínculo (futuro) deja de verse de inmediato.
- Hilos con 400 mensajes y respuestas de ejercicio de 2 000 caracteres: se paginan o se acotan sin romper la pantalla.
- Un curso borrado o una lección sin generar: el panel no falla; el hilo conservado de una lección que luego falla sigue visible.
- Un mensaje con HTML, enlaces `javascript:` o texto muy largo se muestra inerte.
- El adulto con dos alumnos: cada panel solo trae los datos de su alumno, aunque el id de curso o de pieza sea de otro.
- Un adulto que es también alumno de sí mismo no existe; el propio adulto no ve «su panel».
- Dos adultos con el mismo menor: no existe en v1 (un solo tutor por menor).

### Fuera de alcance (v1)
- Notificaciones por correo, informes en PDF y control del tiempo de uso (de la feature en bruto).
- Leer el texto de las lecciones, ejercicios y exámenes generados del alumno.
- Modificar nada del alumno desde el panel (borrar conversaciones, corregir notas, reanudar cursos).
- Desbloquear una pregunta concreta, segunda capa del filtro (F5-5, F7-2) y filtro en otros idiomas (F4-3).
- Gasto en tokens o por modelo, y límites de gasto editables desde el panel.
- Panel de un adulto sobre otros adultos.

### Definición de hecho
- Todos los AC con un test; casos borde con test; el panel no modifica ningún dato (comprobado por tests que cuentan filas antes y después).
- Un adulto solo ve a sus alumnos y el resto responde 404 igual (incluidos los ids de curso o pieza de otro alumno).
- Auditoría de consultas sin contenido; logs sin contenido (test de logs).
- Verificado con el stack levantado (adulto con dos alumnos, otro adulto, un menor) y una pasada visual en el navegador.
- `security-checklist` pasada; no hay llamadas a IA nuevas (no aplica `llm-integration-review`, se anota).
- Documentación al día (README, ARCHITECTURE, CLAUDE, BACKLOG: cierra F4-4, F6-4 y F7-4).
