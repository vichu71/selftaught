package es.vicai.studylab.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import es.vicai.studylab.owner.OwnerPolicy;
import es.vicai.studylab.security.AuthUser;
import java.util.List;
import java.util.UUID;

/**
 * Datos mínimos del usuario: el adulto expone su correo, el menor su identificador de acceso (nunca ambos). {@code owner} (F14) solo aparece, con valor true, para el propietario:
 * sirve para mostrar el enlace del panel; el permiso real lo vuelve a comprobar el servidor en cada llamada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(UUID id, String displayName, UserKind kind, List<String> roles, String email, String loginId, Boolean owner) {

    public static MeResponse of(UserRepository.UserRow user) {
        return new MeResponse(user.id(), user.displayName(), user.kind(), user.kind().roles(), user.email(), user.loginId(), null);
    }

    public static MeResponse of(AuthUser user) {
        return new MeResponse(user.id(), user.displayName(), user.kind(), user.roles(), user.email(), user.loginId(), null);
    }

    public static MeResponse of(AuthUser user, OwnerPolicy policy) {
        return of(user).withOwner(policy.isOwner(user));
    }

    /** Marca al propietario; para cualquier otro el campo se omite. */
    public MeResponse withOwner(boolean isOwner) {
        return new MeResponse(id, displayName, kind, roles, email, loginId, isOwner ? Boolean.TRUE : null);
    }
}
