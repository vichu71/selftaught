package es.vicai.studylab.user;

import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.auth.SessionService;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserRepository.StudentRow;
import es.vicai.studylab.web.ApiException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Gestión de menores por su tutor. Quién puede llamar y sobre qué menor lo decide AccessGuard antes de llegar aquí. */
@Service
public class StudentService {

    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int MAX_ATTEMPTS = 10;

    private final UserRepository users;
    private final SessionService sessions;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final SecureRandom random;

    public StudentService(UserRepository users, SessionService sessions, AuditService audit, PasswordEncoder encoder,
                          TransactionTemplate tx, SecureRandom random) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.encoder = encoder;
        this.tx = tx;
        this.random = random;
    }

    public List<StudentRow> list(AuthUser tutor) {
        return users.studentsOf(tutor.id());
    }

    /**
     * Da de alta a un menor y lo vincula al tutor. El identificador de acceso sale del texto del adulto; si ya
     * existe se le añade un sufijo corto. El nombre visible no puede repetirse entre los menores activos del tutor.
     */
    public StudentRow create(AuthUser tutor, String displayName, String loginHint, String password, String ip) {
        String base = slug(loginHint);
        if (base.length() < 3 || base.length() > 30) {
            throw ApiException.validation("El nombre de usuario debe tener entre 3 y 30 letras o números");
        }
        String name = displayName.trim();
        String hash = encoder.encode(password);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String loginId = attempt == 0 ? base : base + "-" + randomSuffix();
            UUID studentId = UUID.randomUUID();
            try {
                tx.executeWithoutResult(status -> {
                    users.lockUser(tutor.id()); // serializa altas simultáneas del mismo tutor
                    if (users.activeStudentNameExists(tutor.id(), name)) {
                        throw new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN", "Ya tienes un menor con ese nombre");
                    }
                    users.insertMinor(studentId, name, loginId, hash);
                    users.link(tutor.id(), studentId);
                });
                audit.record("STUDENT_CREATED", AuditService.OK, tutor.id(), studentId, ip);
                return new StudentRow(studentId, name, loginId, UserStatus.ACTIVE);
            } catch (DuplicateKeyException loginIdTaken) {
                // otro menor ya usa ese identificador: se reintenta con otro sufijo
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_TAKEN", "No se pudo generar un nombre de usuario libre");
    }

    public void resetPassword(AuthUser tutor, UUID studentId, String newPassword, String ip) {
        String hash = encoder.encode(newPassword);
        tx.executeWithoutResult(status -> {
            users.setPassword(studentId, hash);
            sessions.revokeAll(studentId);
        });
        audit.record("STUDENT_PASSWORD_RESET", AuditService.OK, tutor.id(), studentId, ip);
    }

    /** Desactiva (nunca borra): el menor no puede entrar y sus sesiones se cierran. Idempotente. */
    public void deactivate(AuthUser tutor, UUID studentId, String ip) {
        tx.executeWithoutResult(status -> {
            users.changeStatus(studentId, UserStatus.ACTIVE, UserStatus.DEACTIVATED);
            sessions.revokeAll(studentId);
        });
        audit.record("STUDENT_DEACTIVATED", AuditService.OK, tutor.id(), studentId, ip);
    }

    /** Idempotente. */
    public void reactivate(AuthUser tutor, UUID studentId, String ip) {
        users.changeStatus(studentId, UserStatus.DEACTIVATED, UserStatus.ACTIVE);
        audit.record("STUDENT_REACTIVATED", AuditService.OK, tutor.id(), studentId, ip);
    }

    /** "Ñandú López!" → "nandu-lopez": solo a-z, 0-9 y guiones, sin acentos. */
    static String slug(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(3);
        for (int i = 0; i < 3; i++) sb.append(SUFFIX_ALPHABET.charAt(random.nextInt(SUFFIX_ALPHABET.length())));
        return sb.toString();
    }
}
