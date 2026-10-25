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

---

## Actualización tras la implementación (FASE 8, 25-10-2026)

Estado: **hecha y probada** (backend 604 tests, frontend 157, entorno 32; cobertura del backend 94 %). Especificación refinada: `sdd/specs/US-F04-asistente-creacion-curso.md`;
diseño: `sdd/specs/ART-F04-propuesta-asistente.md`; informe: `sdd/reports/F04-testing-report.md`; pendientes: `sdd/BACKLOG.md` (F4-1 a F4-5).

**Cambios de alcance decididos con el usuario durante el ciclo**
- **«Aceptar» guarda el curso como pendiente de generar** (`PENDING_GENERATION`): la tabla de cursos nace en F04 y aquí se enlaza el material de F03. La generación llega con F05.
- **Menores:** un tema inapropiado no se crea y el menor ve «Contenido inapropiado. Consulta con tu adulto.», sin categoría ni motivo. El intento queda guardado para el tutor (su pantalla es F08).
  Filtro en dos capas (lista local de frases + valoración del modelo en la misma llamada); a los adultos no se les aplica.
- **Límites por usuario (A2):** 30 consultas por hora y 5 cursos pendientes; cierra A2 para el asistente y A3 (el filtro local es el punto de enganche reutilizable por F05 y F07).
- La edad del menor no se guarda: el nivel de partida se pregunta dentro del asistente.

**Casos borde que no estaban en la ficha y aparecieron al implementar**
- La salida del modelo llega a veces envuelta en un bloque de código o rota: se acepta el bloque, se reintenta una vez y, si sigue mal, el asistente sigue sin preguntas y con valores por defecto.
- **Un menor con la salida del modelo inservible**: sin la valoración del modelo, el tema se acepta porque ya pasó la lista local. F05 debe volver a comprobarlo (F4-5).
- Un cliente puede **inyectar valores o campos** en una respuesta: el servidor los ignora, porque solo usa ids de opciones de las preguntas que él guardó.
- «Aceptar» una consulta ya consumida devuelve el mismo curso (200) aunque lleguen respuestas distintas; con documentos borrados o aún no listos entre pasos, 404 y la consulta sigue abierta.
- Los temas escolares delicados (guerras, reproducción, drogas y salud, prevención del suicidio) **deben pasar** el filtro: por eso la lista son frases concretas y no palabras sueltas.
- Un disfraz típico (`P O R N O`, `p0rn0`, `PÓRNO`) se normaliza antes de comparar, pero **no se unen palabras** ("por no" no es "porno").
- Los cursos pendientes cuentan también los que están `GENERATING`; las consultas bloqueadas cuentan para el límite de frecuencia y no para el de pendientes.

**Fuera de alcance real (documentado, no hecho)**
Retención de las consultas (F4-1), desbloqueo de un tema por el tutor (F4-2), filtro en otros idiomas (F4-3), pantalla del tutor con los intentos bloqueados (F4-4, F08) y la medición con un
modelo real barato de cuántas veces falla el JSON (F4-5).
