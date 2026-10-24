---
name: sdd-feature
description: Ejecuta el ciclo SDD completo (8 fases) para una feature del proyecto. Úsala con /sdd-feature F01 (o F02...F09).
argument-hint: "[F01..F09]"
disable-model-invocation: true
---

Ejecuta el ciclo SDD para la feature `$ARGUMENTS`.

1. Lee `sdd/SDD_PROCESS.md` y `sdd/PROJECT.md`.
2. Lee el fichero de la feature: `sdd/features/$ARGUMENTS-*.md`. Si no existe o no se indicó
   argumento, lista las features de `sdd/features/`, propón la siguiente según el orden y las
   dependencias de la sección 10 de `PROJECT.md`, y espera confirmación.
3. Comprueba que las features de las que depende ya están completadas (mira el código y
   `ARCHITECTURE.md`). Si no lo están, avisa antes de empezar.
4. Sigue `SDD_PROCESS.md` al pie de la letra, fase por fase, pidiendo confirmación al final de cada
   fase. No escribas código antes de la FASE 6.
5. Antes de cerrar la FASE 7, si la feature toca endpoints, autenticación, permisos, entrada de
   usuario, subida de ficheros o llamadas a IA, usa la skill `security-checklist`. Si toca llamadas a
   IA, usa también `llm-integration-review`.
