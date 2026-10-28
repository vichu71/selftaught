# F16 — Informe de pruebas (Inicio y Mi material)

## Resultado
- Frontend: **972/972** y `vue-tsc` sin errores (911 que ya existían, todos en verde sin tocarlos, más 61 de F16: `ActionCard`, `Disclosure`, `EmptyNote`, `DropZone`, `StudentCard`, `MaterialList`, Inicio, Mi material, el botón «Ver actividad» y el diseño).
- Entorno (`scripts/tests`): **57/57**.
- Backend: sin cambios (F16 es solo frontend), no se repitió su suite.
- Pasada en el navegador con el stack de demostración aislado (proyecto `studylab-demo`, variables ficticias fuera del repositorio, IA de desarrollo): escritorio 1280 px y móvil 390 y 360 px, como **tutora con dos menores**, como **adulto sin menores** y como **menor**; subida real de ficheros con arrastrar y soltar, borrado con confirmación y alta plegable.
- Ningún test llama a una API de pago. No se tocó el stack real ni el `.env` real.

## Resultados por criterio de aceptación

| AC | Descripción | Test | Estado |
|----|-------------|------|--------|
| AC-1 | Tres tarjetas-atajo que son el enlace entero, con 44 px y sin subrayado; la de Resultados sin dinero para un menor | `ActionCard.spec`, `InicioF16.spec`, navegador | ✅ |
| AC-2 | Cada menor es una tarjeta con estado en palabras e icono, usuario y botones; las acciones siguen funcionando | `StudentCard.spec`, `InicioF16.spec`, `StudentsPanel.spec`, navegador | ✅ |
| AC-3 | Alta plegada en «Añadir un menor» (abierta sin menores), con `aria-expanded`; mismos campos, validaciones y mensajes | `Disclosure.spec`, `InicioF16.spec`, navegador | ✅ |
| AC-4 | «Ver actividad» en «Tus peques» a `/alumnos/{id}` | `ResultsView.spec`, navegador (abre «Actividad de Hugo») | ✅ |
| AC-5 | Zona de subida: es la etiqueta del `<input type="file">` real; elegir y arrastrar usan la misma subida y los mismos errores; estados en palabras | `DropZone.spec`, `MaterialF16.spec`, navegador | ✅ |
| AC-6 | Documentos como tarjetas con icono por tipo, estado con icono, motivo del error y borrado con confirmación | `MaterialList.spec`, `MaterialF16.spec`, `MaterialView.spec`, navegador | ✅ |
| AC-7 | Material de los menores con las mismas tarjetas en solo lectura; avisos al menor sin cambios | `MaterialF16.spec`, `HomeView.spec`, navegador | ✅ |
| AC-8 | Vacíos amables con ilustración; errores en palabras | `EmptyNote.spec`, `InicioF16.spec`, `MaterialF16.spec`, navegador | ✅ |
| AC-9 | Nombres de documentos, cursos y menores como texto, también hostiles y largos | todos los de componentes y vistas | ✅ |
| AC-10 | Colores solo de `tokens.css`, estado nunca solo con color, 44 px, movimiento solo con `no-preference`, sin desbordamiento a 360–430 px, teclado | `tests/design/home-material.spec`, navegador | ✅ |
| AC-11 | Sin regresiones: F01, F03, F08 y F15 en verde | suite completa (911 que ya existían, sin cambios) | ✅ |

## Casos borde verificados en el navegador
- Tutora con 2 menores: alta plegada (`aria-expanded="false"`, formulario oculto), se abre y se cierra con el botón; adulto sin menores (Luis): nota amable con la mascota y alta abierta; menor (Hugo): sin panel de menores, con su aviso y sin «Material de mis menores».
- Soltar un `.exe` → «Tipo no admitido. Usa PDF, Word (.docx) o texto (.txt, .md).»; un fichero vacío → «El fichero está vacío.»; dos ficheros → se sube el primero y se avisa («Se sube un fichero cada vez: he cogido el primero.»); el segundo no se sube.
- Un `.txt` soltado se sube, se procesa y sale «Listo» con su icono, tipo y tamaño; «Borrar» pide confirmación, «Cancelar» lo deja y «Sí, borrar» lo borra.
- El `<input type="file">` real sigue en la página (visualmente oculto, no `display:none`), recibe el foco con el teclado y la zona dibuja su anillo de foco; «Elegir fichero» es su etiqueta.
- Móvil (390 y 360 px): sin desbordamiento horizontal en Inicio y Mi material, ningún objetivo táctil por debajo de 44 px, las tarjetas-atajo se apilan, la zona de subida, «Inicio» y los enlaces de la barra inferior son pulsables (`elementFromPoint`).

## Revisión de seguridad (security-checklist)
Bien: es solo frontend, sin endpoints, sin migración y sin dependencias nuevas; la subida usa **la misma** `material.upload` que antes (tipo por extensión en cliente, tamaño y vacío, y el servidor sigue decidiendo el tipo por contenido, F03), y arrastrar y soltar no abre un segundo camino: entrega el primer fichero a esa subida, con los mismos errores; el fichero arrastrado no se ejecuta ni se lee en el navegador (solo se envía); el `<input>` real conserva `accept`; nombres de documentos, cursos y menores se pintan siempre como texto (sin `v-html`, también hostiles y largos); «Ver actividad» solo es un enlace, y `/alumnos/{id}` sigue exigiendo ser su tutor (404 si no); los avisos legales al menor (`guardian-notice`, `material-minor-notice`) no cambian de texto ni de sitio y un menor sigue sin ver el panel de menores; el borrado de documentos sigue pidiendo confirmación.
Pendiente / decisiones aceptadas:
- 🟡 Se sube un fichero cada vez (F16-1); sin vista previa ni renombrar (F16-2).
- No comprobable desde aquí: HTTPS, cabeceras y puertos del despliegue real (siguen en `sdd/BACKLOG.md`, D1–D6 y F14-1).

## Revisión de la integración con IA
No aplica: no llama a ningún modelo.

## Cambios de la FASE 7
- Ninguno en el código (la pasada visual no encontró nada que corregir).

## Issues encontrados
- Ninguno.
