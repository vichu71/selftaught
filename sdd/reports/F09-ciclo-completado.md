## Ciclo SDD completado — US-F09

- Feature: **F09 · Edición de temario y compartir por código**
- Rama: `feature/F09-edicion-compartir` (sin push ni fusión hasta que lo pidas)
- Tests: **1 674/1 674 pasados** (backend 1 170 · frontend 461 · entorno 43) + 4 de Ollama real opt-in de F02, omitidos
- Cobertura: backend 94,7 % de líneas (84,2 % de ramas); `courseedit` 100 %, `share` 94,7 %
- Verificado además con el stack levantado (nginx → backend → PostgreSQL → servidor de IA simulado por HTTP, con el generador real en marcha): 43 comprobaciones, y una pasada por el navegador integrado (escritorio y 375 px, por el contenido de la página, sin capturas).
  **Ninguna llamada a una API de pago.** Informe: `sdd/reports/F09-testing-report.md`.

### Cambios respecto al plan original
- **Sin cambios de diseño:** ADR-63 a 66 se cumplieron tal cual; solo se anotó en ADR-63 y ADR-65 lo visto al implementar (el tope de piezas se comprueba antes que el límite por hora; el aviso de copia bloqueada del panel no lleva el id del curso ajeno).
- **Amplía F05 (ADR-42):** una pieza hecha ya no es «inmutable»: solo se regenera ante una petición explícita y confirmada del propietario (el generador sigue sin tocar lo que está `DONE`). `CLAUDE.md` lo dice.
- **Amplía F08:** el panel del adulto suma `COURSE_EDIT_BLOCKED` y `COURSE_COPY_BLOCKED` a sus avisos.
- El detalle del curso gana `version`, `copy.originVersion` (nunca el id del original) y `outdated` en cada pieza; el rol de la aplicación gana `DELETE` sobre temas y capítulos.
- Un menor puede **crear** códigos de sus propios cursos (supuesto mío, confirmado al no haber objeción) y canjear con filtro sobre toda la copia.
- Nuevos pendientes menores: **F9-1 a F9-9** (los dos últimos, F9-8 y F9-9, salen de la revisión de seguridad).

### Próximos pasos sugeridos
1. **Probar con tu modelo real:** el tutor (F7-1, F7-3), la corrección con IA (F6-1) y, ahora, lo que escribe la IA al añadir un tema o regenerar una pieza y cuánto cuesta; solo lo puedes medir tú con tus claves.
2. **Con F09 se cierra el mapa de la v1** (F01 a F09). Lo siguiente es **F10: mejora del frontend** (diseño visual, navegación común, pantalla principal, pantalla de edición más clara, accesibilidad y pruebas en móvil real); todavía no existe su ficha.
3. **Antes de desplegar:** HTTPS y proxy con IP real (D1 a D6), límite de frecuencia en la subida de material (F3-6), PDFBox (F3-4), filtro de menores en otros idiomas y con segunda capa (F4-3, F5-5, F7-2, F8-6, F9-7).
4. **Pequeñas mejoras:** F7-6 (que vaciar el chat no reinicie el límite del tutor), F6-6 reescrito, F8-7, F8-8, F9-8 y F9-9.
