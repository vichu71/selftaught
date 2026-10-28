# F16 · Pulido de «Inicio» y «Mi material» — FASE 2: Proposal Artifacts

Contrato: `sdd/specs/US-F16-pulido-inicio-material.md`. Contexto: `ARCHITECTURE.md` (ADR-1 a 93) y `PROJECT.md`. **Solo frontend**: sin backend, sin migración, sin tareas de IA y **sin librerías nuevas**.

## Ajustes a la FASE 1 que salen de leer el código

| Ajuste | Motivo | Efecto |
|--------|--------|--------|
| Los **textos de estado** de un documento no cambian (Subido, Procesando, Listo, Error); solo ganan icono | `MaterialStatus` y los tests de F03 ya fijan esos textos; cambiarlos (p. ej. «Procesándose») rompería tests sin aportar | D6 de la FASE 1 ajustada |
| El formulario de alta se pliega con **`v-show`**, no con `v-if` | Conserva el estado de los campos y el DOM que usan los tests de F01 (`create-form`, `display-name`…); oculto no se enfoca ni lo lee un lector de pantalla | Los tests de alta siguen funcionando; se añade el test de plegado |
| Arrastrar varios ficheros: se toma **el primero** | La subida de hoy es de uno en uno (`files?.[0]`); mantenerlo evita tocar el almacén | Se avisa en palabras («Se sube un fichero cada vez: he cogido el primero») |
| Los **vacíos** (cursos, menores, documentos) comparten un componente | Tres sitios con la misma idea y los mismos `data-testid` (`no-courses`, `no-students`, `no-documents`) | `ui/EmptyNote` |

## ART-001 · Stack

Sin cambios: Vue 3 + TypeScript, Lucide para los iconos (`BookOpenText`, `KeyRound`, `BarChart3`, `FileText`, `FileType`, `File`, `Upload`, `CircleCheck`, `Loader`, `TriangleAlert`, `UserRound`, ya en el paquete o equivalentes del mismo paquete), `PlantIllustration` y `Mascot` de F11, `BackLink`, `StatCard` y compañía de `ui/`. Sin nuevas dependencias (lo comprueba el test de entorno).

## ART-002 · Modelo de datos y API

**Ninguno.** Ni migración ni endpoints nuevos ni cambios en los existentes. La subida usa `material.upload(file)` tal cual (mismas comprobaciones de tipo, tamaño y vacío y mismos mensajes); el borrado, `material.remove`; el alta de menores, `api.createStudent`. `GET /api/ai/usage` y el resto no se tocan.

## ART-003 · Estructura de componentes (frontend)

```
ui/ActionCard.vue        RouterLink con forma de tarjeta: icono (componente Lucide por prop), título, descripción; foco visible,
                         min-height var(--size-tap); data-testid por atributos                                                       AC-1
ui/Disclosure.vue        botón con aria-expanded + región con id; v-model:open; la región se oculta con v-show; Enter y Espacio    AC-3
ui/EmptyNote.vue         ilustración (planta de F11 o mascota) + mensaje amable; data-testid por atributos                          AC-8
components/StudentCard.vue  (nace de StudentsPanel) props: student; emite reset(id) y toggle(id): nombre, etiqueta de estado con icono
                         («Activo» / «De baja»), identificador como texto y botones; los data-testid de siempre                       AC-2
components/StudentsPanel.vue  compone StudentCard, el cambio de contraseña y el alta dentro de un Disclosure («Añadir un menor»,
                         abierto si no hay menores); misma lógica                                                                   AC-2, AC-3
material/DropZone.vue    la zona de subida: <label for> grande con icono, texto, tipos y tamaño máximo y «Elegir fichero»;
                         <input type="file" class="visually-hidden" data-testid="file-input"> real; arrastrar y soltar
                         (dragenter/over/leave/drop) con clase is-over; emite choose(File); disabled mientras se sube              AC-5
components/MaterialList.vue   documentos como tarjetas: icono por tipo, nombre, «PDF · 1,2 MB», MaterialStatus con icono, motivo del
                         error, «Borrar» con la confirmación de hoy; los data-testid de F03                                          AC-6, AC-7
components/MaterialStatus.vue mismos textos; icono por estado (CircleCheck, Loader, TriangleAlert) y clase `status-*`            AC-6, AC-10
views/HomeView.vue       «Tu espacio de estudio» → tres ActionCard (Mi material, Tengo un código, Resultados); EmptyNote sin cursos AC-1
views/MaterialView.vue   DropZone + estados (UiState) + lista + material de los menores                                            AC-5, AC-7, AC-8
views/ResultsView.vue    «Tus peques»: botón «Ver actividad» (RouterLink class ui-button) a /alumnos/{id}                          AC-4
```
- Los `data-testid` de F01, F03 y F15 se conservan; los **atributos nuevos** son `results-minor-activity-{id}`, `drop-zone`, `add-student-toggle` y `shortcut-grid`.
- **Teclado y lector de pantalla:** el `<input type="file">` sigue siendo el control (visualmente oculto con `.visually-hidden`, no `display:none`); la zona es su `<label>`, así que Enter y Espacio abren el selector y el foco se ve en la zona (`:focus-within`). Arrastrar y soltar es solo una mejora.
- **Soltar un fichero:** se llama a `choose(primero)`; si hay varios, un aviso en palabras con `role="status"`. Fuera de la zona no pasa nada (los eventos se detienen dentro). Con `uploading`, la zona queda desactivada (`aria-disabled`) y dice «Subiendo…».
- **Texto plano:** nombres de documentos, de cursos y de menores siempre como texto; los nombres largos se parten (`overflow-wrap: anywhere`).
- **CSS** (`components.css`, con tokens; sin colores nuevos): `.action-card`, `.shortcut-grid`, `.disclosure`, `.empty-note`, `.student-card`, `.drop-zone` (con `.is-over` y `.is-disabled`), `.doc-card` (la clase `.visually-hidden` ya existe en `base.css`); objetivos con `var(--size-tap)`; animaciones solo dentro de `prefers-reduced-motion: no-preference`; rejillas que se apilan a 360–430 px.

## ART-004 · Estructura de carpetas

```
frontend/src/ui/{ActionCard.vue, Disclosure.vue, EmptyNote.vue}
frontend/src/material/DropZone.vue
frontend/src/components/{StudentCard.vue (nuevo), StudentsPanel.vue, MaterialList.vue, MaterialStatus.vue (cambian)}
frontend/src/views/{HomeView.vue, MaterialView.vue, ResultsView.vue (cambian)}
frontend/tests/ui/{ActionCard,Disclosure,EmptyNote}.spec.ts · tests/material/DropZone.spec.ts
frontend/tests/components/{StudentCard,MaterialList}.spec.ts · tests/views/{HomeView,MaterialView,ResultsView}.spec.ts (ampliados)
```

## ART-005 · Wireframes

```
INICIO (adulto tutor)
┌────────────────────────────────────────────────────────────────────┐
│ Hola, Ana                                                           │
│ ┌ Siguiente parada ───────────────────────────────────────────────┐ │
│ │ 🌱  Capítulo 1 · Volcanes · Tema 1            [ Seguir leyendo → ] │ │
│ └──────────────────────────────────────────────────────────────────┘ │
│ Tus cursos            [ Seleccionar ]                                │
│ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   [ Nuevo curso ]               │
│ Tu espacio de estudio                                                │
│ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐            │
│ │ 📖 Mi material │ │ 🔑 Tengo un    │ │ 📊 Resultados  │  ← enlaces │
│ │ Sube tus       │ │    código      │ │ Cómo van tus   │    enteros │
│ │ apuntes        │ │ Recibe tu copia│ │ cursos y gasto │            │
│ └────────────────┘ └────────────────┘ └────────────────┘            │
│ Mis menores                                                          │
│ ┌ Hugo  [✔ Activo] · usuario: hugo ─────────────────────────────┐   │
│ │ [ Ver actividad ] [ Cambiar contraseña ] [ Dar de baja ]       │   │
│ └────────────────────────────────────────────────────────────────┘   │
│ [ + Añadir un menor ]  ← abre el formulario (abierto si no hay menores)│
└────────────────────────────────────────────────────────────────────┘

MI MATERIAL
┌────────────────────────────────────────────────────────────────────┐
│ Mi material                                              [ ← Inicio ] │
│ ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ │
│   ⬆  Arrastra un documento aquí o                                    │
│      [ Elegir fichero ]   PDF, Word (.docx) o texto · máx. 10 MB      │
│ └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘ │
│ Mis documentos (2)                                                   │
│ ┌ 📄 apuntes-tema1.pdf   PDF · 1,2 MB   [✔ Listo]    [ Borrar ] ┐    │
│ ┌ 📝 resumen.docx        Word · 80 KB   [⏳ Procesando]          ┐    │
│ ┌ ⚠ roto.pdf  PDF · 3 KB [⚠ Error]  Está protegido con contraseña ┐   │
│ Material de mis menores · solo lectura                               │
└────────────────────────────────────────────────────────────────────┘
```

## ART-006 · ADR

### ADR-94: Atajos, plegables y estados vacíos son componentes de `ui/`; la zona de subida mejora el `<input type="file">` sin sustituirlo
- Motivo: tres pantallas repiten la misma idea (un enlace con frase, un bloque que se abre y se cierra, un «todavía no hay nada»): `ActionCard`, `Disclosure` y `EmptyNote` la dejan en un sitio con el estilo y los tokens, igual que `BackLink` y `StatCard` (ADR-93). Los cambios son de **forma** y conservan los `data-testid`, los textos legales al menor y la lógica de subida, borrado y alta. La zona de subida es una `<label>` del `<input type="file">` real, que sigue siendo el único control (teclado, lector de pantalla, selector del sistema); arrastrar y soltar solo entrega el primer fichero a la misma subida, con las mismas comprobaciones: no hay un segundo camino de subida que probar ni que proteger. El formulario de alta se oculta con `v-show` para no perder lo escrito.
- Alternativas descartadas: sustituir el `<input>` por una zona que no sea su etiqueta (se pierde la accesibilidad); una librería de subida con arrastrar y soltar (dependencia nueva para un gesto); `v-if` en el formulario (se pierde lo escrito al plegarlo y cambia el DOM que prueban los tests de F01); cambiar los textos de estado (rompe tests sin aportar).
- Revisitar si: se quiere subir varios ficheros a la vez o ver una vista previa (entonces cambia el almacén y la API).
