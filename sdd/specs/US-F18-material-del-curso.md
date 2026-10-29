## US-F18 · Material del curso: subirlo al crear el curso y ver dónde se usa

> FASE 1. Acordado con el usuario el 29-10-2026: «cuando cree un curso y añada la documentación para ese curso… esa documentación solo le interesa a ese curso». Se mantiene la **biblioteca del usuario** (F03, ADR-30 a 35) por debajo y se cambia **cómo se usa**. Las imágenes y fotos de apuntes quedan para **F19** (BACKLOG). Las decisiones D1–D8 son propuestas mías a confirmar.

### Como persona que usa Study Lab (adulto o menor)
### Quiero subir los apuntes de un curso en el propio asistente de «Nuevo curso» y ver, en cada curso y en cada documento, qué se relaciona con qué
### Para no dar vueltas entre pantallas y para saber qué apuntes usa cada curso

Contexto: hoy, al crear un curso, el asistente solo deja **elegir entre documentos ya subidos**; si no hay ninguno, manda a «Mi material» con un enlace y hay que volver. La relación curso–documento **ya existe** en la base de datos (`course_document`, F04) y la API del curso ya devuelve sus documentos (`documents: [{id, name}]`), pero la pantalla del curso solo los enseña como una línea de texto y «Mi material» no dice en qué curso se usa cada documento. No cambia el modelo ni la privacidad: el material sigue siendo del usuario y toda consulta filtra por propietario.

### Decisiones propuestas

| # | Decisión | Propuesta |
|---|----------|-----------|
| D1 | **Subir dentro del asistente** | En el paso del material de «Nuevo curso», con «Sí», aparece la zona de subida de Mi material (`DropZone`) con el título «Añade los apuntes de este curso». Es **la misma subida** (`material.upload`: mismos tipos, tamaño, vacío y mensajes); no hay un segundo camino. |
| D2 | **Selección automática** | Un documento subido desde el asistente se **marca solo** cuando termina de procesarse (`READY`), sin pasar del máximo de 5; mientras tanto se ve «procesando…» y no se puede elegir; si falla, se dice el motivo en palabras (los mismos de Mi material). |
| D3 | **Reutilizar** | La lista de documentos ya subidos sigue debajo («o elige alguno que ya tengas»): reutilizar un apunte no obliga a subirlo otra vez. Sin documentos, ya no manda a «Mi material»: invita a subir aquí. |
| D4 | **Material en la pantalla del curso** | La línea «Documentos: a, b» pasa a una tarjeta **«Material de este curso»** con cada documento (icono por tipo y nombre como texto). Sin documentos, la tarjeta no sale. **Sin cambios de backend** para esto. |
| D5 | **«Usado en» en Mi material** | Cada documento propio dice en qué cursos se usa (chips con el título del curso que llevan a él) o «Sin usar todavía». Un endpoint nuevo `GET /api/courses/material-usage` devuelve `{usage: {documentId: [{id, title}]}}` solo del que llama. **No se toca `DocumentView`** (ADR-92: un contrato fijado por tests no se amplía; endpoint aparte). |
| D6 | **Borrar un documento usado** | La confirmación de borrado avisa: «Se usa en N curso(s). Los cursos ya creados no cambian.» (el curso conserva su contenido ya generado; solo desaparece el enlace). El flujo y los `data-testid` de borrado no cambian. |
| D7 | **Menores y filtrado** | Igual que hoy: un menor sube y elige solo lo suyo, su tutor ve su biblioteca (F08) y el filtrado de contenido de F04/F05 se aplica donde ya se aplicaba. «Material de mis menores» no muestra «Usado en» (es solo lectura de lo que ya ve). |
| D8 | **Lo que no cambia** | Modelo de datos, migraciones, límites de subida, procesado, `MaterialSearch`, avisos al menor, `DocumentView` y los endpoints de F03/F04/F05. Sin librerías nuevas. |

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Subir en el asistente:** con «Sí» en el paso del material hay una zona de subida; elegir o soltar un fichero llama a la misma subida de Mi material y da los mismos errores (tipo, tamaño, vacío, servidor). |
| AC-2 | **Autoselección:** un documento subido desde el asistente se marca cuando pasa a `READY`, nunca antes ni por encima de 5; uno con error no se marca y dice su motivo; uno ya marcado no se desmarca solo. |
| AC-3 | **Reutilizar:** los documentos ya subidos siguen apareciendo para elegirlos; `Continuar` exige de 1 a 5 como hasta ahora con «Sí». |
| AC-4 | **Curso:** la pantalla del curso enseña «Material de este curso» con los documentos enlazados (nombre como texto, icono por tipo) y nada si no hay ninguno; un documento borrado ya no aparece. |
| AC-5 | **API de uso:** `GET /api/courses/material-usage` exige sesión, devuelve por documento los cursos **del que llama** que lo usan (id y título), ordenados del más reciente al más antiguo, y nunca documentos ni cursos de otro usuario; sin uso devuelve un mapa vacío. |
| AC-6 | **Mi material:** cada documento propio dice «Usado en» con sus cursos (enlaces con forma de chip) o «Sin usar todavía»; si la lectura falla, la lista de documentos sigue funcionando sin esa línea. |
| AC-7 | **Borrar:** la confirmación de borrado de un documento usado dice en cuántos cursos se usa y que los cursos ya creados no cambian; borrar sigue pidiendo confirmación y funcionando como hoy. |
| AC-8 | **Privacidad:** el endpoint y la pantalla no devuelven contenido, hash ni propietario; un menor solo ve lo suyo, su tutor no lo lee por este endpoint (404/vacío), y los títulos de curso y nombres de documento salen siempre como texto. |
| AC-9 | **Sin regresiones:** los tests de F03, F04, F05 y F16 siguen en verde; `DocumentView` y el resto de contratos no cambian. |
| AC-10 | **Diseño y accesibilidad:** colores solo de `tokens.css`; estados con palabras e icono; objetivos de 44 px; sin desbordamiento a 360–430 px; la zona de subida del asistente se usa con teclado. |

### Casos borde
- Sin documentos y con «Sí»: se invita a subir; con «No» no se pide nada.
- Subir un fichero repetido (mismo contenido): el mismo error que en Mi material.
- Subir mientras otro documento se procesa; subir el sexto con cinco ya marcados: se sube pero no se marca y se avisa del máximo.
- Salir del asistente a medias: los documentos subidos quedan en la biblioteca (no se pierden ni se borran).
- Un curso cuyo documento se borró después: el curso sigue, sin ese documento en su tarjeta.
- Un documento usado por varios cursos: los chips salen todos, el más reciente primero; títulos largos se parten.
- Menor: ve «Usado en» solo de sus cursos; su tutor no ve ese endpoint.

### Fuera de alcance (v1)
- **Imágenes y fotos de apuntes** (leerlas con un modelo de visión u OCR): feature aparte, **F19**; se anota en el BACKLOG con sus riesgos (coste por imagen, filtrado de menores, texto no fiable).
- Cambiar el material de un curso ya creado (añadir o quitar documentos y regenerar): sería una edición de curso (F09) y gasta IA.
- Subir varios ficheros a la vez, vista previa, renombrar o carpetas.

### Definición de hecho
- Todos los AC con test (backend del endpoint, incluidos acceso y aislamiento; frontend del asistente, del curso y de Mi material), tests de F03–F05 y F16 en verde.
- Pasada en el navegador (escritorio y móvil) subiendo un documento desde «Nuevo curso» hasta ver el curso con su material, y «Usado en» en Mi material.
- `security-checklist` pasada (endpoint nuevo con datos del usuario); `llm-integration-review` no aplica (sin cambios de IA).
- ADR nuevo en `ARCHITECTURE.md`; README, CLAUDE y BACKLOG al día (F18-x y F19); informe en `sdd/reports/`.
