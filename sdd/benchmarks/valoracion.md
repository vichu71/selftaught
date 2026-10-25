# Valoración del benchmark F02 (25-10-2026)

Hecha por Claude leyendo las salidas de `raw/`. Muestra leída: las 3 lecciones de inglés, las 3 fichas del tema de IA y las 3 evaluaciones de
cocina (el resto no se leyó). Es una valoración **cualitativa**, no sustituye a la del usuario. Datos de coste y tokens: `report.md`.

## Resultado técnico
- Las 27 llamadas funcionaron: **los tres adaptadores quedan verificados contra las APIs reales** (incluida la cabecera `x-goog-api-key`).
- Ninguna respuesta vacía con razonamiento. Una `LENGTH`: Haiku, evaluación de cocina, truncada a mitad del solucionario (6 000 tokens).
  Acción: `app.ai.tasks.assessment.max-output-tokens` sube a 10 000 (máximo duro 16 000).
- Las salidas son **Markdown libre**, no JSON: en F05 hará falta pedir un formato estructurado y validarlo.
- Gemini y OpenAI escriben fórmulas en **LaTeX** (`$…$`, `\[…\]`); Haiku no. El frontend no renderiza LaTeX: pedirlo explícitamente en
  la plantilla ("sin LaTeX") o añadir un renderizador seguro.

## Fichas (OUTLINE, tema IA)
| Modelo | Valoración |
|---|---|
| Haiku 5.5 | Más detallada y la mejor adaptada al nivel (cada capítulo con su contenido, requisitos realistas). Muy buena para su coste. |
| Gemini 3.6 Flash | Correcta pero genérica; lista temas sin detalle. Dirigida a un público muy amplio. |
| GPT-6.1 Sol | La más concisa y mejor secuenciada; precisa, pero con poco detalle por capítulo. |

## Lecciones (LESSON, tema inglés)
| Modelo | Valoración |
|---|---|
| Haiku 5.5 | Completa y práctica (tabla de formas, contracciones, negación, preguntas, respuestas cortas). Sin errores vistos. |
| Gemini 3.6 Flash | La más cercana y didáctica; presente simple frente a continuo, con ejemplos explicados. Sin errores vistos; algo larga. |
| GPT-6.1 Sol | La más pedagógica en el método (ejemplos paso a paso, transformaciones). Más corta; sin errores vistos. |

## Evaluaciones (ASSESSMENT, tema cocina) — donde más se diferencian
| Modelo | Valoración |
|---|---|
| Haiku 5.5 | Ejercicios numéricos bien resueltos (1, 2, 5), pero el 3 mezcla un apartado absurdo y el 4 es confuso; en el test, la pregunta 4 da 3:1 como proporción clásica y el ejercicio 4 usaba 2:1. Truncada. **Calidad irregular.** |
| Gemini 3.6 Flash | Contenido profundo, pero **nivel demasiado alto para "cocina básica"** (mayonesa cortada, fisicoquímica) y un fallo de redacción ("vertiéndola del tiempo"). Examen más difícil que los ejercicios. |
| GPT-6.1 Sol | **La mejor**: ejercicios prácticos y de dificultad creciente real, cálculos correctos, seguridad alimentaria bien tratada y examen alineado con lo ejercitado. |

## Recomendación provisional por tarea
| Tarea | Modelo | Motivo |
|---|---|---|
| `OUTLINE` | Haiku 5.5 | Mejor detalle y coste ~0,0006 USD por ficha |
| `LESSON` | Haiku 5.5 o Gemini 3.6 Flash (a decidir con la valoración del usuario) | Calidad parecida; Haiku cuesta una sexta parte |
| `ASSESSMENT` | GPT-6.1 Sol | Mayor calidad y coherencia; es lo que más importa acertar (respuestas correctas) |
| `TUTOR` | Haiku 5.5 | Se llama mucho: rapidez y coste |

Coste estimado de un curso tipo con esta mezcla: ≈ 0,0006 + 8 × 0,001 + 9 × 0,02 ≈ **0,19 USD**. Un test de 3 temas es poca muestra:
conviene repetir con temas del uso real y con temas "difíciles" (matemáticas, programación) antes de fijar los modelos definitivos.

## Pendiente
- Contrastar el gasto total del run (0,2079 USD) con los paneles de Anthropic, Google y OpenAI. **Precio de OpenAI aún sin confirmar.**
- Valoración manual del usuario en la hoja de `report.md`.
- Repetir el run tras subir el límite de `ASSESSMENT` para comprobar que Haiku ya no se trunca (opcional).
