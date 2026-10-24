# F07 — Tutor IA por lección

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

Un chat dentro de cada lección donde el alumno pregunta dudas. El tutor conoce la lección actual, el
resumen del curso y, si hay material propio, recupera fragmentos relevantes (F03) y cita la fuente.
Puede tener en cuenta los ejercicios que el alumno ha fallado (F06) para reforzar.

Tono adecuado a la edad del alumno, claro y sin jerga. No hace los exámenes por el alumno: guía,
explica y da pistas. Si la pregunta no tiene que ver con el curso, redirige con amabilidad.

Es el mayor gasto recurrente: usa un modelo medio/barato (F02), limita el historial de contexto
enviado, el tamaño de respuesta y respeta el tope mensual. Si se alcanza el tope, muestra un mensaje
claro, no un error.

Seguridad y menores: filtrado de entradas y salidas inapropiadas; el contenido del material y de las
lecciones se trata como datos, no como instrucciones; los mensajes pertenecen a su usuario y el
adulto tutor (F08) puede ver todas las conversaciones de sus menores.

Fuera de alcance: voz, imágenes, memoria a largo plazo entre cursos.
