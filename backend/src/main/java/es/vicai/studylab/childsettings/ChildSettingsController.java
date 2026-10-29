package es.vicai.studylab.childsettings;

import es.vicai.studylab.security.AccessGuard;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ajustes que un tutor decide por su menor (F19, ADR-104). Cada método empieza por {@code requireStudentOf}: sin vínculo, menor ajeno o inexistente, id mal formado
 * y el propio menor (que no es tutor de nadie) responden el mismo 404. SecurityConfig deja pasar a cualquier sesión por esta ruta para que un menor reciba ese
 * 404 y no un 403 que delate que la ruta existe.
 */
@RestController
public class ChildSettingsController {

    public record PhotoPermission(boolean enabled) {}

    public record PhotoPermissionRequest(Boolean enabled) {}

    private static final String PATH = "/api/tutor/students/{id}/photo-material";

    private final ChildSettingsService service;
    private final AccessGuard guard;

    public ChildSettingsController(ChildSettingsService service, AccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping(PATH)
    PhotoPermission get(@AuthenticationPrincipal AuthUser user, @PathVariable String id, HttpServletRequest request) {
        UUID student = guard.requireStudentOf(user, id, request.getRemoteAddr());
        return new PhotoPermission(service.photoMaterial(student));
    }

    @PutMapping(PATH)
    PhotoPermission put(@AuthenticationPrincipal AuthUser user, @PathVariable String id, @RequestBody PhotoPermissionRequest body, HttpServletRequest request) {
        UUID student = guard.requireStudentOf(user, id, request.getRemoteAddr());
        if (body == null || body.enabled() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Datos no válidos: enabled");
        return new PhotoPermission(service.setPhotoMaterial(user.id(), student, body.enabled(), request.getRemoteAddr()));
    }
}
