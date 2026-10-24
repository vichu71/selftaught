# SDD_PROCESS.md

> Este documento instruye a Claude Code para ejecutar el ciclo completo de desarrollo
> SDD (Specification-Driven Development) partiendo de PROJECT.md (contexto común) y de un fichero de feature concreto (features/Fxx-*.md).
> Es genérico y reutilizable en cualquier proyecto.

---

## INSTRUCCIONES PARA CLAUDE CODE

Cuando el usuario te indique que empiece, debes:

1. Leer `PROJECT.md` (decisiones y reglas globales, que mandan sobre cualquier suposición) y el fichero
   de la feature indicada por el usuario (`features/Fxx-*.md`). Si no indica ninguna, propón la
   siguiente según el orden de PROJECT.md.
2. Ejecutar el ciclo SDD completo en el orden definido aquí, para UNA sola feature cada vez
3. No saltarte ningún paso
4. No escribir código hasta haber completado los pasos previos
5. Confirmar con el usuario al finalizar cada FASE antes de continuar

---

## CICLO SDD COMPLETO

```
FASE 1 → REFINED USER STORY       (enrich_us)
FASE 2 → PROPOSAL ARTIFACTS       (stack, datos, endpoints, componentes)
FASE 3 → BRANCH                   (convención de rama)
FASE 4 → TESTS                    (TDD, antes del código)
FASE 5 → DOCUMENTATION            (README, ARCHITECTURE.md, CLAUDE.md)
FASE 6 → CODE                     (implementación)
FASE 7 → TESTING REPORT           (ejecutar y documentar)
FASE 8 → PROPOSAL UPDATE          (actualizar si algo cambió)
```

---

## FASE 1 — REFINED USER STORY (`enrich_us`)

Lee la descripción de `FEATURE.md` y genera una User Story refinada con este formato exacto:

```markdown
## US-{ID} · {Título corto}

### Como {rol de usuario}
### Quiero {acción}
### Para que {beneficio}

### Criterios de aceptación

| # | Criterio |
|---|----------|
| AC-1 | ... |
| AC-2 | ... |

### Casos borde
- {caso borde 1}
- {caso borde 2}

### Fuera de alcance (v1)
- {exclusión 1}
- {exclusión 2}

### Definición de hecho
- {criterio de completitud 1}
- {criterio de completitud 2}
```

**Reglas:**
- Deduce el rol de usuario de la descripción
- Genera todos los criterios de aceptación necesarios para cubrir la feature completa
- Identifica y documenta casos borde (duplicados, vacíos, inválidos, permisos)
- Define claramente qué queda fuera de alcance para v1
- **Espera confirmación del usuario antes de continuar a FASE 2**

---

## FASE 2 — PROPOSAL ARTIFACTS

Genera los siguientes artefactos en este orden:

### ART-001 · Stack tecnológico

Si PROJECT.md ya fija el stack, no lo vuelvas a decidir: confirma que cubre la feature y documenta
solo las desviaciones o librerías nuevas. En caso contrario, propón el stack más adecuado y justifica
cada elección.
Formato:

```markdown
| Capa | Tecnología | Motivo |
|------|-----------|--------|
| Frontend | ... | ... |
| Backend | ... | ... |
| Base de datos | ... | ... |
```

### ART-002 · Modelo de datos

Define las entidades necesarias con sus campos y tipos.
Incluye relaciones entre entidades.

### ART-003 · Endpoints API

Lista todos los endpoints necesarios:
```
METHOD  /ruta     → descripción + validaciones
```

### ART-004 · Estructura de componentes

Si hay frontend, define el árbol de componentes con qué AC cubre cada uno.

### ART-005 · Estructura de carpetas

Define el árbol de carpetas completo del proyecto con comentarios.

### ART-006 · Wireframe

Dibuja en ASCII el layout principal de la interfaz.

### ART-007 · ADR (Decisiones técnicas)

Documenta cada decisión técnica relevante:
```markdown
### ADR-{N}: {Título}
- Motivo: {por qué}
- Alternativas descartadas: {qué más consideraste}
- Revisitar si: {condición}
```

**Reglas:**
- Elige el stack más simple que cubra los requisitos
- Evita sobreingeniería en v1
- **Espera confirmación del usuario antes de continuar a FASE 3**

---

## FASE 3 — BRANCH

Crea la rama de trabajo:

```bash
git init  # solo si no existe repositorio
git checkout -b feature/{US-ID}-{descripcion-corta}
```

Confirma la rama creada antes de continuar.

---

## FASE 4 — TESTS (TDD)

Escribe los tests **antes** del código.

**Reglas estrictas:**
- Un test por cada criterio de aceptación
- Un test por cada caso borde
- Los tests deben fallar en este punto. Es correcto y esperado.
- Cobertura mínima objetivo: 80%

### Backend

Usa el framework de tests del stack definido en FASE 2.
Estructura mínima:

Un fichero de tests por entidad o servicio, con fixtures compartidos, siguiendo las convenciones del
framework del stack (p. ej. JUnit + Spring Boot Test contra la BD del perfil `test` de Docker Compose en backend, Vitest en frontend).
Los tests se ejecutan en contenedor con los scripts estándar definidos en PROJECT.md (sección 4b).
Todo test que llame a un modelo de IA usa un proveedor falso (mock); nunca se llama a APIs de pago en tests.

Cada test debe:
- Tener nombre descriptivo que indique qué AC o caso borde cubre
- Comentario indicando el AC: `# AC-1`
- Ser independiente de otros tests

### Frontend

Usa el framework de tests del stack definido en FASE 2.
Testea comportamiento, no implementación.
Mockea siempre las llamadas a la API.

---

## FASE 5 — DOCUMENTATION

Genera estos tres ficheros (si ya existen por features anteriores, actualízalos sin borrar lo previo):

### README.md

```markdown
# {Nombre del proyecto}

{Descripción en 2 líneas}

## Stack
{Lista el stack}

## Arrancar en local
{Comandos exactos para backend y frontend}

## Tests
{Comandos exactos}
```

### ARCHITECTURE.md

```markdown
# Arquitectura — {Nombre del proyecto}

## Visión general
{Diagrama ASCII del flujo de datos}

## Estructura de carpetas
{Árbol completo con comentarios}

## Decisiones técnicas (ADR)
{Todos los ADR de FASE 2}
```

### CLAUDE.md

```markdown
# CLAUDE.md — {Nombre del proyecto}

## Qué es este proyecto
{Descripción breve}

## Comandos útiles
{Comandos de arranque y tests}

## Reglas de negocio importantes
{Reglas críticas que no están en el código}

## Lo que NO hacer
{Restricciones v1 del SPEC}
```

---

## FASE 6 — CODE

Implementa el código siguiendo estas reglas:

**Orden de implementación:**
1. Modelos / entidades de base de datos
2. Lógica de negocio / servicios
3. Endpoints / controllers
4. Componentes UI (de más genérico a más específico)
5. Integración frontend-backend

**Reglas de código:**
- Implementa solo lo necesario para que los tests pasen
- No añadas funcionalidad no especificada en FASE 1 o FASE 2
- Nombres descriptivos en inglés
- Funciones con una sola responsabilidad
- Manejo explícito de errores en todos los endpoints
- Validaciones en frontend (comodidad) y en backend (obligatorias, es la que cuenta)
- Todo endpoint comprueba quién puede llamarlo, y todo dato se filtra por su propietario

**Al terminar cada capa**, ejecuta los tests correspondientes antes de continuar.

---

## FASE 7 — TESTING REPORT

Ejecuta todos los tests y genera este reporte:

```markdown
## Testing Report — {fecha}

### Resumen
- Tests totales: X
- Tests pasados: X ✅
- Tests fallidos: X ❌
- Cobertura: X%

### Resultados por criterio de aceptación

| AC | Descripción | Test | Estado |
|----|-------------|------|--------|
| AC-1 | ... | test_... | ✅ |

### Casos borde verificados

| Caso borde | Test | Estado |
|------------|------|--------|
| URL inválida | test_... | ✅ |

### Revisión de seguridad

Si la feature toca endpoints, autenticación, permisos, entrada de usuario, subida de ficheros o
llamadas a IA, ejecuta la skill `security-checklist` y documenta aquí qué se revisó y qué queda pendiente.

### Issues encontrados
{Lista cualquier problema, desviación o deuda técnica}
```

---

## FASE 8 — PROPOSAL UPDATE

Revisa lo implementado y actualiza los artefactos si:

- Cambió alguna decisión técnica → añade ADR nuevo en ARCHITECTURE.md
- Apareció un caso borde no contemplado → añádelo a FEATURE.md
- Cambió algún endpoint o modelo → actualiza ARCHITECTURE.md
- Algo resultó fuera de alcance real → documéntalo en FEATURE.md

Al finalizar, genera un resumen del ciclo completado:

```markdown
## Ciclo SDD completado — US-{ID}

- Feature: {nombre}
- Rama: feature/{rama}
- Tests: X/X pasados
- Cobertura: X%
- Cambios respecto al plan original: {lista o "ninguno"}
- Próximos pasos sugeridos: {deuda técnica, mejoras v2}
```

---

## CONVENCIÓN DE COMMITS

Usa siempre este formato:

```
feat: descripción corta en presente
test: descripción
docs: descripción
fix: descripción
refactor: descripción
```

---

## LO QUE NUNCA DEBES HACER

- Escribir código antes de tener los tests (FASE 4 siempre antes que FASE 6)
- Implementar algo no especificado en FASE 1 o FASE 2
- Saltarte la confirmación del usuario entre fases
- Asumir ambigüedades: si algo no está claro en FEATURE.md, pregunta
- Ignorar casos borde definidos en FASE 1
