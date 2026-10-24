package es.vicai.studylab.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpos de las peticiones de /api/auth. La validación del backend es la que cuenta. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 50) String displayName) {}

    public record VerifyRequest(@NotBlank @Size(max = 128) String token) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record LoginRequest(
            @NotBlank @Size(max = 254) String identifier,
            @NotBlank @Size(max = 128) String password) {}

    public record ResetRequest(
            @NotBlank @Size(max = 128) String token,
            @NotNull @Size(min = 10, max = 128) String newPassword) {}

    /** Respuesta constante de los endpoints que no deben revelar si una cuenta existe. */
    public record Accepted(String message) {
        public static final Accepted INSTANCE =
                new Accepted("Si los datos son correctos, recibirás un correo con las instrucciones");
    }
}
