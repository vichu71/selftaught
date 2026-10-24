package es.vicai.studylab.security;

import es.vicai.studylab.audit.AuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Denegado por defecto: solo los endpoints públicos de /api/auth y /actuator/health se abren sin sesión;
 * /api/tutor/** exige ser adulto. Las peticiones autenticadas que modifican datos exigen CSRF.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String[] PUBLIC_AUTH = {
            "/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification",
            "/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password"};

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").path("/"));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcClient jdbc, CsrfTokenRepository csrfRepository,
                                            AuditService audit) throws Exception {
        // Manejador "plano": el valor de la cookie XSRF-TOKEN se reenvía tal cual en la cabecera X-XSRF-TOKEN
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers(PUBLIC_AUTH)
                        // Por defecto Spring borra la cookie XSRF-TOKEN en cada petición que ve como "autenticación
                        // nueva", y con nuestro filtro sin HttpSession eso es TODA petición autenticada: las
                        // escrituras siguientes darían 403. El token se genera en cada login, así que no hace falta.
                        .sessionAuthenticationStrategy((authentication, request, response) -> { }))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_AUTH).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/tutor/**").hasRole("TUTOR")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDenied(audit)))
                .addFilterBefore(new SessionAuthenticationFilter(jdbc), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 403 (rol insuficiente o CSRF ausente): se audita quién lo intentó. */
    private AccessDeniedHandler accessDenied(AuditService audit) {
        return (request, response, exception) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
                audit.record("ACCESS_DENIED", AuditService.DENIED, user.id(), null, request.getRemoteAddr());
            }
            response.setStatus(HttpStatus.FORBIDDEN.value());
        };
    }
}
