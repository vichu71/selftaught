package es.vicai.studylab.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import es.vicai.studylab.ai.budget.BudgetService;
import es.vicai.studylab.ai.budget.UsageRepository;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de gasto de IA (solo lectura). Cada usuario ve lo suyo; el adulto además ve el estado del tope global, su tope personal
 * y lo de SUS menores (tutela de F01) con el tope de cada uno. El menor solo ve su número de llamadas y si alcanzó su tope:
 * ni importes ni topes.
 */
@RestController
public class AiUsageController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record You(long calls, BigDecimal spendUsd, BigDecimal limitUsd, boolean limitReached) {}

    public record Budget(BigDecimal percent, BudgetService.Level level) {}

    public record Minor(UUID id, String displayName, long calls, BigDecimal spendUsd, BigDecimal limitUsd, boolean limitReached) {}

    /** Una tarea con las veces que se usó este mes; el importe solo lo ve un adulto (un menor nunca recibe importes). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskUse(String task, long calls, BigDecimal spendUsd) {}

    public record UsageTasks(String month, List<TaskUse> tasks) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UsageResponse(String month, You you, Budget budget, List<Minor> minors) {}

    private final UsageRepository usage;
    private final BudgetService budget;

    public AiUsageController(UsageRepository usage, BudgetService budget) {
        this.usage = usage;
        this.budget = budget;
    }

    /**
     * Gasto del mes de QUIEN LLAMA repartido por tarea, de más a menos (F15, ADR-92). Sin parámetros: el usuario sale siempre de la sesión.
     * Un menor recibe solo la tarea y las veces, nunca importes.
     */
    @GetMapping("/api/ai/usage/tasks")
    UsageTasks tasks(@AuthenticationPrincipal AuthUser user) {
        boolean minor = user.kind() == UserKind.MINOR;
        List<TaskUse> tasks = usage.userMonthByTask(user.id()).stream()
                .map(t -> new TaskUse(t.task(), t.calls(), minor ? null : t.spendUsd())).toList();
        return new UsageTasks(usage.currentMonth(), tasks);
    }

    @GetMapping("/api/ai/usage")
    UsageResponse usage(@AuthenticationPrincipal AuthUser user) {
        String month = usage.currentMonth();
        UsageRepository.UserUsage mine = usage.userMonth(user.id());
        if (user.kind() == UserKind.MINOR) {
            BudgetService.UserLimit limit = budget.userLimit(user.id(), true, mine.spendUsd());
            return new UsageResponse(month, new You(mine.calls(), null, null, limit.reached()), null, null);
        }
        BudgetService.Status status = budget.status();
        BudgetService.UserLimit limit = budget.userLimit(user.id(), false, mine.spendUsd());
        List<Minor> minors = usage.minorsMonth(user.id()).stream().map(m -> {
            BudgetService.UserLimit minorLimit = budget.userLimit(m.id(), true, m.spendUsd());
            return new Minor(m.id(), m.displayName(), m.calls(), m.spendUsd(), minorLimit.limitUsd(), minorLimit.reached());
        }).toList();
        return new UsageResponse(month, new You(mine.calls(), mine.spendUsd(), limit.limitUsd(), limit.reached()),
                new Budget(status.percent(), status.level()), minors);
    }
}
