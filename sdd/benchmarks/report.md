# Benchmark F02 — 2026-10-25

Tope del run: 5 USD · Gastado: 0.207889 USD · Llamadas: 27

Curso tipo = 1 ficha + 8 lecciones + 9 evaluaciones (8 de ejercicios y 1 examen). Las salidas completas están en `raw/`.

## Tema 1: Inglés de 2º de la ESO

| Modelo | Tarea | Tok. entrada | Tok. salida | Coste (USD) | ms | Fin |
|---|---|---:|---:|---:|---:|---|
| anthropic/claude-haiku-5-5 | OUTLINE | 249 | 1539 | 0.000794 | 8032 | STOP |
| anthropic/claude-haiku-5-5 | LESSON | 242 | 2077 | 0.001063 | 9824 | STOP |
| anthropic/claude-haiku-5-5 | ASSESSMENT | 244 | 4153 | 0.002101 | 18736 | STOP |
| gemini/gemini-3.6-flash | OUTLINE | 146 | 1515 | 0.005791 | 11103 | STOP |
| gemini/gemini-3.6-flash | LESSON | 138 | 2545 | 0.009647 | 21292 | STOP |
| gemini/gemini-3.6-flash | ASSESSMENT | 137 | 3517 | 0.013292 | 24224 | STOP |
| openai/gpt-6.1-sol | OUTLINE | 154 | 479 | 0.005098 | 10271 | STOP |
| openai/gpt-6.1-sol | LESSON | 148 | 1068 | 0.010976 | 20460 | STOP |
| openai/gpt-6.1-sol | ASSESSMENT | 144 | 1835 | 0.018638 | 26659 | STOP |

## Tema 2: Inteligencia artificial: qué es y cómo funciona

| Modelo | Tarea | Tok. entrada | Tok. salida | Coste (USD) | ms | Fin |
|---|---|---:|---:|---:|---:|---|
| anthropic/claude-haiku-5-5 | OUTLINE | 256 | 1181 | 0.000616 | 5448 | STOP |
| anthropic/claude-haiku-5-5 | LESSON | 249 | 2009 | 0.001029 | 10145 | STOP |
| anthropic/claude-haiku-5-5 | ASSESSMENT | 251 | 5841 | 0.002946 | 23021 | STOP |
| gemini/gemini-3.6-flash | OUTLINE | 147 | 1419 | 0.005432 | 8437 | STOP |
| gemini/gemini-3.6-flash | LESSON | 139 | 1829 | 0.006963 | 16851 | STOP |
| gemini/gemini-3.6-flash | ASSESSMENT | 138 | 3771 | 0.014245 | 20888 | STOP |
| openai/gpt-6.1-sol | OUTLINE | 154 | 390 | 0.004208 | 7063 | STOP |
| openai/gpt-6.1-sol | LESSON | 148 | 1009 | 0.010386 | 24005 | STOP |
| openai/gpt-6.1-sol | ASSESSMENT | 144 | 1912 | 0.019408 | 30852 | STOP |

## Tema 3: Cocina básica: técnicas y recetas esenciales

| Modelo | Tarea | Tok. entrada | Tok. salida | Coste (USD) | ms | Fin |
|---|---|---:|---:|---:|---:|---|
| anthropic/claude-haiku-5-5 | OUTLINE | 255 | 977 | 0.000514 | 5029 | STOP |
| anthropic/claude-haiku-5-5 | LESSON | 248 | 2305 | 0.001177 | 12411 | STOP |
| anthropic/claude-haiku-5-5 | ASSESSMENT | 250 | 6000 | 0.003025 | 27009 | LENGTH |
| gemini/gemini-3.6-flash | OUTLINE | 145 | 1503 | 0.005745 | 11157 | STOP |
| gemini/gemini-3.6-flash | LESSON | 137 | 2463 | 0.009339 | 15730 | STOP |
| gemini/gemini-3.6-flash | ASSESSMENT | 136 | 4570 | 0.017240 | 37909 | STOP |
| openai/gpt-6.1-sol | OUTLINE | 153 | 433 | 0.004636 | 8558 | STOP |
| openai/gpt-6.1-sol | LESSON | 147 | 1112 | 0.011414 | 24235 | STOP |
| openai/gpt-6.1-sol | ASSESSMENT | 143 | 2188 | 0.022166 | 37069 | STOP |

## Coste estimado por curso

| Modelo | USD por curso tipo |
|---|---:|
| anthropic/claude-haiku-5-5 | 0.033580 |
| gemini/gemini-3.6-flash | 0.209190 |
| openai/gpt-6.1-sol | 0.272686 |

Es una **estimación** con la tabla de precios de `ai-pricing.properties`: contrástala con el panel de cada proveedor.

## Hoja de valoración manual (1–5)

Lee las salidas de `raw/` y puntúa. Criterios: precisión del contenido, claridad, adecuación al nivel y formato.

| Modelo | Tema | Tarea | Precisión | Claridad | Nivel | Formato | Notas |
|---|---|---|:-:|:-:|:-:|:-:|---|
| anthropic/claude-haiku-5-5 | Inglés de 2º de la ESO | OUTLINE |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Inglés de 2º de la ESO | LESSON |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Inglés de 2º de la ESO | ASSESSMENT |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Inteligencia artificial: qué es y cómo funciona | OUTLINE |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Inteligencia artificial: qué es y cómo funciona | LESSON |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Inteligencia artificial: qué es y cómo funciona | ASSESSMENT |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Cocina básica: técnicas y recetas esenciales | OUTLINE |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Cocina básica: técnicas y recetas esenciales | LESSON |  |  |  |  |  |
| anthropic/claude-haiku-5-5 | Cocina básica: técnicas y recetas esenciales | ASSESSMENT |  |  |  |  |  |
| gemini/gemini-3.6-flash | Inglés de 2º de la ESO | OUTLINE |  |  |  |  |  |
| gemini/gemini-3.6-flash | Inglés de 2º de la ESO | LESSON |  |  |  |  |  |
| gemini/gemini-3.6-flash | Inglés de 2º de la ESO | ASSESSMENT |  |  |  |  |  |
| gemini/gemini-3.6-flash | Inteligencia artificial: qué es y cómo funciona | OUTLINE |  |  |  |  |  |
| gemini/gemini-3.6-flash | Inteligencia artificial: qué es y cómo funciona | LESSON |  |  |  |  |  |
| gemini/gemini-3.6-flash | Inteligencia artificial: qué es y cómo funciona | ASSESSMENT |  |  |  |  |  |
| gemini/gemini-3.6-flash | Cocina básica: técnicas y recetas esenciales | OUTLINE |  |  |  |  |  |
| gemini/gemini-3.6-flash | Cocina básica: técnicas y recetas esenciales | LESSON |  |  |  |  |  |
| gemini/gemini-3.6-flash | Cocina básica: técnicas y recetas esenciales | ASSESSMENT |  |  |  |  |  |
| openai/gpt-6.1-sol | Inglés de 2º de la ESO | OUTLINE |  |  |  |  |  |
| openai/gpt-6.1-sol | Inglés de 2º de la ESO | LESSON |  |  |  |  |  |
| openai/gpt-6.1-sol | Inglés de 2º de la ESO | ASSESSMENT |  |  |  |  |  |
| openai/gpt-6.1-sol | Inteligencia artificial: qué es y cómo funciona | OUTLINE |  |  |  |  |  |
| openai/gpt-6.1-sol | Inteligencia artificial: qué es y cómo funciona | LESSON |  |  |  |  |  |
| openai/gpt-6.1-sol | Inteligencia artificial: qué es y cómo funciona | ASSESSMENT |  |  |  |  |  |
| openai/gpt-6.1-sol | Cocina básica: técnicas y recetas esenciales | OUTLINE |  |  |  |  |  |
| openai/gpt-6.1-sol | Cocina básica: técnicas y recetas esenciales | LESSON |  |  |  |  |  |
| openai/gpt-6.1-sol | Cocina básica: técnicas y recetas esenciales | ASSESSMENT |  |  |  |  |  |
