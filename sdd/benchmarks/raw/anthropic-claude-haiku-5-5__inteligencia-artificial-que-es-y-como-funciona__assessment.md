# anthropic/claude-haiku-5-5 · ASSESSMENT

**Tema:** Inteligencia artificial: qué es y cómo funciona

**Tokens:** entrada 251 · salida 5841 · **Coste:** 0.002946 USD · **Fin:** STOP

---

# Inteligencia artificial: qué es y cómo funciona

## Parte 1. Ejercicios de dificultad creciente

### Ejercicio 1 (básico): Clasificar sistemas

Indica cuáles de los siguientes sistemas son **programación clásica** (reglas escritas explícitamente) y cuáles **aprendizaje automático** (el comportamiento se obtiene a partir de datos):

a) Un termostato que enciende la calefacción si la temperatura baja de 19 °C.
b) Un filtro de spam que se ajusta a partir de miles de correos etiquetados como spam o no spam.
c) Una calculadora que suma dos números.
d) Un recomendador de películas que mejora a medida que recibe valoraciones de los usuarios.

**Solución comentada:**
- a) **Programación clásica.** La regla (si T < 19 °C, encender) está escrita a mano y no cambia con los datos.
- b) **Aprendizaje automático.** Nadie escribe las reglas de spam; el modelo las extrae de los ejemplos etiquetados.
- c) **Programación clásica.** La operación suma está definida explícitamente.
- d) **Aprendizaje automático.** El sistema mejora con la experiencia (los datos de valoraciones).

**Idea clave:** en el aprendizaje automático el programa no se escribe con reglas, sino que se *entrena* con datos.

---

### Ejercicio 2 (fácil-medio): Métricas a partir de una matriz de confusión

Un clasificador de correos se evalúa con 100 correos. Los resultados son:

- Verdaderos positivos (TP) = 40
- Falsos positivos (FP) = 10
- Falsos negativos (FN) = 5
- Verdaderos negativos (TN) = 45

Calcula la **exactitud (accuracy)**, la **precisión (precision)** y la **sensibilidad (recall)**.

**Solución comentada:**
- Exactitud = (TP + TN) / total = (40 + 45) / 100 = **0,85**. Es la proporción de aciertos globales.
- Precisión = TP / (TP + FP) = 40 / 50 = **0,80**. De los correos marcados como spam, el 80 % realmente lo son.
- Sensibilidad = TP / (TP + FN) = 40 / 45 ≈ **0,889**. De los correos que son spam, el modelo detecta casi el 89 %.

**Idea clave:** la exactitud puede ocultar problemas; conviene mirar también precisión y sensibilidad.

---

### Ejercicio 3 (medio): Un perceptrón y una actualización de pesos

Una neurona de tipo perceptrón tiene pesos $w_1 = 0{,}5$, $w_2 = -0{,}3$ y sesgo $b = 0{,}1$. Su salida es $y = 1$ si $z = w_1x_1 + w_2x_2 + b \geq 0$, y $y = 0$ en caso contrario.

a) Calcula la salida para la entrada $(x_1, x_2) = (1, 0)$.
b) Calcula la salida para la entrada $(x_1, x_2) = (0, 1)$, cuyo valor objetivo es $t = 1$.
c) Aplica la regla de aprendizaje $w_i \leftarrow w_i + \eta\,(t - y)\,x_i$ y $b \leftarrow b + \eta\,(t - y)$ con $\eta = 0{,}1$, y comprueba si ahora la salida para $(0, 1)$ es correcta.

**Solución comentada:**
a) $z = 0{,}5 \cdot 1 + (-0{,}3) \cdot 0 + 0{,}1 = 0{,}6 \geq 0 \Rightarrow y = 1$.

b) $z = 0{,}5 \cdot 0 + (-0{,}3) \cdot 1 + 0{,}1 = -0{,}2 < 0 \Rightarrow y = 0$. La salida es incorrecta, porque el objetivo es 1.

c) El error es $t - y = 1 - 0 = 1$.
- $w_2 \leftarrow -0{,}3 + 0{,}1 \cdot 1 \cdot 1 = -0{,}2$
- $w_1$ no cambia, porque $x_1 = 0$.
- $b \leftarrow 0{,}1 + 0{,}1 \cdot 1 = 0{,}2$

Nueva $z$ para $(0, 1)$: $-0{,}2 + 0{,}2 = 0 \geq 0 \Rightarrow y = 1$. Ya es correcta.

**Idea clave:** aprender es ajustar los pesos en proporción al error y a la entrada que lo causó.

---

### Ejercicio 4 (medio-alto): Probabilidad condicionada con el teorema de Bayes

Sea $P(\text{spam}) = 0{,}4$, $P(\text{“oferta”} \mid \text{spam}) = 0{,}5$ y $P(\text{“oferta”} \mid \text{no spam}) = 0{,}1$. Calcula $P(\text{spam} \mid \text{“oferta”})$.

**Solución comentada:**
Por el teorema de Bayes:

$$P(\text{spam}\mid\text{oferta}) = \frac{P(\text{oferta}\mid\text{spam})\,P(\text{spam})}{P(\text{oferta}\mid\text{spam})\,P(\text{spam}) + P(\text{oferta}\mid\text{no spam})\,P(\text{no spam})}$$

Sustituyendo, con $P(\text{no spam}) = 1 - 0{,}4 = 0{,}6$:

$$P(\text{spam}\mid\text{oferta}) = \frac{0{,}5 \cdot 0{,}4}{0{,}5 \cdot 0{,}4 + 0{,}1 \cdot 0{,}6} = \frac{0{,}20}{0{,}26} \approx 0{,}769$$

**Idea clave:** muchos clasificadores (como el de Bayes ingenuo) actualizan creencias con evidencias de este modo. Observa que la probabilidad a priori (0,4) sube a 0,769 tras observar la palabra.

---

### Ejercicio 5 (avanzado): Un paso de descenso del gradiente

Una neurona con activación sigmoide calcula $a = \sigma(z)$, donde $z = wx + b$ y $\sigma(z) = \dfrac{1}{1+e^{-z}}$. Tiene entrada $x = 2$, objetivo $y = 1$, pesos iniciales $w = 0{,}5$ y $b = 0$, y función de pérdida $L = \tfrac{1}{2}(a - y)^2$. Usa tasa de aprendizaje $\eta = 0{,}1$.

a) Calcula $a$ y la pérdida inicial.
b) Calcula $\dfrac{\partial L}{\partial w}$ y $\dfrac{\partial L}{\partial b}$ usando la regla de la cadena. Recuerda que $\sigma'(z) = \sigma(z)\,(1-\sigma(z))$.
c) Realiza una actualización del descenso del gradiente y comprueba que la pérdida disminuye.

**Solución comentada:**

a) $z = 0{,}5 \cdot 2 + 0 = 1$, así que $a = \sigma(1) \approx 0{,}7311$. La pérdida inicial es $L = \tfrac{1}{2}(0{,}7311 - 1)^2 = \tfrac{1}{2}(0{,}2689)^2 \approx 0{,}0362$.

b) Por la regla de la cadena:
$$\frac{\partial L}{\partial a} = a - y, \qquad \frac{\partial a}{\partial z} = a(1-a), \qquad \frac{\partial z}{\partial w} = x, \qquad \frac{\partial z}{\partial b} = 1$$

Entonces:
- $\dfrac{\partial L}{\partial b} = (a-y)\,a(1-a) = (-0{,}2689)(0{,}1966) \approx -0{,}0529$
- $\dfrac{\partial L}{\partial w} = \dfrac{\partial L}{\partial b}\cdot x \approx -0{,}1057$

c) Actualizamos:
- $w \leftarrow 0{,}5 - 0{,}1\cdot(-0{,}1057) \approx 0{,}5106$
- $b \leftarrow 0 - 0{,}1\cdot(-0{,}0529) \approx 0{,}0053$

Nueva salida: $z = 0{,}5106 \cdot 2 + 0{,}0053 \approx 1{,}0265$, $a \approx 0{,}7362$. Nueva pérdida: $\tfrac{1}{2}(0{,}2638)^2 \approx 0{,}0348 < 0{,}0362$. La pérdida ha disminuido.

**Idea clave:** entrenar una red consiste en repetir este cálculo de gradientes (retropropagación) y actualizar los parámetros en la dirección que reduce el error.

---

## Parte 2. Mini examen tipo test

Elige una única respuesta correcta por pregunta.

**1.** ¿En qué se diferencia el aprendizaje automático de la programación clásica?
- a) En el aprendizaje automático las reglas se escriben a mano.
- b) En el aprendizaje automático el sistema aprende patrones a partir de datos.
- c) El aprendizaje automático siempre requiere redes neuronales.
- d) El aprendizaje automático no necesita datos.

**2.** Un clasificador tiene TP = 40, FP = 10, FN = 5 y TN = 45. ¿Cuál es su exactitud (accuracy)?
- a) 0,80
- b) 0,85
- c) 0,89
- d) 0,40

**3.** ¿Cuál es el valor de la función sigmoide $\sigma(0) = \dfrac{1}{1+e^{0}}$?
- a) 0
- b) 0,5
- c) 1
- d) $e$

**4.** ¿Para qué se separan los datos en conjunto de entrenamiento y conjunto de prueba?
- a) Para acelerar el entrenamiento con menos datos.
- b) Para estimar cómo se comporta el modelo con datos que no ha visto durante el entrenamiento.
- c) Para eliminar del modelo las etiquetas incorrectas.
- d) Para obtener un modelo con más parámetros.

**5.** Un modelo obtiene un rendimiento muy alto en el conjunto de entrenamiento y mucho más bajo en el de prueba. ¿Qué fenómeno indica esto?
- a) Subajuste (underfitting).
- b) Sobreajuste (overfitting).
- c) Convergencia perfecta.
- d) Falta de una función de pérdida.

---

### Clave de respuestas

| Pregunta | Respuesta correcta |
|---|---|
| 1 | **b** |
| 2 | **b** |
| 3 | **b** |
| 4 | **b** |
| 5 | **b** |

**Justificación breve:**
1. El aprendizaje automático extrae patrones de los datos en lugar de depender de reglas escritas a mano (ejercicio 1).
2. (40 + 45) / 100 = 0,85 (ejercicio 2).
3. $\sigma(0) = 1/(1+1) = 0{,}5$ (ejercicio 5).
4. La evaluación en datos no vistos mide la capacidad de generalización.
5. Una gran diferencia entre entrenamiento y prueba es el síntoma clásico del sobreajuste.
