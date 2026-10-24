# F05 — Generación del curso

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

Con la ficha de F04 (y, si hay, el material de F03), la IA monta el curso por fases:
**temario → capítulos → lecciones → ejercicios → examen por tema.**

El **temario sale primero** (segundos) y queda visible para el usuario; el resto del contenido se
genera en segundo plano, en orden, como tarea asíncrona con progreso en vivo (SSE o WebSocket). El
alumno puede empezar el tema 1 mientras se genera lo demás. Cada fase valida la salida del modelo
contra un esquema estricto (JSON versionado) y reintenta de forma acotada si no es válida.

Con material propio: el contenido se apoya en los fragmentos recuperados y **cita la fuente**. Lo que
añade el modelo por su cuenta se marca como complemento. Sin material: se genera desde el
conocimiento del modelo (valorar búsqueda web como mejora posterior). Todo contenido lleva aviso de
"generado por IA".

Se usan los modelos por nivel y los controles de gasto de F02 (registro de coste por curso, tope
mensual, una generación concurrente por usuario). Si se alcanza el tope o falla el proveedor, el
trabajo queda en estado reanudable y se avisa al usuario; nunca se regenera lo ya generado.

El curso se guarda como documento estructurado y versionado exportable a JSON (sin material original
ni datos del usuario).

Fuera de alcance: búsqueda web en la generación, regeneración parcial de lecciones concretas,
revisión humana del contenido.
