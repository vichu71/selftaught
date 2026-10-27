package es.vicai.studylab.ai.budget;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.audit.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Vigila el gasto mensual (ADR-19). En modo WARN (el predeterminado) avisa al llegar al umbral y al 100 % y sigue; en BLOCK,
 * además, rechaza una llamada si el tope ya se alcanzó o si su peor caso lo superaría. Con varias llamadas simultáneas el
 * sobrepaso en BLOCK queda acotado por las llamadas en curso.
 */
@Service
public class BudgetService {

    public enum Level { OK, WARNING, OVER }

    public record Status(BigDecimal percent, Level level) {}

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UsageRepository usage;
    private final AiProperties props;
    private final AuditService audit;

    public BudgetService(UsageRepository usage, AiProperties props, AuditService audit) {
        this.usage = usage;
        this.props = props;
        this.audit = audit;
    }

    /**
     * Tope personal del usuario (siempre) y, en modo BLOCK, tope global. Una llamada de coste 0 (embeddings locales) nunca se
     * bloquea. El tope personal se comprueba primero: es el que explica el rechazo a quien lo ha alcanzado.
     */
    public void checkBeforeCall(UUID userId, BigDecimal worstCaseUsd) {
        if (worstCaseUsd.signum() == 0) return;
        BigDecimal userSpent = usage.userMonthSpend(userId);
        if (userSpent.add(worstCaseUsd).compareTo(effectiveUserLimit(userId)) > 0) {
            audit.record("AI_USER_LIMIT_REACHED", AuditService.DENIED, userId, userId, null, Map.of("month", usage.currentMonth()));
            log.warn("Llamada de IA rechazada: tope mensual personal alcanzado");
            throw AiException.of(AiErrorKind.USER_LIMIT);
        }
        if (props.budget().mode() != AiProperties.BudgetMode.BLOCK) return;
        BigDecimal spent = usage.monthSpend();
        if (spent.add(worstCaseUsd).compareTo(props.budget().effectiveLimit()) > 0) {
            audit.record("AI_BUDGET_BLOCKED", AuditService.DENIED, userId, userId, null, Map.of("month", usage.currentMonth()));
            log.warn("Llamada de IA rechazada: tope mensual alcanzado");
            throw AiException.of(AiErrorKind.BUDGET_BLOCKED);
        }
    }

    /** Tope personal de un usuario y si ya lo alcanzó este mes. */
    public record UserLimit(BigDecimal limitUsd, boolean reached) {}

    public UserLimit userLimit(UUID userId, boolean minor, BigDecimal spentUsd) {
        BigDecimal limit = usage.userLimitOverride(userId).orElseGet(() -> props.budget().userLimit(minor));
        return new UserLimit(limit, spentUsd.compareTo(limit) >= 0);
    }

    /** El tope personal que vale hoy: la excepción que fijó el propietario (F14) o, si no hay, el de la configuración según sea menor o adulto. */
    private BigDecimal effectiveUserLimit(UUID userId) {
        return usage.userLimitOverride(userId).orElseGet(() -> props.budget().userLimit(usage.isMinor(userId)));
    }

    /** Tras una llamada con coste: avisa una vez por mes al llegar al umbral y al 100 %. */
    public void afterCall(UUID userId) {
        BigDecimal limit = props.budget().effectiveLimit();
        BigDecimal spent = usage.monthSpend();
        String month = null;
        for (int threshold : new TreeSet<>(java.util.List.of(props.budget().warnAtPercent(), 100))) {
            // spent / limit >= threshold / 100, sin redondeos
            if (spent.multiply(HUNDRED).compareTo(limit.multiply(BigDecimal.valueOf(threshold))) < 0) continue;
            if (month == null) month = usage.currentMonth();
            if (usage.raiseAlert(month, threshold)) {
                BigDecimal percent = percent(spent, limit);
                audit.record("AI_BUDGET_WARNING", AuditService.OK, userId, null, null,
                        Map.of("threshold", threshold, "percent", percent, "month", month));
                log.warn("Gasto de IA al {} % del tope mensual (aviso del {} %)", percent, threshold);
            }
        }
    }

    public Status status() {
        BigDecimal limit = props.budget().effectiveLimit();
        BigDecimal spent = usage.monthSpend();
        Level level = spent.multiply(HUNDRED).compareTo(limit.multiply(HUNDRED)) >= 0 ? Level.OVER
                : spent.multiply(HUNDRED).compareTo(limit.multiply(BigDecimal.valueOf(props.budget().warnAtPercent()))) >= 0
                        ? Level.WARNING : Level.OK;
        return new Status(percent(spent, limit), level);
    }

    private static BigDecimal percent(BigDecimal spent, BigDecimal limit) {
        return spent.multiply(HUNDRED).divide(limit, 1, RoundingMode.HALF_UP);
    }
}
