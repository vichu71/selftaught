package es.vicai.studylab.study;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estudio de los cursos propios (F06). Todos los endpoints exigen sesión (por defecto en SecurityConfig) y el CSRF cubre los que modifican. Un curso, pieza o
 * intento ajenos responden igual que uno inexistente (404); el tutor no lee los intentos de su menor por esta API (su panel es F08).
 */
@RestController
public class StudyController {

    private static final String PIECE = "/api/courses/{course}/pieces/{piece}";

    private final StudyService study;
    private final GradingService grading;

    public StudyController(StudyService study, GradingService grading) {
        this.study = study;
        this.grading = grading;
    }

    @GetMapping("/api/study/progress")
    List<StudyService.CourseProgress> progress(@AuthenticationPrincipal AuthUser user) {
        return study.progress(user);
    }

    @GetMapping("/api/courses/{course}/study")
    StudyService.Overview overview(@AuthenticationPrincipal AuthUser user, @PathVariable String course) {
        return study.overview(user, course);
    }

    @PutMapping(PIECE + "/done")
    ResponseEntity<Void> done(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece, @RequestBody StudyService.DoneRequest body) {
        study.markDone(user, course, piece, body);
        return ResponseEntity.noContent().build();
    }

    // ---------- ejercicios ----------

    @PostMapping(PIECE + "/exercises/{item}/attempts")
    ResponseEntity<StudyService.ExerciseAnswered> answer(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece,
                                                         @PathVariable String item, @RequestBody StudyService.AnswerRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(study.sendAnswer(user, course, piece, item, body));
    }

    @PutMapping(PIECE + "/exercises/{item}/attempts/{attempt}/result")
    ResponseEntity<Void> result(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece, @PathVariable String item,
                                @PathVariable String attempt, @RequestBody StudyService.ResultRequest body) {
        study.setResult(user, course, piece, item, attempt, body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(PIECE + "/exercises/{item}/attempts/{attempt}/ai-grade")
    GradingService.Verdict aiGrade(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece, @PathVariable String item,
                                   @PathVariable String attempt, HttpServletRequest request) {
        return grading.grade(user, course, piece, item, attempt, request.getRemoteAddr());
    }

    // ---------- exámenes ----------

    @PostMapping(PIECE + "/exam/attempts")
    ResponseEntity<StudyService.ExamStarted> start(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece) {
        StudyService.Started started = study.startExam(user, course, piece);
        return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK).body(started.exam());
    }

    @PostMapping(PIECE + "/exam/attempts/{attempt}/submit")
    ExamGrader.Result submit(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece, @PathVariable String attempt,
                             @RequestBody StudyService.SubmitRequest body) {
        return study.submitExam(user, course, piece, attempt, body);
    }

    @GetMapping(PIECE + "/exam/attempts/{attempt}")
    ExamGrader.Result examAttempt(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece, @PathVariable String attempt) {
        return study.examAttempt(user, course, piece, attempt);
    }

    // ---------- historial ----------

    @GetMapping(PIECE + "/attempts")
    StudyService.Attempts attempts(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece) {
        return study.attempts(user, course, piece);
    }
}
