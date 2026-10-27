package es.vicai.studylab.ai.benchmark;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @param completed false si el run se detuvo antes de gastar más que su propio tope
 * @param failures  modelos que fallaron (tipo de error genérico): se omite el resto de sus llamadas y el run sigue con los demás
 */
public record ImageBenchmarkResult(Path report, Path page, boolean completed, List<String> topics, int calls, BigDecimal spentUsd, Map<ModelRef, String> failures) {}
