# anthropic/claude-haiku-5-5 · ASSESSMENT

**Tema:** Cocina básica: técnicas y recetas esenciales

**Tokens:** entrada 250 · salida 6000 · **Coste:** 0.003025 USD · **Fin:** LENGTH

---

# Evaluación: Cocina básica, técnicas y recetas esenciales

## Ejercicios

### Ejercicio 1 (dificultad baja): Equivalencias de medidas

**Enunciado:** Una receta pide 3 cucharadas soperas de aceite y 2 cucharaditas de sal. Sabiendo que 1 cucharada sopera equivale a 15 ml y 1 cucharadita a 5 ml, ¿cuántos mililitros de ingredientes se necesitan en total?

**Solución comentada:**
- Aceite: 3 × 15 ml = 45 ml
- Sal: 2 × 5 ml = 10 ml
- Total: 45 ml + 10 ml = **55 ml**

*Comentario:* Este ejercicio trabaja la conversión entre medidas de cocina. Conviene aplicar cada factor por separado antes de sumar.

---

### Ejercicio 2 (dificultad baja-media): Escalado de recetas

**Enunciado:** Una receta para 4 personas lleva 320 g de harina, 200 ml de leche y 2 huevos. ¿Qué cantidades se necesitan para 10 personas?

**Solución comentada:**
1. Factor de escala: 10 ÷ 4 = 2,5
2. Harina: 320 × 2,5 = **800 g**
3. Leche: 200 × 2,5 = **500 ml**
4. Huevos: 2 × 2,5 = 5 → **5 huevos**

*Comentario:* El factor se aplica a todos los ingredientes por igual. Un error habitual es escalar solo algunos. En la práctica, los ingredientes de sabor fuerte (sal, especias) a menudo conviene ajustarlos al final, probando la preparación.

---

### Ejercicio 3 (dificultad media): Temperaturas de horno

**Enunciado:** Una receta americana indica precalentar el horno a 375 °F. Un horno eléctrico europeo se gradúa en grados Celsius. Sabiendo que la conversión es °C = (°F − 32) × 5/9, ¿a qué temperatura en °C hay que programarlo? Además, calcula cuántos grados Celsius más alta es la temperatura del agua hirviendo a nivel del mar (100 °C) respecto a 60 °C.

**Solución comentada:**
1. Aplicamos la fórmula: (375 − 32) × 5/9 = 343 × 5/9 = 1715/9 ≈ 190,6 °C
2. Se redondea a **190 °C** (o a 180–190 °C según la receta; la escala de 10 en 10 de muchos hornos hace práctico usar 190 °C).
3. Diferencia: 100 − 60 = **40 °C**

*Comentario:* Es importante recordar que la conversión de temperaturas no es lineal como la de longitudes: primero se resta 32 y después se multiplica por 5/9. Confundir el orden de las operaciones es el error más frecuente.

---

### Ejercicio 4 (dificultad media-alta): Proporciones en una vinagreta

**Enunciado:** Quieres preparar 500 ml de vinagreta con una proporción de 2 partes de aceite por cada 1 parte de vinagre. ¿Cuántos mililitros de cada ingrediente necesitas? Si además quieres añadir 1 cucharadita de mostaza (5 ml) como ingrediente extra, ¿cuántos mililitros de líquido en total tendrás? ¿Y cuál es ahora la proporción real si la mostaza se considera parte del aliño?

**Solución comentada:**
1. Partes totales: 2 + 1 = 3
2. Valor de una parte: 500 ÷ 3 ≈ 166,7 ml
3. Aceite (2 partes): 2 × 166,7 ≈ **333,3 ml**
4. Vinagre (1 parte): **166,7 ml**
5. Con la mostaza, el total es 500 + 5 = **505 ml**, aunque la mostaza no sustituye al líquido, sino que se suma.
6. Para la proporción real, la mostaza es un emulsionante y no cambia la relación aceite:vinagre (sigue siendo 2:1). Si se quisiera recalcular, habría que decir que el total de la mezcla es de 505 ml, con 333,3 ml de aceite, 166,7 ml de vinagre y 5 ml de mostaza.

*Comentario:* El ejercicio combina razonamiento proporcional con la comprensión técnica de la emulsión. La mostaza ayuda a estabilizar la vinagreta, por lo que se añade para mejorar la textura, no para modificar la proporción base.

---

### Ejercicio 5 (dificultad alta): Planificación de un menú con tiempos

**Enunciado:** Vas a servir un pollo asado a las 14:00. Datos:
- Pollo de 2 kg. Tiempo de asado: 25 minutos por cada 500 g.
- Precalentamiento del horno: 15 minutos antes de introducir el pollo.
- Reposo de la carne tras el asado: 15 minutos.
- Puré de patata: 30 minutos de cocción, que puede hacerse mientras el pollo está en el horno.
- La temperatura interior mínima del pollo debe ser de 75 °C.

Calcula a qué hora debes encender el horno, a qué hora debes meter el pollo, a qué hora debes sacarlo y a qué hora debes empezar el puré.

**Solución comentada:**
1. **Tiempo de asado:** 2000 g ÷ 500 g = 4 bloques de 500 g → 4 × 25 min = **100 min**
2. **Tiempo total previo a servir:** precalentamiento (15) + asado (100) + reposo (15) = 130 min = **2 h 10 min**
3. **Hora de encender el horno:** 14:00 − 2 h 10 min = **11:50**
4. **Hora de meter el pollo:** 11:50 + 15 min = **12:05**
5. **Hora de sacarlo:** 12:05 + 100 min = **13:45**
6. **Hora de inicio del reposo:** 13:45, que termina a las 14:00 (15 min) ✔
7. **Hora de empezar el puré:** el puré debe estar listo a las 14:00 y tarda 30 min. Debe empezar a las **13:30**. Como el pollo está en el horno hasta las 13:45, el puré puede cocinarse durante el asado, por lo que basta con empezar a las 13:30 o antes, siempre que no se quede frío.
8. **Comprobación de seguridad:** antes de sacar el pollo a las 13:45, debe medirse la temperatura en la parte más gruesa (muslo sin tocar el hueso). Si no llega a 75 °C, se prolonga el asado unos minutos y se reajusta el plan.

*Comentario:* Este ejercicio de planificación inversa consiste en partir de la hora de servicio y retroceder sumando todos los tiempos. Es una habilidad básica en cocina profesional. El reajuste por temperatura interior demuestra que los tiempos son orientativos y que la comprobación con termómetro es la referencia de seguridad.

---

## Mini examen tipo test

**Pregunta 1.** ¿Cuántos mililitros equivalen a una cucharada sopera, según la medida estándar que se usa en el ejercicio 1?
- a) 5 ml
- b) 10 ml
- c) 15 ml
- d) 20 ml

**Pregunta 2.** La técnica de cocción que consiste en cocer un alimento en un líquido a una temperatura cercana a los 70–85 °C, sin que llegue a hervir con fuerza, se llama:
- a) Saltear
- b) Escalfar
- c) Gratinar
- d) Freír

**Pregunta 3.** A nivel del mar, el agua hierve a una temperatura de:
- a) 90 °C
- b) 100 °C
- c) 110 °C
- d) 120 °C

**Pregunta 4.** En la vinagreta clásica, la proporción de referencia más habitual entre aceite y vinagre es:
- a) 1:1
- b) 3:1 (3 partes de aceite por 1 de vinagre)
- c) 1:3
- d) 5:1

**Pregunta 5.** La reacción química responsable del dorado y de los sabores tostados de alimentos como el pan, la carne asada o la cebolla dorada es:
- a) La reacción de Maillard
- b) La gelatinización
- c) La emulsificación
- d) La pasteurización

---

## Solucionario del mini examen

| Pregunta | Respuesta correcta | Justificación |
|---|---|---|
| 1 | **c) 15 ml** | Es la medida estándar de la cucharada sopera utilizada en el ejercicio 1. |
| 2 | **b) Escalfar** | Es la cocción en líquido caliente sin ebullición fuerte. Saltear y freír son técnicas con grasa a alta temperatura. |
| 3 | **b) 100 °C** | Es la temperatura de ebullición del agua a 1 atm de presión (nivel del mar). |
| 4 | **b) 3:1** | Es la proporción clásica de referencia para vinagretas, aunque puede variar según el gusto. |
| 5 | **a) La reacción de Maillard** | Es la reacción entre aminoácidos y azúcares reductores que se produce con calor y da color y sabor tost
