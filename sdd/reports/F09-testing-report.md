# F09 — Informe de pruebas (FASE 7)

Edición de temario y compartir por código. Rama `feature/F09-edicion-compartir`. Contrato: `sdd/specs/US-F09-edicion-compartir.md`; diseño y ADR-63 a 66: `sdd/specs/ART-F09-propuesta-edicion-compartir.md` y `ARCHITECTURE.md`.

## Resultado

| Comprobación | Resultado |
|---|---|
| Tests del backend (contenedor, BD efímera, proveedor falso) | 1 170 ejecutados, 0 fallos, 4 omitidos (los de Ollama real opt-in); 119 son de F09 |
| Tests del frontend | 461 de 461; typecheck (`docker build --target build frontend`) correcto |
| Tests de entorno | 43 de 43 |
| Cobertura de líneas | 94,7 % en total (4 054/4 283), 84,2 % de ramas; `courseedit` 100 % (90,2 % de ramas), `share` 94,7 % (86,7 %), `guardian` 96,6 % |
| Stack real aislado (nginx → backend → BD → «modelo» simulado por HTTP, cookies y CSRF reales) | 43 comprobaciones, todas correctas |
| Navegador integrado (pantalla de edición, códigos y «Tengo un código», escritorio y 375 px) | Correcto, sin desbordamiento horizontal |

Una vez, en la suite completa, falló `StudyLimitsTest` (de F06) con un tiempo de ejecución de 7 553 s: el ordenador se suspendió a mitad del test y el reloj de la BD se movió dos horas. Repetido solo pasa (8 de 8) y la pasada de cobertura posterior, con los 1 170 tests, no falló. No es un fallo de código.

## Qué se verificó con el stack levantado

Proyecto aislado `studylab-f03` con contraseñas ficticias y un servidor que imita la API de Anthropic; el stack del usuario no se tocó. Dos adultos (Ana y Luis) y un menor (Hugo, de Ana), con el worker de generación real en marcha.

- **Editar el temario:** renombrar un tema, reordenar temas y capítulos, un orden que no coincide (400), nombres vacíos o largos (400), cada edición sube la versión.
- **Añadir con IA:** un capítulo nuevo devuelve 201, el curso vuelve a la cola, mientras se genera editar da 409 `COURSE_BUSY`, y la cola genera **solo lo nuevo** (1 lección y 1 ejercicios; ni temario ni examen). El examen del tema queda «desactualizado».
- **Lecciones y regenerar:** editar el texto (sin fuentes, sigue «hecha», sus ejercicios y el examen se mantienen marcados y el intento del alumno sigue); solo lecciones y de 1 a 20 000 caracteres; regenerar unos ejercicios (202) genera solo esa pieza, limpia su marca y borra sus intentos sin tocar el chat de la lección.
- **Quitar:** un capítulo, y temas hasta dejar uno; el último no se puede quitar (400).
- **Permisos:** otro usuario recibe 404 en todo; sin CSRF 403; sin sesión se rechaza; ni un menor edita el curso de otro.
- **Compartir y canjear:** el código sale con el formato esperado y no se vuelve a ver (la lista solo enseña su estado); Luis canjea en minúsculas y con espacios y recibe su copia `READY` con versión 1, `copy.originVersion` correcto y **sin el id del original**; el código cuenta 1 uso; la copia lleva el texto editado, sin fuentes, sin progreso ni intentos; lo que hace Luis en su copia no toca el original.
- **Códigos que no valen:** cinco entradas distintas (inexistente, corta, larguísima, vacía, inyección SQL) responden **byte a byte igual**; tras revocar, el código responde igual que uno inexistente y la copia ya hecha sigue; tras unos 10 fallos, `CODE_RATE_LIMITED` (429).
- **Menores:** un título que el filtro rechaza no se guarda (mensaje acordado) y no llega al modelo; uno normal sí; un menor puede crear un código y canjear uno limpio; canjear un curso con texto peligroso → 422 `COPY_NOT_ALLOWED` y no queda ningún curso. El adulto tutor ve `COURSE_EDIT_BLOCKED` y `COURSE_COPY_BLOCKED` en su panel, sin el texto ni el id del curso ajeno.
- **BD, auditoría y logs:** los hashes son SHA-256 válidos y el código no está en ninguna parte; la auditoría tiene `COURSE_SHARE_CREATED/REDEEMED/REVOKED`, `SHARE_CODE_FAILED`, `COURSE_COPY_BLOCKED` y `COURSE_EDIT_BLOCKED` con ids, y ni una fila lleva el código ni los textos editados; el log del backend, cero apariciones del código ni de lo escrito.

Errores míos de la propia pasada, corregidos en el script y no en el código: un reordenamiento con solo dos de seis temas (el servidor lo rechazó bien con 400) y las versiones esperadas tras ese error.

## Lo que NO se ha podido verificar

- **Copia con material propio por HTTP real:** el stack aislado no tiene el modelo de embeddings de Ollama; está cubierto por `ShareRedeemTest` (con fuentes reales: sin documentos, sin fuentes, sin marcas `[n]` y sin ningún id del original en el texto de la copia).
- **Captura de pantalla:** el panel del navegador estaba oculto, así que se comprobó por el DOM (texto, controles, anchura de página). El aspecto fino queda para F10: la pantalla de edición funciona pero es densa (unos 120 botones con un curso de 6 temas).
- **Calidad de lo que genera la IA con un modelo real** al añadir y regenerar: solo la puedes medir tú con tus claves.
- HTTPS, cabeceras y puertos de despliegue: siguen en el backlog de despliegue (D1 a D6).

## Revisión `security-checklist`

Bien:
- **Autorización:** toda edición y gestión de códigos va con el usuario de la sesión como propietario (404 igual para lo ajeno, comprobado con 10 endpoints y con temas, capítulos y piezas de otro curso del mismo usuario); el canje solo lee un curso ajeno por el hash de un código válido y su copia sale del propietario que figura en el código, nunca de lo que envía el cliente; el adulto tutor no edita los cursos de su menor.
- **CSRF y sesión** en todo lo que cambia datos. **SQL** parametrizado y con propietario (test de arquitectura).
- **El código es un secreto:** 80 bits de `SecureRandom` (probado: sin `java.util.Random`), solo su SHA-256 en la BD, mostrado una vez, nunca en logs, auditoría ni respuestas de lista; respuesta indistinguible ante cualquier fallo (sin enumeración); límites por usuario en la BD de fallos y de copias.
- **Concurrencia:** cada edición bloquea la fila del curso (`FOR UPDATE`); cuatro ediciones simultáneas y dos canjes simultáneos pasan sin dejar el temario a medias.
- **Entrada:** longitudes, tipos y caracteres de control validados en el backend; el Markdown y los títulos se pintan siempre como texto o con el visor seguro.
- **Datos:** la copia no puede llevar lo que no tiene columna (material, fuentes, progreso, intentos, chat, consultas, uso de IA); un test cuenta filas y busca ids del original; el detalle del curso nunca enseña `origin_id`.
- Sin secretos, variables ni dependencias nuevas; los valores de ingeniería van en `app.course-edit.*` y `app.share.*`.

Pendiente o a vigilar (en el backlog):
- 🟡 **F9-8:** el panel del adulto no muestra los códigos que crea su menor ni cuándo canjean uno de sus cursos (los eventos ya están en la auditoría).
- 🟡 **F9-9:** las ediciones que no usan IA no tienen límite de frecuencia (son de un usuario autenticado sobre su propio curso).
- 🟡 **F9-6:** sin moderación del contenido compartido entre adultos; el filtro solo protege a los menores y la lista local es corta y en español (F4-3).
- 🟡 Los códigos caducados o revocados no se purgan (tabla pequeña); no hay aviso al propietario de cuántas personas canjearon, solo el contador.

## Revisión `llm-integration-review`

| Punto | Estado |
|---|---|
| Ninguna llamada a un modelo nueva: editar y canjear no usan `AiGateway` ni adaptadores (test de arquitectura) | OK |
| Lo nuevo y lo regenerado lo genera el generador de F05: tarea y modelo por configuración, uso registrado con tarea, modelo, tokens, coste, usuario y curso | OK |
| Tope mensual propio del usuario y global; pausa por `BUDGET` sin error (probado: añadir con el tope alcanzado deja el curso en pausa y las piezas pendientes) | OK |
| Límite extra de 20 ediciones con IA por hora y usuario, contado en la BD; una petición rechazada no cambia nada ni llama al modelo | OK |
| Una sola generación a la vez por usuario y reintentos acotados (el cerrojo de F05 no se toca; editar exige que el curso no esté en cola ni generándose) | OK |
| Nunca se regenera lo hecho: solo ante una petición explícita y confirmada del propietario (amplía ADR-42, documentado) | OK |
| Texto del usuario (títulos, resúmenes, objetivos) al modelo como **datos**, nunca en las instrucciones | OK (usa las plantillas existentes) |
| Menores: filtro sobre lo que escriben antes de guardarlo o enviarlo a la IA y sobre toda la copia; el generador sigue filtrando lo que produce | OK |
| Salida del modelo validada con los parsers de F05 antes de guardarla | OK |
| Sin datos personales nuevos en los prompts; el material no viaja a las copias | OK |
| Las lecciones editadas a mano no cuentan como «generado por IA» para el modelo de gasto | NO APLICA |

## Cambios hechos durante la FASE 7

- `sdd/BACKLOG.md`: F9-8 y F9-9.
