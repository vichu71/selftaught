# openai/gpt-6.1-sol · ASSESSMENT

**Tema:** Inteligencia artificial: qué es y cómo funciona

**Tokens:** entrada 144 · salida 1912 · **Coste:** 0.019408 USD · **Fin:** STOP

---

# Inteligencia artificial: qué es y cómo funciona

## Cinco ejercicios de dificultad creciente

### 1. Definir la inteligencia artificial
**Dificultad: básica**

**Enunciado:** Explica qué es la inteligencia artificial (IA) y menciona dos tareas que pueden realizar los sistemas de IA. ¿Necesita una IA tener conciencia para realizar esas tareas?

**Solución comentada:**

La IA es un campo de la informática que desarrolla sistemas capaces de realizar tareas como reconocer patrones, comprender o generar lenguaje, elaborar predicciones y tomar decisiones para alcanzar objetivos.

Dos ejemplos son:
- Reconocer objetos en imágenes.
- Traducir textos entre idiomas.

No es necesario atribuirles conciencia para explicar su funcionamiento. Su capacidad para resolver tareas no demuestra que tengan experiencias subjetivas.

**Comentario:** No debe confundirse un comportamiento aparentemente inteligente con una mente humana.

---

### 2. Distinguir IA, aprendizaje automático y automatización
**Dificultad: básica-intermedia**

**Enunciado:** Valora si las siguientes afirmaciones son verdaderas o falsas y justifica tu respuesta.

1. Todo programa informático es un sistema de IA.
2. El aprendizaje automático es una parte de la IA.
3. Toda IA necesita aprender a partir de datos.
4. Un sistema que utiliza reglas explícitas para razonar puede formar parte de la IA.

**Solución comentada:**

1. **Falsa.** Ejecutar instrucciones no basta para considerar que un programa es IA. Una calculadora convencional, por ejemplo, realiza operaciones sin que por ello se clasifique como IA.
2. **Verdadera.** El aprendizaje automático comprende métodos que ajustan modelos a partir de datos.
3. **Falsa.** También existen enfoques de IA basados en reglas, lógica y búsqueda que no requieren aprender sus reglas a partir de datos.
4. **Verdadera.** Los sistemas expertos son un ejemplo clásico de IA basada en conocimientos y reglas.

**Comentario:** La IA es el campo general; el aprendizaje automático es uno de sus enfoques. No son sinónimos.

---

### 3. Explicar el entrenamiento y la inferencia
**Dificultad: intermedia**

**Enunciado:** Se quiere desarrollar un clasificador de correo no deseado mediante aprendizaje supervisado. Ordena estas etapas y explica su función:

- Evaluar el modelo con datos reservados.
- Recopilar correos con etiquetas de «no deseado» y «legítimo».
- Utilizar el modelo para clasificar correos nuevos.
- Separar los datos de entrenamiento y de prueba.
- Entrenar el modelo.

**Solución comentada:**

1. **Recopilar correos etiquetados.** Las etiquetas indican qué resultado debe aprender a predecir el modelo.
2. **Separar entrenamiento y prueba.** Los datos de prueba se reservan para comprobar el rendimiento fuera de los ejemplos utilizados para ajustar el modelo.
3. **Entrenar el modelo.** Un algoritmo ajusta sus parámetros para reducir los errores en los datos de entrenamiento.
4. **Evaluar con los datos reservados.** Permite estimar su capacidad de generalizar a otros correos.
5. **Clasificar correos nuevos.** Esta utilización del modelo entrenado se denomina **inferencia**.

**Comentario:** Durante la inferencia no es necesario modificar los parámetros. Usar un sistema de IA no implica que esté aprendiendo continuamente.

---

### 4. Comparar formas de aprendizaje
**Dificultad: intermedia-alta**

**Enunciado:** Identifica el enfoque de aprendizaje más adecuado para cada tarea y explica qué señal guía el proceso.

1. Aprender a reconocer dígitos utilizando imágenes que incluyen la etiqueta correcta.
2. Agrupar documentos por semejanza sin disponer de categorías asignadas previamente.
3. Aprender a elegir acciones en un entorno recibiendo recompensas o penalizaciones.

**Solución comentada:**

| Tarea | Enfoque | Señal que guía el aprendizaje |
|---|---|---|
| Reconocer dígitos etiquetados | **Aprendizaje supervisado** | La diferencia entre la predicción y la etiqueta correcta. |
| Agrupar documentos sin categorías previas | **Aprendizaje no supervisado** | La estructura de los datos, según el objetivo y el criterio de semejanza elegidos. |
| Elegir acciones mediante recompensas | **Aprendizaje por refuerzo** | La recompensa recibida al interactuar con el entorno. |

**Comentario:** Los grupos obtenidos sin supervisión no tienen por qué coincidir con categorías útiles para las personas. Asimismo, en aprendizaje por refuerzo, maximizar una recompensa mal diseñada puede producir comportamientos no deseados.

---

### 5. Analizar críticamente un sistema de IA
**Dificultad: alta**

**Enunciado:** Evalúa esta afirmación:

> «Un modelo generativo que escribe respuestas fluidas demuestra que todo lo que dice es correcto; además, si funciona bien con sus datos de entrenamiento, funcionará igual con cualquier entrada nueva».

Identifica dos errores y propone un procedimiento para comprobar la fiabilidad del sistema.

**Solución comentada:**

**Primer error: confundir fluidez con veracidad.**  
Un modelo puede generar texto coherente que contenga errores, referencias inexistentes o afirmaciones sin respaldo. La calidad de la redacción no garantiza la exactitud.

**Segundo error: confundir rendimiento de entrenamiento con generalización.**  
Un modelo puede ajustarse demasiado a sus ejemplos de entrenamiento —**sobreajuste**— y fallar con entradas nuevas. También puede perder rendimiento si cambia el tipo de datos que recibe.

**Procedimiento de comprobación:**

1. Definir las tareas y los criterios de evaluación antes de realizar las pruebas.
2. Preparar preguntas con respuestas verificables mediante fuentes fiables.
3. Usar pruebas independientes de los datos empleados para ajustar el modelo, en la medida en que pueda comprobarse esa independencia.
4. Incluir casos habituales, ambiguos y difíciles.
5. Registrar errores y contrastar las afirmaciones con las fuentes.
6. Mantener supervisión humana cuando un fallo pueda tener consecuencias importantes.

**Comentario:** Una evaluación aporta evidencia sobre unas condiciones concretas; no demuestra que el sistema sea infalible.

---

## Mini examen: cinco preguntas tipo test

*Cada pregunta tiene una única respuesta correcta.*

### 1. ¿Qué relación existe entre IA y aprendizaje automático?

A. Son exactamente lo mismo.  
B. El aprendizaje automático es una parte de la IA.  
C. Toda IA utiliza redes neuronales.  
D. La IA es una parte del aprendizaje automático.

**Respuesta correcta: B.** Existen otros enfoques de IA, como los basados en reglas y búsqueda.

### 2. ¿Qué sucede principalmente durante el entrenamiento de un modelo de aprendizaje automático?

A. Se garantiza que nunca cometerá errores.  
B. Se comprueba que posee conciencia.  
C. Se ajusta el modelo a partir de los datos y de un objetivo de aprendizaje.  
D. Se convierte toda respuesta en un hecho verdadero.

**Respuesta correcta: C.** El entrenamiento busca mejorar el modelo según un objetivo, sin garantizar perfección.

### 3. ¿Para qué sirve un conjunto de prueba independiente?

A. Para estimar el rendimiento con datos no utilizados para ajustar el modelo.  
B. Para asegurar que el modelo funciona en cualquier situación.  
C. Para sustituir los datos de entrenamiento.  
D. Para eliminar automáticamente todos los sesgos.

**Respuesta correcta: A.** Ayuda a evaluar la generalización, dentro de las condiciones de la prueba.

### 4. ¿Qué caracteriza al aprendizaje por refuerzo?

A. Utiliza exclusivamente imágenes etiquetadas.  
B. No tiene ningún objetivo.  
C. Consiste únicamente en agrupar datos semejantes.  
D. Aprende a elegir acciones mediante una señal de recompensa.

**Respuesta correcta: D.** El agente busca mejorar el resultado acumulado de sus acciones.

### 5. ¿Qué puede concluirse de una respuesta fluida de una IA generativa?

A. Que su contenido ha sido verificado.  
B. Que todas sus fuentes existen.  
C. Que puede ser lingüísticamente convincente sin ser correcta.  
D. Que el sistema comprende exactamente como una persona.

**Respuesta correcta: C.** La fluidez y la exactitud deben evaluarse por separado.
