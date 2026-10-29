# F18 · Material del curso — informe de pruebas (FASE 7)

Fecha: 2026-10-29. Rama `feature/F18-material-del-curso`. Backend mínimo (un endpoint de lectura), sin migración y sin librerías.

## Resultados

| Suite | Resultado |
|-------|-----------|
| Backend (JUnit + Spring Boot, en contenedor) | **1495 / 1495** (4 omitidos; 8 tests nuevos en `MaterialUsageTest`, el resto sin cambios) |
| Frontend (Vitest, en contenedor) | **1127 / 1127** (93 ficheros; 29 tests nuevos de F18 y 1 actualizado a propósito) |
| Compilación (`vue-tsc --noEmit` + `vite build`, en la imagen del frontend) | sin errores |
| Entorno | no se ha tocado (sin dependencias nuevas) |

Los tests de F18 se escribieron **antes** del código (FASE 4) y pasan tras la FASE 6. El único test antiguo que cambió es `CourseStatusView › shows what was agreed…`: comprobaba que los documentos salían en la ficha «Lo que acordamos»; ahora salen en la tarjeta «Material de este curso» (D4, a propósito).

## Criterios de aceptación

| AC | Cómo se comprueba |
|----|-------------------|
| AC-1 subir en el asistente | `views/NewCourseMaterial.spec`: la zona solo con «Sí», sube con la misma subida (`uploadMaterial`), mismo error de duplicado |
| AC-2 autoselección | `material/autoSelect.spec` (6 casos puros) y `NewCourseMaterial.spec` (marca al pasar de `PROCESSING` a `READY` con temporizadores simulados, marca de inmediato si ya está listo, no marca uno con error y dice el motivo) |
| AC-3 reutilizar | `NewCourseMaterial.spec`: los ya subidos siguen, «o elige alguno que ya tengas», `Continuar` exige de 1 a 5 |
| AC-4 curso | `components/CourseMaterial.spec` (iconos, nada sin documentos, texto hostil) y `CourseStatusView`; backend: un documento borrado desaparece del curso |
| AC-5 API de uso | `course/MaterialUsageTest` (sesión, vacío, dos cursos más reciente primero, claves) y `api/materialusage.spec` |
| AC-6 «Usado en» | `components/MaterialUsage.spec`: chips a `/cursos/{id}`, «Sin usar todavía», fallo del endpoint sin romper la lista |
| AC-7 borrar | `MaterialUsage.spec`: aviso «Se usa en 2 cursos… no cambian», sigue pidiendo confirmación, sin aviso si no se usa |
| AC-8 privacidad | `MaterialUsageTest`: solo `id` y `title`, nada de contenido, hash ni propietario; otro usuario y tutor ↔ menor no ven lo ajeno; texto hostil como texto |
| AC-9 sin regresiones | tests de F03–F05 y F16 en verde; `DocumentView` no cambia |
| AC-10 diseño | colores solo de tokens (test de tokens), zona de subida con la misma `<label>` accesible, objetivos de 44 px en los chips |

## Pasada en el navegador (stack de demostración con backend y frontend de F18)

Flujo completo como propietaria: **Nuevo curso → Sí → subir `apuntes-mates-1eso.txt` → «subido» → «listo» y marcado solo → Continuar → Aceptar → pantalla del curso con «Material de este curso» → Mi material con «Usado en: Matemáticas de primero de la ESO» y «Sin usar todavía» en el otro documento → Borrar → «Se usa en 1 curso. Los cursos ya creados no cambian.»**.

Encontrado y corregido durante la pasada:
- **Tarjeta del documento aplastada al confirmar el borrado:** con el aviso y los botones, la columna del nombre quedaba en 53 px y el nombre se partía letra a letra. Ahora, mientras se confirma, las acciones ocupan una fila propia (`is-confirming`).

El curso creado en la demo se pausa porque la demo usa el proveedor de IA falso (límite conocido, no de F18).

## Revisión de seguridad (`security-checklist`)

- **Autorización:** `GET /api/courses/material-usage` exige sesión (por defecto en `SecurityConfig`), no recibe parámetros y la consulta lleva `owner_id` en el enlace, el curso y el documento; probado con otro usuario y con tutor ↔ menor.
- **Datos:** solo `id` y `title`; sin contenido, hash, nombre de documento ni propietario. Techo de 2000 filas.
- **Entrada y salida:** SQL parametrizado; títulos de curso y nombres de documento como texto en el frontend (probado con HTML hostil); sin `v-html`.
- **Abuso y coste:** el endpoint es de lectura y barato; la subida del asistente es la **misma** de Mi material (mismos límites), sin camino nuevo. Sigue pendiente el límite de frecuencia de subida (F3-6).
- **Menores:** nada nuevo en filtrado (el asistente ya aplicaba F04/F05); un menor solo ve lo suyo.
- **No comprobable desde el código:** despliegue (HTTPS, puertos), igual que en F17.

`llm-integration-review` no aplica (sin cambios de IA).

## Pendiente

BACKLOG F18-1 a F18-3 y **F19** (imágenes y fotos de apuntes, pedida por el usuario). Falta una pasada a ojo como menor.
