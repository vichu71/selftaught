package es.vicai.studylab.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BeansConfig {

    /** Argon2id con los parámetros mínimos recomendados por OWASP (19 MiB, 2 iteraciones, 1 hilo). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
