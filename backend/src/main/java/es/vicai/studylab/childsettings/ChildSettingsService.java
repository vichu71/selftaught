package es.vicai.studylab.childsettings;

import es.vicai.studylab.ai.AiProperties;
import es.vicai.studylab.ai.AiTask;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.user.UserRepository;
import es.vicai.studylab.user.UserStatus;
import es.vicai.studylab.web.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ajustes que un tutor decide por su menor (F19, ADR-104). El vínculo tutor–menor lo comprueba el controlador antes de llegar aquí. */
@Service
public class ChildSettingsService {

    private final ChildSettingsRepository settings;
    private final UserRepository users;
    private final AuditService audit;
    private final AiProperties ai;

    public ChildSettingsService(ChildSettingsRepository settings, UserRepository users, AuditService audit, AiProperties ai) {
        this.settings = settings;
        this.users = users;
        this.audit = audit;
        this.ai = ai;
    }

    /** ¿Puede este usuario subir fotos? Un adulto sí (si el servidor las ofrece); un menor solo con el permiso de su tutor. Para la subida y las capacidades. */
    public boolean photosEnabledFor(UUID userId, boolean minor) {
        return !minor || settings.photoMaterial(userId);
    }

    public boolean photoMaterial(UUID childId) {
        return settings.photoMaterial(childId);
    }

    @Transactional
    public boolean setPhotoMaterial(UUID tutorId, UUID childId, boolean enabled, String ip) {
        if (!ai.isConfigured(AiTask.OCR)) throw photosOff();
        users.findStudentOf(tutorId, childId).filter(s -> s.status() == UserStatus.ACTIVE).orElseThrow(ChildSettingsService::inactive);
        if (settings.setPhotoMaterial(childId, enabled, tutorId)) {
            audit.record(enabled ? "CHILD_PHOTOS_ENABLED" : "CHILD_PHOTOS_DISABLED", AuditService.OK, tutorId, childId, ip, Map.of("childId", childId.toString()));
        }
        return enabled;
    }

    private static ApiException photosOff() {
        return new ApiException(HttpStatus.CONFLICT, "PHOTOS_NOT_AVAILABLE", "La lectura de fotos no está activada en este servidor");
    }

    private static ApiException inactive() {
        return new ApiException(HttpStatus.CONFLICT, "STUDENT_NOT_ACTIVE", "Esta cuenta está desactivada");
    }
}
