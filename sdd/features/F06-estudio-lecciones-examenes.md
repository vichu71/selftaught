# F06 — Estudio: lecciones, ejercicios, exámenes y progreso

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

La experiencia de estudio. El alumno ve la lista de sus cursos con su progreso, entra en un curso y
navega por temas, capítulos y lecciones. Cada lección se muestra con el contenido en Markdown
**sanitizado** (nunca HTML crudo), con las citas a la fuente si las hay.

Tras las lecciones hay ejercicios de práctica con corrección y explicación, y un **examen por tema**
de autoevaluación (no acredita nada). Se guardan los intentos y las respuestas; el historial permite
ver qué se ha fallado. Se calcula el progreso por lección, tema y curso.

Mientras el curso se está generando (F05) se muestran las lecciones ya listas y un indicador claro
de cuáles faltan.

Correcciones: preguntas con solución cerrada (test, verdadero/falso, huecos) se corrigen en el
backend sin IA; las de respuesta abierta pueden usar la IA con rúbrica (respetando los controles de
gasto de F02). Valorar motor de cálculo para matemáticas/ciencias.

Debe ser usable en móvil. Mínimo de AEDIA: curso, lecciones, examen por tema, progreso.

Fuera de alcance: medallas, rachas, tiempo de estudio, calendario, repaso espaciado y flashcards
(candidatas a v2).
