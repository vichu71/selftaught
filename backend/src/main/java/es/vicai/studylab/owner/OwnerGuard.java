package es.vicai.studylab.owner;

import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Lo primero de cada endpoint de {@code /api/owner/**}. Quien tiene sesión y no es propietario recibe exactamente la respuesta que da Spring a una ruta que no existe
 * (la misma excepción, el mismo manejador): así el panel no se distingue de una ruta inventada. Un adulto que lo intenta deja un {@code ACCESS_DENIED} con su id, como en F08.
 */
@Component
public class OwnerGuard {

    private final OwnerPolicy policy;
    private final AuditService audit;

    public OwnerGuard(OwnerPolicy policy, AuditService audit) {
        this.policy = policy;
        this.audit = audit;
    }

    public void require(AuthUser user, HttpServletRequest request) throws NoResourceFoundException {
        if (policy.isOwner(user)) return;
        if (user != null && user.kind() == UserKind.ADULT) {
            audit.record("ACCESS_DENIED", AuditService.DENIED, user.id(), null, request.getRemoteAddr(), Map.of("resource", "owner"));
        }
        String path = request.getRequestURI();
        throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), path.startsWith("/") ? path.substring(1) : path);
    }
}
