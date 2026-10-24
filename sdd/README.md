# Cómo usar este SDD

Esta carpeta ya está dentro del proyecto. Junto a ella están `CLAUDE.md` y `.claude/` (skills y hooks).

## Contenido

- `PROJECT.md` — contexto común, Claude lo lee siempre y manda sobre cualquier suposición.
- `SDD_PROCESS.md` — el proceso de 8 fases.
- `TOOLING.md` — skills, hooks y por qué no hay plugin.
- `features/` — F01 a F09, una por pieza del proyecto.

## Cómo arrancar (una feature cada vez)

Abre Claude Code en la raíz del proyecto y escribe:

```
/sdd-feature F01
```

Claude hará las 8 fases y te pedirá confirmación al acabar cada una. Cuando termine, repite con la
siguiente (F02, F03...), siguiendo el orden de la sección 10 de `PROJECT.md`.

Si la skill no aparece, el equivalente es:

```
Lee sdd/SDD_PROCESS.md y sdd/PROJECT.md. Ejecuta el ciclo SDD para la feature sdd/features/F01-base-cuentas-roles.md. Empieza.
```

## Notas

- Las features son texto en bruto: Claude las refina en la fase 1 y te preguntará lo que no esté claro.
- Las fases 5 y 8 generan/actualizan `README.md`, `ARCHITECTURE.md` y `CLAUDE.md` en la raíz del
  proyecto (no dentro de `sdd/`), sin borrar lo anterior.
- Si cambias una decisión importante, actualízala en `PROJECT.md`.
