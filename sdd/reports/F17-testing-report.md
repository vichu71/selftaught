# F17 · Alegría y colorido — informe de pruebas (FASE 7)

Fecha: 2026-10-29. Rama `feature/F17-alegria-color`. **Solo frontend**: sin backend, migración ni librerías.

## Resultados

| Suite | Resultado |
|-------|-----------|
| Frontend (Vitest, en contenedor) | **1098 / 1098** (88 ficheros; 126 tests nuevos de F17 sobre los 972 anteriores) |
| Compilación (`vue-tsc --noEmit` + `vite build`, en la imagen del frontend) | sin errores |
| Entorno (`scripts/tests/run-env-tests.sh`) | **57 / 57** (la lista de dependencias exactas no cambia) |
| Backend | no se ha tocado; se mantiene el último resultado de F16 (1487 / 1487) |

Los tests de F17 se escribieron **antes** del código (FASE 4: 78 fallaban) y pasan todos tras la FASE 6. El único fallo intermedio fue de la prueba de política de seguridad (`never uses v-html or innerHTML`): un comentario de `greeting.ts` nombraba `v-html`; se reformuló.

## Criterios de aceptación

| AC | Cómo se comprueba |
|----|-------------------|
| AC-1 tokens de zona | `design/f17.spec.ts` (3 tonos × 4 zonas, claro y noche, rellenos distintos) y `design/tokens.spec.ts` (ningún color fuera de `tokens.css`) |
| AC-2 contraste | `design/contrast.spec.ts` ampliado: 16 parejas por esquema (texto sobre relleno; tono de texto sobre superficie, papel y cielo) en claro y noche |
| AC-3 barra | `layouts/NavBarF17.spec.ts`: burbuja con icono y `data-zone` por enlace, `aria-current` solo en el activo, testids conservados |
| AC-4 saludo | `layouts/greeting.spec.ts` y `NavBarF17.spec.ts`: primer nombre, vacío, 80 caracteres, HTML como texto, sin importes |
| AC-5 movimiento | `design/f17.spec.ts`: ninguna `animation`, `transition` ni `@keyframes` fuera de `prefers-reduced-motion` |
| AC-6 móvil | Navegador a 390 px: 4 enlaces de 91 × 52 px, `elementFromPoint` acierta en los cuatro, sin desbordamiento (`scrollWidth` 390) |
| AC-7 acento por página | `layouts/zone.spec.ts`, `AppShell · zone` y pasada en el navegador (Inicio verde, Material azul, Resultados ámbar, Propietario violeta) |
| AC-8 pantallas migradas | `views/ZonasF17.spec.ts`: 11 vistas con `PageHeader`; los tests de F01–F16 de esas pantallas siguen en verde sin cambios |
| AC-9 estados con forma | `ui/UiTagF17.spec.ts`: `ok`, `warn`, `bad` e `info` llevan icono |
| AC-10 alegría medida | `ui/Celebration.spec.ts`, `views/ZonasF17.spec.ts` (celebra al marcar, no al abrir una lección hecha, ni al desmarcar, ni si falla) y navegador (12 piezas de confeti, texto «¡Hecho!») |
| AC-11 texto plano y privacidad | Nombres hostiles como texto en barra y cabecera; sin `v-html` en ninguna vista; un menor no ve importes en la barra ni en Inicio |
| AC-12 sin regresiones | 972 tests anteriores intactos; dependencias sin cambios (entorno 57 / 57) |

## Pasada en el navegador (stack de demostración, código de F17)

Escritorio 1280 × 800 y móvil 390 × 844, con la cuenta propietaria: Inicio, Mi material, Resultados, Panel del propietario, Tengo un código y una lección.

Encontrado y corregido durante la pasada:
- **Chip de saludo vacío en móvil:** una regla antigua ocultaba solo el texto del nombre y dejaba una píldora verde vacía junto a los botones. Ahora se oculta el chip entero por debajo de 48 rem (el Inicio ya saluda con el `h1`).
- **Burbuja de la cabecera desalineada** en pantallas estrechas, cuando el título ocupa dos líneas: ahora se alinea arriba.

Sin hallazgos en: contraste visible de las burbujas y el relleno activo, barra inferior tapada por el contenido, desbordamiento horizontal, foco ni objetivos de 44 px.

## Revisión de seguridad (`security-checklist`, solo frontend)

- **Autorización y backend:** sin endpoints nuevos ni cambios en rutas; la guarda `ownerOnly` y las respuestas 404 no se tocan. El enlace «Propietario» sigue siendo comodidad del menú; el permiso lo da el servidor.
- **Entrada y salida:** nombres de personas, cursos y documentos salen como texto; el saludo recorta a 20 caracteres y escapa por plantilla. La prueba de política recorre `src/` y no encuentra `v-html` ni `innerHTML`.
- **Menores:** la barra y el Inicio no muestran dinero, topes ni datos de otros usuarios a un menor (probado); el aviso del tutor y el de material no cambian.
- **Secretos y dependencias:** sin claves nuevas; sin librerías nuevas (el test de entorno lo confirma); el confeti es CSS y no usa `canvas`.
- **Accesibilidad:** el estado nunca va solo en color; el confeti es `aria-hidden` y el texto «¡Hecho!» va en `role="status"`.
- **No comprobable desde el código:** despliegue (HTTPS, puertos) sigue pendiente como en F16 (BACKLOG D1–D6).

`llm-integration-review` no aplica (sin IA).

## Pendiente (BACKLOG F17-1 a F17-3)

Sin tema elegible por usuario ni alto contraste; la celebración es igual para todos; las pantallas interiores (Nuevo curso, Editar, Intentos, panel del tutor) usan cabecera y tonos de zona pero no se han rediseñado a fondo. Falta además una pasada visual como **tutor con menores** y como **menor** (se comprobó por tests, no a ojo en el navegador).
