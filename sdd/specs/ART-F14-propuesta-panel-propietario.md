# F14 · Panel del propietario — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F14-panel-propietario.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 86) y `PROJECT.md`. Sin IA, sin tareas ni plantillas nuevas, **sin librerías nuevas** (ni de gráficas).

## Ajustes a la FASE 1 que salen de leer el código

| Ajuste | Motivo | Efecto |
|--------|--------|--------|
| AC-2: un **anónimo** recibe 401 y un POST sin CSRF recibe 403, como en cualquier otra ruta; el **404 uniforme** es para quien tiene sesión y no es propietario | `anyRequest().authenticated()` responde 401 a un anónimo en todas las rutas (existan o no) y el CSRF salta antes del controlador. Cambiarlo solo para `/api/owner` sería lo único que *distinguiría* esa ruta | No se toca `SecurityConfig`; el 404 lo da `OwnerGuard` en el controlador |
| AC-11: la **fecha de verificación** de un precio hoy está solo en comentarios de `ai-pricing.properties` | La configuración no la guarda como dato | Se añade una clave **opcional** `verified-on` a cada precio (texto y imagen) y se rellena con las fechas de los comentarios; sin ella, la pantalla dice «sin fecha». No cambia el arranque (no es obligatoria) |
| AC-10: `audit_event` solo tiene índice por `(subject_user_id, occurred_at)` | Contar `LOGIN_OK` por día en 90 días sin índice por acción recorrería toda la tabla | `V12` crea `audit_event_action_idx (action, occurred_at)` |
| Nuevo: `GET /api/me` incluye `owner: true` solo para el propietario | El enlace del menú debe aparecer solo para él | Cambio mínimo en `MeResponse` (campo omitido si es falso); no es un permiso: el servidor lo vuelve a comprobar en cada llamada |
| El propietario **no necesita rol en `AuthUser`** | `ACTIVE` ya implica correo verificado y el filtro de sesión ya exige `ACTIVE` | `OwnerGuard` compara `user.email()` con `OWNER_EMAIL` y exige `kind = ADULT`; no cambian `AuthUser`, `UserKind` ni el filtro |

## ART-001 · Stack

Sin cambios: Vue 3 + TypeScript (Reka UI vía `UiDialog` y `UiTabs`), Spring Boot (JdbcClient), PostgreSQL. Paquete nuevo `owner/` en el backend. Las gráficas son componentes Vue propios que dibujan SVG con elementos (como `src/map/`), colores solo de `tokens.css`. El paquete **no habla con el modelo** (nada de `AiGateway`, `MaterialSearch` ni `GenerationLock`) ni lee contenido (lo comprueba `OwnerArchitectureTest`).

## ART-002 · Modelo de datos (`V12__owner_panel.sql`)

```sql
-- Tope mensual de IA fijado por el propietario; si no hay fila manda el de la configuración (ADR-88)
CREATE TABLE user_limit_override (
    user_id   uuid          PRIMARY KEY REFERENCES app_user (id),
    limit_usd numeric(8,2)  NOT NULL CHECK (limit_usd >= 0 AND limit_usd <= 500),
    set_by    uuid          NOT NULL REFERENCES app_user (id),
    set_at    timestamptz   NOT NULL DEFAULT now()
);

-- Límite de frecuencia del panel, contado en la BD (ADR-89)
CREATE TABLE owner_action (
    id         bigserial   PRIMARY KEY,
    owner_id   uuid        NOT NULL REFERENCES app_user (id),
    kind       varchar(6)  NOT NULL CHECK (kind IN ('QUERY', 'ACTION')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX owner_action_idx ON owner_action (owner_id, kind, created_at);

-- Para contar eventos por acción y día en el rango de 90 días
CREATE INDEX audit_event_action_idx ON audit_event (action, occurred_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_limit_override TO studylab_app;
GRANT SELECT, INSERT, DELETE ON owner_action TO studylab_app;      -- DELETE: poda de filas de más de 2 h
GRANT USAGE, SELECT ON SEQUENCE owner_action_id_seq TO studylab_app;
```

- `audit_event`, `ai_usage` y `login_attempt` **no cambian** (el panel solo los lee: el rol de la aplicación ya tiene `SELECT`). La tabla `audit_event` sigue siendo de solo añadir.
- `pg_database_size` y los conteos de tablas grandes los puede pedir el rol de la aplicación (se comprueba con el rol real).
- Sin `ON DELETE CASCADE` hacia `app_user`: las cuentas no se borran nunca (F01).

## ART-003 · Endpoints

Todos bajo `/api/owner`. Sesión y `OwnerGuard.require(user)` primero (404 igual que una ruta inexistente si no es propietario; sin auditoría de «ruta inexistente», pero un intento de un **adulto** deja `ACCESS_DENIED` con su id, como en F08). Las lecturas cuentan para el límite de consultas y las escrituras exigen CSRF.

```
GET  /api/owner/ai?range=7|30|90          gasto: hoy/mes/rango, proyección, % tope global, serie diaria, reparto por
                                          modelo/proveedor/tarea/usuario(10), tokens, llamadas, % errores y bloqueos,
                                          duración media y p95 por modelo, coste por curso y por usuario activo, benchmark aparte
GET  /api/owner/prices                    tabla de precios vigente: modelo, USD/millón (o por imagen), verified-on, antigua (>90 días)
GET  /api/owner/accounts?range=…          altas por día, adultos/menores, cuentas por estado, activos 1/7/30, sesiones abiertas,
                                          LOGIN_OK/LOGIN_FAILED/LOGIN_LOCKED por día
GET  /api/owner/usage?range=…             cursos por estado y por día, piezas, lecciones, intentos y exámenes, tutor, material,
                                          ilustraciones, códigos de compartir, cursos borrados
GET  /api/owner/health                    colas, piezas FAILED/PENDING, documentos y fotos pendientes/fallidos, errores de IA por
                                          tipo y proveedor, bloqueos de contenido, tamaño de la BD, filas de tablas grandes
GET  /api/owner/users?kind=&status=&sort=spend|activity|created&dir=&page=&size=
                                          lista paginada (size ≤ 100, 25 por defecto)
POST /api/owner/users/{id}/deactivate     204
POST /api/owner/users/{id}/reactivate     204
POST /api/owner/users/{id}/revoke-sessions 204
PUT  /api/owner/users/{id}/limit          {limitUsd: 0.00–500.00} → 204
DELETE /api/owner/users/{id}/limit        204  (vuelve al tope por defecto)
```

Reglas comunes:
1. `range` ausente = 30; cualquier otro valor → 400 (`ApiException.validation`).
2. **Límite de frecuencia** (`OwnerRateLimiter`): antes de cada consulta, `count(*) FROM owner_action WHERE owner_id=? AND kind=? AND created_at > now()-interval '1 hour'`; ≥ 120 (QUERY) o ≥ 60 (ACTION) → 429 `OWNER_RATE_LIMIT` y no se ejecuta nada; si pasa, se inserta la fila. Se podan las de más de 2 h al insertar.
3. **Acciones** (`OwnerUserService`, una transacción por acción, con `SELECT … FOR UPDATE` del usuario): id mal formado o inexistente → 404; el propio propietario como destino de `deactivate`/`revoke-sessions` → 400 `CANNOT_TARGET_SELF`. `deactivate` hace `changeStatus(ACTIVE→DEACTIVATED)` + `sessions.revokeAll` (reutiliza `UserRepository`/`SessionService` de F01); `reactivate` solo `DEACTIVATED→ACTIVE`; si ya estaba en ese estado, 204 idempotente. Un `PENDING_VERIFICATION` no se activa desde aquí (no es una cuenta usable) → 400.
4. **Tope:** `PUT` hace `INSERT … ON CONFLICT (user_id) DO UPDATE`; `DELETE` quita la fila. El valor se valida en el backend (rango, dos decimales).
5. **Auditoría** (`OWNER_USER_DEACTIVATED`, `OWNER_USER_REACTIVATED`, `OWNER_SESSIONS_REVOKED`, `OWNER_LIMIT_SET`, `OWNER_LIMIT_CLEARED`), dentro de la transacción, con actor y sujeto; `details` solo lleva `{limitUsd}` en el tope. **Nunca** correo ni nombre.
6. Los GET no escriben nada salvo la fila de `owner_action`. Las consultas son SQL agregado con parámetros nombrados (`WHERE occurred_at >= :from`), sin cargar filas en memoria; `from` se calcula con `now() - range` en la BD (UTC, días completos).

### Cifras: definiciones exactas
- **Mes en curso** = mes natural UTC (igual que `IN_MONTH` de `UsageRepository`). **Proyección** = gasto del mes ÷ días transcurridos (incl. hoy, fraccionado a medio día mínimo) × días del mes; `reliable=false` si hay menos de 3 días de datos.
- **Activos 1/7/30** = usuarios distintos con `LOGIN_OK` en `audit_event` en esa ventana (D7). **Sesiones abiertas** = `revoked_at IS NULL AND expires_at > now()`.
- **% errores** = `status='ERROR'` ÷ llamadas; **% bloqueos** = `status='BLOCKED'` ÷ llamadas (0 si no hay llamadas). **p95** = `percentile_cont(0.95)` de `duration_ms` con `status='OK'` por modelo. Solo `source='APP'`; `BENCHMARK` va en `benchmark: {calls, costUsd}`.
- **Coste por curso** = suma de `cost_usd` de filas con `course_id` no nulo ÷ cursos distintos en el rango. **Por usuario activo** = coste total del rango ÷ activos 30 (o ÷ activos del rango).
- **Última actividad de un usuario** = máximo entre su último `LOGIN_OK` y su última fila de `ai_usage`.

### Respuesta de la lista (lo único que sale de cada persona)
```
{ id, kind, status, displayName?  (solo ADULT), email? (solo ADULT), loginId? (solo MINOR),
  createdAt, lastActivityAt, monthSpendUsd, limitUsd, limitIsCustom, courses (nº), tutorId? (solo MINOR) }
```
`displayName` y `loginId` se envían como texto; **nunca** los títulos de los cursos ni nada de su contenido. Un test de «campos prohibidos» recorre el JSON de cada endpoint.

## ART-004 · Cambios en código existente (mínimos)

| Dónde | Cambio |
|-------|--------|
| `BudgetService.checkBeforeCall` y `userLimit(...)` | El tope pasa a `effectiveUserLimit(userId, minor)`: fila de `user_limit_override` si existe; si no, `props.budget().userLimit(minor)`. Lo usan igual `AiUsageController` y `GuardianService`, así que el adulto tutor y el propio usuario ven **el tope real** |
| `UsageRepository` | `userLimitOverride(UUID)` (consulta con `user_id` en el `WHERE`) |
| `MeController` / `MeResponse` | Campo `owner` (omitido si falso) calculado con `OwnerPolicy` |
| `ai-pricing.properties`, `AiProperties.Price`, `AiImageProperties.ImagePrice` | Clave opcional `verified-on` (fecha) |
| `application.properties`, `compose.yaml`, `.env.example` | `app.owner.email=${OWNER_EMAIL:}`, `OWNER_EMAIL` opcional (vacía = sin panel); sin valor por defecto |
| `frontend/src/router/index.ts`, `App.vue`, `stores/auth` | Ruta `/propietario` con guarda `ownerOnly`; enlace solo si `me.owner` |

## ART-005 · Estructura de componentes (frontend)

```
views/OwnerView.vue                 UiTabs: Gasto · Usuarios · Uso · Salud; selector de rango (7/30/90) y «Actualizar»
owner/StatCard.vue                  cifra + etiqueta + texto de estado (no solo color)
owner/LineChart.vue                 serie diaria en SVG con elementos Vue (path/line/text), tokens de color
owner/BarList.vue                   reparto en barras horizontales (modelo, proveedor, tarea, usuario)
owner/DataTable.vue                 la tabla equivalente de cada gráfica; en móvil scroll DENTRO de su caja
owner/UserTable.vue                 lista paginada con filtros y menú de acciones
owner/UserActionDialog.vue          UiDialog: qué cambia (desactivar / reactivar / revocar / tope), avisos de los casos borde
api/client.ts                       ownerAi, ownerPrices, ownerAccounts, ownerUsage, ownerHealth, ownerUsers, ownerDeactivate,
                                    ownerReactivate, ownerRevoke, ownerSetLimit, ownerClearLimit
```
Estados de carga, vacío y error por pestaña con `UiState` (sin códigos del backend; 429 → «Demasiadas consultas, espera un poco»). Los nombres de personas se pintan como texto. Un tutor desactivado o un tope por debajo de lo gastado se avisan en el diálogo. Colores nuevos (series, estados) solo en `tokens.css` y en `tests/design/contrast.spec.ts`.

## ART-006 · Estructura de carpetas

```
backend/src/main/java/es/vicai/studylab/owner/
  OwnerPolicy.java            @ConfigurationProperties app.owner.email; isOwner(AuthUser)
  OwnerGuard.java             require(user, ip): 404 uniforme + ACCESS_DENIED si es adulto
  OwnerRateLimiter.java       límites de la BD (ART-003 regla 2)
  OwnerRange.java             7|30|90, validación
  OwnerController.java        GET ai/prices/accounts/usage/health
  OwnerUserController.java    GET users + las 5 acciones
  OwnerUserService.java       transacción por acción + auditoría
  OwnerStatsRepository.java   SQL agregado (solo lectura, parámetros nombrados)
  OwnerUserRepository.java    lista, bloqueo FOR UPDATE, override del tope
backend/src/main/resources/db/migration/V12__owner_panel.sql
backend/src/test/java/…/owner/  OwnerTestBase, OwnerAccessTest, OwnerStatsTest, OwnerUsersTest, OwnerLimitTest,
                                OwnerArchitectureTest, OwnerNoContentTest (campos prohibidos), OwnerRateLimitTest
frontend/src/views/OwnerView.vue, frontend/src/owner/*.vue (+ tests/)
```

## ART-007 · Wireframe

```
Panel del propietario                        Rango: [7] [30] [90]     [Actualizar]
 Gasto | Usuarios | Uso | Salud
┌ Gasto de IA ──────────────────────────────────────────────────────────────┐
│ Hoy 0,42 $   Mes 6,10 $   Proyección 14,80 $ (fiable)   Tope global 61 % ▲ │
│ Coste por día (30 d)  ▁▂▂▅▃▂▇▄▂▁▂▃▅▆▂…       [ver tabla]                   │
│ Por modelo    claude-haiku-5-5 ████████ 3,90 $   gemini-3.6-flash ███ 2,20 $│
│ Errores 2,1 %  Bloqueos 0,4 %  Duración media 3,1 s  p95 9,8 s             │
│ Precios vigentes: haiku 0,10/0,50 (25-10-2026)  gemini 0,75/3,75 (25-10…)  │
└───────────────────────────────────────────────────────────────────────────┘
┌ Usuarios ─────────────────────────────────────────────────────────────────┐
│ Tipo ▾  Estado ▾  Orden: gasto ▾                          25 por página    │
│ Ana (adulto)   ACTIVE   alta 12-10  activa hoy   5,20 $ / 10 $   3 cursos  │
│ m-7f3a (menor) ACTIVE   alta 15-10  activa ayer  0,80 $ / 2 $    1 curso   │
│                                          [Desactivar] [Revocar] [Tope…]    │
└───────────────────────────────────────────────────────────────────────────┘
```

## ART-008 · ADR

### ADR-87: Propietario por variable de entorno, calculado en cada petición, con 404 uniforme
- Motivo: no existe rol global y crear uno en la BD abre la puerta a escalada (un endpoint mal protegido, una inyección o un adulto que se cambie el rol). Un correo en el entorno del servidor solo lo cambia quien controla el despliegue. Se compara el correo de la sesión (ya `ACTIVE`, luego verificado) en cada petición, sin cachés ni columnas. Quien no es propietario recibe el mismo 404 que una ruta inexistente: no se revela que el panel existe. No hace falta tocar `AuthUser`, `UserKind` ni `SecurityConfig`.
- Alternativas descartadas: `is_owner` en `app_user` (editable por cualquier bug de escritura sobre la tabla); cuenta aparte con 2FA (fuera de v1, rompe «adulto = alumno + tutor»); `hasRole("OWNER")` en `SecurityConfig` (daría 403, que delata la ruta).
- Revisitar si: hay más de un propietario o gestión de roles (entonces tabla de roles con auditoría y 2FA).

### ADR-88: El tope de gasto por usuario pasa a la BD como excepción al valor de la configuración
- Motivo: el propietario debe ajustar un tope sin reiniciar. Una tabla `user_limit_override` con una fila solo cuando hay excepción deja el comportamiento actual intacto (menor 2 USD, adulto 10 USD) y el tope **sigue cortando siempre** (A1/ADR-29). `BudgetService` lo consulta en cada llamada (una lectura por clave primaria); lo ven igual el usuario, su tutor y el panel. Se acota a 0–500 USD para evitar errores de teclado.
- Alternativas descartadas: guardar todos los topes en la BD (migración de datos y un valor nuevo por usuario creado); cachear el valor (un cambio tardaría en cortar); topes por rol editables (otro modelo de datos).
- Revisitar si: se quiere un tope por semana o por tarea.

### ADR-89: Estadísticas agregadas en SQL, con rangos fijos, índice por acción y límites en la BD
- Motivo: el panel es la consulta más pesada de la app; si no se acota, un rango abierto o un refresco en bucle cargarían la BD. Rangos fijos (7/30/90), todo con `GROUP BY` en SQL y parámetros nombrados, sin traer filas; índice nuevo `(action, occurred_at)` en `audit_event`; límites por propietario contados en la BD (120 consultas y 60 acciones por hora, como el resto de límites de la app, nunca por IP: D1). Sin refresco automático. La lista de usuarios calcula gasto y actividad para ordenar sobre todos los usuarios: es válido para una base familiar (cientos de cuentas) y se anota el límite.
- Alternativas descartadas: vistas materializadas o tablas de resumen (datos que se desfasan y un trabajo más); caché en memoria (se pierde al reiniciar y complica los tests); métricas con Prometheus (fuera de v1, D11).
- Revisitar si: hay miles de cuentas o el panel tarda más de un segundo; entonces, resumen diario precalculado.

### ADR-90: Gráficas como elementos Vue y SVG, sin librería, siempre con su tabla
- Motivo: ninguna librería de gráficas está en el paquete y la política de nginx es `default-src 'self'`; añadir una cambia la lista de dependencias exactas del test de entorno y trae superficie nueva. Dos tipos de gráfica (serie diaria y barras de reparto) se dibujan con elementos Vue como el mapa (F10/F11), con colores de `tokens.css`. Cada gráfica lleva debajo su tabla equivalente: es accesible y es lo que prueban los tests de DOM; un estado nunca se dice solo con color.
- Alternativas descartadas: Chart.js/ECharts (peso, dependencia, canvas inaccesible); `v-html` con SVG generado (prohibido).
- Revisitar si: hacen falta gráficas interactivas (zoom, comparar periodos).

### ADR-91: El propietario ve cifras de cuenta, no contenido, y de un menor solo lo mínimo
- Motivo: encaja con «datos mínimos» para menores (F01) y con F08 (el tutor ve el contenido; el propietario, no). La lista enseña de un menor su identificador de acceso, estado, fechas, gasto, tope y tutor; nunca su nombre visible, temas, cursos, respuestas ni conversaciones. Un test de arquitectura impide que `owner/` consulte tablas de contenido y otro recorre el JSON buscando campos prohibidos. El propietario puede desactivar a un menor porque desactivar es reversible (F01); no puede borrar cuentas.
- Alternativas descartadas: que el propietario vea nombres y temas de menores (innecesario para operar); lista solo agregada (no permitiría actuar sobre una cuenta concreta).
- Revisitar si: se pide moderación de contenido (sería una feature con filtros y auditoría propias).
