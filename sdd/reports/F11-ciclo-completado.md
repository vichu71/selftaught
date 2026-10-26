## Ciclo SDD completado — US-F11

- Feature: Piel «Mundo que crece» (plantas por curso, camino por un paisaje, tokens y fuentes nuevos)
- Rama: `feature/F11-piel-mundo-que-crece` (sin fusionar en `main`; «Mapa» sigue siendo la piel de `main`)
- Tests: 659/659 del frontend y 47/47 de entorno; backend sin cambios
- Cobertura del frontend: 97,1 % de líneas
- Cambios respecto al plan original:
  - El personaje de «tú estás aquí» va junto a la parada, a su derecha sobre el camino, en vez de encima de la etiqueta.
  - Plantas más grandes (9 rem) y tarjetas más anchas en el Inicio; enlaces de las tarjetas y de resultados a 44 px.
  - Un `width: 100%` general de las ilustraciones se sustituyó por tamaños por contexto.
  - Se mantuvieron Fredoka y Newsreader y cuatro fases de planta, como se confirmó en la FASE 2; sin prototipos previos, como pidió el usuario.
- Próximos pasos sugeridos: decidir qué piel se queda en `main` (BACKLOG F11-1); `Cache-Control: no-cache` para `index.html` al desplegar (F11-6); pasada manual a 360/430 px, zoom y teclado (F10-10); decoración de las colinas si se quiere más paisaje (F11-7); ilustración profesional de la mascota si «Mundo» se queda (F11-3).
- Lo que solo puede decidir quien use la aplicación: cuál de las dos pieles gusta en el uso diario (informe de pruebas, tabla comparativa).
