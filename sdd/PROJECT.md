# PROJECT.md — Study Lab (nombre provisional)

> Contexto común a todas las features. Claude Code lo lee SIEMPRE antes de cualquier feature
> (ver SDD_PROCESS.md). Lo que está aquí manda sobre suposiciones. Si algo contradice una feature,
> se pregunta al usuario.

## 1. Visión

Web de ayuda al estudio con IA. El usuario escribe qué quiere aprender ("inglés de 2º de la ESO") y
la IA le monta un curso: temas, capítulos, lecciones, ejercicios y un examen por tema. Es un
laboratorio personal/familiar, **no** formación reglada: sin diplomas, sin currículo oficial, sin
supervisión académica. Aun así, la calidad del contenido debe ser lo mejor posible.

Referencia de UX (solo inspiración, no copiar): platform.learningviable.com (AEDIA). De ahí se toma
la idea de panel de progreso; en v1 solo lo mínimo.

## 2. Usuarios y roles

| Rol | Descripción |
|-----|-------------|
| Alumno | Crea y estudia cursos. Puede ser un menor sin conocimientos técnicos (usuario clave). |
| Tutor (adulto) | Da de alta a los menores desde su cuenta y queda vinculado a ellos. Ve todo lo de sus menores (cursos, progreso, intentos y conversaciones con el tutor IA), en solo lectura. Todo menor tiene siempre un tutor asociado. |

Un mismo adulto puede ser alumno y tutor. No hay rol admin global en v1 (la configuración global
va por variables de entorno).

## 3. Principios de diseño

1. **Simplicidad extrema para el alumno**: de la caja de texto a un curso en menos de un minuto, sin
   jerga técnica. Preguntas con opciones por defecto y un "como tú veas".
2. **Laboratorio, no academia**: autoevaluación, no acreditación. Todo contenido lleva aviso de
   "generado por IA" y, si hay material del usuario, citas a la fuente.
3. **Coste controlado**: el gasto en IA se mide, se limita y nunca se desborda en silencio.
4. **Privacidad del menor**: se guardan los mínimos datos personales posibles.
5. **Nunca regenerar lo que ya existe**: el curso se guarda y se reutiliza.
6. **Preparado para crecer, sin construir un SaaS**: multiusuario y compartir por código sí; pagos,
   planes y facturación no.

## 4. Stack (decidido)

| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Frontend | Vue 3 + TypeScript (web responsive) | Elección del usuario |
| Backend | Spring Boot (Java) | Elección del usuario; Spring AI cubre varios proveedores y pgvector |
| Base de datos | PostgreSQL + pgvector | Relacional + búsqueda vectorial para RAG en un solo sistema |
| Embeddings | Modelo local vía Ollama | Coste cero en indexado |
| Despliegue | Docker Compose | Puertos internos (backend, BD) no expuestos a Internet |

Generación de cursos: tareas asíncronas con progreso en vivo (SSE o WebSocket); nunca una petición
HTTP que espera minutos.

## 4b. Docker y operación por Claude

El proyecto debe poder construirse, arrancarse, probarse y diagnosticarse **solo con Docker**, para que
Claude pueda operar el entorno (si el usuario se lo concede) sin instalar nada en la máquina.

- Todo corre en Docker Compose: frontend, backend, PostgreSQL + pgvector y Ollama. En el host solo
  hace falta Docker (y Git).
- Un comando levanta el entorno completo: `docker compose up -d --build`. Perfiles: `dev` (recarga en
  caliente, proveedor de IA falso por defecto, datos de ejemplo) y `prod`.
- Healthchecks en todos los servicios y `depends_on` con `condition: service_healthy`; un comando
  simple debe poder decir si el entorno está sano.
- **Tests ejecutables en contenedor** con un comando por parte (backend, frontend), contra la BD del
  perfil `test` de Compose. Sin Testcontainers ni acceso al socket de Docker desde contenedores.
- Logs a stdout, estructurados, consultables con `docker compose logs`. Sin secretos ni datos
  personales en logs.
- Scripts estándar (Makefile o `scripts/`, nombres fijados en F01): `up`, `down`, `logs`,
  `test-backend`, `test-frontend`, `seed`, `reset-dev-db`.
- Seguridad del entorno: puertos publicados solo en `127.0.0.1`; backend y BD solo en la red interna
  de Compose salvo en `dev`; contenedores sin `privileged` y sin montar `docker.sock`; procesos no
  root; datos en volúmenes con nombre (Postgres, modelos de Ollama).
- Secretos: `.env` en el host fuera del repositorio (Claude no lo lee ni lo escribe) y `.env.example`
  documentado; Compose los inyecta. Claude puede operar el stack sin ver ningún secreto.
- Operaciones destructivas (`down -v`, borrar volúmenes, `system prune`) solo las decide el usuario.
- Ollama en contenedor con el modelo de embeddings; GPU opcional.

## 5. IA: modelos y control de gasto

- **Capa de proveedor intercambiable**; el modelo se configura **por tarea**, sin tocar código.
- Estrategia por niveles (el modelo concreto se decide con un mini benchmark en la feature F02):

| Tarea | Necesidad | Nivel |
|-------|-----------|-------|
| Ficha de curso y temario | Máxima calidad, poca salida | Top |
| Lecciones, ejercicios, exámenes | Buena calidad, mucha salida | Medio o top (batch si el proveedor lo permite) |
| Tutor (chat) | Rápido, barato, fiable | Medio / barato |
| Embeddings | Barato y constante | Local (Ollama) |

- **Controles obligatorios**: registro de tokens y coste por llamada, curso y usuario; tope de gasto
  mensual configurable con corte automático; límite de tokens de salida por llamada; **una sola
  generación concurrente por usuario**; caché de prompts y batch cuando aplique. No hay límite diario
  de cursos (decisión del usuario), solo estos topes.
- Las llamadas a IA en tests siempre usan un proveedor falso.
- Antes de elegir proveedor se revisa su política de datos (menores).

## 6. Modelo de dominio (preliminar, a refinar en cada feature)

- **User** (rol, email, datos mínimos) · **Guardianship** (tutor ↔ alumno menor)
- **Course** (propietario, título, ficha, estado, `origin_id`, `version`, código de compartir)
- **Topic → Chapter → Lesson** (contenido en Markdown sanitizado) · **Exercise** · **Exam / Question**
- **Attempt** (intentos de ejercicios y exámenes) · **Progress**
- **SourceDocument** (material subido, privado) · **SourceChunk** (fragmento + embedding)
- **GenerationJob** (estado, progreso, coste) · **AiUsage** (tokens y coste por llamada)
- **ShareCode** (código, curso, caducidad, revocado) · **TutorMessage**

El curso se modela como un documento estructurado y versionado exportable a JSON (esquema con
número de versión). Un curso exportado **nunca** incluye el material original subido ni datos del
usuario.

## 7. Compartir cursos

Compartir por **código**. Quien lo introduce recibe **su propia copia** (fork), editable —incluido el
temario— sin afectar al original. Sin edición colaborativa en v1. El curso guarda `origin_id` y
`version` para poder avisar de versiones nuevas en el futuro. El material subido (RAG) es siempre
privado de su propietario.

## 8. Seguridad transversal (reglas que aplican a TODAS las features)

- Todo endpoint comprueba **quién** puede llamarlo; todo dato se filtra por propietario. Un tutor
  solo ve a sus alumnos asociados y solo en lectura.
- Validación en el **backend** de toda entrada (rangos, longitudes, nulos, tipo y tamaño de fichero).
- SQL siempre parametrizado. Prohibido `v-html`/`innerHTML` con datos de usuario o de IA: el contenido
  de lecciones es Markdown/JSON que se renderiza **sanitizado**.
- Secretos (API keys de IA, JWT, contraseñas) solo en variables de entorno del backend. Si falta un
  secreto obligatorio, la app falla al arrancar. Nunca un valor por defecto inseguro.
- Login con límite de intentos; HTTPS en despliegue.
- Subida de ficheros: lista de tipos permitidos (PDF, Word, texto), tamaño máximo, nombre de fichero
  saneado, nunca ejecutar contenido subido, el texto extraído se trata como **datos, no como
  instrucciones** (defensa ante prompt injection en el RAG).
- Menores: datos personales mínimos; filtrado de temas y de respuestas inapropiadas (también en el
  tutor IA); el tutor adulto ve toda la actividad del menor y el menor lo sabe (aviso en la interfaz).
- Al terminar cada feature que toque estos puntos: skill `security-checklist`.

## 9. Fuera de alcance de v1 (todo el proyecto)

Pagos, planes, facturación, SaaS público, moderación a escala, gamificación (medallas, rachas),
calendario, edición colaborativa, sugerir cambios al autor, materiales que no sean PDF/Word/texto
(enlaces web, vídeo, audio), diplomas o certificados, app móvil nativa.

## 10. Mapa de features (orden recomendado)

| ID | Feature | Depende de |
|----|---------|-----------|
| F01 | Base técnica, cuentas y roles | — |
| F02 | Capa de IA y control de gasto | F01 |
| F03 | Material propio (subida, indexado RAG) | F01, F02 |
| F04 | Asistente de creación de curso | F01, F02 |
| F05 | Generación del curso | F02, F04 (y F03 si hay material) |
| F06 | Estudio: lecciones, ejercicios, exámenes, progreso | F05 |
| F07 | Tutor IA por lección | F02, F06 (y F03) |
| F08 | Panel del adulto tutor | F01, F06 |
| F09 | Edición de temario y compartir por código | F05, F06 |

Cada feature se ejecuta con el ciclo de SDD_PROCESS.md en su propia rama.

## 11. Preguntas abiertas (no bloquean el arranque)

- Mini benchmark de modelos (F02): 3 temas de prueba muy distintos (p. ej. inglés 2º ESO, IA,
  una materia práctica) con 3-4 proveedores, comparando calidad y coste real por curso.
- Nombre definitivo del producto.
- Filtrado de contenido para menores: nivel de detalle (a concretar en F04/F07).
- Ejercicios con solución comprobable (matemáticas/ciencias): valorar motor de cálculo frente a
  rúbrica del LLM (a concretar en F06).
