package es.vicai.studylab.owner;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La lista de usuarios y las acciones del propietario sobre una cuenta (F14). Cada endpoint empieza por la guardia y por su límite de frecuencia (consultas o acciones); las
 * acciones, además, exigen CSRF como cualquier otra escritura.
 */
@RestController
public class OwnerUserController {

    private final OwnerGuard guard;
    private final OwnerRateLimiter limiter;
    private final OwnerUserService service;

    public OwnerUserController(OwnerGuard guard, OwnerRateLimiter limiter, OwnerUserService service) {
        this.guard = guard;
        this.limiter = limiter;
        this.service = service;
    }

    @GetMapping("/api/owner/users")
    OwnerReports.UserPage users(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String kind, @RequestParam(required = false) String status,
                                @RequestParam(required = false) String sort, @RequestParam(required = false) String dir, @RequestParam(required = false) String page,
                                @RequestParam(required = false) String size, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.QUERY);
        return service.list(kind, status, sort, dir, page, size);
    }

    @PostMapping("/api/owner/users/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@AuthenticationPrincipal AuthUser user, @PathVariable String id, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.ACTION);
        service.deactivate(user, id, request.getRemoteAddr());
    }

    @PostMapping("/api/owner/users/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@AuthenticationPrincipal AuthUser user, @PathVariable String id, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.ACTION);
        service.reactivate(user, id, request.getRemoteAddr());
    }

    @PostMapping("/api/owner/users/{id}/revoke-sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSessions(@AuthenticationPrincipal AuthUser user, @PathVariable String id, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.ACTION);
        service.revokeSessions(user, id, request.getRemoteAddr());
    }

    @PutMapping("/api/owner/users/{id}/limit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setLimit(@AuthenticationPrincipal AuthUser user, @PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.ACTION);
        service.setLimit(user, id, body, request.getRemoteAddr());
    }

    @DeleteMapping("/api/owner/users/{id}/limit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearLimit(@AuthenticationPrincipal AuthUser user, @PathVariable String id, HttpServletRequest request) throws NoResourceFoundException {
        guard.require(user, request);
        limiter.check(user, OwnerRateLimiter.Kind.ACTION);
        service.clearLimit(user, id, request.getRemoteAddr());
    }
}
