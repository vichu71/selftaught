# Prueba del entorno en la nube (Claude Code en la web)

> 29-10-2026. Sesión cloud, contenedor efímero, rama `claude/f19-spec` desde `main`. Un comando por cosa, sin instalar nada global y sin tocar `.env`. Solo se creó `frontend/node_modules` y la caché de Maven (ambas ignoradas por git).

## Resumen

| Qué | Resultado |
|-----|-----------|
| Docker CLI / Compose | ✅ instalados (Docker 29.9.2, Compose v5.6.1) |
| **Docker daemon** | ❌ **no hay**: `/var/run/docker.sock` no existe |
| Java | ✅ OpenJDK 21.0.12 |
| Maven | ✅ 3.9.11 (no hay Gradle; el proyecto usa Maven) |
| Node / npm | ✅ Node v22.22.0 · npm 10.9.4 |
| `npm ci` en `frontend/` | ✅ 199 paquetes en 6 s |
| Vitest en `frontend/` | ✅ **93 ficheros, 1127 tests en verde** (43 s) |
| Compilar backend (`mvn test-compile`) | ✅ 30 s, dependencias bajadas por el proxy |
| Tests de backend **sin BD** | ✅ los puros (parsers): 42 de 42 · ❌ los que arrancan Spring: fallan por falta de entorno (ver abajo) |
| PostgreSQL | ❌ no hay servidor (solo el cliente `psql`) |
| Git | ✅ 2.43.0 |
| GitHub | ✅ solo por herramientas MCP (no hay `gh`) |

Máquina: Linux 6.18 x86_64, 4 CPU, 16 GB de RAM, ~30 GB libres.

## Detalle y errores

### Docker
- `docker --version` y `docker compose version` funcionan, pero `docker ps` falla: *failed to connect to the docker API at unix:///var/run/docker.sock*.
- Consecuencia: **no se puede** `docker compose up`, ni `make test-backend` / `make test-frontend` / `make test-env` tal como los define el proyecto (todos usan contenedores), ni la pasada «con el stack levantado» que piden las reglas de F01. Tampoco `sh scripts/tests/run-env-tests.sh`.
- No se probó nada para arrancar un daemon (sería instalar/privilegiar algo fuera de lo pedido y el proyecto prohíbe `privileged` y `docker.sock`).

### Frontend (sin Docker)
- `npm ci` y `npx vitest run` funcionan directamente en `frontend/`, así que **los tests de frontend sí se pueden ejecutar aquí** sin pasar por Compose. Aviso de npm: `lucide-vue-next@1.0.0` está deprecado (usar `@lucide/vue`); no afecta a los tests.
- No se probó `npm run build` (vue-tsc + vite) ni la pasada en navegador. Hay Chromium + Playwright preinstalados, pero para ver la app haría falta el backend con BD.

### Backend (sin BD)
- `mvn -B -q -DskipTests test-compile` → OK.
- `mvn -Dtest='AiArchitectureTest,ImageArchitectureTest,*ParserTest' test` → 55 tests, 0 fallos, **13 errores**:
  - `IntakeParserTest`, `ExercisesAndExamParserTest`, `OutlineParserTest`, `GradeParserTest`: 42/42 en verde (no necesitan contexto de Spring).
  - `AiArchitectureTest` (4) e `ImageArchitectureTest` (9): error de **contexto de Spring**, `PlaceholderResolutionException: Could not resolve placeholder 'TEST_DB_APP_PASSWORD'`. Es esperado: estas clases son `@SpringBootTest` y necesitan las variables del perfil `test` y una PostgreSQL con pgvector (en el proyecto, el servicio `db-test` de Compose). No es un fallo del código.
- Consecuencia: la mayoría de los tests de backend (aislamiento por `owner_id`, migraciones, rol `studylab_app`, etc.) **no se pueden pasar aquí** mientras no haya BD. No hay `postgres` ni `pg_ctl` instalados; instalar PostgreSQL + pgvector sería instalar software del sistema y no se hizo.

### Red y proxy
- El tráfico HTTPS sale por un proxy del agente: `npm` y `mvn` funcionaron sin cambios. Los conectores MCP de `plugin:engineering:*` fallaron al conectar (`ERR_PROXY_TUNNEL: 403`); irrelevante para el proyecto.
- Ollama no se probó (necesita Docker); `make pull-embedding-model` no es posible aquí.

## Conclusión

Sirve para **documentación, specs, trabajo de frontend (con sus tests de Vitest) y compilar el backend con sus tests puros**. **No sirve todavía** para el ciclo completo del proyecto: sin daemon de Docker no hay stack, ni tests de integración del backend, ni verificación «con el stack levantado» ni pasada en navegador de punta a punta.

Opciones para cerrar la brecha (a decidir por el usuario, no se ha hecho nada de esto):
1. Una PostgreSQL con pgvector accesible (instalada en un script de preparación del entorno o un servicio externo) y exportar las variables `TEST_DB_*` del perfil `test` como secretos del entorno cloud: bastaría para `mvn test` sin Docker.
2. Un entorno cloud con Docker disponible, si la plataforma lo ofrece.
3. Mantener la regla actual: lo que necesita Docker (tests de integración, stack, navegador) lo sigue ejecutando el usuario en su máquina Windows.
