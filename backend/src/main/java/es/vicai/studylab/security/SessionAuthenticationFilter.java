package es.vicai.studylab.security;

import es.vicai.studylab.auth.TokenCodec;
import es.vicai.studylab.user.UserKind;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica la petición a partir de la cookie de sesión. La sesión debe existir (por hash), no estar revocada
 * ni caducada, y el usuario seguir ACTIVE: así una baja o un cambio de contraseña surten efecto al instante.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE = "STUDYLAB_SESSION";

    private final JdbcClient jdbc;

    public SessionAuthenticationFilter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        sessionToken(request).flatMap(this::findUser).ifPresent(user -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }

    private Optional<String> sessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank() && cookie.getValue().length() <= 128) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private Optional<AuthUser> findUser(String token) {
        return jdbc.sql("""
                SELECT u.id, u.kind, u.display_name, u.email::text AS email, u.login_id::text AS login_id,
                       s.id AS session_id
                FROM auth_session s JOIN app_user u ON u.id = s.user_id
                WHERE s.token_hash = :hash AND s.revoked_at IS NULL AND s.expires_at > now()
                  AND u.status = 'ACTIVE'""")
                .param("hash", TokenCodec.sha256Hex(token))
                .query((rs, i) -> new AuthUser(rs.getObject("id", UUID.class), UserKind.valueOf(rs.getString("kind")),
                        rs.getString("display_name"), rs.getString("email"), rs.getString("login_id"),
                        rs.getObject("session_id", UUID.class)))
                .optional();
    }
}
