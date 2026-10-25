package es.vicai.studylab.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiResponse;
import es.vicai.studylab.ai.FinishReason;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.course.CourseException;
import es.vicai.studylab.safety.ContentGuard;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import es.vicai.studylab.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El tutor IA de una lección (F07, ADR-55 a 58). Cada pregunta reserva su sitio en la BD (límites por hora y por día y una sola respuesta a la vez, por usuario),
 * pide la respuesta a {@link AiGateway} con la tarea {@code TUTOR}, la valida (un reintento) y guarda el intercambio de golpe. Para un menor, la pregunta y la
 * respuesta pasan por {@link ContentGuard}: un bloqueo no es un error, queda en el hilo para su adulto tutor. Ni los logs ni la auditoría llevan lo que se dice.
 */
@Service
public class TutorService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TutorService.class);

    public static final int MAX_QUESTION = 1000;
    static final String FILTERED_ANSWER = "No puedo ayudarte con eso. Consulta con tu adulto.";

    // ---------- contrato HTTP ----------

    public record AskRequest(Object question) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceRef(int n, Integer page) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessageView(UUID id, String role, String content, String status, List<SourceRef> sources, Instant createdAt) {}

    public record ThreadView(boolean canClear, List<MessageView> messages) {}

    public record Exchange(MessageView user, MessageView tutor) {}

    private final TutorRepository repo;
    private final TutorProperties props;
    private final TutorContext context;
    private final TutorAnswerValidator validator;
    private final AiGateway gateway;
    private final ContentGuard guard;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public TutorService(TutorRepository repo, TutorProperties props, TutorContext context, TutorAnswerValidator validator, AiGateway gateway, ContentGuard guard,
                        AuditService audit, TransactionTemplate tx, ObjectMapper json) {
        this.repo = repo;
        this.props = props;
        this.context = context;
        this.validator = validator;
        this.gateway = gateway;
        this.guard = guard;
        this.audit = audit;
        this.tx = tx;
        this.json = json;
    }

    // ---------- leer y vaciar ----------

    public ThreadView thread(AuthUser user, String rawCourse, String rawPiece) {
        TutorRepository.LessonRow lesson = lesson(user, rawCourse, rawPiece);
        return new ThreadView(user.kind() != UserKind.MINOR, repo.thread(lesson.id(), user.id()).stream().map(this::view).toList());
    }

    public void clear(AuthUser user, String rawCourse, String rawPiece) {
        TutorRepository.LessonRow lesson = lesson(user, rawCourse, rawPiece);
        if (user.kind() == UserKind.MINOR) throw TutorException.protectedThread();
        repo.clear(lesson.id(), user.id());
    }

    // ---------- preguntar ----------

    public Exchange ask(AuthUser user, String rawCourse, String rawPiece, AskRequest body, String ip) {
        UUID courseId = TutorService.id(rawCourse);
        TutorRepository.LessonRow lesson = lesson(user, rawCourse, rawPiece);
        String question = question(body == null ? null : body.question());
        if (!"DONE".equals(lesson.status())) throw new ApiException(HttpStatus.CONFLICT,"PIECE_NOT_READY", "Esta lección todavía no está lista");

        boolean minor = user.kind() == UserKind.MINOR;
        boolean blocked = minor && guard.localCheck(question).isPresent();
        Reserved reserved = reserve(user.id(), courseId, lesson.id(), question, blocked);
        if (blocked) {
            audit.record("TUTOR_QUESTION_BLOCKED", AuditService.DENIED, user.id(), user.id(), ip, Map.of("courseId", courseId.toString(), "pieceId", lesson.id().toString()));
            return reserved.exchange();
        }

        try {
            TutorContext.Prepared prepared = context.prepare(user.id(), minor, courseId, lesson, question);
            TutorAnswerValidator.Answer answer = answer(prepared);
            boolean filtered = minor && guard.localCheck(answer.text()).isPresent();
            if (filtered) {
                audit.record("TUTOR_ANSWER_FILTERED", AuditService.DENIED, user.id(), user.id(), ip, Map.of("courseId", courseId.toString(), "pieceId", lesson.id().toString()));
            }
            List<SourceRef> sources = filtered ? List.of() : prepared.pack().sources().stream().filter(s -> answer.citations().contains(s.n())).map(s -> new SourceRef(s.n(), s.page())).toList();
            return complete(user.id(), courseId, lesson.id(), reserved.userMessage(), question, filtered ? "FILTERED" : "OK", filtered ? FILTERED_ANSWER : answer.text(), sources);
        } catch (RuntimeException e) {
            // Nada de mensajes huérfanos: la pregunta queda marcada como fallida y el alumno la conserva en su caja
            repo.resolve(reserved.userMessage(), user.id(), "FAILED");
            throw e;
        }
    }

    private record Reserved(UUID userMessage, Exchange exchange) {}

    /** Comprueba los límites y reserva la pregunta en una transacción por usuario; un bloqueo de menor guarda también su respuesta fija. */
    private Reserved reserve(UUID userId, UUID courseId, UUID pieceId, String question, boolean blocked) {
        return tx.execute(status -> {
            repo.lockUser(userId);
            TutorRepository.Counts counts = repo.counts(userId);
            if (counts.lastHour() >= props.maxPerHour() || counts.lastDay() >= props.maxPerDay()) throw TutorException.rateLimited();
            if (repo.hasRecentPending(userId, props.busyWindow().toSeconds())) throw TutorException.busy();
            if (!blocked) return new Reserved(repo.insert(courseId, pieceId, userId, "USER", "PENDING", question, null), null);

            UUID asked = repo.insert(courseId, pieceId, userId, "USER", "BLOCKED", question, null);
            UUID answered = repo.insert(courseId, pieceId, userId, "TUTOR", "BLOCKED", CourseException.BLOCKED_MESSAGE, null);
            Reserved reserved = new Reserved(asked, exchange(asked, answered, userId));
            repo.prune(pieceId, userId, props.maxStored());
            return reserved;
        });
    }

    /** Pide la respuesta al modelo y la valida; si no sirve, un único reintento. Un tope o un proveedor caído suben tal cual. */
    private TutorAnswerValidator.Answer answer(TutorContext.Prepared prepared) {
        for (int tries = 0; tries < 2; tries++) {
            try {
                AiResponse response = gateway.complete(prepared.request());
                if (response.finishReason() == FinishReason.LENGTH) {
                    log.info("Respuesta del tutor descartada (intento {}): cortada por el límite de salida", tries + 1);
                    continue;
                }
                var valid = validator.validate(response.text(), prepared.pack().size());
                if (valid.isPresent()) return valid.get();
                // Solo la categoría del problema, nunca el texto
                log.info("Respuesta del tutor descartada (intento {}): {}", tries + 1, validator.reasonFor(response.text(), prepared.pack().size()));
            } catch (AiException e) {
                // Una respuesta vacía ya cobrada o una entrada demasiado grande cuentan como un intento fallido, no como una caída del proveedor
                if (e.kind() != AiErrorKind.INVALID_RESPONSE && e.kind() != AiErrorKind.INPUT_TOO_LARGE) throw e;
                log.info("Respuesta del tutor descartada (intento {}): {}", tries + 1, e.kind());
            }
        }
        throw TutorException.failed();
    }

    /** Guarda el intercambio de golpe: la pregunta pasa a correcta, entra la respuesta y se poda el hilo. Si el hilo se vació mientras tanto, la pregunta se guarda de nuevo. */
    private Exchange complete(UUID userId, UUID courseId, UUID pieceId, UUID pending, String question, String status, String content, List<SourceRef> sources) {
        return tx.execute(txStatus -> {
            UUID asked = repo.resolve(pending, userId, "OK") == 1 ? pending : repo.insert(courseId, pieceId, userId, "USER", "OK", question, null);
            UUID answered = repo.insert(courseId, pieceId, userId, "TUTOR", status, content, sources.isEmpty() ? null : write(sources));
            Exchange exchange = exchange(asked, answered, userId);
            repo.prune(pieceId, userId, props.maxStored());
            return exchange;
        });
    }

    private Exchange exchange(UUID asked, UUID answered, UUID userId) {
        return new Exchange(view(repo.message(asked, userId).orElseThrow()), view(repo.message(answered, userId).orElseThrow()));
    }

    // ---------- validación y ayudas ----------

    static UUID id(String raw) {
        try {
            if (raw == null) throw ApiException.notFound();
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound();
        }
    }

    /** La lección propia del usuario en ese curso; ajena, inexistente o de otro tipo responden igual (404). */
    private TutorRepository.LessonRow lesson(AuthUser user, String rawCourse, String rawPiece) {
        return repo.lesson(id(rawCourse), id(rawPiece), user.id()).orElseThrow(ApiException::notFound);
    }

    /** Entre 1 y 1 000 caracteres, sin caracteres de control salvo el salto de línea. */
    static String question(Object raw) {
        if (!(raw instanceof String text)) throw ApiException.validation("Escribe una pregunta");
        String question = text.strip();
        if (question.isEmpty() || question.length() > MAX_QUESTION) throw ApiException.validation("La pregunta debe tener entre 1 y " + MAX_QUESTION + " caracteres");
        for (int i = 0; i < question.length(); i++) {
            char c = question.charAt(i);
            if (Character.isISOControl(c) && c != '\n') throw ApiException.validation("La pregunta lleva caracteres no permitidos");
        }
        return question;
    }

    private MessageView view(TutorRepository.MessageRow row) {
        List<SourceRef> sources = row.sources() == null ? null : read(row.sources());
        return new MessageView(row.id(), row.role(), row.content(), row.status(), sources == null || sources.isEmpty() ? null : sources, row.createdAt());
    }

    private String write(List<SourceRef> sources) {
        try {
            return json.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron guardar las fuentes");
        }
    }

    private List<SourceRef> read(String raw) {
        try {
            return json.readValue(raw, new TypeReference<List<SourceRef>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
