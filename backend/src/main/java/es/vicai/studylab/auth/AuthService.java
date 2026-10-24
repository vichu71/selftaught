package es.vicai.studylab.auth;

import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.mail.MailService;
import es.vicai.studylab.user.UserRepository;
import es.vicai.studylab.user.UserRepository.UserRow;
import es.vicai.studylab.user.UserStatus;
import es.vicai.studylab.web.ApiException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Casos de uso de cuentas de adultos y de login (adultos y menores).
 * Registro, reenvío y recuperación responden igual exista o no la cuenta; el correo se envía fuera de la transacción.
 */
@Service
public class AuthService {

    static final Duration VERIFY_TTL = Duration.ofHours(24);
    static final Duration RESET_TTL = Duration.ofHours(1);

    public record LoginResult(UserRow user, String sessionToken) {}

    private final UserRepository users;
    private final EmailTokenRepository tokens;
    private final SessionService sessions;
    private final AttemptThrottle throttle;
    private final AuditService audit;
    private final MailService mail;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    /** Hash de relleno: se verifica contra él cuando la cuenta no existe, para no delatarlo por el tiempo de respuesta. */
    private final String dummyHash;

    public AuthService(UserRepository users, EmailTokenRepository tokens, SessionService sessions,
                       AttemptThrottle throttle, AuditService audit, MailService mail, PasswordEncoder encoder,
                       TransactionTemplate tx) {
        this.users = users;
        this.tokens = tokens;
        this.sessions = sessions;
        this.throttle = throttle;
        this.audit = audit;
        this.mail = mail;
        this.encoder = encoder;
        this.tx = tx;
        this.dummyHash = encoder.encode("relleno-para-igualar-tiempos");
    }

    // ---------- registro y verificación ----------

    public void register(String rawEmail, String password, String displayName, String ip) {
        throttle.checkMailQuota(ip);
        String email = normalizeEmail(rawEmail);
        String hash = encoder.encode(password); // se calcula siempre: el tiempo no delata si el correo existe

        Optional<UserRow> existing = users.findByEmail(email);
        if (existing.isEmpty()) {
            UUID id = UUID.randomUUID();
            try {
                String token = tx.execute(s -> {
                    users.insertAdult(id, email, displayName.trim(), hash);
                    return tokens.issue(id, EmailTokenRepository.VERIFY_EMAIL, VERIFY_TTL);
                });
                audit.record("REGISTER", AuditService.OK, id, id, ip);
                mail.sendVerification(email, token);
                return;
            } catch (DuplicateKeyException race) {
                existing = users.findByEmail(email); // otro registro simultáneo con el mismo correo
            }
        }
        existing.ifPresent(user -> {
            audit.record("REGISTER_DUPLICATE", AuditService.DENIED, null, user.id(), ip);
            if (user.status() == UserStatus.PENDING_VERIFICATION) {
                mail.sendVerification(email, tokens.issue(user.id(), EmailTokenRepository.VERIFY_EMAIL, VERIFY_TTL));
            } else {
                mail.sendAlreadyRegistered(email);
            }
        });
    }

    public void verifyEmail(String token, String ip) {
        UUID userId = tx.execute(s -> {
            UUID id = tokens.consume(token, EmailTokenRepository.VERIFY_EMAIL).orElseThrow(ApiException::invalidToken);
            users.activateIfPending(id);
            return id;
        });
        audit.record("EMAIL_VERIFIED", AuditService.OK, userId, userId, ip);
    }

    public void resendVerification(String rawEmail, String ip) {
        throttle.checkMailQuota(ip);
        String email = normalizeEmail(rawEmail);
        users.findByEmail(email)
                .filter(user -> user.status() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(user -> mail.sendVerification(email,
                        tokens.issue(user.id(), EmailTokenRepository.VERIFY_EMAIL, VERIFY_TTL)));
    }

    // ---------- recuperación de contraseña ----------

    public void forgotPassword(String rawEmail, String ip) {
        throttle.checkMailQuota(ip);
        String email = normalizeEmail(rawEmail);
        users.findByEmail(email).filter(user -> user.status() == UserStatus.ACTIVE).ifPresent(user -> {
            String token = tokens.issue(user.id(), EmailTokenRepository.RESET_PASSWORD, RESET_TTL);
            audit.record("PASSWORD_RESET_REQUESTED", AuditService.OK, user.id(), user.id(), ip);
            mail.sendPasswordReset(email, token);
        });
    }

    public void resetPassword(String token, String newPassword, String ip) {
        String hash = encoder.encode(newPassword);
        UUID userId = tx.execute(s -> {
            UUID id = tokens.consume(token, EmailTokenRepository.RESET_PASSWORD).orElseThrow(ApiException::invalidToken);
            users.setPassword(id, hash);
            sessions.revokeAll(id); // cambiar la contraseña cierra todas las sesiones abiertas
            return id;
        });
        audit.record("PASSWORD_RESET_DONE", AuditService.OK, userId, userId, ip);
    }

    // ---------- login y logout ----------

    public LoginResult login(String identifier, String password, String ip) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        String identifierHash = TokenCodec.sha256Hex(normalized);

        if (throttle.isLoginLocked(identifierHash, ip)) {
            audit.record("LOGIN_LOCKED", AuditService.DENIED, null, null, ip);
            throw ApiException.locked();
        }

        Optional<UserRow> user = normalized.contains("@") ? users.findByEmail(normalized) : users.findByLoginId(normalized);
        boolean passwordOk = encoder.matches(password, user.map(UserRow::passwordHash).orElse(dummyHash));
        if (user.isEmpty() || !passwordOk || user.get().status() != UserStatus.ACTIVE) {
            throttle.recordLogin(identifierHash, ip, false);
            audit.record("LOGIN_FAILED", AuditService.FAILED, null, user.map(UserRow::id).orElse(null), ip);
            throw ApiException.invalidCredentials();
        }

        throttle.recordLogin(identifierHash, ip, true);
        String token = sessions.create(user.get().id());
        audit.record("LOGIN_OK", AuditService.OK, user.get().id(), user.get().id(), ip);
        return new LoginResult(user.get(), token);
    }

    public void logout(UUID sessionId, UUID userId, String ip) {
        sessions.revoke(sessionId);
        audit.record("LOGOUT", AuditService.OK, userId, userId, ip);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
