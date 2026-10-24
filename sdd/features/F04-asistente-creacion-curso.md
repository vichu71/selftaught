# F04 — Asistente de creación de curso

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

La puerta de entrada pensada para un niño sin conocimientos técnicos. Una pantalla con una caja de
texto: "¿Qué quieres estudiar?" (ej. "inglés de 2º de la ESO"). Después, un asistente corto:

1. "¿Tienes información que aportar?" Sí/No. Si es sí, permite subir material (F03).
2. De 0 a 3 preguntas, **solo si son relevantes**, decididas por la IA según el tema (p. ej. cuántos
   temas, si quiere examen por tema, enfoque: gramática/vocabulario/conversación). Cada pregunta con
   opciones por defecto marcadas y una opción "como tú veas".
3. Resumen de lo que se va a montar y botón **Aceptar**.

El resultado es una **ficha de curso** estructurada (tema, enfoque, nivel de partida, objetivo,
número de temas, exámenes por tema sí/no, ¿hay material?), que es el contrato con la generación (F05).
Al aceptar se lanza la generación y se lleva al usuario a ver el progreso.

Sin jerga técnica en ningún texto. Debe funcionar bien en móvil.

Seguridad: validar en backend la longitud y contenido de la petición; filtrar temas inapropiados para
menores; el texto del usuario va al modelo como dato, nunca como instrucciones del sistema; límite
de una generación concurrente.

Fuera de alcance: test diagnóstico de nivel (valorar para v2), edición de la ficha tras aceptar.
