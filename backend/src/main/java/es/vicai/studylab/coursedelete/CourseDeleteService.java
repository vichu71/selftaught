package es.vicai.studylab.coursedelete;

import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import es.vicai.studylab.web.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Borrar cursos propios (F13, ADR-84 a 86): definitivo, todo o nada y en una transacción. Solo el adulto propietario; un curso en cola o generándose se rechaza.
 * No habla con el modelo. Ni logs ni auditoría llevan títulos: solo ids.
 */
@Service
public class CourseDeleteService {

    static final int MAX_IDS = 50;
    private static final Set<String> BUSY = Set.of("PENDING_GENERATION", "GENERATING");

    private final CourseDeleteRepository repo;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public CourseDeleteService(CourseDeleteRepository repo, AuditService audit, TransactionTemplate tx) {
        this.repo = repo;
        this.audit = audit;
        this.tx = tx;
    }

    /** {@code DELETE /api/courses/{id}}: un id mal formado responde como uno inexistente. */
    public void deleteOne(AuthUser user, String rawId, String ip) {
        rejectMinor(user);
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound();
        }
        delete(user, List.of(id), ip);
    }

    /** {@code POST /api/courses/delete {ids}}: de 1 a 50 ids distintos y bien formados (400 si no). */
    public void deleteMany(AuthUser user, Map<String, Object> body, String ip) {
        rejectMinor(user);
        delete(user, parse(body), ip);
    }

    private static void rejectMinor(AuthUser user) {
        if (user.kind() == UserKind.MINOR) throw CourseDeleteException.childCannotDelete();
    }

    private static List<UUID> parse(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> raw) || raw.isEmpty() || raw.size() > MAX_IDS) throw ApiException.validation("Indica de 1 a 50 cursos");
        Set<UUID> seen = new HashSet<>();
        for (Object item : raw) {
            UUID id;
            try {
                if (!(item instanceof String text)) throw ApiException.validation("Los cursos no son válidos");
                id = UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("Los cursos no son válidos");
            }
            if (!seen.add(id)) throw ApiException.validation("Hay cursos repetidos");
        }
        return List.copyOf(seen);
    }

    private void delete(AuthUser user, List<UUID> ids, String ip) {
        tx.executeWithoutResult(status -> {
            List<CourseDeleteRepository.Locked> locked = repo.lock(ids, user.id());
            if (locked.size() != ids.size()) throw ApiException.notFound();
            if (locked.stream().anyMatch(c -> BUSY.contains(c.status()))) throw CourseDeleteException.busy();
            repo.blankIntakes(locked.stream().map(CourseDeleteRepository.Locked::intakeId).toList(), user.id());
            repo.delete(ids, user.id());
            for (CourseDeleteRepository.Locked course : locked) {
                audit.record("COURSE_DELETED", AuditService.OK, user.id(), user.id(), ip, Map.of("courseId", course.id().toString()));
            }
        });
    }
}
