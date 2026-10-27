package es.vicai.studylab.illustrations;

import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiImageRequest;
import es.vicai.studylab.ai.AiImageResponse;
import es.vicai.studylab.ai.lock.GenerationLock;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.courseedit.CourseActionRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * La cola de ilustraciones (ADR-79). {@link #processPending()} descubre lo que falta, y por cada imagen pendiente comprueba los límites, construye el prompt, llama a
 * la capa de IA con el cerrojo del usuario, valida la imagen del proveedor y la guarda. Nada de esto toca {@code course.status}: un fallo, un tope o una pausa de
 * imágenes no pausa un curso. Ni los logs ni la auditoría llevan el prompt, el título, la imagen ni mensajes del proveedor: solo ids y el tipo de error.
 */
@Service
public class IllustrationGenerator {

    private static final Logger log = LoggerFactory.getLogger(IllustrationGenerator.class);
    private static final int BATCH = 50;
    static final String ACTION_IMAGE = "AI_IMAGE";

    private final ImageRepository images;
    private final IllustrationProperties props;
    private final ImagePromptBuilder prompts;
    private final ImageProcessor processor;
    private final AiGateway gateway;
    private final GenerationLock locks;
    private final CourseActionRepository actions;
    private final AuditService audit;

    public IllustrationGenerator(ImageRepository images, IllustrationProperties props, ImagePromptBuilder prompts, ImageProcessor processor, AiGateway gateway,
                                 GenerationLock locks, CourseActionRepository actions, AuditService audit) {
        this.images = images;
        this.props = props;
        this.prompts = prompts;
        this.processor = processor;
        this.gateway = gateway;
        this.locks = locks;
        this.actions = actions;
        this.audit = audit;
    }

    /** Procesa lo pendiente una vez; devuelve cuántas imágenes llegaron a un estado final (hecha, fallida o bloqueada). Síncrono: los tests lo llaman a mano. */
    public int processPending() {
        if (!gateway.imageEnabled()) return 0; // sin proveedor, las ilustraciones están apagadas
        images.enqueueMissing();
        Set<UUID> skipped = new HashSet<>();
        int ended = 0;
        for (ImageRepository.Pending next : images.pending(BATCH)) {
            if (skipped.contains(next.ownerId())) continue;
            GenerationLock.Lease lease;
            try {
                lease = locks.acquire(next.ownerId());
            } catch (AiException e) {
                skipped.add(next.ownerId()); // otra generación suya en marcha, o usuario no válido: se deja para después
                continue;
            }
            try (lease) {
                if (process(next, lease)) ended++;
                else skipped.add(next.ownerId());
            } catch (RuntimeException e) {
                log.error("Ilustración {}: fallo inesperado ({})", next.id(), e.getClass().getSimpleName());
                skipped.add(next.ownerId());
            }
        }
        return ended;
    }

    /** Devuelve true si la imagen llegó a un estado final; false si se queda pendiente (sin presupuesto, sin cupo o sin cerrojo). */
    private boolean process(ImageRepository.Pending next, GenerationLock.Lease lease) {
        if (actions.inTheLastHour(next.ownerId(), ACTION_IMAGE) >= props.maxPerHour()) return false;

        var prompt = prompts.build(next.title(), next.summary());
        if (prompt.isEmpty()) {
            images.saveEnded(next.id(), next.ownerId(), "BLOCKED", "SANITIZE");
            audited("COURSE_IMAGE_BLOCKED", AuditService.DENIED, next);
            return true;
        }
        if (!lease.renew()) return false; // el cerrojo ya no es suyo: no se escribe nada

        AiImageResponse response;
        boolean attempted = true;
        try {
            response = gateway.generateImage(AiImageRequest.of(next.ownerId(), next.courseId(), prompt.get()));
        } catch (AiException e) {
            AiErrorKind kind = e.kind();
            if (kind == AiErrorKind.USER_LIMIT || kind == AiErrorKind.BUDGET_BLOCKED || kind == AiErrorKind.USER_NOT_ALLOWED || kind == AiErrorKind.CONFIG
                    || kind == AiErrorKind.GENERATION_BUSY) {
                return false; // sin llamada al proveedor: la fila sigue pendiente y el curso no cambia
            }
            actions.record(next.ownerId(), ACTION_IMAGE);
            if (kind == AiErrorKind.MODERATED) {
                images.saveEnded(next.id(), next.ownerId(), "BLOCKED", "MODERATION");
                audited("COURSE_IMAGE_BLOCKED", AuditService.DENIED, next);
            } else {
                images.saveEnded(next.id(), next.ownerId(), "FAILED", kind.name());
                audited("COURSE_IMAGE_FAILED", AuditService.FAILED, next);
            }
            return true;
        }
        actions.record(next.ownerId(), ACTION_IMAGE);

        ImageProcessor.Processed processed;
        try {
            processed = processor.process(response.bytes());
        } catch (ImageRejectedException e) {
            images.saveEnded(next.id(), next.ownerId(), "FAILED", "IMAGE_" + e.reason().name());
            audited("COURSE_IMAGE_FAILED", AuditService.FAILED, next);
            return true;
        }
        images.saveDone(next.id(), next.ownerId(), processed.bytes(), processed.mime(), prompts.altText(next.title()));
        audited("COURSE_IMAGE_GENERATED", AuditService.OK, next);
        log.debug("Ilustración {} generada ({} bytes)", next.id(), processed.bytes().length);
        return true;
    }

    /** Auditoría solo con ids, fuera de cualquier transacción. */
    private void audited(String action, String outcome, ImageRepository.Pending next) {
        audit.record(action, outcome, next.ownerId(), next.ownerId(), null, Map.of("courseId", next.courseId().toString(), "topicId", next.topicId().toString()));
    }
}
