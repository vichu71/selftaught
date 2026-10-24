# F09 — Edición de temario y compartir por código

> Feature en bruto. Claude Code la refina siguiendo SDD_PROCESS.md. Contexto en PROJECT.md.

El propietario de un curso puede editar su temario (reordenar, renombrar, añadir o quitar temas y
capítulos, y editar lecciones) y pedir a la IA que genere el contenido de lo nuevo (respetando los
controles de gasto de F02).

Compartir: el propietario genera un **código** de curso. Otro usuario lo introduce y recibe **su
propia copia** (fork), editable, sin afectar al original ni compartir ninguna otra cosa. El curso
copiado guarda `origin_id` y `version`. El propietario puede revocar el código y fijar caducidad.
La copia **nunca** incluye el material original subido ni datos del usuario; los códigos no son
adivinables y tienen límite de intentos al canjearlos.

Edición colaborativa en el mismo curso: no. Si un compañero mejora su copia, comparte su propio
código.

Fuera de alcance: sugerir cambios al autor y fusionarlos, avisos de versión nueva del original,
importación/exportación manual de JSON por el usuario (el formato interno ya es exportable; la
interfaz se valora después).
