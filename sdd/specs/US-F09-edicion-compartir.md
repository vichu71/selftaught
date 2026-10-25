## US-F09 · Edición de temario y compartir por código

### Como propietario de un curso (adulto o menor)
### Quiero editar el temario y las lecciones de mi curso, pedir a la IA que genere lo nuevo y compartirlo con un código que otra persona canjea para recibir su propia copia
### Para que el curso se ajuste a lo que necesito y pueda pasárselo a otros sin regalar mi material ni mis datos

Decisiones del usuario (FASE 1): al editar una lección **sus ejercicios y su examen se mantienen** y el propietario puede regenerarlos si quiere; los códigos **caducan a los 7 días por defecto** y valen para **varias personas**; **los menores también canjean códigos, con filtro**
de contenido; añadir con IA es **una adición por petición** y una generación a la vez, con los controles de F05 y F02.

Supuestos míos (a confirmar): un menor también puede **crear** códigos de sus propios cursos (su contenido ya pasó el filtro); lo que un menor escribe al editar (títulos, resúmenes, lecciones) pasa por `ContentGuard` antes de guardarse; solo se comparte un curso **terminado** (`READY`);
la edición se bloquea mientras el curso se genera; borrar un curso entero queda fuera (no existe aún).

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | **Editar el temario:** el propietario puede renombrar un tema (título y resumen) y un capítulo (título y objetivo), reordenar temas y capítulos dentro de su tema, y quitar un tema o un capítulo (con sus piezas, y con ello el progreso, intentos y conversaciones del tutor de esas piezas). Un curso siempre conserva al menos un tema y cada tema al menos un capítulo. |
| AC-2 | **Añadir con IA:** el propietario añade un tema (título y resumen) o un capítulo (título y objetivo) y se crean sus piezas pendientes (lección y ejercicios; y el examen si es un tema y el curso lleva examen por tema). El curso vuelve a la cola de F05 y la IA genera **solo lo nuevo**, con el mismo cerrojo de una generación por usuario, el tope de piezas por curso (150) y los controles de gasto de F02 (pausa por tope o proveedor caído, «Reanudar»). Una adición por petición. |
| AC-3 | **Editar una lección:** el propietario sustituye el texto de una lección terminada (Markdown, de 1 a 20 000 caracteres, sin caracteres de control). Sus fuentes de material dejan de mostrarse (ya es texto del propietario). El texto se pinta siempre con el visor seguro. |
| AC-4 | **Ejercicios tras editar:** los ejercicios y el examen de esa lección **se mantienen** y se marcan «la lección cambió»; el propietario puede **regenerar** una pieza (lección, ejercicios o examen) con IA, que sustituye su contenido y borra los intentos y la conversación que dependían de él (con confirmación). Es la única forma de regenerar una pieza ya hecha (F05 queda ampliado). |
| AC-5 | **Límite y gasto:** añadir con IA y regenerar cuentan para un límite por usuario (**20 por hora**, en la BD) y para el tope mensual; una petición rechazada por límite no cambia nada ni llama al modelo. |
| AC-6 | **Estado del curso:** las ediciones se rechazan (409) mientras el curso está en cola o generándose; se permiten en `READY`, `PAUSED` y `FAILED`. Dos ediciones a la vez no se pisan (la segunda recibe 409 o se aplica después, sin dejar el temario a medias). |
| AC-7 | **Versión:** el curso lleva un número de `version` que sube con cada edición guardada (temario, lección o regeneración). Se muestra al propietario. |
| AC-8 | **Crear un código:** el propietario de un curso terminado crea un código de compartir: no adivinable (al menos 60 bits de aleatoriedad), con caducidad (7 días por defecto, entre 1 y 90), de varios usos. Se muestra **una sola vez**; después solo se ve su estado (creación, caducidad, usos, activo o revocado). Un curso tiene como máximo 5 códigos activos. |
| AC-9 | **Revocar:** el propietario revoca un código cuando quiera (idempotente) y deja de valer de inmediato. Un código caducado o revocado se rechaza igual que uno inexistente. |
| AC-10 | **Canjear:** cualquier usuario con sesión introduce un código y recibe **su propia copia** (curso nuevo `READY`, propiedad de quien canjea). La copia lleva el temario y el contenido generado de las piezas hechas; guarda `origin_id` y `origin_version` (la versión del original en ese momento) y empieza en su `version` 1. Editarla no afecta al original ni al revés. |
| AC-11 | **Qué no se copia:** nunca el material subido ni sus fragmentos ni sus enlaces, ni las fuentes de las lecciones (se quitan también las marcas `[n]`), ni el progreso, intentos, conversaciones con el tutor, consultas del asistente, uso de IA o datos de ningún usuario. Lo comprueba un test que cuenta las filas y busca ids del original. |
| AC-12 | **Límite de intentos:** canjear tiene límite por usuario en la BD (**10 fallos por hora**, y 10 copias por hora); un código mal escrito, caducado, revocado o inexistente responden **igual** (404) y los fallos cuentan para el límite (429 `CODE_RATE_LIMITED`). Sin límites por IP (D1). |
| AC-13 | **Menores:** si quien canjea es un menor, todo el texto de la copia pasa por `ContentGuard` antes de crearla; si salta, no se crea nada, ve el mensaje acordado «Contenido inapropiado. Consulta con tu adulto.» y su adulto tutor lo ve en los avisos del panel (`COURSE_COPY_BLOCKED`). Lo que un menor escribe al editar también pasa por el filtro antes de guardarse o de enviarse a la IA; si salta, no se guarda, ve el mismo mensaje y su adulto lo ve como `COURSE_EDIT_BLOCKED`. |
| AC-14 | **Permisos:** solo el propietario edita, regenera y gestiona códigos de su curso; un curso, tema, capítulo, pieza o código ajeno responde 404 igual que uno inexistente; el adulto tutor no edita los cursos de su menor. Todo lo que cambia datos exige sesión y CSRF. |
| AC-15 | **Auditoría y logs:** crear, revocar y canjear un código, los canjes fallidos y las copias bloqueadas quedan en la auditoría solo con ids; ni el código ni los textos editados van a logs ni a la auditoría. |
| AC-16 | **Interfaz:** una pantalla de edición del curso (reordenar con botones, renombrar, añadir, quitar con confirmación, editar lección, regenerar), una sección para compartir (crear, ver estado, revocar, copiar el código) y una pantalla «Tengo un código». Estados de carga, error y vacío claros, sin códigos del backend, nada de `v-html`. |

### Casos borde
- Quitar el último tema, o el último capítulo de un tema: rechazado (400).
- Reordenar con una lista que no contiene exactamente los mismos ids, con repetidos o con ids de otro curso: 400/404 y no cambia nada.
- Renombrar con vacío, solo espacios, demasiado largo o con caracteres de control: 400.
- Añadir cuando se supera el tope de piezas del curso: rechazado (422) y no cambia nada.
- Editar una lección que aún no está generada (`PENDING` o `FAILED`): 409.
- Editar o regenerar mientras otra generación del mismo usuario está en marcha: 409/429 sin pisarla.
- Canjear el código de un curso que luego se edita: la copia es la versión del momento; el original puede seguir cambiando.
- Canjear el código de un curso propio: permitido (genera una copia propia).
- Canjear el mismo código dos veces por la misma persona: dos copias (cada una cuenta para el límite de copias).
- Un código copiado con espacios, en minúsculas o con guiones: se normaliza antes de comprobarlo.
- Un código con caracteres raros o muy largo: se rechaza sin tocar la BD y cuenta como fallo.
- Un menor canjea un curso con una sola lección dudosa: no se crea la copia entera.
- El original se borra o deja de existir: la copia sigue y su `origin_id` queda vacío.
- Revocar un código que ya canjeó gente: las copias ya hechas no se tocan.

### Fuera de alcance (v1)
- Edición colaborativa, sugerir cambios al autor y fusionarlos, avisos de «hay una versión nueva del original».
- Importar o exportar JSON por parte del usuario (la exportación actual no cambia).
- Que el propietario vea quién canjeó su código (solo cuántas veces).
- Borrar un curso entero y que un adulto pase una copia a su menor.
- Editar a mano los ejercicios y las preguntas de examen (solo se regeneran con IA).
- Arrastrar y soltar para reordenar (v1 usa botones de subir y bajar).

### Definición de hecho
- Todos los AC con un test; casos borde con test; el original no cambia nunca por lo que haga una copia (y viceversa), comprobado contando filas.
- Un código ajeno, caducado, revocado o inexistente responde igual; el límite de fallos se cuenta en la BD.
- La copia no contiene ningún dato del original que no sea su contenido: test de fugas (ids, documentos, usuarios).
- Menores: filtro en la copia y en lo que escriben; avisos en el panel del adulto.
- Verificado con el stack levantado (dos usuarios, un menor, un servidor de IA simulado) y una pasada visual en el navegador.
- `security-checklist` y `llm-integration-review` pasadas (las ediciones envían títulos al modelo como datos y usan el cerrojo y el gasto de F02/F05).
- Documentación al día (README, ARCHITECTURE con ADR nuevos, CLAUDE, BACKLOG); F05 anota que regenerar una pieza hecha ahora es posible solo por petición explícita del propietario.
