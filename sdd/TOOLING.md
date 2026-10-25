# TOOLING — skills, hooks y plugins

## Decisión

| Pieza | Qué se usa | Dónde |
|-------|-----------|-------|
| Proceso | Skill del proyecto `sdd-feature` (`/sdd-feature F01`) | `.claude/skills/sdd-feature/` |
| Revisión de IA | Skill del proyecto `llm-integration-review` | `.claude/skills/llm-integration-review/` |
| Revisión de seguridad | Skill `security-checklist` (ya está en tu configuración global) | global |
| Guardarraíles | 2 hooks `PreToolUse` en Node + permisos `deny` | `.claude/settings.json`, `.claude/hooks/` |
| Plugin | **Ninguno por ahora** | — |

## Skills ya disponibles que encajan (globales, no hay que copiarlas)

| Cuándo | Skill |
|--------|-------|
| FASE 2 (diseño, ADRs) | `engineering:system-design`, `engineering:architecture` |
| FASE 4 (estrategia de tests) | `engineering:testing-strategy` |
| FASE 5 (documentación) | `engineering:documentation` |
| Antes de dar por buena una feature | `code-review` |
| Antes de desplegar (tras F09) | `engineering:deploy-checklist`, `security-checklist` |
| Si algo falla | `engineering:debug` |

## Hooks (deterministas: se ejecutan siempre, no dependen de que Claude se acuerde)

- `guard-files.mjs` (Write/Edit): bloquea escribir `.env`, claves y certificados (permite
  `.env.example`) y bloquea contenido que parezca una clave de API.
- `guard-bash.mjs` (Bash): bloquea `git push --force`, `--no-verify`, `git reset --hard`, leer `.env`
  con `cat`/`type`, y revisa el diff de cada `git commit` buscando claves.
- `settings.json` además deniega la lectura de `.env`, `.env.local` y `*.pem`.

Requieren **Node** instalado (ya lo necesita el frontend Vue). Si Node no está, el hook falla sin
bloquear y no protege: comprobarlo en F01.

### Claude al mando del entorno Docker (opt-in, decide el usuario)
Los hooks ya impiden `--privileged`, `docker.sock`, `down -v`, `volume rm/prune` y `system prune`, tanto
en comandos como en ficheros Compose/Dockerfile. Para que Claude opere el stack sin pedir permiso en cada
comando, el usuario puede añadir a `.claude/settings.json` (en `permissions.allow`):
`"Bash(docker compose up*)"`, `"Bash(docker compose ps*)"`, `"Bash(docker compose logs*)"`,
`"Bash(docker compose run --rm*)"`, `"Bash(docker compose exec*)"` y `"Bash(docker compose build*)"`.
No se incluye por defecto: se concede cuando el usuario lo decida.

### Candidatos a hook más adelante (cuando existan los comandos)
- Ejecutar los tests tras editar código (cuando F01 defina el comando de tests).
- Linter/formateo automático tras editar.

## Por qué no un plugin
Un plugin sirve para empaquetar y repartir skills, hooks y agentes entre varios proyectos o equipos.
Aquí hay un solo proyecto, así que `.claude/` dentro del repositorio es más simple y viaja con el
código. Si el proceso SDD se reutiliza en otros proyectos, ese es el momento de empaquetar
`sdd-feature`, `SDD_PROCESS.md` y los hooks como plugin.

## Skills que probablemente convenga crear después (no ahora)
- Una skill de **esquema JSON del curso** cuando se diseñe en F05, para mantener el formato versionado.
- Una skill de **benchmark de modelos** tras F02, si el benchmark se repite.
Se crean cuando haya algo real que codificar, no antes.

## Nota importante
Esto es la configuración de **Claude Code** para construir el proyecto. Las llamadas a IA de la propia
aplicación (capa propia de proveedores, Ollama, Anthropic, OpenAI, Gemini) son otra cosa y se definen en F02.
