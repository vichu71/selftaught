package es.vicai.studylab.ai.benchmark;

import java.math.BigDecimal;
import java.util.List;

/** Qué modelos de imagen comparar y cuánto puede gastar como máximo el run (USD). {@code topicOverride} sustituye los temas del fichero (solo para pruebas). */
public record ImageBenchmarkPlan(List<ModelRef> models, BigDecimal budgetUsd, String topicOverride) {

    public static ImageBenchmarkPlan of(List<ModelRef> models, BigDecimal budgetUsd) {
        if (models == null || models.isEmpty()) throw new IllegalArgumentException("Indica al menos un modelo");
        if (budgetUsd == null || budgetUsd.signum() <= 0) throw new IllegalArgumentException("El tope del run debe ser mayor que 0");
        return new ImageBenchmarkPlan(List.copyOf(models), budgetUsd, null);
    }

    public ImageBenchmarkPlan withTopicOverride(String topic) {
        return new ImageBenchmarkPlan(models, budgetUsd, topic);
    }
}
