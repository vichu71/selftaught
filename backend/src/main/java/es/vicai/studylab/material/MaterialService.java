package es.vicai.studylab.material;

import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.childsettings.ChildSettingsService;
import es.vicai.studylab.material.image.PhotoProcessor;
import es.vicai.studylab.material.image.PhotoRejectedException;
import es.vicai.studylab.material.extract.FileTypeDetector;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserRepository;
import es.vicai.studylab.user.UserStatus;
import es.vicai.studylab.web.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida, consulta y borrado del material de un usuario (ADR-30, 35). El original se guarda en la base de datos solo hasta procesarlo;
 * el procesado lo hace {@link MaterialWorker}. Un documento ajeno y uno inexistente responden igual (404).
 */
@Service
public class MaterialService {

    public static final int MAX_NAME = 120;
    private static final String DEFAULT_NAME = "documento";

    /** Los documentos de un menor a cargo del tutor que consulta. */
    public record MinorMaterial(Student student, List<DocumentView> documents) {}

    public record Student(UUID id, String displayName) {}

    public record Listing(List<DocumentView> own, List<MinorMaterial> minors) {}

    /** Qué puede subir quien llama: solo eso, ni modelo ni coste ni límites (F19). */
    public record Capabilities(boolean images) {}

    private final MaterialRepository documents;
    private final MaterialProperties props;
    private final FileTypeDetector detector;
    private final UserRepository users;
    private final AuditService audit;
    private final AiGateway gateway;
    private final ChildSettingsService childSettings;
    private final PhotoProcessor photos;

    public MaterialService(MaterialRepository documents, MaterialProperties props, FileTypeDetector detector, UserRepository users,
                           AuditService audit, AiGateway gateway, ChildSettingsService childSettings, PhotoProcessor photos) {
        this.gateway = gateway;
        this.childSettings = childSettings;
        this.photos = photos;
        this.documents = documents;
        this.props = props;
        this.detector = detector;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public DocumentView upload(AuthUser user, MultipartFile file, String ip) {
        if (file == null || file.isEmpty()) throw MaterialException.empty();
        if (file.getSize() > props.maxFileBytes()) throw MaterialException.tooLarge(props.maxFileMb());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (content.length > props.maxFileBytes()) throw MaterialException.tooLarge(props.maxFileMb());
        MaterialType type = detector.detect(content).orElseThrow(MaterialException::typeNotAllowed);
        boolean photo = type == MaterialType.IMAGE;
        if (photo) checkPhoto(user, content);

        // Se bloquea al usuario para que dos subidas a la vez no se salten el límite de documentos
        users.lockUser(user.id());
        if (documents.count(user.id()) >= props.maxDocuments()) throw MaterialException.limitReached(props.maxDocuments());
        if (photo && documents.countImageUploads(user.id()) >= props.photos().maxReadsPerDay()) throw MaterialException.imageReadRateLimit();

        UUID id = UUID.randomUUID();
        String name = sanitizeName(file.getOriginalFilename());
        if (!documents.insert(id, user.id(), name, type, content, sha256(content))) throw MaterialException.duplicate();
        if (photo) documents.insertImageUpload(user.id()); // en la misma transacción que el documento: una foto rechazada no gasta el límite
        audit.record("DOCUMENT_UPLOADED", AuditService.OK, user.id(), user.id(), ip, Map.of("documentId", id.toString(), "mediaType", type.name()));
        return view(documents.find(id).orElseThrow(), user.id());
    }

    /**
     * Comprobaciones de una foto antes de insertar nada y sin llamar al modelo (ART-003): el servidor la ofrece (si no, 415 como cualquier tipo no admitido), el menor tiene el permiso
     * de su tutor (403), pesa lo permitido (413) y su cabecera se lee sin decodificarla (400 si no se puede, 413 si declara demasiados píxeles).
     */
    private void checkPhoto(AuthUser user, byte[] content) {
        if (!gateway.readEnabled()) throw MaterialException.typeNotAllowed();
        if (!childSettings.photosEnabledFor(user.id(), user.kind() == es.vicai.studylab.user.UserKind.MINOR)) throw MaterialException.childImagesNotEnabled();
        if (content.length > props.maxImageBytes()) throw MaterialException.imageTooLargeInMb(props.photos().maxMb());
        try {
            photos.inspect(content);
        } catch (PhotoRejectedException e) {
            throw PhotoRejectedException.TOO_LARGE.equals(e.kind()) ? MaterialException.imageTooLarge() : MaterialException.imageInvalid();
        }
    }

    /** Lo que esta sesión puede hacer con el material hoy: por ahora, si puede subir fotos (el servidor las ofrece y, si es un menor, su tutor las permite). */
    public Capabilities capabilities(AuthUser user) {
        boolean minor = user.kind() == es.vicai.studylab.user.UserKind.MINOR;
        return new Capabilities(gateway.readEnabled() && childSettings.photosEnabledFor(user.id(), minor));
    }

    public Listing list(AuthUser user) {
        List<DocumentView> own = documents.listOf(user.id()).stream().map(d -> view(d, user.id())).toList();
        List<MinorMaterial> minors = new ArrayList<>();
        if (user.kind() == es.vicai.studylab.user.UserKind.ADULT) {
            for (UserRepository.StudentRow student : users.studentsOf(user.id())) {
                if (student.status() != UserStatus.ACTIVE) continue;
                List<DocumentView> theirs = documents.listOf(student.id()).stream().map(d -> view(d, user.id())).toList();
                minors.add(new MinorMaterial(new Student(student.id(), student.displayName()), theirs));
            }
        }
        return new Listing(own, minors);
    }

    public DocumentView get(AuthUser user, String rawId) {
        MaterialRepository.DocumentRow row = visible(user, rawId);
        return view(row, user.id());
    }

    /** Solo el propietario borra. El tutor de un menor ve el documento, así que su intento es un 403 y se audita. */
    public void delete(AuthUser user, String rawId, String ip) {
        MaterialRepository.DocumentRow row = visible(user, rawId);
        if (!row.ownerId().equals(user.id())) {
            audit.record("ACCESS_DENIED", AuditService.DENIED, user.id(), row.ownerId(), ip, Map.of("resource", "material"));
            throw MaterialException.forbidden();
        }
        if (documents.delete(row.id(), user.id())) {
            audit.record("DOCUMENT_DELETED", AuditService.OK, user.id(), user.id(), ip,
                    Map.of("documentId", row.id().toString(), "mediaType", row.type().name()));
        }
    }

    /** El documento si es del usuario o de un menor activo suyo; si no, 404 (igual que uno inexistente). */
    private MaterialRepository.DocumentRow visible(AuthUser user, String rawId) {
        UUID id = parse(rawId);
        if (id == null) throw ApiException.notFound();
        MaterialRepository.DocumentRow row = documents.find(id).orElseThrow(ApiException::notFound);
        if (row.ownerId().equals(user.id())) return row;
        boolean tutorOfOwner = user.kind() == es.vicai.studylab.user.UserKind.ADULT && users.isTutorOf(user.id(), row.ownerId())
                && users.studentsOf(user.id()).stream().anyMatch(s -> s.id().equals(row.ownerId()) && s.status() == UserStatus.ACTIVE);
        if (tutorOfOwner) return row;
        throw ApiException.notFound();
    }

    private static DocumentView view(MaterialRepository.DocumentRow d, UUID viewer) {
        return new DocumentView(d.id(), d.name(), d.type(), d.sizeBytes(), d.status(), d.errorKind(), d.chunkCount(), d.pageCount(),
                d.createdAt(), d.ownerId().equals(viewer));
    }

    private static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Sin rutas, sin caracteres de control, normalizado y de longitud acotada. Solo sirve para mostrar: nunca se usa como ruta. */
    static String sanitizeName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        StringBuilder clean = new StringBuilder();
        name.codePoints().filter(cp -> !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT).forEach(clean::appendCodePoint);
        String result = clean.toString().strip();
        if (result.length() > MAX_NAME) {
            int end = MAX_NAME;
            if (Character.isHighSurrogate(result.charAt(end - 1))) end--;
            result = result.substring(0, end).strip();
        }
        return result.isEmpty() ? DEFAULT_NAME : result;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
