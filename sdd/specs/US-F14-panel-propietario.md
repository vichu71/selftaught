## US-F14 · Panel del propietario (dashboard de administración)

> FASE 1. Decididas por el usuario (27-10-2026): propietario por variable de entorno, **ver y actuar**, métricas de gasto, usuarios y conexiones, uso y salud, «precios» = coste real a los proveedores. El resto son propuestas mías a confirmar (marcadas «propuesta»).

### Como propietario de la aplicación
### Quiero una pantalla de dashboard con las estadísticas de la app y poder actuar sobre las cuentas y sus topes
### Para que vea cuánto gasta la IA y quién, cuánta gente la usa, si algo falla, y pueda cortar o ajustar un uso abusivo sin tocar la base de datos a mano

Contexto: hoy **no existe un rol global** (`PROJECT.md`: «no hay rol admin global en v1»; `AuthUser` solo conoce `TUTOR`/`STUDENT` según `kind`). El gasto de cada llamada ya se registra en `ai_usage` (usuario, tarea, proveedor, modelo, tokens, coste, estado, error, duración); las conexiones, en `audit_event` (`LOGIN_OK`, `LOGIN_FAILED`, `LOGIN_LOCKED`, `REGISTER`…) y `auth_session`; el uso, en las tablas de cursos, piezas, estudio, tutor, material e ilustraciones. **No** se guarda IP útil ni geolocalización (`login_attempt` va hasheada), así que «conexiones» son sesiones y actividad, no mapas. Los topes por usuario viven hoy en la configuración (`app.ai.user-limit-*`, menor 2 USD, adulto 10 USD), no en la BD: para cambiarlos desde el panel hace falta guardarlos.

### Decisiones

| # | Decisión | Propuesta |
|---|----------|-----------|
| D1 ✅ (usuario) | Quién es el propietario | La cuenta **adulta, `ACTIVE` y con correo verificado** cuyo correo coincide con la variable de entorno `OWNER_EMAIL` del backend. El rol (`OWNER`) se calcula en cada petición, **nunca** se guarda en la BD ni se puede pedir por API. Si la variable falta o está vacía, el panel no existe (todo responde 404) y la app arranca igual. Un único propietario en v1. |
| D2 ✅ (usuario) | Alcance sobre las personas | **Ver y actuar.** Acciones: desactivar/reactivar una cuenta, fijar el tope mensual de IA de un usuario (o volver al de por defecto) y revocar sus sesiones. Todas con auditoría. |
| D3 | Menores (propuesta) | La lista enseña de un menor solo su **identificador de acceso**, tipo, estado, fechas, gasto y su tutor (id corto); **nunca** su nombre visible, temas, respuestas, conversaciones ni cursos. Puede desactivarlo, no ver lo que escribe. El aviso al menor de que su adulto ve su actividad (F08) **no cambia**, pero la política de privacidad/README dice que el propietario ve cifras de cuenta y gasto. |
| D4 | Adultos (propuesta) | Se enseña nombre visible y correo (el propietario ya es quien da acceso a la app). Nunca el contenido de sus cursos, material o conversaciones. |
| D5 ✅ (usuario) | «Precios» | **Coste real pagado a proveedores**: lo que ya registra `ai_usage` + la tabla de precios vigente con la fecha en que se verificó cada una. Nada de planes ni cobro a clientes. |
| D6 | Fuente del gasto (propuesta) | Se cuenta `source = 'APP'`; las llamadas del benchmark (`BENCHMARK`) salen aparte, en una línea propia, y no cuentan para los topes (como hoy). Las filas de cursos ya borrados (F13) siguen sumando. |
| D7 | «Activo» (propuesta) | Un usuario está **activo** en una ventana si tiene al menos un `LOGIN_OK` en ella (1, 7 y 30 días). Limitación: una sesión larga sin volver a entrar no cuenta; se dice en la pantalla. Última actividad = último `LOGIN_OK` o llamada de IA. |
| D8 | Tope por usuario (propuesta) | Tabla nueva `user_limit_override (user_id, limit_usd, set_by, set_at)`: si hay fila manda sobre el valor de la configuración; si no, el de por defecto. `limit_usd` entre 0,00 y 500,00 (0 = sin uso de IA). `BudgetService` lo lee en cada comprobación. El tope sigue **cortando siempre** (A1). |
| D9 | Ventanas y coste de las consultas (propuesta) | Rangos fijos: 7, 30 y 90 días (por defecto 30); fuera de ellos → 400. Todo se agrega **en SQL** con índices (`ai_usage_occurred_idx`, `audit_event` por acción y fecha), sin cargar filas en memoria; la lista de usuarios va paginada (25, máximo 100). Sin refresco automático: botón «Actualizar». Límite por propietario en la BD: 120 consultas por hora al panel. |
| D10 | Gráficas (propuesta) | **Sin librería nueva**: componentes Vue que dibujan SVG con elementos (como el mapa de F10/F11), colores solo de `tokens.css`, y debajo de cada gráfica **la tabla equivalente** (accesible, y única forma probada por los tests de DOM). Cada estado se dice también con texto, no solo con color. |
| D11 | Salud del sistema (propuesta) | Solo con lo que ya existe, **sin instrumentar nada nuevo**: cursos por estado (cola y en curso), piezas `PENDING`/`FAILED`, documentos y ilustraciones pendientes o fallidos, errores de IA recientes por tipo y proveedor, bloqueos de contenido, tamaño de la BD (`pg_database_size`) y filas de las tablas grandes. CPU, memoria y latencia del servidor quedan fuera (necesitan un sistema de métricas). |
| D12 | Dónde vive (propuesta) | Paquete `owner/` en el backend (`/api/owner/**`) y vista `OwnerView` (`/propietario`) en el frontend, con enlace visible **solo** para el propietario. El paquete **no llama a ningún modelo** (nada de `AiGateway`, `MaterialSearch` ni `GenerationLock`) y **no lee contenido** (lecciones, respuestas, conversaciones, temas, títulos de curso: lo comprueba un test de arquitectura). |

### Qué muestra (v1)

- **Gasto de IA:** coste de hoy, del mes y del rango; **proyección a fin de mes** (ritmo del mes en curso); % del tope global y de los topes personales; coste por día (serie) y reparto por modelo, proveedor, tarea y usuario (los 10 mayores); tokens de entrada y salida; llamadas, % de errores y de bloqueos, duración media y p95 por modelo; coste medio por curso generado y por usuario activo; tabla de precios vigente con su fecha de verificación y aviso si alguna tiene más de 90 días.
- **Usuarios y conexiones:** altas por día; adultos y menores; cuentas por estado (`ACTIVE`, `PENDING_VERIFICATION`, `DEACTIVATED`); activos 1/7/30 días; sesiones abiertas ahora; inicios de sesión correctos, fallidos y bloqueos por día.
- **Uso de la app:** cursos por estado y creados por día; piezas hechas, fallidas y pendientes; lecciones hechas, intentos de ejercicio y de examen, exámenes superados; preguntas al tutor (y bloqueadas/filtradas); documentos subidos por estado; ilustraciones por estado; códigos de compartir creados, canjeados y revocados; cursos borrados.
- **Salud:** lo de D11.
- **Usuarios (lista):** tipo, estado, alta, última actividad, gasto del mes y su tope, nº de cursos; filtro por tipo y estado; acciones de D2.

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Propietario (D1):** `OWNER_EMAIL` coincide (sin distinguir mayúsculas) con el correo de una cuenta adulta `ACTIVE` verificada ⇒ esa sesión tiene el rol `OWNER`; con otro correo, un menor, una cuenta sin verificar o desactivada, no. Con la variable vacía o ausente nadie lo es y la app arranca. El rol no sale de ninguna columna ni cabecera. |
| AC-2 | **Acceso:** todo `/api/owner/**` exige sesión y rol `OWNER`; quien tiene sesión y no es propietario (adulto, tutor, menor) recibe **404 igual que una ruta inexistente** (no se revela que el panel existe); un anónimo recibe 401, como en cualquier otra ruta. Las acciones exigen además CSRF (sin él, 403 como en todas las rutas). |
| AC-3 | **Resumen y series:** los endpoints de gasto, usuarios, uso y salud devuelven las cifras de «Qué muestra» para un rango de 7, 30 o 90 días; otro valor → 400. Un test siembra filas conocidas y comprueba cada cifra exacta (incluida la proyección y los percentiles) y que `BENCHMARK` queda aparte. |
| AC-4 | **Sin contenido (D3, D4, D12):** ninguna respuesta del paquete lleva títulos de curso, temas, texto de lecciones, respuestas, conversaciones, documentos ni prompts; de un menor, solo identificador, tipo, estado, fechas, gasto y tutor. Un test de arquitectura y uno de «campos prohibidos» sobre el JSON lo comprueban. |
| AC-5 | **Lista de usuarios:** paginada (25 por defecto, máximo 100, 400 si se pasa), filtrable por tipo y estado, ordenable por gasto y por última actividad; el gasto del mes coincide con `ai_usage`. |
| AC-6 | **Desactivar/reactivar (D2):** el propietario puede desactivar o reactivar cualquier cuenta **menos la suya**; desactivar **revoca sus sesiones**; un usuario desactivado ya no entra ni tiene sesión. Un id ajeno al sistema o mal formado → 404. Desactivar a un tutor no desactiva a sus menores (se avisa en el diálogo). |
| AC-7 | **Tope por usuario (D8):** fijar `limit_usd` (0,00–500,00; otro valor → 400) guarda la fila y desde la siguiente llamada de IA manda ese tope; quitar el valor vuelve al de por defecto. El tope sigue cortando siempre y el mensaje al usuario no cambia. Test con el `BudgetService` real. |
| AC-8 | **Revocar sesiones:** marca `revoked_at` en todas las sesiones abiertas del usuario (también las del propietario sobre otros, nunca la suya propia: eso es «cerrar sesión»); la siguiente petición de ese usuario da 401. |
| AC-9 | **Auditoría:** `OWNER_USER_DEACTIVATED`, `OWNER_USER_REACTIVATED`, `OWNER_LIMIT_SET`, `OWNER_SESSIONS_REVOKED` con actor, sujeto, valor del tope y resultado; **nunca** correo, nombre ni contenido. Los rechazos (permiso, validación) no crean evento de acción; un intento de un no propietario **sí** deja `ACCESS_DENIED` con el id. Ni logs ni auditoría llevan correos ni nombres del panel. |
| AC-10 | **Límites (D9):** 120 consultas por hora y propietario contadas en la BD; la 121.ª → 429 con mensaje claro y sin tocar `ai_usage`. Las acciones tienen su propio límite de 60 por hora. Las consultas agregan en SQL (un test comprueba que el plan usa los índices de fecha de `ai_usage` y el nuevo `(action, occurred_at)` de `audit_event` en el rango). |
| AC-11 | **Precios (D5):** la pantalla muestra la tabla de precios vigente (modelo, precio, fecha de verificación) leída de la configuración; la fecha es una clave opcional nueva `verified-on` en cada precio (hoy solo estaba en comentarios) y, si falta, se dice «sin fecha»; sin llamar a ningún proveedor ni modelo. |
| AC-12 | **Interfaz:** ruta `/propietario` y enlace en la navegación **solo** para el propietario (los demás no ven el enlace y, si entran por URL, ven la pantalla de «no encontrado» de la app). Tarjetas de cifras clave, gráficas SVG con su tabla equivalente (D10), selector de rango, lista de usuarios con acciones y diálogos de Reka UI (nada de `confirm()`) que dicen qué cambia; estados de carga, vacío y error sin códigos del backend; sin `v-html`; datos de personas siempre como texto. |
| AC-13 | **Accesibilidad y diseño:** colores nuevos solo en `tokens.css` y en `contrast.spec.ts` (AA, claro y noche); un estado nunca solo con color; objetivos de 44 px; usable a 360–430 px (las tablas se apilan o hacen scroll **dentro** de su caja, sin desbordar la página). |
| AC-14 | **Configuración:** `OWNER_EMAIL` en `.env.example` y en el compose (opcional); un test de entorno comprueba que sin ella el backend arranca y que **no** hay valor por defecto. |
| AC-15 | **Aislamiento con el resto:** el panel es de solo lectura salvo las cuatro acciones de D2; no modifica `ai_usage`, cursos, piezas ni nada de estudio (test que cuenta filas antes y después de consultar). |

### Casos borde
- `OWNER_EMAIL` apunta a una cuenta que aún no existe: nadie es propietario hasta que se registre y verifique el correo. Si cambia la variable, el propietario anterior pierde el rol en la siguiente petición.
- El propietario desactiva o revoca su propia cuenta: 400 (no se permite); desactivar otra siempre se puede.
- El propietario intenta quitarse el rol o cambiar `OWNER_EMAIL` desde la app: no existe esa acción.
- Un usuario con gasto y sin filas en `auth_session`/`audit_event`: aparece con última actividad vacía, no rompe la lista.
- Tabla `ai_usage` vacía (instalación nueva): todas las cifras a 0 y las gráficas con su estado vacío, sin divisiones por cero (% de errores, medias, proyección).
- Mes con un solo día de datos: la proyección se muestra marcada como «poco fiable» (menos de 3 días).
- Rango sin datos de un modelo: no aparece en el reparto en vez de salir con 0.
- Tope personal por debajo de lo ya gastado en el mes: el usuario queda cortado ya (comportamiento actual de A1); el diálogo lo avisa.
- Dos acciones a la vez sobre el mismo usuario: la última manda, ambas auditadas, sin estado a medias (transacción por acción).
- Usuario desactivado con sesión abierta: la revocación y el estado cambian juntos; sus tareas de IA en curso terminan y se contabilizan (regla de F02).
- Un tutor desactivado: sus menores siguen activos pero su panel de tutor deja de funcionar; el diálogo lo dice.
- Correo con mayúsculas o espacios en `OWNER_EMAIL`: se normaliza; el campo con varios correos separados por comas **no** se admite (un solo propietario).

### Fuera de alcance (v1)
- Varios propietarios o roles intermedios, 2FA, cuenta de administración aparte.
- Ver o moderar contenido (cursos, material, conversaciones, respuestas), suplantar a un usuario, cambiar contraseñas o correos, borrar cuentas.
- Facturación a clientes, planes, cuotas por plan y márgenes.
- Métricas del servidor (CPU, memoria, latencia HTTP), alertas por correo o push, exportar a CSV, comparar periodos y refresco automático.
- Geolocalización, IP y dispositivos (no se guardan).
- Cambiar los topes globales o los precios desde la pantalla (siguen en la configuración).

### Definición de hecho
- Todos los AC y casos borde con test (backend y frontend); tests de seguridad: no propietario → 404 en cada endpoint (con y sin CSRF, anónimo, adulto, menor, tutor), cada campo prohibido ausente, `OWNER_EMAIL` ausente/mal puesta; las cifras con datos sembrados conocidos.
- Probado con el **rol real de la aplicación** (la migración da solo los permisos necesarios sobre `user_limit_override`) y con cookies reales (sin `csrf()` de MockMvc) en las acciones.
- Verificado con el stack aislado (proyecto nuevo, variables ficticias con un `OWNER_EMAIL` de prueba; nunca el `.env` real): datos de IA y sesiones sembrados, una pasada en el navegador en escritorio y móvil, y comprobación de que otro usuario no ve ni el enlace ni la ruta.
- `security-checklist` pasada (es el endpoint más sensible de la app); `llm-integration-review` no aplica (no habla con modelos); ADR nuevos en `ARCHITECTURE.md` (rol de propietario, tope en BD, gráficas sin librería); README, CLAUDE y BACKLOG al día; informe en `sdd/reports/`.
