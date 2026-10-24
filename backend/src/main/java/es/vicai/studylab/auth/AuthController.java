package es.vicai.studylab.auth;

import es.vicai.studylab.auth.AuthDtos.Accepted;
import es.vicai.studylab.auth.AuthDtos.EmailRequest;
import es.vicai.studylab.auth.AuthDtos.LoginRequest;
import es.vicai.studylab.auth.AuthDtos.RegisterRequest;
import es.vicai.studylab.auth.AuthDtos.ResetRequest;
import es.vicai.studylab.auth.AuthDtos.VerifyRequest;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.security.SessionAuthenticationFilter;
import es.vicai.studylab.user.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final SessionService sessions;
    private final CsrfTokenRepository csrfRepository;

    public AuthController(AuthService auth, SessionService sessions, CsrfTokenRepository csrfRepository) {
        this.auth = auth;
        this.sessions = sessions;
        this.csrfRepository = csrfRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Accepted register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        auth.register(body.email(), body.password(), body.displayName(), request.getRemoteAddr());
        return Accepted.INSTANCE;
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody VerifyRequest body, HttpServletRequest request) {
        auth.verifyEmail(body.token(), request.getRemoteAddr());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Accepted resendVerification(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        auth.resendVerification(body.email(), request.getRemoteAddr());
        return Accepted.INSTANCE;
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Accepted forgotPassword(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        auth.forgotPassword(body.email(), request.getRemoteAddr());
        return Accepted.INSTANCE;
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
        auth.resetPassword(body.token(), body.newPassword(), request.getRemoteAddr());
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = auth.login(body.identifier(), body.password(), request.getRemoteAddr());

        ResponseCookie session = ResponseCookie.from(SessionAuthenticationFilter.COOKIE, result.sessionToken())
                .httpOnly(true).secure(request.isSecure()).sameSite("Strict").path("/")
                .maxAge(Duration.ofSeconds(sessions.ttlSeconds())).build();
        response.addHeader(HttpHeaders.SET_COOKIE, session.toString());

        // El frontend lee esta cookie y la reenvía en X-XSRF-TOKEN en las peticiones que modifican datos
        CsrfToken csrf = csrfRepository.generateToken(request);
        csrfRepository.saveToken(csrf, request, response);
        return MeResponse.of(result.user());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        auth.logout(user.sessionId(), user.id(), request.getRemoteAddr());
        ResponseCookie expired = ResponseCookie.from(SessionAuthenticationFilter.COOKIE, "")
                .httpOnly(true).secure(request.isSecure()).sameSite("Strict").path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    }
}
