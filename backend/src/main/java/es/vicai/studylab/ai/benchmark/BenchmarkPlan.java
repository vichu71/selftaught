package es.vicai.studylab.ai.benchmark;

import java.math.BigDecimal;
import java.util.List;

/** Qué modelos comparar y cuánto puede gastar como máximo el run (USD). */
public record BenchmarkPlan(List<ModelRef> models, BigDecimal budgetUsd) {

    public static BenchmarkPlan of(List<ModelRef> models, BigDecimal budgetUsd) {
        if (models == null || models.isEmpty()) throw new IllegalArgumentException("Indica al menos un modelo");
        if (budgetUsd == null || budgetUsd.signum() <= 0) throw new IllegalArgumentException("El tope del run debe ser mayor que 0");
        return new BenchmarkPlan(List.copyOf(models), budgetUsd);
    }
}
