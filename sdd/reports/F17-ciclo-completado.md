# F17 · Alegría y colorido — ciclo SDD completado (FASE 8)

Fecha de cierre: 2026-10-29. Rama `feature/F17-alegria-color` (**sin fusionar en `main` y sin subir**).

## Qué se entrega

Cada zona de la app tiene su color (Mapa verde, Mi material azul, Resultados ámbar, Propietario violeta; el naranja queda para el botón primario). La barra superior y la inferior de móvil llevan un icono en burbuja de color por destino, marcan el sitio actual con relleno y sombra gruesa, y saludan por el primer nombre. Once pantallas estrenan `PageHeader`; `UiTag` dice los estados con icono; hay pequeños detalles de alegría (gesto de la mascota, destello de la planta al 100 %, «¡Hecho!» con confeti, saludo por franja del día) que existen solo con movimiento permitido.

## Fases

| Fase | Resultado | Commit |
|------|-----------|--------|
| 1–4 | Historia (12 AC), propuesta (ADR-95 a 97) y 126 tests nuevos (78 fallaban) | `d2223ca` |
| 5 | ARCHITECTURE, README, CLAUDE y BACKLOG | `3e753b1` |
| 6 | Código | ver `git log` |
| 7–8 | Informe de pruebas y este cierre | ver `git log` |

## Decisiones que cambiaron respecto a la propuesta

- La burbuja de zona de los enlaces y las páginas lee `--zone-*`; el relleno del enlace activo invierte los colores de la burbuja para que siga viéndose.
- El chip de saludo se oculta entero en móvil (la regla antigua dejaba una píldora vacía).
- El saludo por franja del día vive en una línea aparte en Inicio (`home-daypart`); el `h1` «Hola, …» no cambia para no romper los tests de F01 y F10.

## Cifras

Frontend 1098 / 1098 · compilación y `vue-tsc` limpios · entorno 57 / 57 · backend sin cambios (1487 / 1487 en F16).

## Lecciones

- Una regla de movimiento se comprueba **leyendo el CSS** (nada de `animation` ni `transition` fuera de `prefers-reduced-motion`), no probando animaciones.
- Un comentario que nombra una API prohibida (`v-html`) también salta en la prueba de política: no se nombra.
- Ocultar solo el texto de un chip deja el chip vacío: se oculta el contenedor. Se vio solo en el navegador a 390 px, no en los tests de DOM.
- `docker compose up --no-deps frontend` necesita `--profile dev` cuando se combinan `compose.yaml` y `compose.dev.yaml`.

## Pendiente

BACKLOG F17-1 a F17-3, y las notas de despliegue D1–D6 y F14-1. Sin fusionar ni subir: lo decide el usuario. El stack de demostración (`studylab-demo`, puerto 18080) sigue levantado con el código de F17.
