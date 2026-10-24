package es.vicai.studylab.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import es.vicai.studylab.security.AuthUser;
import java.util.List;
import java.util.UUID;

/** Datos mínimos del usuario: el adulto expone su correo, el menor su identificador de acceso (nunca ambos). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(UUID id, String displayName, UserKind kind, List<String> roles, String email, String loginId) {

    public static MeResponse of(UserRepository.UserRow user) {
        return new MeResponse(user.id(), user.displayName(), user.kind(), user.kind().roles(), user.email(),
                user.loginId());
    }

    public static MeResponse of(AuthUser user) {
        return new MeResponse(user.id(), user.displayName(), user.kind(), user.roles(), user.email(), user.loginId());
    }
}
