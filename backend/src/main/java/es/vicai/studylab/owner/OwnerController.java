package es.vicai.studylab.owner;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los cinco informes de solo lectura del panel del propietario (F14). Cada uno empieza por la guardia (404 para quien no es propietario) y por el límite de frecuencia; ninguno
 * devuelve contenido de nadie.
 */
@RestController
public class OwnerController {

    private final OwnerGuard guard;
    private final OwnerRateLimiter limiter;
    private final OwnerReportService reports;

    public OwnerController(OwnerGuard guard, OwnerRateLimiter limiter, OwnerReportService reports) {
        this.guard = guard;
        this.limiter = limiter;
        this.reports = reports;
    }

    @GetMapping("/api/owner/ai")
    OwnerReports.Ai ai(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String range, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.QUERY);
        return reports.ai(OwnerRange.parse(range));
    }

    @GetMapping("/api/owner/prices")
    OwnerReports.Prices prices(@AuthenticationPrincipal AuthUser user, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.QUERY);
        return reports.prices();
    }

    @GetMapping("/api/owner/accounts")
    OwnerReports.Accounts accounts(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String range, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.QUERY);
        return reports.accounts(OwnerRange.parse(range));
    }

    @GetMapping("/api/owner/usage")
    OwnerReports.Usage usage(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String range, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.QUERY);
        return reports.usage(OwnerRange.parse(range));
    }

    @GetMapping("/api/owner/health")
    OwnerReports.Health health(@AuthenticationPrincipal AuthUser user, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.QUERY);
        return reports.health();
    }
}
