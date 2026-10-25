
---

## Cierre del ciclo (25-10-2026)

**Decisiones del usuario:** ejercicios con **autocorrección y «Corregir con IA»**, lección hecha con **«Marcar como hecha»**, examen **repetible sin límite**, con **respuestas tras entregar** y **nota mínima del 50 %**. ADR-49 a 54.

**Casos borde que no estaban en la ficha y aparecieron al implementar**
- La pieza de F05 cambia: la lección trae `done` y los enlaces a la anterior y a la siguiente (que saltan las no generadas); los ejercicios y el examen ya no llevan solución ni opción correcta (ADR-50).
- Un intento de examen que no se entregó nunca no cuenta y no tiene resultado (409); los abiertos de más de un día se purgan al empezar otro examen.
- Una corrección de IA que falla (proveedor, veredicto roto o tope) **sigue gastando el cupo diario** del intento y el intento no se puede volver a corregir; el alumno puede enviar otro intento o autocorregirse.
- El comentario del modelo que el filtro rechaza para un menor se sustituye por un texto fijo y se conserva el veredicto.
- Una lección marcada que luego deja de estar `DONE` no cuenta como hecha.
- Los envíos por hora se cuentan sobre los intentos conservados: el máximo por hora no debe superar al de intentos por pieza (ADR-54, F6-6).

**Fuera de alcance real (documentado, no hecho)**
Medir con un modelo real la calidad de «Corregir con IA» (F6-1), el límite de «una a la vez» con varias instancias (F6-2), la exportación con soluciones (F6-3), los intentos del tutor (F6-4, F08), más tipos de pregunta y motor de
cálculo (F6-5) y validar el invariante de configuración al arrancar (F6-6).
