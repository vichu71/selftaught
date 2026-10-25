package es.vicai.studylab.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.vicai.studylab.ai.AiErrorKind;
import es.vicai.studylab.ai.AiException;
import es.vicai.studylab.ai.AiGateway;
import es.vicai.studylab.ai.AiRequest;
import es.vicai.studylab.ai.AiResponse;
import es.vicai.studylab.ai.AiTask;
import es.vicai.studylab.ai.FinishReason;
import es.vicai.studylab.ai.lock.GenerationLock;
import es.vicai.studylab.audit.AuditService;
import es.vicai.studylab.course.CourseSheet;
import es.vicai.studylab.material.Fragment;
import es.vicai.studylab.material.MaterialSearch;
import es.vicai.studylab.safety.ContentGuard;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Genera los cursos en cola (ADR-42 a 47). Un curso es primero su temario y después una pieza tras otra (lección, ejercicios, examen), cada una
 * validada contra un esquema estricto, con un reintento y sin que una mala tumbe el curso. Una pieza hecha no se vuelve a pedir nunca. El cerrojo
 * del usuario se renueva antes de cada llamada. Al llegar al tope de gasto o caer el proveedor el curso se PAUSA con su motivo y no se gasta más
 * hasta que el usuario pulse «Reanudar». Para un menor, todo lo que va al modelo (fragmentos de material) y todo lo que se guarda pasa por
 * {@link ContentGuard}. Nunca se registra ni se audita contenido: solo ids y un motivo genérico.
 *
 * <p>{@link #processPending()} es síncrono (lo usan los tests y el hilo de fondo).
 */
@Component
public class CourseGenerator {

    private static final Logger log = LoggerFactory.getLogger(CourseGenerator.class);

    private static final String REINFORCEMENT = "Esta persona es una alumna o un alumno de edad escolar. Escribe un contenido apto para escolares: "
            + "sin violencia gráfica, sin contenido sexual explícito, sin drogas, sin autolesiones ni instrucciones para hacer daño, y con un tono educativo.";

    private enum Action { REQUEUE, PAUSE, FAIL }

    /** Parada ordenada de la generación de un curso. */
    private static final class Halt extends RuntimeException {
        private final transient Action action;
        private final String reason;

        Halt(Action action, String reason) {
            super(null, null, false, false);
            this.action = action;
            this.reason = reason;
        }
    }

    private record Context(GenerationRepository.Claim claim, CourseSheet sheet, GenerationLock.Lease lease) {
        UUID courseId() {
            return claim.id();
        }

        UUID ownerId() {
            return claim.ownerId();
        }

        boolean minor() {
            return claim.minor();
        }

        String audience() {
            return minor() ? "escolar" : "general";
        }
    }

    /** El resultado de comprobar la respuesta del modelo: o el contenido listo para guardar, o el motivo genérico por el que no sirve. */
    private record Checked(String markdown, String json, String sources, String problem) {
        static Checked ok(String markdown, String json, String sources) {
            return new Checked(markdown, json, sources, null);
        }

        static Checked bad(String problem) {
            return new Checked(null, null, null, problem);
        }
    }

    private static final String INVALID = "INVALID_OUTPUT";
    private static final String BLOCKED = "CONTENT_BLOCKED";

    private final GenerationRepository repo;
    private final AiGateway gateway;
    private final GenerationLock locks;
    private final MaterialSearch search;
    private final ContentGuard guard;
    private final OutlineParser outlines;
    private final LessonValidator lessons;
    private final ExercisesParser exercises;
    private final ExamParser exams;
    private final AuditService audit;
    private final GenerationProperties props;
    private final ObjectMapper json;

    public CourseGenerator(GenerationRepository repo, AiGateway gateway, GenerationLock locks, MaterialSearch search, ContentGuard guard,
                           OutlineParser outlines, LessonValidator lessons, ExercisesParser exercises, ExamParser exams, AuditService audit,
                           GenerationProperties props, ObjectMapper json) {
        this.repo = repo;
        this.gateway = gateway;
        this.locks = locks;
        this.search = search;
        this.guard = guard;
        this.outlines = outlines;
        this.lessons = lessons;
        this.exercises = exercises;
        this.exams = exams;
        this.audit = audit;
        this.props = props;
        this.json = json;
    }

    // ---------- la cola ----------

    /** Genera todos los cursos en cola, uno tras otro, y devuelve cuántos empezó. Un curso devuelto a la cola no se reclama dos veces en la pasada. */
    public int processPending() {
        Set<UUID> seen = new HashSet<>();
        int started = 0;
        Optional<GenerationRepository.Claim> next;
        while ((next = repo.claimNext(seen)).isPresent()) {
            GenerationRepository.Claim claim = next.get();
            seen.add(claim.id());
            if (generate(claim)) started++;
        }
        return started;
    }

    /** Al arrancar: lo que quedó generándose vuelve a la cola y sigue por lo que falte. */
    public void recoverInterrupted() {
        repo.recoverInterrupted();
    }

    private boolean generate(GenerationRepository.Claim claim) {
        GenerationLock.Lease lease;
        try {
            lease = locks.acquire(claim.ownerId());
        } catch (AiException e) { // ocupado o usuario dado de baja: el curso espera su turno
            repo.requeue(claim.id(), claim.ownerId());
            return false;
        }
        try (lease) {
            Halt halt = null;
            try {
                run(new Context(claim, readSheet(claim), lease));
            } catch (Halt h) {
                halt = h;
            } catch (AiException e) {
                halt = haltFor(e);
            } catch (RuntimeException e) {
                // Solo el tipo: el mensaje puede llevar texto del curso. Se pausa para no repetir un fallo interno en bucle
                log.error("Fallo inesperado al generar el curso {} ({})", claim.id(), e.getClass().getSimpleName());
                halt = new Halt(Action.PAUSE, "PROVIDER");
            }
            if (halt != null) stop(claim, halt);
        }
        return true;
    }

    private void stop(GenerationRepository.Claim claim, Halt halt) {
        Map<String, String> details = halt.reason == null ? Map.of("courseId", claim.id().toString())
                : Map.of("courseId", claim.id().toString(), "reason", halt.reason);
        switch (halt.action) {
            case REQUEUE -> repo.requeue(claim.id(), claim.ownerId());
            case PAUSE -> {
                repo.finish(claim.id(), claim.ownerId(), "PAUSED", halt.reason);
                audit.record("COURSE_GENERATION_PAUSED", AuditService.FAILED, claim.ownerId(), claim.ownerId(), null, details);
            }
            case FAIL -> {
                repo.finish(claim.id(), claim.ownerId(), "FAILED", halt.reason);
                audit.record("COURSE_GENERATION_FAILED", AuditService.FAILED, claim.ownerId(), claim.ownerId(), null, details);
            }
        }
    }

    private static Halt haltFor(AiException e) {
        return switch (e.kind()) {
            case USER_LIMIT, BUDGET_BLOCKED -> new Halt(Action.PAUSE, "BUDGET");
            case USER_NOT_ALLOWED, GENERATION_BUSY -> new Halt(Action.REQUEUE, null);
            default -> new Halt(Action.PAUSE, "PROVIDER");
        };
    }

    /** Una respuesta vacía o una entrada demasiado grande no es culpa del proveedor: cuenta como un intento fallido de la pieza. */
    private static boolean isOutputProblem(AiException e) {
        return e.kind() == AiErrorKind.INVALID_RESPONSE || e.kind() == AiErrorKind.INPUT_TOO_LARGE;
    }

    private CourseSheet readSheet(GenerationRepository.Claim claim) {
        try {
            return json.readValue(claim.sheetJson(), CourseSheet.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha del curso no válida");
        }
    }

    // ---------- un curso ----------

    private void run(Context ctx) {
        if (!repo.hasOutline(ctx.courseId(), ctx.ownerId())) createOutline(ctx);
        for (GenerationRepository.PieceRow piece : repo.piecesToGenerate(ctx.courseId(), ctx.ownerId())) {
            renew(ctx);
            // Los ejercicios se escriben a partir de su lección: si la lección no salió, esperan a que «Reanudar» la reintente
            if ("EXERCISES".equals(piece.kind()) && repo.doneLesson(piece.chapterId(), ctx.ownerId()).isEmpty()) continue;
            generatePiece(ctx, piece);
        }
        GenerationRepository.Counts counts = repo.counts(ctx.courseId(), ctx.ownerId());
        if (counts.failed() > 0 || counts.pending() > 0) throw new Halt(Action.PAUSE, "CONTENT");
        repo.finish(ctx.courseId(), ctx.ownerId(), "READY", null);
        audit.record("COURSE_GENERATION_COMPLETED", AuditService.OK, ctx.ownerId(), ctx.ownerId(), null, Map.of("courseId", ctx.courseId().toString()));
    }

    private void renew(Context ctx) {
        if (!ctx.lease().renew()) throw new Halt(Action.REQUEUE, null); // el cerrojo ya no es nuestro: no se escribe nada más
    }

    // ---------- el temario ----------

    private void createOutline(Context ctx) {
        OutlineParser.Outline outline = null;
        for (int attempt = 0; attempt < 2 && outline == null; attempt++) {
            renew(ctx);
            AiResponse response;
            try {
                response = gateway.complete(outlineRequest(ctx, attempt));
            } catch (AiException e) {
                if (isOutputProblem(e)) continue;
                throw e;
            }
            if (response.finishReason() == FinishReason.LENGTH) continue;
            Optional<OutlineParser.Outline> parsed = outlines.parse(response.text(), ctx.sheet().topicsCount());
            if (parsed.isEmpty()) continue;
            OutlineParser.Outline candidate = parsed.get();
            // Un menor: si el modelo juzga el tema no apto, el curso falla sin generar nada (ADR-47, cierra F4-5)
            if (ctx.minor() && !candidate.appropriate()) throw new Halt(Action.FAIL, "TOPIC_NOT_ALLOWED");
            if (candidate.topics().isEmpty()) continue;
            if (ctx.minor() && blocked(outlineText(candidate))) continue;
            outline = candidate;
        }
        if (outline == null) throw new Halt(Action.PAUSE, "CONTENT");

        int pieces = 0;
        for (OutlineParser.Topic topic : outline.topics()) pieces += topic.chapters().size() * 2 + (ctx.sheet().examPerTopic() ? 1 : 0);
        if (pieces > props.maxPieces()) throw new Halt(Action.FAIL, "LIMIT");
        repo.saveOutline(ctx.courseId(), ctx.ownerId(), outline, ctx.sheet().examPerTopic());
    }

    private AiRequest outlineRequest(Context ctx, int attempt) {
        CourseSheet sheet = ctx.sheet();
        AiRequest request = AiRequest.of(AiTask.OUTLINE, ctx.ownerId(), "generation-outline").course(ctx.courseId())
                .data("pieza", "temario").data("audiencia", ctx.audience()).data("tema", sheet.topic()).data("enfoque", sheet.focus())
                .data("nivel", sheet.startLevel()).data("objetivo", sheet.goal()).data("numero_temas", String.valueOf(sheet.topicsCount()))
                .data("examen_por_tema", sheet.examPerTopic() ? "si" : "no");
        return attempt > 0 && ctx.minor() ? request.data("refuerzo", REINFORCEMENT) : request;
    }

    private static String outlineText(OutlineParser.Outline outline) {
        StringBuilder text = new StringBuilder();
        for (OutlineParser.Topic topic : outline.topics()) {
            text.append(topic.title()).append(". ").append(topic.summary()).append(". ");
            for (OutlineParser.Chapter chapter : topic.chapters()) text.append(chapter.title()).append(". ").append(chapter.objective()).append(". ");
        }
        return text.toString();
    }

    // ---------- una pieza ----------

    private void generatePiece(Context ctx, GenerationRepository.PieceRow piece) {
        String lesson = "EXERCISES".equals(piece.kind()) ? repo.doneLesson(piece.chapterId(), ctx.ownerId()).orElseThrow() : null;
        SourcePack pack = "LESSON".equals(piece.kind()) ? sourcesFor(ctx, piece) : SourcePack.of(List.of(), text -> true);

        int calls = 0;
        boolean sawBlocked = false;
        String problem = INVALID;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) renew(ctx);
            calls++;
            AiResponse response;
            try {
                response = gateway.complete(pieceRequest(ctx, piece, pack, lesson, attempt));
            } catch (AiException e) {
                if (isOutputProblem(e)) continue;
                throw e;
            }
            Checked checked = check(ctx, piece, response, pack);
            if (checked.problem() == null) {
                repo.saveDone(piece.id(), ctx.courseId(), ctx.ownerId(), checked.markdown(), checked.json(), checked.sources(), calls);
                return;
            }
            problem = checked.problem();
            sawBlocked |= BLOCKED.equals(problem);
        }
        repo.saveFailed(piece.id(), ctx.courseId(), ctx.ownerId(), problem, calls);
        log.info("Pieza {} del curso {} sin generar ({})", piece.id(), ctx.courseId(), problem);
        if (sawBlocked) {
            audit.record("COURSE_CONTENT_BLOCKED", AuditService.DENIED, ctx.ownerId(), ctx.ownerId(), null,
                    Map.of("courseId", ctx.courseId().toString(), "pieceId", piece.id().toString(), "kind", piece.kind()));
        }
    }

    private AiRequest pieceRequest(Context ctx, GenerationRepository.PieceRow piece, SourcePack pack, String lesson, int attempt) {
        CourseSheet sheet = ctx.sheet();
        String unit = piece.topicTitle() + ": " + piece.topicSummary();
        AiRequest request;
        switch (piece.kind()) {
            case "LESSON" -> {
                request = AiRequest.of(AiTask.LESSON, ctx.ownerId(), "generation-lesson").course(ctx.courseId()).data("pieza", "leccion")
                        .data("audiencia", ctx.audience()).data("tema", sheet.topic()).data("enfoque", sheet.focus()).data("nivel", sheet.startLevel())
                        .data("unidad", unit).data("capitulo", piece.chapterTitle()).data("objetivo", piece.chapterObjective());
                if (!pack.isEmpty()) request = request.data("fuentes", pack.text());
            }
            case "EXERCISES" -> request = AiRequest.of(AiTask.ASSESSMENT, ctx.ownerId(), "generation-exercises").course(ctx.courseId())
                    .data("pieza", "ejercicios").data("audiencia", ctx.audience()).data("tema", sheet.topic()).data("nivel", sheet.startLevel())
                    .data("unidad", unit).data("capitulo", piece.chapterTitle()).data("objetivo", piece.chapterObjective()).data("leccion", lesson);
            default -> {
                StringBuilder chapters = new StringBuilder();
                for (GenerationRepository.ChapterRef chapter : repo.chaptersOf(piece.topicId(), ctx.ownerId())) {
                    chapters.append("- ").append(chapter.title()).append(": ").append(chapter.objective()).append('\n');
                }
                request = AiRequest.of(AiTask.ASSESSMENT, ctx.ownerId(), "generation-exam").course(ctx.courseId()).data("pieza", "examen")
                        .data("audiencia", ctx.audience()).data("tema", sheet.topic()).data("nivel", sheet.startLevel()).data("unidad", unit)
                        .data("capitulos", chapters.toString());
            }
        }
        return attempt > 0 && ctx.minor() ? request.data("refuerzo", REINFORCEMENT) : request;
    }

    /** Los fragmentos del material del curso más cercanos al capítulo, numerados; para un menor, sin los que el filtro rechaza. */
    private SourcePack sourcesFor(Context ctx, GenerationRepository.PieceRow piece) {
        List<UUID> documents = repo.documentsOf(ctx.courseId(), ctx.ownerId());
        if (documents.isEmpty()) return SourcePack.of(List.of(), text -> true);
        String query = ctx.sheet().topic() + ". " + piece.topicTitle() + ". " + piece.chapterTitle() + ". " + piece.chapterObjective();
        if (query.length() > 1900) query = query.substring(0, 1900);
        List<Fragment> fragments = search.search(ctx.ownerId(), query, SourcePack.MAX_FRAGMENTS * 2, documents);
        return SourcePack.of(fragments, text -> !ctx.minor() || !blocked(text));
    }

    // ---------- comprobar la respuesta ----------

    private Checked check(Context ctx, GenerationRepository.PieceRow piece, AiResponse response, SourcePack pack) {
        if (response.finishReason() == FinishReason.LENGTH) return Checked.bad(INVALID); // cortada por el límite de salida
        String text = response.text();
        try {
            switch (piece.kind()) {
                case "LESSON" -> {
                    Optional<LessonValidator.Lesson> lesson = lessons.validate(text, pack.size());
                    if (lesson.isEmpty()) return Checked.bad(INVALID);
                    if (ctx.minor() && blocked(lesson.get().markdown())) return Checked.bad(BLOCKED);
                    List<SourcePack.Source> used = pack.sources().stream().filter(s -> lesson.get().citations().contains(s.n())).toList();
                    return Checked.ok(lesson.get().markdown(), null, json.writeValueAsString(used));
                }
                case "EXERCISES" -> {
                    Optional<ExercisesParser.Exercises> parsed = exercises.parse(text);
                    if (parsed.isEmpty()) return Checked.bad(INVALID);
                    StringBuilder all = new StringBuilder();
                    for (ExercisesParser.Item item : parsed.get().items()) all.append(item.statement()).append(' ').append(item.solution()).append(' ').append(item.explanation()).append(' ');
                    if (ctx.minor() && blocked(all.toString())) return Checked.bad(BLOCKED);
                    return Checked.ok(null, json.writeValueAsString(Map.of("schemaVersion", 1, "exercises", parsed.get().items())), null);
                }
                default -> {
                    Optional<ExamParser.Exam> parsed = exams.parse(text);
                    if (parsed.isEmpty()) return Checked.bad(INVALID);
                    StringBuilder all = new StringBuilder();
                    for (ExamParser.Question q : parsed.get().questions()) {
                        all.append(q.text()).append(' ').append(String.join(" ", q.options())).append(' ').append(q.explanation()).append(' ');
                    }
                    if (ctx.minor() && blocked(all.toString())) return Checked.bad(BLOCKED);
                    return Checked.ok(null, json.writeValueAsString(Map.of("schemaVersion", 1, "questions", parsed.get().questions())), null);
                }
            }
        } catch (JsonProcessingException e) {
            return Checked.bad(INVALID);
        }
    }

    private boolean blocked(String text) {
        return guard.localCheck(text).isPresent();
    }
}
