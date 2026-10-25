# F08 — Informe de pruebas (FASE 7)

Panel del adulto tutor. Rama `feature/F08-panel-tutor`. Contrato: `sdd/specs/US-F08-panel-tutor-adulto.md`; diseño y ADR-59 a 62: `sdd/specs/ART-F08-propuesta-panel.md` y `ARCHITECTURE.md`.

## Resultado

| Comprobación | Resultado |
|---|---|
| Tests del backend (contenedor, BD efímera, proveedor falso) | 1 051 ejecutados, 0 fallos, 4 omitidos (los de Ollama real opt-in de F02); 76 son del panel |
| Tests del frontend | 389 de 389; typecheck (`docker build --target build frontend`) correcto |
| Tests de entorno | 41 de 41 |
| Cobertura de líneas | 94,3 % en total (3 591/3 809), 83,8 % de ramas; el paquete `guardian` 96,6 % de líneas y 84,7 % de ramas |
| Stack real aislado (nginx → backend → BD → «modelo» simulado por HTTP, cookies y CSRF reales) | 35 comprobaciones, todas correctas |
| Navegador integrado (panel del adulto y vista de conversación, escritorio y 375 px) | Correcto, sin desbordamiento horizontal y sin ningún botón ni cuadro de escritura en el panel |

## Qué se verificó con el stack levantado

Proyecto aislado `studylab-f03` con contraseñas ficticias y un servidor que imita la API de Anthropic; el stack del usuario no se tocó. Una adulta (Ana) con dos menores (Hugo y Marta), otro adulto (Luis) con su curso, y actividad real de Hugo: preguntas al tutor
(normales, una bloqueada y una cuya respuesta se filtra), un ejercicio con autocorrección y corrección de IA, una respuesta bloqueada antes de corregir, un examen entregado, una lección hecha y un tema de curso rechazado.

- **Resumen:** Hugo y solo su curso, avance (1 de 12 lecciones) idéntico al que ve él, última actividad, 4 avisos de los 30 días, consumo del mes frente a su tope de 2 USD con reparto por tarea. Sin tokens, modelos, correo ni identificador de acceso.
- **Curso e intentos:** avance por tema y nota del examen; enunciado, respuesta, autocorrección y veredicto de la IA; preguntas de examen falladas. Ni texto de lecciones ni soluciones ni explicaciones.
- **Conversaciones y avisos:** un hilo de 8 mensajes en orden con sus marcas (`BLOCKED`, `FILTERED`) y lo que el alumno escribió; los avisos de las cuatro fuentes, del más reciente al más antiguo; el tema rechazado lleva su texto y los de tutor apuntan al hilo sin repetir la pregunta. Los avisos de Marta son solo de Marta.
- **Vínculo y permisos:** otro adulto recibe 404 en los seis endpoints y un alumno ajeno responde igual que uno inexistente; un menor recibe 403 (también en el suyo); sin sesión se rechaza; un curso o una lección de otro alumno (de Marta o de Luis) por el panel de Hugo da 404; un id mal formado, 404.
- **Solo lectura:** `POST`, `PUT`, `PATCH` y `DELETE` sobre los seis caminos devuelven 4xx (ninguno 5xx) y el hilo del alumno queda intacto; ni Hugo ni su adulto pueden vaciarlo por la API del chat.
- **Alumno dado de baja:** se sigue pudiendo consultar.
- **Auditoría y logs:** 8 `GUARDIAN_VIEW` (intentos, conversaciones e hilo, con ids de curso y pieza) y 16 `ACCESS_DENIED`; ninguna fila de auditoría lleva texto del alumno y el log del backend no tiene respuestas, preguntas, lecciones ni «bomba».

Visto en el navegador y corregido: con el stub el gasto salía como «0,00 USD» (mostraba cero un gasto de milésimas) y «1 llamadas». Ahora un gasto por debajo de un céntimo se dice «menos de 0,01 USD» y se distingue singular y plural.

## Lo que NO se ha podido verificar

- **Captura de pantalla:** el panel del navegador estaba oculto durante la pasada, así que se comprobó por el DOM (texto, enlaces, controles y anchura de página), no por captura. El aspecto visual fino queda para F10 (mejora del frontend).
- **Gasto con un modelo real:** el reparto por tarea y el tope se comprobaron con precios reales de `claude-haiku-5-5` y un servidor simulado; las cifras reales las verás con tus claves.
- HTTPS, cabeceras y puertos de despliegue: siguen en el backlog de despliegue (D1 a D6).

## Revisión `security-checklist`

Bien: los seis endpoints cuelgan de `/api/tutor/**` (solo adultos) y cada uno empieza por `AccessGuard.requireStudentOf` (lo exige `GuardianArchitectureTest`); el curso y la pieza se buscan con el **alumno** como propietario, así que no hay acceso a datos de otro alumno por id (IDOR); solo `GET`, sin SQL que escriba ni CSRF necesario;
SQL siempre parametrizado y con propietario (`owner_id`, `user_id` en el consumo, `actor_user_id` en la auditoría); respuestas sin correo, identificador de acceso, hash ni ids de otros usuarios; nada de `v-html` (el texto del alumno y del modelo se pinta como texto o con `MarkdownContent`); sin secretos nuevos ni dependencias nuevas;
cada consulta de actividad se audita con ids y fuera de transacción, y ni logs ni auditoría llevan lo que escribió el alumno; el menor ve en su pantalla que su adulto puede ver sus cursos, respuestas, intentos y conversaciones.

Pendiente o a vigilar:
- 🟡 **F8-7 (nuevo):** sin límite de frecuencia en las lecturas del panel y el contador de avisos del resumen se acota a 1 000. Son lecturas de un adulto autenticado sobre datos acotados.
- 🟡 **F8-8 (nuevo):** cada intento de un adulto sobre un alumno ajeno añade un `ACCESS_DENIED` (comportamiento de F01); un bucle haría crecer la auditoría.
- 🟡 Los avisos salen de lo guardado: si se purgan las consultas bloqueadas (F4-1) o se borra un curso, desaparecen (F8-2).
- Lo que no se puede comprobar desde el código (HTTPS, firewall, proxy con IP real): D1 a D6; comando de comprobación en el despliegue: `curl -m 5 http://IP:8080/api/tutor/students/x/overview` desde fuera debe responder 401.

`llm-integration-review`: NO APLICA, el panel no llama a ningún modelo (lo comprueba un test de arquitectura: ni `AiGateway`, ni adaptadores, ni `PromptAssembler`, ni `MaterialSearch`). No hay tarea, plantilla ni variable de IA nuevas.

## Cambios hechos durante la FASE 7

- `GuardianUsage`: gasto por debajo de un céntimo y plural de «llamada».
- `sdd/BACKLOG.md`: F8-7 y F8-8.
