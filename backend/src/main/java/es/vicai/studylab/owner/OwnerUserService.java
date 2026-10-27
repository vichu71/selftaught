package es.vicai.studylab.owner;

import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.auth.SessionService;
import es.vicai.studylab.owner.OwnerReports.UserPage;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserRepository;
import es.vicai.studylab.user.UserStatus;
import es.vicai.studylab.web.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La lista de usuarios y las acciones del propietario sobre una cuenta (F14, ADR-88 y 91): desactivar, reactivar, revocar sesiones y fijar o quitar el tope. Cada acción es una
 * transacción con la cuenta bloqueada y su auditoría dentro: si algo falla no queda nada a medias. Repetir un cambio no hace nada (ni se audita otra vez). El propietario no puede
 * desactivarse ni revocarse a sí mismo. La auditoría lleva ids y, en el tope, el importe; nunca correos ni nombres.
 */
@Service
public class OwnerUserService {

    private static final Set<String> KINDS = Set.of("ADULT", "MINOR");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "PENDING_VERIFICATION", "DEACTIVATED");
    private static final Set<String> SORTS = Set.of("spend", "activity", "created");
    private static final Set<String> DIRECTIONS = Set.of("asc", "desc");
    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;
    private static final BigDecimal MAX_LIMIT = new BigDecimal("500");

    private final OwnerUserRepository repo;
    private final UserRepository users;
    private final SessionService sessions;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final AiProperties props;

    public OwnerUserService(OwnerUserRepository repo, UserRepository users, SessionService sessions, AuditService audit, TransactionTemplate tx, AiProperties props) {
        this.repo = repo;
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.tx = tx;
        this.props = props;
    }

    // ---------- lista ----------

    public UserPage list(String kind, String status, String sort, String dir, String rawPage, String rawSize) {
        String kindFilter = oneOf(kind, KINDS, "El tipo debe ser ADULT o MINOR");
        String statusFilter = oneOf(status, STATUSES, "El estado no es válido");
        String sortBy = sort == null ? "created" : oneOf(sort, SORTS, "La ordenación debe ser spend, activity o created");
        String direction = dir == null ? "desc" : oneOf(dir, DIRECTIONS, "La dirección debe ser asc o desc");
        int page = number(rawPage, 0, 0, Integer.MAX_VALUE / MAX_SIZE, "La página no es válida");
        int size = number(rawSize, DEFAULT_SIZE, 1, MAX_SIZE, "El tamaño debe estar entre 1 y 100");
        var items = repo.page(kindFilter, statusFilter, sortBy, direction, page, size, props.budget().userLimit(true), props.budget().userLimit(false));
        return new UserPage(items, repo.total(kindFilter, statusFilter), page, size);
    }

    private static String oneOf(String value, Set<String> allowed, String message) {
        if (value == null) return null;
        if (!allowed.contains(value)) throw ApiException.validation(message);
        return value;
    }

    private static int number(String raw, int fallback, int min, int max, String message) {
        if (raw == null) return fallback;
        try {
            int value = Integer.parseInt(raw.strip());
            if (value < min || value > max) throw ApiException.validation(message);
            return value;
        } catch (NumberFormatException e) {
            throw ApiException.validation(message);
        }
    }

    // ---------- acciones ----------

    public void deactivate(AuthUser owner, String rawId, String ip) {
        UUID id = other(owner, rawId);
        tx.executeWithoutResult(status -> {
            repo.lock(id).orElseThrow(ApiException::notFound);
            if (users.changeStatus(id, UserStatus.ACTIVE, UserStatus.DEACTIVATED)) {
                sessions.revokeAll(id);
                audit.record("OWNER_USER_DEACTIVATED", AuditService.OK, owner.id(), id, ip);
            }
        });
    }

    public void reactivate(AuthUser owner, String rawId, String ip) {
        UUID id = parse(rawId);
        tx.executeWithoutResult(status -> {
            OwnerUserRepository.Target target = repo.lock(id).orElseThrow(ApiException::notFound);
            if ("PENDING_VERIFICATION".equals(target.status())) throw ApiException.validation("La cuenta aún no ha verificado su correo");
            if (users.changeStatus(id, UserStatus.DEACTIVATED, UserStatus.ACTIVE)) audit.record("OWNER_USER_REACTIVATED", AuditService.OK, owner.id(), id, ip);
        });
    }

    public void revokeSessions(AuthUser owner, String rawId, String ip) {
        UUID id = other(owner, rawId);
        tx.executeWithoutResult(status -> {
            repo.lock(id).orElseThrow(ApiException::notFound);
            sessions.revokeAll(id);
            audit.record("OWNER_SESSIONS_REVOKED", AuditService.OK, owner.id(), id, ip);
        });
    }

    public void setLimit(AuthUser owner, String rawId, Map<String, Object> body, String ip) {
        UUID id = parse(rawId);
        BigDecimal limit = amount(body);
        tx.executeWithoutResult(status -> {
            repo.lock(id).orElseThrow(ApiException::notFound);
            repo.upsertLimit(id, limit, owner.id());
            audit.record("OWNER_LIMIT_SET", AuditService.OK, owner.id(), id, ip, Map.of("limitUsd", limit.toPlainString()));
        });
    }

    public void clearLimit(AuthUser owner, String rawId, String ip) {
        UUID id = parse(rawId);
        tx.executeWithoutResult(status -> {
            repo.lock(id).orElseThrow(ApiException::notFound);
            if (repo.clearLimit(id)) audit.record("OWNER_LIMIT_CLEARED", AuditService.OK, owner.id(), id, ip);
        });
    }

    /** Un id mal formado responde como uno que no existe. */
    private static UUID parse(String rawId) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound();
        }
    }

    /** El destinatario de una acción que el propietario no puede hacerse a sí mismo. */
    private static UUID other(AuthUser owner, String rawId) {
        UUID id = parse(rawId);
        if (id.equals(owner.id())) throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_TARGET_SELF", "No puedes hacerlo sobre tu propia cuenta");
        return id;
    }

    /** Un número de 0 a 500 con hasta dos decimales; un texto, un booleano o un cuerpo sin importe no valen. */
    private static BigDecimal amount(Map<String, Object> body) {
        if (body == null || !(body.get("limitUsd") instanceof Number number)) throw ApiException.validation("Indica el tope en dólares, de 0 a 500");
        BigDecimal value;
        try {
            value = new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            throw ApiException.validation("El tope no es válido");
        }
        if (value.signum() < 0 || value.compareTo(MAX_LIMIT) > 0 || value.stripTrailingZeros().scale() > 2) throw ApiException.validation("El tope debe estar entre 0 y 500, con dos decimales como mucho");
        return value.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
