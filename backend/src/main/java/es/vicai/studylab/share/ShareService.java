package es.vicai.studylab.share;

import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.course.CourseException;
import es.vicai.studylab.courseedit.CourseActionRepository;
import es.vicai.studylab.courseedit.CourseEditRepository;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import es.vicai.studylab.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Compartir un curso por código (F09, ADR-64 y 65). El código son 80 bits de {@link SecureRandom}; solo se guarda su SHA-256 y se enseña una vez. Canjear normaliza el texto, busca
 * por hash y no distingue por qué falla (siempre el mismo 404); los fallos y las copias por hora se limitan por usuario en la BD. La copia la hace {@link CourseCopier}. Ni el código
 * ni los textos van a logs ni a la auditoría.
 */
@Service
public class ShareService {

    /** Sin I, O, 0 ni 1 para que no se confundan al copiarlos a mano: 32 símbolos = 5 bits por carácter, 16 caracteres = 80 bits. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 16;
    private static final Pattern VALID = Pattern.compile("[A-HJ-NP-Z2-9]{16}");
    private static final int MAX_INPUT = 100;

    public record Created(UUID id, String code, Instant expiresAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CodeView(UUID id, Instant createdAt, Instant expiresAt, Instant revokedAt, int uses, boolean active) {}

    public record Redeemed(UUID id, String title) {}

    private enum Kind { CREATED, NOT_FOUND, BLOCKED }

    private record Outcome(Kind kind, UUID copy, ShareRepository.Target target) {}

    private final ShareRepository repo;
    private final CourseEditRepository courses;
    private final CourseActionRepository actions;
    private final CourseCopier copier;
    private final ShareProperties props;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public ShareService(ShareRepository repo, CourseEditRepository courses, CourseActionRepository actions, CourseCopier copier, ShareProperties props, AuditService audit,
                        TransactionTemplate tx) {
        this.repo = repo;
        this.courses = courses;
        this.actions = actions;
        this.copier = copier;
        this.props = props;
        this.audit = audit;
        this.tx = tx;
    }

    // ---------- el propietario ----------

    public Created create(AuthUser user, String rawCourse, Map<String, Object> body, String ip) {
        UUID courseId = id(rawCourse);
        int days = days(body);
        UUID codeId = UUID.randomUUID();
        Created created = tx.execute(status -> {
            String state = courses.lockCourse(courseId, user.id()).orElseThrow(ApiException::notFound);
            if (!"READY".equals(state)) throw new ApiException(HttpStatus.CONFLICT, "COURSE_NOT_READY", "Solo se puede compartir un curso terminado");
            if (repo.activeCount(courseId, user.id()) >= props.maxActive()) throw new ApiException(HttpStatus.CONFLICT, "SHARE_LIMIT", "Un curso puede tener como máximo cinco códigos activos");
            String code = newCode();
            Instant expires = repo.insert(codeId, courseId, user.id(), sha256(code), days);
            return new Created(codeId, display(code), expires);
        });
        audit.record("COURSE_SHARE_CREATED", AuditService.OK, user.id(), user.id(), ip, Map.of("courseId", courseId.toString(), "codeId", codeId.toString()));
        return created;
    }

    public List<CodeView> list(AuthUser user, String rawCourse) {
        UUID courseId = id(rawCourse);
        if (!courses.courseExists(courseId, user.id())) throw ApiException.notFound();
        return repo.list(courseId, user.id()).stream().map(c -> new CodeView(c.id(), c.createdAt(), c.expiresAt(), c.revokedAt(), c.uses(), c.active())).toList();
    }

    public void revoke(AuthUser user, String rawCourse, String rawCode, String ip) {
        UUID courseId = id(rawCourse);
        UUID codeId = id(rawCode);
        if (!courses.courseExists(courseId, user.id())) throw ApiException.notFound();
        if (repo.revoke(codeId, courseId, user.id()) == 1) {
            audit.record("COURSE_SHARE_REVOKED", AuditService.OK, user.id(), user.id(), ip, Map.of("courseId", courseId.toString(), "codeId", codeId.toString()));
        } else if (!repo.exists(codeId, courseId, user.id())) {
            throw ApiException.notFound();
        }
    }

    // ---------- quien canjea ----------

    public Redeemed redeem(AuthUser user, Map<String, Object> body, String ip) {
        if (body == null || !(body.get("code") instanceof String code)) throw ApiException.validation("Escribe el código");
        boolean minor = user.kind() == UserKind.MINOR;
        Outcome outcome = tx.execute(status -> {
            actions.lockUser(user.id());
            actions.purgeOld(user.id());
            if (actions.inTheLastHour(user.id(), "REDEEM_FAIL") >= props.maxFailuresPerHour() || actions.inTheLastHour(user.id(), "REDEEM_OK") >= props.maxCopiesPerHour()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CODE_RATE_LIMITED", "Has probado demasiados códigos seguidos. Espera un rato e inténtalo de nuevo");
            }
            String normalized = normalize(code);
            var target = normalized == null ? java.util.Optional.<ShareRepository.Target>empty() : repo.findByHash(sha256(normalized));
            if (target.isEmpty()) return fail(user);
            CourseCopier.Result copy = copier.copy(target.get().courseId(), target.get().ownerId(), user.id(), minor);
            if (copy.status() == CourseCopier.Status.BLOCKED) return new Outcome(Kind.BLOCKED, null, target.get());
            if (copy.status() == CourseCopier.Status.UNAVAILABLE) return fail(user);
            repo.addUse(target.get().codeId());
            actions.record(user.id(), "REDEEM_OK");
            return new Outcome(Kind.CREATED, copy.courseId(), target.get());
        });

        return switch (outcome.kind()) {
            case NOT_FOUND -> {
                audit.record("SHARE_CODE_FAILED", AuditService.DENIED, user.id(), user.id(), ip, Map.of());
                throw ApiException.notFound();
            }
            case BLOCKED -> {
                audit.record("COURSE_COPY_BLOCKED", AuditService.DENIED, user.id(), user.id(), ip, Map.of("courseId", outcome.target().courseId().toString()));
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COPY_NOT_ALLOWED", CourseException.BLOCKED_MESSAGE);
            }
            case CREATED -> {
                audit.record("COURSE_SHARE_REDEEMED", AuditService.OK, user.id(), outcome.target().ownerId(), ip,
                        Map.of("courseId", outcome.target().courseId().toString(), "copyId", outcome.copy().toString(), "codeId", outcome.target().codeId().toString()));
                yield new Redeemed(outcome.copy(), title(outcome.copy(), user.id()));
            }
        };
    }

    private Outcome fail(AuthUser user) {
        actions.record(user.id(), "REDEEM_FAIL");
        return new Outcome(Kind.NOT_FOUND, null, null);
    }

    private String title(UUID course, UUID owner) {
        return courses.title(course, owner);
    }

    // ---------- el código ----------

    private String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    /** En grupos de cuatro para copiarlo y leerlo cómodo. */
    private static String display(String code) {
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8, 12) + "-" + code.substring(12);
    }

    /** Mayúsculas, sin espacios ni guiones; {@code null} si no puede ser un código nuestro. */
    static String normalize(String raw) {
        if (raw.length() > MAX_INPUT) return null;
        String code = raw.strip().toUpperCase().replaceAll("[\\s-]", "");
        return VALID.matcher(code).matches() ? code : null;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private int days(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("days");
        if (raw == null) return props.defaultDays();
        if (raw instanceof Integer days && days >= 1 && days <= props.maxDays()) return days;
        throw ApiException.validation("La caducidad debe ser de 1 a " + props.maxDays() + " días");
    }

    private static UUID id(String raw) {
        try {
            if (raw == null) throw ApiException.notFound();
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound();
        }
    }
}
