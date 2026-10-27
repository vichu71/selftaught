package es.vicai.studylab.owner;

import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quién es el propietario (F14, ADR-87): la cuenta adulta cuyo correo coincide con {@code OWNER_EMAIL}. El correo solo vive en la configuración del servidor: el rol no se guarda
 * ni se pide por API, se calcula en cada petición. Sin correo configurado nadie es propietario y el panel no existe. El filtro de sesión solo deja pasar cuentas activas, es decir, con
 * el correo verificado.
 */
@ConfigurationProperties(prefix = "app.owner")
public record OwnerPolicy(String email) {

    public boolean isOwner(AuthUser user) {
        return user != null && isOwner(user.kind(), user.email());
    }

    public boolean isOwner(UserKind kind, String accountEmail) {
        if (email == null || email.isBlank() || kind != UserKind.ADULT || accountEmail == null) return false;
        return email.strip().equalsIgnoreCase(accountEmail.strip());
    }
}
