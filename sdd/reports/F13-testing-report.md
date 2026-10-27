# F13 — Informe de pruebas (borrar cursos)

## Resultado
- Backend: suite completa en verde (1358 tests, 0 fallos, 4 omitidos; 35 son de F13). En la primera pasada completa falló una vez `IllustrationGeneratorTest.ac4_…` (línea 89); pasó suelto y en una segunda pasada completa, sin cambios de código entre medias: se trata como intermitente y queda anotado (F13-6).
- Frontend: 741/741 y el build con `vue-tsc` compila (tras el último ajuste de texto se repitió la suite completa).
- Verificación con el stack (proyecto aislado `studylab-f13`, variables ficticias fuera del repositorio, puertos remapeados para no chocar con el stack real, correo de Mailpit, IA de desarrollo): **34/34**.
- Pasada visual en el navegador (escritorio 1280 px y móvil 390 px, sin desbordamiento horizontal, objetivos táctiles de 44 px): selección múltiple, diálogo, mensaje de curso ocupado, borrado desde la pantalla del curso y título hostil como texto.
- Ningún test llama a una API de pago. No se tocó el stack real ni el `.env` real.

## Resultados por criterio de aceptación

| AC | Descripción | Test | Estado |
|----|-------------|------|--------|
| AC-1 | Borrar se lleva el curso y sus dependientes, 204 | `ac1_deleteTakesTheCourse…`, `ac1_itWorksThroughTheBulkEndpoint…`, `ac1_theCourseDisappearsFromTheList`, `ac1_theLibraryDocumentsSurvive…` | ✅ |
| AC-2 | Sesión y CSRF; ajeno, inexistente o mal formado 404 igual; tutor no borra | `ac2_itRequiresASessionAndTheCsrfToken`, `ac2_anotherUsersCourse…`, `ac2_theGuardianCannotDelete…` | ✅ |
| AC-3 | El menor recibe 403 | `ac3_aMinorGets403OnBothEndpoints…` | ✅ |
| AC-4 | 409 `COURSE_BUSY` en cola o generándose; se borra en `READY`, `PAUSED`, `FAILED` | `ac4_…` (parametrizados) | ✅ |
| AC-5 | `ai_usage` y consumo mensual se conservan | `ac5_theAiSpendRows…` | ✅ |
| AC-6 | Copias intactas con `origin_id` nulo; códigos dejan de valer | `ac6_copiesSurvive…`, `ac6_deletingACopyWhoseOriginIsAlreadyGone…` | ✅ |
| AC-7 | `course_intake` vaciada pero conservada | `ac7_theIntakeRowStays…`, `ac7_aBlockedIntake…` | ✅ |
| AC-8 | Auditoría con ids, sin títulos; rechazos sin evento; logs sin títulos | `ac8_eachDeletedCourse…`, `ac8_rejectionsLeaveNoAuditEvent`, `ac8_logsNeverContain…` | ✅ |
| AC-9 | No toca otros cursos ni usuarios | `ac9_deletingOneCourseTouchesNoOther…` | ✅ |
| AC-10 | Interfaz: diálogo, sin `confirm()`, sin código del backend, sin `v-html` | `DeleteCourses.spec.ts`, `CourseDelete.spec.ts` | ✅ |
| AC-11 | Doble pulsación / 404 sin error visible | `sends the request only once…`, `treats a 404 as «already deleted»…` | ✅ |
| AC-12 | Borrado múltiple todo o nada, 1–50 ids | `ac12_…` (5 tests) y los de la lista | ✅ |

## Casos borde verificados

| Caso borde | Test | Estado |
|------------|------|--------|
| Ilustración pendiente en un curso borrado | `edge_imagesOfEveryStatusGoWithTheCourse…` | ✅ |
| Curso en pausa con piezas pendientes no vuelve a la cola | `ac4_aPausedCourseWithPendingPieces…` | ✅ |
| Borrar dos veces | `edge_deletingTheSameCourseTwice…` | ✅ |
| Original ya borrado y luego la copia | `ac6_deletingACopyWhoseOriginIsAlreadyGone…` | ✅ |
| Cincuenta ids (límite) y 51 | `ac12_fiftyIdsIsTheLimit…`, `ac12_invalidLists…` | ✅ |
| Lista vacía, repetidos, no UUID, nulos, cuerpo sin `ids` | `ac12_invalidLists…` | ✅ |
| Uno ajeno o uno ocupado dentro de la lista: no se borra ninguno | `ac12_ifOneIsForeign…`, `ac12_ifOneIsBusy…` | ✅ |

Estructura (`CourseDeleteArchitectureTest`): sin `AiGateway`, `MaterialSearch` ni `GenerationLock`; bloqueo `FOR UPDATE … ORDER BY id`; toda consulta sobre `course`/`course_intake` con `owner_id` y parametrizada; la migración solo concede `DELETE` sobre `course`.

## Verificación con el stack (34/34)
Dos adultos, un menor con su curso y una copia canjeada por código. Sin sesión (el CSRF responde 403 antes que la sesión; con token y sin sesión el test de MockMvc da 401) y sin CSRF: rechazado en los dos endpoints. Ajeno e inexistente: 404 con el mismo cuerpo, también en lote, sin borrar nada. Menor: 403 `CHILD_CANNOT_DELETE`; el tutor adulto: 404 sobre el curso de su menor. Lista vacía, repetidos, no UUID y cuerpo sin `ids`: 400. Un curso `GENERATING` dentro del lote: 409 `COURSE_BUSY`, no se borra ni el otro, y sin evento de auditoría. Borrado real de dos cursos: 204; desaparecen sus piezas, temas, progreso, conversación y código de compartir; el curso restante y el del menor siguen; doble borrado y detalle: 404; el gasto de IA (filas y suma) queda igual; la copia sigue con `origin_id` nulo y el mismo número de piezas; el código ya no canjea (404); la consulta del asistente queda con tema vacío, preguntas nulas y sin documentos; dos eventos `COURSE_DELETED` con ids y sin títulos; el log del backend no contiene ningún título. Todo ello con el rol real de la aplicación (`studylab_app`), que es lo que prueba que el `GRANT` de `V11` basta.

Nota de método: el modelo falso del perfil de desarrollo no devuelve un temario válido (los cursos quedan `PAUSED`), así que el contenido de los cursos se sembró por SQL; el borrado no depende de cómo se generó. El volumen de `studylab-f03` ya existía con otras contraseñas ficticias y no se borró (solo lo hace el usuario): se usó un proyecto nuevo, `studylab-f13`.

## Pasada visual
Escritorio: modo «Seleccionar» con casillas, «Seleccionar todos», contador y «Borrar (n)»; el diálogo lista los cursos como texto (también `<b>negrita</b>` y `<img …>` salen como texto), dice qué se pierde y qué se conserva y enlaza a exportar cada curso; con un curso ocupado en la selección aparece «Algún curso se está generando ahora mismo…» sin código; al quitarlo de la selección el borrado sale y la lista se refresca. Móvil (390 px): el diálogo cabe, sin desbordamiento, botones de 44 px. Pantalla del curso: «Borrar curso» → diálogo → vuelve al inicio.
Corregido en esta pasada: «1 seleccionados» → «1 seleccionado».

## Revisión de seguridad (security-checklist)
Bien: ambos endpoints exigen sesión (por defecto en `SecurityConfig`) y CSRF; el curso se busca con el usuario de la sesión como propietario y un ajeno, inexistente o mal formado responde el mismo 404 (también en lote); el menor recibe 403 antes de tocar la BD y el tutor adulto solo ve el 404 de «ajeno»; la lista está acotada (1–50, sin repetidos, solo cadenas UUID) y validada en el backend; SQL parametrizado (`ANY(:ids)`), sin `v-html` (títulos como texto, probado con entradas hostiles); una transacción con las filas bloqueadas en orden de id evita estados a medias, interbloqueos y carreras con ediciones y con la generación (un curso en cola o generándose se rechaza; una ilustración en vuelo no escribe sobre un curso borrado); la auditoría y los logs llevan solo ids; ningún secreto ni dependencia nueva; el aislamiento entre usuarios se prueba contando filas.
Pendiente / decisiones aceptadas:
- 🟡 Sin límite de frecuencia: borrar no gasta ni llama a la IA y no hay recurso que proteger (ADR-84).
- 🟡 El borrado es definitivo; la red de seguridad es el aviso y la exportación previa (F13-1).
- 🟡 Un menor no puede limpiar sus cursos y el tutor tampoco puede hacerlo por él (F13-2).
- 🟡 Ya existente: un anónimo recibe 403 (CSRF) y no 401 en las peticiones que cambian datos; no filtra nada.
- No comprobable desde aquí: HTTPS, cabeceras y puertos del despliegue real (siguen en `sdd/BACKLOG.md`, D1–D6).

## Revisión de la integración con IA
No aplica: el paquete no llama a ningún modelo (lo comprueba un test de arquitectura). Se conserva `ai_usage`, el registro de gasto y la base de los topes mensuales.

## Cambios de la FASE 7
- `CoursesPanel.vue`: «1 seleccionado» en singular.
- Ninguno en backend.

## Issues encontrados
- Intermitente (ya existente): `IllustrationGeneratorTest.ac4_…` falló una vez en una pasada completa y pasó en la repetición; sospecha de dependencia entre clases por la BD compartida (F12 ya corrigió dos casos parecidos). Anotado en BACKLOG (F13-6).
