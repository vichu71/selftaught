## Ciclo SDD completado — US-F10

- Feature: Rediseño del frontend «Mapa» (identidad visual, navegación común, mapa del curso, vistas rediseñadas)
- Rama: `feature/F10-rediseno-frontend`
- Tests: 590/590 del frontend y 46/46 de entorno; backend sin cambios
- Cobertura del frontend: 97,0 % de líneas
- Cambios respecto al plan original:
  - «Sigue donde lo dejaste» elige el curso terminado con **más avance** (sin llegar al 100 %) en vez del primero de la lista (ADR-67 actualizado).
  - Los **ejercicios no son parada** y se abren desde su lección; se añadió la etiqueta «Lista» (preparada y sin estudiar).
  - El mapa usa **tramos en ángulo recto** que salen hacia un lado, nombres en dos líneas (ancho) o a la derecha (móvil).
  - `UiCard` se eliminó por no usarse; las vistas usan sobre todo las clases compartidas de `components.css`, no `UiField`/`UiButton` (BACKLOG F10-8).
  - Los tests de F06–F09 que exigían «sin librerías nuevas» ahora comparan con la lista vigente de dependencias (`FRONTEND_DEPS`).
  - El diálogo de confirmación de la edición pasó de un aviso en línea a un diálogo modal (Reka UI), y la edición se divide en pestañas Temario y Compartir.
- Próximos pasos sugeridos: BACKLOG F10-1 a F10-11 (pasada manual a 360/430 px, zoom y teclado; herramienta de accesibilidad; migrar vistas a los componentes base; simplificar la edición; `lastStudiedAt`); decidir nombre y logo definitivos; probar la variante «Mundo que crece» si se quiere otra piel.
- Lo que solo puede decidir quien use la aplicación: si el estilo gusta en el uso diario (el mapa con muchos temas), y si compensa la variante «Mundo».
