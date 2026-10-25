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
 * Consulta de gasto de IA (solo lectura). Cada usuario ve lo suyo; el adulto además ve el estado del tope global y lo de SUS
 * menores (tutela de F01). El menor solo ve su número de llamadas: ni importes ni tope.
 */
@RestController
public class AiUsageController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record You(long calls, BigDecimal spendUsd) {}

    public record Budget(BigDecimal percent, BudgetService.Level level) {}

    public record Minor(UUID id, String displayName, long calls, BigDecimal spendUsd) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UsageResponse(String month, You you, Budget budget, List<Minor> minors) {}

    private final UsageRepository usage;
    private final BudgetService budget;

    public AiUsageController(UsageRepository usage, BudgetService budget) {
        this.usage = usage;
        this.budget = budget;
    }

    @GetMapping("/api/ai/usage")
    UsageResponse usage(@AuthenticationPrincipal AuthUser user) {
        String month = usage.currentMonth();
        UsageRepository.UserUsage mine = usage.userMonth(user.id());
        if (user.kind() == UserKind.MINOR) {
            return new UsageResponse(month, new You(mine.calls(), null), null, null);
        }
        BudgetService.Status status = budget.status();
        List<Minor> minors = usage.minorsMonth(user.id()).stream()
                .map(m -> new Minor(m.id(), m.displayName(), m.calls(), m.spendUsd())).toList();
        return new UsageResponse(month, new You(mine.calls(), mine.spendUsd()), new Budget(status.percent(), status.level()), minors);
    }
}
