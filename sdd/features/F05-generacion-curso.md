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

---

## Cierre del ciclo (25-10-2026)

**Decisiones del usuario:** el progreso se consulta cada pocos segundos (sondeo de 3 s), las lecciones se ven con formato (visor de Markdown sin HTML) y, ante un tope o un fallo del proveedor, el curso se
pausa con aviso y botón «Reanudar» (sin reintentos automáticos). ADR-42 a 47 (+ ADR-48).

**Casos borde que no estaban en la ficha y aparecieron al implementar**
- Los **ejercicios se escriben a partir de su lección**: si la lección falló, los ejercicios esperan (siguen `PENDING`) hasta que «Reanudar» la reintente.
- Un temario que el modelo juzga **no apto pero con temas** se usa igual para un adulto; para un menor falla sin generar nada, y un temario no apto **mal formado** se lee solo por su valoración.
- Un temario `appropriate=true` sin temas es inválido y se reintenta una vez; si sigue mal, el curso se pausa por `CONTENT` y reanudar vuelve a pedir el temario.
- Una respuesta **cortada por el límite de salida** (`LENGTH`) cuenta como pieza inválida aunque el texto parezca válido; una respuesta vacía ya cobrada o una entrada demasiado grande cuentan como intento fallido
  de la pieza, no como caída del proveedor.
- Al arrancar, los cursos que quedaron `GENERATING` vuelven a la cola **y se liberan los cerrojos de sus propietarios** (si no, esperarían hasta 20 minutos a que caducasen).
- El reclamo de la cola **no toca a quien ya tiene un cerrojo vigente**, para no llenar la auditoría de denegaciones cada 3 s mientras otra generación suya está en marcha.
- Un curso devuelto a la cola (cerrojo perdido, propietario dado de baja) no se vuelve a reclamar en la misma pasada.
- Un fallo interno inesperado pausa el curso (motivo `PROVIDER`) en lugar de repetirse en bucle.
- Un salto de línea o tabulación está permitido en enunciados y soluciones; cualquier otro carácter de control no.

**Fuera de alcance real (documentado, no hecho)**
Medir con modelos reales cuántas piezas fallan (F5-1), enlaces del visor para menores (F5-2), más hilos de generación (F5-3), borrar un curso (F5-4) y una segunda capa de filtro sobre cada lección para
menores (F5-5). La regeneración de una pieza concreta, la búsqueda web y la corrección de ejercicios (F06) siguen fuera.
