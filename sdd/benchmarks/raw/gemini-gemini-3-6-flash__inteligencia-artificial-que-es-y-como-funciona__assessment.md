# gemini/gemini-3.6-flash · ASSESSMENT

**Tema:** Inteligencia artificial: qué es y cómo funciona

**Tokens:** entrada 138 · salida 3771 · **Coste:** 0.014245 USD · **Fin:** STOP

---

# Evaluación del Tema: Inteligencia Artificial (Qué es y cómo funciona)

Como profesor del curso, he preparado la siguiente propuesta de evaluación dividida en dos bloques: una serie de ejercicios prácticos guiados de dificultad creciente y un mini-examen tipo test para comprobar la adquisición de conocimientos teóricos fundamentales.

---

## Bloque 1: Ejercicios Prácticos de Dificultad Creciente

### Ejercicio 1: Clasificación de tipos de IA (Nivel: Básico)
**Enunciado:** 
Determina si los siguientes sistemas informáticos corresponden a una **IA Estrecha o Débil (ANI)** o a una **IA General o Fuerte (AGI)**. Justifica brevemente tu respuesta en cada caso:
1. Un sistema que juega al ajedrez a nivel de gran maestro (ej. Deep Blue).
2. Un asistente virtual que responde preguntas sobre el tiempo, reproduce música y programa alarmas (ej. Alexa o Siri).
3. Un ente artificial con conciencia propia capaz de aprender cualquier tarea intelectual humana sin entrenamiento previo específico.

**Solución comentada:**
1. **IA Estrecha (ANI):** Está diseñada y optimizada para cumplir únicamente una tarea específica (jugar al ajedrez). No puede aplicar ese conocimiento para realizar otras tareas.
2. **IA Estrecha (ANI):** Aunque parece realizar múltiples funciones, en realidad combina varios modelos de IA especializados (procesamiento de lenguaje natural, búsqueda de información, gestión de comandos) dentro de un dominio limitado y predefinido.
3. **IA General (AGI):** Es una forma hipotética de IA que posee capacidad cognitiva similar o superior a la humana, capaz de razonar, adaptarse y transferir conocimiento entre dominios abstractos. Actualmente no existe.

---

### Ejercicio 2: Paradigmas de Aprendizaje Automático (Nivel: Básico-Intermedio)
**Enunciado:**
Identifica el tipo de aprendizaje automático (*Machine Learning*) más adecuado para resolver cada uno de los siguientes problemas: **Aprendizaje Supervisado**, **Aprendizaje No Supervisado** o **Aprendizaje por Refuerzo**.

A. Un banco quiere agrupar a sus clientes en función de sus hábitos de gasto sin tener categorías previas definidas.
B. Un sistema para un coche autónomo que aprende a aparcar mediante ensayo y error, recibiendo recompensas cuando lo hace bien y penalizaciones cuando comete un error.
C. Un algoritmo para diagnosticar si una radiografía médica muestra neumonía o no, entrenado previamente con miles de imágenes etiquetadas por radiólogos como "enfermo" o "sano".

**Solución comentada:**
* **A -> Aprendizaje No Supervisado:** Se utiliza porque los datos no tienen etiquetas previas. El algoritmo busca patrones o agrupaciones implícitas (*clustering*) en los datos.
* **B -> Aprendizaje por Refuerzo:** El sistema (agente) aprende a tomar decisiones óptimas interactuando con un entorno mediante un mecanismo de recompensas y penalizaciones.
* **C -> Aprendizaje Supervisado:** El modelo aprende a partir de un conjunto de datos etiquetados (imágenes asociadas a un diagnóstico correcto o "verdad del terreno").

---

### Ejercicio 3: Estructura de una Red Neuronal (Nivel: Intermedio)
**Enunciado:**
Describe brevemente las tres capas principales que componen una **Red Neuronal Artificial (ANN)** y explica cuál es el papel clave de la **función de activación** en las neuronas artificiales.

**Solución comentada:**
1. **Capa de Entrada (*Input Layer*):** Recibe las características o datos de entrada del mundo exterior (por ejemplo, los píxeles de una imagen o valores numéricos).
2. **Capa(s) Oculta(s) (*Hidden Layer(s)*):** Procesan la información mediante la aplicación de pesos (*weights*) y sesgos (*biases*). En el *Deep Learning*, existen múltiples capas ocultas que permiten extraer características progresivamente más complejas y abstractas.
3. **Capa de Salida (*Output Layer*):** Produce el resultado final del modelo (por ejemplo, una probabilidad de clasificación o una predicción numérica).

* **Papel de la Función de Activación:** Introduce **no linealidad** en la red. Sin ella, una red neuronal sería equivalente a una simple combinación lineal de sus entradas, lo que le impediría aprender patrones complejos o no lineales en los datos.

---

### Ejercicio 4: Evaluación de Métricas de Rendimiento (Nivel: Intermedio-Avanzado)
**Enunciado:**
Un modelo de IA enfocado en la detección de *spam* en correo electrónico procesó un conjunto de prueba de 100 correos, obteniendo los siguientes resultados:
* Verdaderos Positivos (VP - Correos de spam detectados correctamente como spam): **40**
* Verdaderos Negativos (VN - Correos legítimos detectados correctamente como legítimos): **50**
* Falsos Positivos (FP - Correos legítimos marcados erróneamente como spam): **5**
* Falsos Negativos (FN - Correos de spam marcados erróneamente como legítimos): **5**

Calcula la **Exactitud (*Accuracy*)** y la **Precisión (*Precision*)** del modelo utilizando las fórmulas correspondientes.

**Solución comentada:**
* **Fórmula de Exactitud (*Accuracy*):** $\text{Exactitud} = \frac{VP + VN}{VP + VN + FP + FN}$
  $$\text{Exactitud} = \frac{40 + 50}{40 + 50 + 5 + 5} = \frac{90}{100} = 0.90 \text{ o } 90\%$$
  *Interpretación:* El modelo clasifica correctamente el 90% del total de los correos.

* **Fórmula de Precisión (*Precision*):** $\text{Precisión} = \frac{VP}{VP + FP}$
  $$\text{Precisión} = \frac{40}{40 + 5} = \frac{40}{45} \approx 0.8889 \text{ o } 88.89\%$$
  *Interpretación:* De todos los correos que el sistema identificó como *spam*, el 88.89% realmente lo eran.

---

### Ejercicio 5: Diseño del Flujo de Trabajo (Pipeline) de IA (Nivel: Avanzado)
**Enunciado:**
Un equipo de desarrollo quiere crear un sistema de IA para predecir el precio de alquiler de viviendas. Enumera en orden cronológico las **5 fases principales del flujo de trabajo (*pipeline*)** que deben seguir desde la concepción del problema hasta el despliegue del modelo, explicando brevemente qué se realiza en cada fase.

**Solución comentada:**
1. **Recolección e Ingesta de Datos:** Recopilar datos históricos relevante de pisos (superficie en m², ubicación, número de habitaciones, precio de alquiler histórico).
2. **Preprocesamiento y Limpieza de Datos:** Tratar valores faltantes, eliminar duplicados, normalizar o escalar variables numéricas y codificar variables categóricas (como el barrio) a formato numérico.
3. **División de Datos y Selección del Modelo:** Separar el conjunto de datos en entrenamiento (*train*) y prueba (*test*). Elegir los algoritmos de regresión adecuados (ej. Regresión Lineal, Random Forest o Redes Neuronales).
4. **Entrenamiento del Modelo y Ajuste de Hiperparámetros:** Entrenar el algoritmo seleccionado utilizando los datos de entrenamiento para que ajuste sus pesos internos y minimice el error de predicción.
5. **Evaluación y Despliegue (*Deployment*):** Evaluar el modelo con el conjunto de prueba utilizando métricas como el Error Cuadrático Medio (MSE). Si cumple con los requisitos de calidad, se despliega en producción para predecir precios con datos reales.

---

## Bloque 2: Mini-Examen Tipo Test

**Indicaciones:** Responde a cada una de las preguntas seleccionando la opción correcta (solo hay una respuesta válida por pregunta).

### Pregunta 1
¿Cuál es la diferencia fundamental entre la programación tradicional y el Aprendizaje Automático (*Machine Learning*)?
- [ ] A) La programación tradicional utiliza algoritmos y el Machine Learning no usa matemática.
- [ ] B) En la programación tradicional se introducen datos y reglas para obtener respuestas; en Machine Learning se introducen datos y respuestas para que el sistema aprenda las reglas.
- [ ] C) El Machine Learning solo funciona en computadoras cuánticas, mientras que la programación tradicional opera en ordenadores comunes.
- [ ] D) No existe diferencia, ambos términos son sinónimos exactos.

### Pregunta 2
¿Qué fenómeno ocurre cuando un modelo de IA aprende "de memoria" los datos de entrenamiento, rindiendo excelente con ellos pero fallando al intentar generalizar con datos nuevos?
- [ ] A) Underfitting (Subajuste).
- [ ] B) Backpropagation (Retropropagación).
- [ ] C) Overfitting (Sobreajuste).
- [ ] D) Clustering (Agrupamiento).

### Pregunta 3
En el contexto de las redes neuronales artificiales, ¿qué función cumple el algoritmo de **Retropropagación (*Backpropagation*)**?
- [ ] A) Recopilar más datos de internet automáticamente.
- [ ] B) Calcular el error de la salida y propagarlo hacia atrás para ajustar los pesos y reducir el error.
- [ ] C) Convertir datos de texto en imágenes numéricas.
- [ ] D) Apagar la red neuronal si la temperatura del procesador sube demasiado.

### Pregunta 4
Si entrenamos un modelo de IA con datos sesgados o no representativos de la realidad, ¿cuál será el resultado más probable?
- [ ] A) El modelo corregirá automáticamente los sesgos gracias a su inteligencia.
- [ ] B) El modelo fallará y no podrá entrenarse.
- [ ] C) El modelo perpetuará o amplificará dichos sesgos en sus predicciones.
- [ ] D) El modelo cambiará automáticamente de Aprendizaje Supervisado a No Supervisado.

### Pregunta 5
¿A qué se refiere el término **"Caja Negra" (*Black Box*)** en la Inteligencia Artificial?
- [ ] A) Al chasis físico donde se montan los servidores de IA.
- [ ] B) A la dificultad o imposibilidad de interpretar o explicar fácilmente cómo un modelo complejo (como una red neuronal profunda) llega a una decisión concreta.
- [ ] C) A la copia de seguridad que se guarda antes de entrenar un modelo.
- [ ] D) Un tipo de algoritmo exclusivo para ciberseguridad.

---

### Solucionario del Mini-Examen

1. **Respuesta correcta: B**  
   *Comentario:* En el enfoque tradicional, los programadores escriben explícitamente las reglas. En Machine Learning, el algoritmo analiza los datos y los resultados esperados para inferir/aprender las reglas subyacentes.

2. **Respuesta correcta: C**  
   *Comentario:* El *overfitting* o sobreajuste ocurre cuando el modelo memoriza el ruido y los detalles específicos del conjunto de entrenamiento, perdiendo la capacidad de generalizar ante datos no vistos.

3. **Respuesta correcta: B**  
   *Comentario:* *Backpropagation* es el algoritmo fundamental que calcula el gradiente del error respecto a cada peso de la red, permitiendo actualizar dichos pesos mediante algoritmos como el Descenso del Gradiente.

4. **Respuesta correcta: C**  
   *Comentario:* Los modelos de IA no tienen criterio ético propio; aprenden estrictamente a partir de los patrones presentes en los datos (*Garbage in, garbage out*). Si los datos están sesgados, las salidas también lo estarán.

5. **Respuesta correcta: B**  
   *Comentario:* El problema de la "caja negra" afecta especialmente al *Deep Learning*, donde la complejidad de millones de parámetros entrelazados dificulta la explicabilidad del proceso de toma de decisiones del modelo.
