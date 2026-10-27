# F12 — Informe de pruebas (ilustraciones de portada generadas por IA)

## Resultado
- Backend: suite completa en verde (1323 tests, 0 fallos, 4 omitidos) tras la FASE 6; repetida tras los cambios de la FASE 7 (1323 tests, 0 fallos).
- Frontend: 713/713 y `vue-tsc` compila. Entorno: 54/54. Ningún test llama a una API de pago (proveedor falso).
- Verificación con el stack (proyecto aislado `studylab-f03`, servidor simulado de imágenes con el adaptador de OpenAI por HTTP real): **25/25**.
- Pasada visual en el navegador (escritorio 1280 px y móvil 390 px, sin desbordamiento horizontal): interruptor «Con ilustraciones» apagado por defecto con la estimación de coste; portadas con la rotulación «Ilustración generada por IA» y su texto alternativo; planta de reserva en un tema cuya imagen falló; «Dibujar con IA» y «Quitar» en la edición.

## Verificación con el stack (servidor simulado)
Sin interruptor no hay filas ni llamadas al proveedor; con interruptor, una imagen `DONE` por tema y el curso sigue `READY`. La imagen sale como JPEG ≤ 300 kB, ≤ 768 px y sin metadatos, con `Cache-Control: private, no-cache`, ETag (304) y `nosniff`. La clave va en la cabecera, nunca en la URL; el modelo sale de la configuración; el prompt es la plantilla más el motivo entre comillas.
Entrada hostil del proveedor: HTML con script y una cabecera de 30000×30000 px acaban en `FAILED` con una sola llamada, sin reintento y sin mostrar el mensaje del proveedor. Quitar una imagen no la recrea el hilo; el límite de regeneraciones por tema responde 429 `REGENERATION_LIMIT`. Ajeno o inexistente: 404; sin sesión: 401; sin CSRF: 403. La copia de un curso compartido lleva las imágenes `DONE` y nace con `illustrations = false`. Los logs del backend no contienen prompts, títulos, claves ni mensajes del proveedor.

## Revisión de seguridad (security-checklist)
Bien: todos los endpoints exigen sesión y filtran por propietario (`owner_id` en cada consulta de `course_image`); menores → 404; SQL parametrizado; sin `v-html` (imagen por `<img>` del mismo origen, CSP `img-src 'self'` ya lo permite); clave solo en el backend; límites por usuario en la BD (20 imágenes/h, 3 regeneraciones por tema, y el límite de ediciones con IA de F09 al regenerar); una sola generación por usuario con `GenerationLock`; la imagen del proveedor se trata como no fiable (tipo por contenido, bytes y píxeles acotados antes de decodificar, re-codificada).
Corregido en la FASE 7:
- 🟠 Una imagen quitada mientras se generaba podía reaparecer como `DONE` (`saveDone`/`saveEnded` no miraban el estado). Ahora solo escriben sobre filas `PENDING`.
- 🟡 La imagen duplicaba `X-Content-Type-Options`; se quita el de la imagen (lo añade Spring Security).
Pendiente / no comprobable desde aquí:
- 🟡 Ya existente (F01): `X-Content-Type-Options: nosniff, nosniff` en todas las respuestas porque nginx y Spring Security lo añaden a la vez. Inofensivo; limpiar en el despliegue.
- Los adaptadores reales de OpenAI y Gemini solo están comprobados contra la documentación: lo confirma `make benchmark-images` (lo lanza el usuario con sus claves; BACKLOG F12-1/F12-2). Aún no hay modelo de imagen con precio en `ai-pricing.properties`.

## Revisión de la integración con IA (llm-integration-review)
OK: llamada solo por `AiGateway` (`ImageArchitectureTest`); modelo y proveedor por configuración; cada llamada registra tarea, modelo, coste y usuario (nunca contenido); tope mensual propio por usuario que corta y avisos de gasto; precio por imagen obligatorio para arrancar con un modelo; cobro aunque la imagen venga rota; reintentos acotados (`FAILED`/`BLOCKED` no se reintentan solos); lo ya hecho no se regenera sin petición; tests con proveedor falso. Menores: no generan ni reciben imágenes (servidor y copia). La imagen ambienta, no enseña hechos, y va rotulada como generada por IA. NO APLICA: separación datos/instrucciones por mensajes (un modelo de imagen solo recibe un prompt): el motivo va saneado, entre comillas y declarado como descripción, no instrucción.

## Cambios de la FASE 7
- `ImageRepository`: `saveDone` y `saveEnded` solo sobre `PENDING`.
- `IllustrationController`: sin cabecera `nosniff` propia.
- `IllustrationTestBase`: cada test parte sin filas de `course_image` ni cupo `AI_IMAGE` de otros tests (dos fallos por contaminación entre clases al ejecutar un subconjunto).
- `IllustrationLimitsTest.ac6_…`: asumía que la imagen que espera por el cupo era la del tema añadido, pero las filas nacen con el mismo `created_at` y las ordena el id (aleatorio): fallaba 1 de cada 3 ejecuciones. Ahora comprueba «2 hechas y 1 pendiente».
