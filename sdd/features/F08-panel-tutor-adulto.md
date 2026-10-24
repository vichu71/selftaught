# F08 — Panel del adulto tutor

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

Un adulto con alumnos menores asociados (F01) ve, en **solo lectura**, los avances de cada uno: qué
cursos tiene, progreso por curso y tema, resultados de los exámenes y ejercicios, y actividad
reciente. Puede ver también el consumo de IA agregado por alumno si resulta útil.

Decisión tomada: el adulto ve **todo** lo del menor, incluidas sus conversaciones con el tutor IA
(F07), sus cursos, intentos y respuestas. El menor debe saber que su adulto puede ver su actividad
(aviso claro en la interfaz).

Seguridad: un tutor solo accede a los alumnos con los que tiene vínculo; ningún endpoint devuelve
datos de otros usuarios; el panel no permite modificar nada del alumno.

Fuera de alcance: notificaciones por correo, informes en PDF, control de tiempo de uso.
