package es.vicai.studylab.illustrations;

import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.course.SheetFactory;
import es.vicai.studylab.courseedit.CourseActionRepository;
import es.vicai.studylab.courseedit.CourseEditException;
import es.vicai.studylab.courseedit.CourseEditProperties;
import es.vicai.studylab.generation.CourseContentService;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import es.vicai.studylab.web.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que el propietario hace con la ilustración de un tema (F12): ver la oferta del asistente, leer la imagen, quitarla y volver a generarla. Quitar y regenerar no
 * suben {@code course.version} ni tocan {@code course.status} (ADR-79): solo se rechazan mientras el curso se está generando. Regenerar no llama a ningún proveedor:
 * deja la fila pendiente y la cola hace el trabajo. Un menor, un curso o tema ajenos y una imagen que no existe responden todos el mismo 404.
 */
@Service
public class IllustrationService {

    private static final Set<String> BUSY = Set.of("PENDING_GENERATION", "GENERATING");

    /** Lo que el asistente muestra junto al interruptor: si se puede usar, cuánto cuesta cada imagen y el máximo de temas de un curso (peor caso del coste). */
    public record Offer(boolean available, BigDecimal pricePerImageUsd, int maxTopics) {}

    private final ImageRepository images;
    private final IllustrationProperties props;
    private final CourseActionRepository actions;
    private final CourseEditProperties editProps;
    private final CourseContentService content;
    private final AiGateway gateway;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public IllustrationService(ImageRepository images, IllustrationProperties props, CourseActionRepository actions, CourseEditProperties editProps,
                               CourseContentService content, AiGateway gateway, AuditService audit, TransactionTemplate tx) {
        this.images = images;
        this.props = props;
        this.actions = actions;
        this.editProps = editProps;
        this.content = content;
        this.gateway = gateway;
        this.audit = audit;
        this.tx = tx;
    }

    public Offer offer(AuthUser user) {
        if (user.kind() == UserKind.MINOR || !gateway.imageEnabled()) return new Offer(false, null, SheetFactory.MAX_TOPICS);
        return new Offer(true, gateway.imagePriceUsd(), SheetFactory.MAX_TOPICS);
    }

    /** Si un adulto puede crear cursos con ilustraciones ahora mismo. */
    public boolean available(AuthUser user) {
        return user.kind() != UserKind.MINOR && gateway.imageEnabled();
    }

    public Optional<ImageRepository.Content> image(AuthUser user, String rawCourse, String rawTopic) {
        UUID course = id(rawCourse);
        UUID topic = id(rawTopic);
        if (course == null || topic == null) return Optional.empty();
        return images.done(topic, course, user.id());
    }

    public CourseContentService.CourseDetailView remove(AuthUser user, String rawCourse, String rawTopic, String ip) {
        UUID course = id(rawCourse);
        UUID topic = id(rawTopic);
        if (course == null || topic == null) throw ApiException.notFound();
        boolean removed = Boolean.TRUE.equals(tx.execute(status -> {
            requireEditable(user, course);
            ImageRepository.Row row = images.find(topic, course, user.id()).orElseThrow(ApiException::notFound);
            if ("REMOVED".equals(row.status())) return false;
            images.remove(row.id(), user.id());
            return true;
        }));
        if (removed) audit.record("COURSE_IMAGE_REMOVED", AuditService.OK, user.id(), user.id(), ip, ids(course, topic));
        return content.detail(user, rawCourse);
    }

    public CourseContentService.CourseDetailView regenerate(AuthUser user, String rawCourse, String rawTopic, String ip) {
        UUID course = id(rawCourse);
        UUID topic = id(rawTopic);
        if (course == null || topic == null || !gateway.imageEnabled()) throw ApiException.notFound();
        boolean queued = Boolean.TRUE.equals(tx.execute(status -> {
            requireEditable(user, course);
            ImageRepository.Row row = images.find(topic, course, user.id()).orElseThrow(ApiException::notFound);
            if ("PENDING".equals(row.status())) return false; // ya está en la cola: no se cuenta ni se cobra otra vez
            if (row.regenerations() >= props.maxRegenerationsPerTopic()) throw IllustrationException.regenerationLimit();
            actions.lockUser(user.id());
            actions.purgeOld(user.id());
            if (actions.inTheLastHour(user.id(), "AI_EDIT") >= editProps.aiActionsPerHour()) throw IllustrationException.rateLimited();
            actions.record(user.id(), "AI_EDIT");
            images.requeue(row.id(), user.id());
            return true;
        }));
        if (queued) audit.record("COURSE_IMAGE_REGENERATED", AuditService.OK, user.id(), user.id(), ip, ids(course, topic));
        return content.detail(user, rawCourse);
    }

    /** Solo el propietario adulto, y nunca mientras el curso está en cola o generándose; bloquea la fila del curso para serializar con la edición. */
    private void requireEditable(AuthUser user, UUID course) {
        if (user.kind() == UserKind.MINOR) throw ApiException.notFound();
        String state = images.lockCourse(course, user.id()).orElseThrow(ApiException::notFound);
        if (BUSY.contains(state)) throw CourseEditException.busy();
    }

    private static Map<String, String> ids(UUID course, UUID topic) {
        return Map.of("courseId", course.toString(), "topicId", topic.toString());
    }

    private static UUID id(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
