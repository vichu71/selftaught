# Arquitectura — Study Lab (nombre provisional)

> Se actualiza al cerrar cada feature. Estado: **F01 (base técnica, cuentas y roles)** y **F02 (capa de IA y control de gasto)
> implementadas y probadas**. Informes de pruebas y seguridad: `sdd/reports/F01-testing-report.md` y `F02-testing-report.md`;
> resumen de cada ciclo en `sdd/reports/F0x-ciclo-completado.md`. Pendientes: `sdd/BACKLOG.md`.

## Visión general

```
 Navegador ──► nginx (frontend, 127.0.0.1:8080)
                 │  /            → web Vue 3 (ficheros estáticos)
                 │  /api/*       → proxy interno ──► backend (Spring Boot :8080, red interna)
                 │                                     │
                 │                                     ├──► db (PostgreSQL + pgvector)
                 │                                     ├──► SMTP (Mailpit en dev / servidor real en prod)
                 │                                     └──► ollama (embeddings: `nomic-embed-text`, 768 dimensiones)
 Un único origen: la cookie de sesión es SameSite=Strict y no hace falta CORS.
```

Flujo de sesión: `login` → el backend crea una fila en `auth_session` (guarda solo el hash del token) y
entrega la cookie `STUDYLAB_SESSION` (HttpOnly) más `XSRF-TOKEN` para el CSRF. Cada petición pasa por
el filtro de sesión → `AccessGuard` (permisos) → controlador. Todo evento relevante se anota en `audit_event`.

## Capa de IA (F02)

```
 feature (F04–F07) ──► AiGateway ──► PromptAssembler    plantilla fija + datos aparte, escapados        (AC-16)
                          │  ├────► BudgetService        gasto del mes, avisos al 80 % y 100 %, corte opcional  (AC-8, 9)
                          │  ├────► UsageRepository      ai_usage: un registro por intento, sin contenido      (AC-6)
                          │  └────► reintentos (máx. 2)  solo ante timeout, 429 transitorio y 5xx              (AC-12)
                          ▼
                     AiProvider (puerto)
       ├─ FakeProvider (dev y tests)   ├─ AnthropicProvider   ├─ OpenAiProvider   ├─ GeminiProvider
       └─ OllamaProvider (embeddings, contenedor `ollama`)
 GenerationLock ── una generación de curso a la vez por usuario, fila en BD con caducidad de 20 min,
                   renovable mientras la generación sigue viva (`Lease.renew()`)                                (AC-11)
 GlobalExceptionHandler ── una AiException que sale por un endpoint tiene su estado HTTP y un mensaje genérico (ADR-28)
 BenchmarkRunner ── solo con el perfil `benchmark` (servicio de Compose de un solo uso, tope propio)         (AC-17)
```

Las features piden una **tarea** (`OUTLINE`, `LESSON`, `ASSESSMENT`, `TUTOR`, `EMBEDDING`); qué proveedor y modelo la atienden es
configuración. El texto del usuario o del material viaja siempre como dato, nunca dentro de las instrucciones.

## Modelo de datos

```
app_user      id, kind (ADULT|MINOR), display_name, email (adulto), login_id (menor),
              password_hash, status (PENDING_VERIFICATION|ACTIVE|DEACTIVATED), created_at, updated_at
guardianship  tutor_id → app_user, student_id → app_user (UNIQUE: un tutor por menor en v1)
email_token   user_id, purpose (VERIFY_EMAIL|RESET_PASSWORD), token_hash, expires_at, used_at
auth_session  user_id, token_hash, created_at, expires_at, revoked_at
login_attempt kind (LOGIN|MAIL), identifier_hash, ip, succeeded, at   (MAIL = cuota de envíos de correo por IP)
audit_event   id, occurred_at, actor_user_id, subject_user_id, action, outcome, ip, details (JSONB)

-- F02 (migración V2)
ai_usage            id, request_id, attempt, occurred_at, user_id (nulo), course_id (nulo), source (APP|BENCHMARK), task,
                    provider, model, input_tokens, output_tokens, tokens_estimated, cost_usd, status (OK|ERROR|BLOCKED),
                    error_kind, finish_reason, duration_ms        -- solo se añade; sin columnas de contenido
ai_budget_alert     month (aaaa-mm), threshold (80|100), raised_at  -- PK (month, threshold): un aviso por mes y umbral
ai_generation_lock  user_id (PK → app_user), owner, acquired_at, expires_at
```

Un `ADULT` exige correo; un `MINOR` exige `login_id` y nunca tiene correo (restricciones `CHECK` en BD).
Los roles se derivan de `kind`: adulto = alumno + tutor, menor = alumno.

## Endpoints (F01)

| Método y ruta | Quién | Notas |
|---|---|---|
| `POST /api/auth/register` | público | 202 siempre; la cuenta queda pendiente |
| `POST /api/auth/verify-email` · `resend-verification` | público | token de un solo uso (24 h) |
| `POST /api/auth/login` | público | cookie de sesión; 429 tras 5 fallos (o 20 por IP) |
| `POST /api/auth/logout` | autenticado | revoca la sesión |
| `POST /api/auth/forgot-password` · `reset-password` | público | token de un solo uso (1 h) |
| `GET /api/me` | autenticado | datos mínimos |
| `GET/POST /api/tutor/students` | adulto | lista y alta de sus menores |
| `POST /api/tutor/students/{id}/reset-password` · `deactivate` · `reactivate` | adulto tutor de `{id}` | 404 si no es suyo |
| `GET /api/ai/usage` | autenticado | adulto: su gasto, el estado del tope global y el de sus menores; menor: solo su nº de llamadas |
| `GET /actuator/health` | red interna | solo `status`, sin detalles |

## Estructura de carpetas

```
selftaught/
├─ compose.yaml · compose.dev.yaml   # servicios/perfiles; puertos de desarrollo aparte
├─ .env.example                      # variables documentadas (el .env real vive solo en tu máquina)
├─ Makefile                          # envoltorio de docker compose
├─ backend/                          # Spring Boot (Maven)
│  └─ src/main/java/es/vicai/studylab/{auth,user,audit,mail,security,config}
│     src/main/java/es/vicai/studylab/ai/{provider,prompt,budget,lock,benchmark}   # F02: capa de IA
│     src/main/resources/db/migration/   # migraciones Flyway (V1 cuentas, V2 IA)
│     src/main/resources/{prompts,benchmark}/ · ai-pricing.properties           # plantillas, temas y precios por modelo
│     src/test/                          # tests de integración (caja negra por HTTP)
├─ frontend/                         # Vue 3 + TS: views, components, stores, api, router, tests
├─ db/init/01-roles.sh               # roles de BD (dueño y aplicación) y extensiones; se ejecuta al crear la BD
├─ scripts/                          # seed y tests de política del entorno
└─ sdd/                              # PROJECT.md, features/, specs/, reports/, benchmarks/ (informes del benchmark), proceso SDD
```

## Decisiones técnicas (ADR)

### ADR-1: Sesiones con token opaco guardado en servidor, no JWT
- Motivo: se revocan al instante (bajas, cambio de contraseña) y no hay clave de firma que custodiar.
- Alternativas descartadas: JWT con refresh token (revocar es complejo).
- Revisitar si: hay varios backends sin BD compartida.

### ADR-2: Un único origen con nginx como proxy
- Motivo: evita CORS y permite cookie `SameSite=Strict`.
- Alternativas descartadas: dominios separados con CORS.
- Revisitar si: el frontend pasa a una CDN en otro dominio.

### ADR-3: Argon2id para contraseñas
- Motivo: recomendación actual de OWASP.
- Alternativas descartadas: bcrypt (válido, pero corta a 72 bytes).
- Revisitar si: el coste de hash afecta al rendimiento del login.

### ADR-4: Identificador de acceso del menor generado por el sistema
- Motivo: el nombre visible puede repetirse; el identificador es único global, con sufijo corto si ya existe.
- Alternativas descartadas: usuario único elegido a mano; "código de familia" en el login.
- Revisitar si: el espacio de identificadores se satura.

### ADR-5: Baja = desactivación y auditoría de solo añadir
- Motivo: se necesita saber todo lo que ha pasado. `audit_event` tiene trigger contra UPDATE/DELETE y el rol de la
  app solo puede INSERT/SELECT. La auditoría no guarda secretos ni el correo de cuentas inexistentes.
- Alternativas descartadas: borrado real de menores.
- Revisitar si: llega una petición de borrado por RGPD.

### ADR-6: Bloqueo de login en BD (`login_attempt`)
- Motivo: sin librerías ni Redis; 5 fallos seguidos por cuenta o 20 por IP en 15 minutos (un login correcto reinicia el
  contador de la cuenta); el identificador se guarda hasheado. La misma tabla (`kind = MAIL`) limita a 10 envíos de
  correo por IP y 15 minutos en registro, reenvío y recuperación, para que la API no sirva para enviar correo masivo.
- Alternativas descartadas: Bucket4j, Redis.
- Revisitar si: hay varias instancias del backend, o si hace falta limitar por destinatario (ver `sdd/BACKLOG.md`).

### ADR-7: Respuestas genéricas anti-enumeración
- Motivo: registro, reenvío, recuperación y login responden igual exista o no la cuenta (el 404 de menores ajenos
  también es idéntico al de ids inexistentes).

### ADR-8: Correo por SMTP desde variables de entorno
- Motivo: `SPRING_MAIL_*`, `MAIL_FROM`, `MAIL_ENABLED`. En `dev` va a Mailpit; en producción el correo no puede
  desactivarse y, si falta SMTP, la app no arranca. Gmail exige contraseña de aplicación.
- Revisitar si: se supera el límite diario del proveedor.

### ADR-9: Un tutor por menor y nombre visible único entre los menores activos de un tutor
- Motivo: simplifica la tutela y evita el doble envío del alta.
- Revisitar si: se necesitan varios tutores por menor.

### ADR-10: Perfiles de Compose
- Motivo: `prod` publica solo el frontend en `127.0.0.1`; `dev` (con `compose.dev.yaml`) añade backend, BD y Mailpit
  también en `127.0.0.1`; `test` usa `db-test` efímera y dos contenedores de un solo uso (`backend-test`,
  `frontend-test`, etiquetados `studylab.runner` y sin healthcheck). Backend y frontend corren con sistema de ficheros de
  solo lectura, sin capacidades y sin root. HTTPS (Caddy) se resuelve al desplegar.
- Desviación respecto a `PROJECT.md` §4b: el perfil `dev` **no** tiene recarga en caliente; el frontend de dev es el mismo
  nginx de producción. Revisitar si el ciclo de desarrollo del frontend se vuelve lento.

### ADR-11: CSRF solo en peticiones autenticadas
- Motivo: los endpoints públicos de `/api/auth` no tienen sesión que abusar; el login entrega `XSRF-TOKEN`
  para el resto. Con `SameSite=Strict` es una segunda capa.

### ADR-12: Tests de caja negra por HTTP con SMTP en memoria
- Motivo: los tests solo dependen de la API y de la BD de pruebas (GreenMail en lugar de Mailpit), así que no
  fijan detalles internos y no envían correo real.
- Límite conocido: MockMvc no ejercita cookies reales con `csrf()`; todo flujo de sesión o CSRF nuevo debe tener además un
  test sin `csrf()` (ver `CsrfCookieFlowTest`) y una prueba con el stack levantado.

### ADR-13: JDBC (JdbcClient) en lugar de JPA
- Motivo: pocas consultas, todas explícitas y parametrizadas; sin ORM entre el SQL y los permisos de BD. El rol de la
  aplicación no tiene DDL y la tabla `audit_event` es de solo añadir, algo que un ORM no deja ver.
- Alternativas descartadas: Spring Data JPA.
- Revisitar si: el modelo de dominio de cursos (F05+) crece lo bastante como para justificar un ORM.

### ADR-14: Dos roles de BD y vistas sin carga diferida
- Motivo: `studylab_owner` solo lo usa Flyway; `studylab_app` no puede cambiar el esquema. El frontend importa sus vistas
  directamente porque la app es pequeña.

### ADR-15: Sin estrategia de "autenticación nueva" para el token CSRF
- Motivo: el filtro de sesión autentica cada petición sin `HttpSession`; Spring Security lo trataba como un login nuevo y
  borraba la cookie `XSRF-TOKEN` en la primera petición autenticada, de modo que las escrituras siguientes daban 403 en el
  navegador (lo detectó la prueba de humo con el stack real; MockMvc no lo veía). Se sustituye esa estrategia por una
  vacía: el token se genera en cada login. Regresión cubierta por `CsrfCookieFlowTest`, que usa cookies reales y un
  contexto de Spring propio (el `csrf()` de MockMvc sustituye de forma permanente el repositorio CSRF del contexto
  compartido).

### ADR-16: Límites por IP y proxy (pendiente para el despliegue)
- Los límites de intentos por IP dependen de que `X-Forwarded-For` lleve la IP real del cliente. nginx la sobrescribe con
  `$remote_addr`; en Docker Desktop (y detrás de un proxy como Caddy) ese valor es la IP del proxy, así que todos los
  usuarios compartirían cuota. Al desplegar hay que configurar `real_ip` en nginx y pasar `X-Forwarded-Proto` del proxy
  de TLS para que la cookie salga con `Secure`.

### ADR-17: Comprobación de secretos como listener y correo síncrono sin propagar errores
- Motivo (secretos): `RequiredSettingsCheck` escucha `ApplicationPreparedEvent`, así falla antes de crear ningún bean y
  ve también las propiedades que inyectan los tests; el error nombra las propiedades que faltan y nunca sus valores.
- Motivo (correo): el envío es síncrono, fuera de la transacción, y un fallo de SMTP se registra sin destinatario ni enlace y
  no cambia la respuesta (para no revelar si la cuenta existe). Contrapartida: la respuesta tarda más cuando se envía un
  correo, lo que permite una enumeración por tiempo (🟡 en `sdd/BACKLOG.md`).
- Revisitar si: se pasa a envío asíncrono con cola.

### ADR-18: Capa de IA propia con adaptadores HTTP, no Spring AI
- Motivo: la capa debe controlar reintentos, timeouts, conteo de tokens y errores, y no registrar contenido. Los tres APIs de chat y
  el de embeddings son estables y simples; una capa propia es más fácil de razonar y se prueba sin red (`MockRestServiceServer`).
  F02 no añade ninguna dependencia nueva.
- Alternativas descartadas: Spring AI (acopla versiones, trae reintentos propios y una autoconfiguración que exige claves).
- Revisitar si: F03 necesita su `VectorStore`, o si el número de proveedores crece mucho.

### ADR-19: Tope mensual en modo `WARN` por defecto
- Motivo (decisión del usuario): sus cuentas de proveedor ya tienen tope duro, así que la app avisa al 80 % y al 100 % (auditoría +
  log + endpoint) y sigue. El modo `BLOCK` (`AI_BUDGET_MODE=BLOCK`) rechaza las llamadas al alcanzar el tope o si su peor caso lo
  superaría; el sobrepaso en `BLOCK` queda acotado por las llamadas en curso. Un proveedor real exige un tope explícito.
- Consecuencia: cambia `PROJECT.md` §3 y §5 y la regla de `CLAUDE.md`, que decían "corte automático".
- Revisitar si: se abre la app a más personas.

### ADR-20: Cerrojo de generación en BD con caducidad de 20 minutos
- Motivo: una caída del backend no deja a nadie bloqueado; el lease lleva un propietario, así que una generación antigua no libera el
  cerrojo de una nueva. Solo aplica a generaciones de curso: el chat del tutor no lo usa.
- **Cambio al implementar:** un curso grande puede tardar más de 20 minutos y, si el cerrojo caducara con la generación viva, un segundo clic
  abriría una generación paralela. El lease tiene `renew()` (amplía la caducidad) y devuelve `false` si ya no es suyo, en cuyo caso la
  generación debe abortar. F05 lo llama entre una llamada de IA y la siguiente.
- Alternativas descartadas: semáforo en memoria (no sobrevive a reinicios ni a varias instancias), advisory locks (se pierden sin avisar).
- Revisitar si: hace falta limitar también el chat del tutor.

### ADR-21: Precios en un fichero de configuración versionado
- Motivo: un modelo sin precio impide arrancar (nunca coste 0 por omisión). El coste es una **estimación**: Gemini factura el
  razonamiento como salida y los modelos con "thinking" lo incluyen en `output_tokens`; el benchmark contrasta el estimado con el
  panel de cada proveedor.
- Revisitar si: los precios cambian a menudo (se podría consultar una fuente externa).

### ADR-22: No se guardan prompts ni respuestas
- Motivo: privacidad de menores. `ai_usage` solo tiene metadatos y es de solo añadir. Un test comprueba que ni los logs llevan
  contenido, mensajes del proveedor o claves.

### ADR-23: Reintentos: máximo 2, solo ante errores transitorios
- Motivo: reintentan timeout, 429 con `retry-after` y 5xx (incluido el 529 de Anthropic). **Nunca** ante clave inválida, petición
  inválida, modelo inexistente, respuesta inválida ni **cuota agotada** (en Anthropic, un tope de gasto llega como 400 o como 429 sin
  `retry-after`; en OpenAI, como 429 `insufficient_quota`): son errores permanentes y reintentarlos solo gasta tiempo.
- Timeouts: el cliente HTTP de cada proveedor usa el mayor timeout de las tareas que lo emplean, no el de cada tarea (limitación conocida).
- Revisitar si: aparece un patrón de errores transitorios que no se cubra.

### ADR-24: Benchmark como servicio de un solo uso con tope propio
- Motivo: gasta dinero real, así que solo existe con el perfil `benchmark` (nunca en el arranque normal), no publica puertos y se
  detiene al alcanzar su tope (por defecto unos 5 USD). El arnés se prueba con el proveedor falso; el run real lo hacemos con las
  claves del usuario. Curso tipo = 1 ficha + 8 lecciones + 9 evaluaciones.
- **Cambio al implementar:** un modelo que falla (clave, nombre mal escrito, cuota) no tumba el run: se anota en una sección "Fallos" del informe,
  se omite el resto de ese modelo y el run sigue con los demás. Sin esto se habría perdido el informe entero por un solo error. Las llamadas de
  un modelo de razonamiento que agotan el límite pensando se registran con su coste (ver ADR-28).

### ADR-25: URL de Ollama configurable, por defecto el contenedor
- Motivo (decisión del usuario): `OLLAMA_BASE_URL=http://ollama:11434`, así la misma imagen de Docker sirve en producción. Para usar
  el Ollama de la máquina bastaría `http://host.docker.internal:11434`.

### ADR-26: Separación instrucciones/datos por construcción
- Motivo: `AiRequest` no tiene ningún modo de colocar datos en la parte de instrucciones. `PromptAssembler` carga una plantilla fija
  (`prompts/<id>.txt`, el id no puede salirse de esa carpeta) y envía los datos en un mensaje aparte, con etiquetas validadas y los
  símbolos `<`, `>` y `&` escapados, para que un dato no pueda cerrar la sección ni abrir otra.

### ADR-27: Credenciales de los proveedores y parámetros no enviados
- Motivo: las claves van siempre en cabeceras, nunca en la URL (Gemini las admite por parámetro, pero eso las dejaría en logs y
  excepciones). No se envía `temperature`: algunos modelos nuevos la rechazan, y se usa el valor por defecto del proveedor.
- **Cambio al implementar:** en producción la URL base de un proveedor de pago debe empezar por `https://`; si no, el backend no arranca
  (una clave nunca viaja en claro). Fuera de producción se admite `http://` para servidores de prueba locales.
- Pendiente de confirmar con el run real: la cabecera `x-goog-api-key` de Gemini y los campos de OpenAI (su documentación no estuvo
  accesible al verificarlos). Confirmado con servidor real: el formato de Ollama (`/api/embed`, 768 dimensiones y 404 con `{"error": …}`).

### ADR-28: Respuestas facturadas pero vacías, y errores de IA hacia HTTP
- Motivo (contabilidad): un modelo con razonamiento puede agotar `max_tokens` pensando y devolver solo bloques de pensamiento. El proveedor
  **cobra** esos tokens, así que los adaptadores devuelven el resultado vacío **con su uso** y es el gateway quien decide: si terminó por
  `LENGTH` se devuelve (texto vacío, coste registrado, fila `OK`); si no, se registra una fila `ERROR` con su coste y se lanza `INVALID_RESPONSE`
  llevando ese coste en la excepción (`AiException.costUsd()`), que el benchmark suma a su gasto. Solo un cuerpo sin la estructura esperada
  (`content`, `choices`, `candidates`) es una respuesta inválida del adaptador. Antes se registraba con coste 0 y el gasto quedaba subestimado.
- Motivo (HTTP): `GlobalExceptionHandler` traduce cada `AiErrorKind` a un estado y un mensaje genérico (`BUDGET_BLOCKED` 429, `GENERATION_BUSY`
  409, `INPUT_TOO_LARGE` 413, `USER_NOT_ALLOWED` 403, `PROVIDER_QUOTA` y `PROVIDER_UNAVAILABLE` 503, el resto 502), con código `AI_<TIPO>`.
  Así ninguna feature improvisa su propio mapeo ni filtra texto del proveedor.
- Revisitar si: se necesita distinguir más casos hacia el frontend.
