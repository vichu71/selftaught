package es.vicai.studylab.ai.benchmark;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @param completed   false si el run se detuvo al alcanzar su propio tope
 * @param costPerCourse coste estimado de un curso tipo por modelo (solo los modelos con las tres tareas medidas)
 */
public record BenchmarkResult(Path report, boolean completed, List<String> topics, int calls, BigDecimal spentUsd,
                              Map<ModelRef, BigDecimal> costPerCourse) {}
