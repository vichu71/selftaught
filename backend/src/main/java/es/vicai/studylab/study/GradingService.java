package es.vicai.studylab.study;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiRequest;
import es.vicai.studylab.ai.AiResponse;
import es.vicai.studylab.ai.AiTask;
import es.vicai.studylab.ai.FinishReason;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.safety.ContentGuard;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserKind;
import es.vicai.studylab.web.ApiException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * «Corregir con IA» (ADR-52): una segunda opinión opcional sobre la respuesta de un ejercicio. Pasa por {@link AiGateway} con la tarea {@code ASSESSMENT}; el
 * enunciado, la solución y la respuesta van como DATOS; el veredicto se valida con un esquema estricto (un reintento). Una corrección por intento, un tope
 * diario por usuario y una a la vez. Para un menor, su respuesta pasa por {@link ContentGuard} antes de salir y el comentario del modelo, antes de guardarse.
 * Nunca se registra ni se audita lo que escribe el alumno; un fallo conserva su respuesta.
 */
@Service
public class GradingService {

    static final String FILTERED_COMMENT = "La IA no puede comentar esta respuesta. Revisa la solución y autocorrígete.";

    public record Verdict(String verdict, String comment) {}

    private final StudyRepository repo;
    private final StudyProperties props;
    private final AiGateway gateway;
    private final GradeParser parser;
    private final ContentGuard guard;
    private final AuditService audit;
    private final ObjectMapper json;
    /** Los usuarios con una corrección en marcha (una sola instancia del backend: ver ADR-52 y F6-2). */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public GradingService(StudyRepository repo, StudyProperties props, AiGateway gateway, GradeParser parser, ContentGuard guard, AuditService audit, ObjectMapper json) {
        this.repo = repo;
        this.props = props;
        this.gateway = gateway;
        this.parser = parser;
        this.guard = guard;
        this.audit = audit;
        this.json = json;
    }

    public Verdict grade(AuthUser user, String rawCourse, String rawPiece, String rawItem, String rawAttempt, String ip) {
        UUID courseId = StudyService.id(rawCourse);
        UUID pieceId = StudyService.id(rawPiece);
        int item = StudyService.item(rawItem);
        StudyRepository.PieceRow piece = repo.piece(courseId, pieceId, user.id()).filter(p -> "EXERCISES".equals(p.kind())).orElseThrow(ApiException::notFound);
        StudyRepository.ExerciseAttemptRow attempt = repo.exerciseAttempt(StudyService.id(rawAttempt), courseId, pieceId, item, user.id()).orElseThrow(ApiException::notFound);

        if (attempt.aiRequestedAt() != null) throw StudyException.alreadyGraded();
        if (repo.aiGradesLastDay(user.id()) >= props.aiGradesPerDay()) throw StudyException.aiLimit();
        if (!running.add(user.id())) throw StudyException.busy();
        try {
            boolean minor = user.kind() == UserKind.MINOR;
            if (minor && guard.localCheck(attempt.answer()).isPresent()) {
                audit.record("STUDY_ANSWER_BLOCKED", AuditService.DENIED, user.id(), user.id(), ip, Map.of("courseId", courseId.toString(), "pieceId", pieceId.toString()));
                throw StudyException.answerNotAllowed();
            }
            if (repo.claimAiGrade(attempt.id(), user.id()) == 0) throw StudyException.alreadyGraded();

            JsonNode exercise = exercise(piece, item);
            AiRequest request = AiRequest.of(AiTask.ASSESSMENT, user.id(), "grade-exercise").course(courseId).data("pieza", "correccion")
                    .data("audiencia", minor ? "escolar" : "general").data("enunciado", exercise.path("statement").asText(""))
                    .data("solucion", exercise.path("solution").asText("")).data("respuesta", attempt.answer()).maxOutputTokens(600);

            GradeParser.Grade grade = null;
            for (int tries = 0; tries < 2 && grade == null; tries++) {
                try {
                    AiResponse response = gateway.complete(request);
                    if (response.finishReason() != FinishReason.LENGTH) grade = parser.parse(response.text()).orElse(null);
                } catch (AiException e) {
                    // Una respuesta vacía ya cobrada o una entrada demasiado grande cuentan como un intento fallido, no como una caída del proveedor
                    if (e.kind() != AiErrorKind.INVALID_RESPONSE && e.kind() != AiErrorKind.INPUT_TOO_LARGE) throw e;
                }
            }
            if (grade == null) throw StudyException.gradingFailed();

            String comment = minor && guard.localCheck(grade.comment()).isPresent() ? FILTERED_COMMENT : grade.comment();
            repo.saveAiGrade(attempt.id(), user.id(), grade.verdict(), comment);
            return new Verdict(grade.verdict(), comment);
        } finally {
            running.remove(user.id());
        }
    }

    private JsonNode exercise(StudyRepository.PieceRow piece, int item) {
        try {
            return Optional.ofNullable(json.readTree(piece.contentJson()).get("exercises")).filter(list -> item < list.size()).map(list -> list.get(item))
                    .orElseThrow(ApiException::notFound);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dato guardado no válido");
        }
    }
}
