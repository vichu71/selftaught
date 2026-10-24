package es.vicai.studylab.security;

import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.user.UserRepository;
import es.vicai.studylab.web.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Comprobación de permisos reutilizable por todas las features: cada dato pertenece a un usuario y un tutor
 * solo toca a sus menores. Un recurso ajeno y uno inexistente responden exactamente igual (404).
 */
@Component
public class AccessGuard {

    private final UserRepository users;
    private final AuditService audit;

    public AccessGuard(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    /** Devuelve el id del menor si {@code tutor} es su tutor; si no, audita el intento y responde 404. */
    public UUID requireStudentOf(AuthUser tutor, String rawStudentId, String ip) {
        UUID studentId = parse(rawStudentId);
        if (studentId == null || !users.isTutorOf(tutor.id(), studentId)) {
            audit.record("ACCESS_DENIED", AuditService.DENIED, tutor.id(), null, ip, Map.of("resource", "student"));
            throw ApiException.notFound();
        }
        return studentId;
    }

    private static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
