package es.vicai.studylab.security;

import es.vicai.studylab.user.UserKind;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Usuario autenticado de la petición actual. Las demás features reciben a quien llama por aquí. */
public record AuthUser(UUID id, UserKind kind, String displayName, String email, String loginId, UUID sessionId) {

    public List<String> roles() {
        return kind.roles();
    }

    public Collection<GrantedAuthority> authorities() {
        return roles().stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }
}
