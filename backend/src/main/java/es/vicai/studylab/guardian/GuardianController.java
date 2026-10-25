package es.vicai.studylab.guardian;

import es.vicai.studylab.security.AccessGuard;
import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel del adulto tutor (F08): solo lectura. {@code /api/tutor/**} ya exige ser adulto (SecurityConfig) y cada método empieza por comprobar que el alumno es
 * suyo: sin vínculo, inexistente o con un id mal formado responden igual (404) y la comprobación deja su rastro en la auditoría.
 */
@RestController
public class GuardianController {

    private static final String STUDENT = "/api/tutor/students/{id}";

    private final GuardianService panel;
    private final AccessGuard guard;

    public GuardianController(GuardianService panel, AccessGuard guard) {
        this.panel = panel;
        this.guard = guard;
    }

    @GetMapping(STUDENT + "/overview")
    GuardianService.Overview overview(@AuthenticationPrincipal AuthUser adult, @PathVariable String id, HttpServletRequest request) {
        return panel.overview(guard.requireStudentOf(adult, id, request.getRemoteAddr()));
    }

    @GetMapping(STUDENT + "/courses/{course}")
    GuardianService.CourseDetail course(@AuthenticationPrincipal AuthUser adult, @PathVariable String id, @PathVariable String course, HttpServletRequest request) {
        return panel.course(guard.requireStudentOf(adult, id, request.getRemoteAddr()), course);
    }

    @GetMapping(STUDENT + "/courses/{course}/attempts")
    GuardianService.Attempts attempts(@AuthenticationPrincipal AuthUser adult, @PathVariable String id, @PathVariable String course, HttpServletRequest request) {
        UUID student = guard.requireStudentOf(adult, id, request.getRemoteAddr());
        return panel.attempts(adult, student, course, request.getRemoteAddr());
    }

    @GetMapping(STUDENT + "/conversations")
    List<GuardianService.ConversationView> conversations(@AuthenticationPrincipal AuthUser adult, @PathVariable String id, HttpServletRequest request) {
        UUID student = guard.requireStudentOf(adult, id, request.getRemoteAddr());
        return panel.conversations(adult, student, request.getRemoteAddr());
    }

    @GetMapping(STUDENT + "/courses/{course}/pieces/{piece}/tutor")
    GuardianService.ThreadView thread(@AuthenticationPrincipal AuthUser adult, @PathVariable String id, @PathVariable String course, @PathVariable String piece,
                                  HttpServletRequest request) {
        UUID student = guard.requireStudentOf(adult, id, request.getRemoteAddr());
        return panel.thread(adult, student, course, piece, request.getRemoteAddr());
    }

    @GetMapping(STUDENT + "/alerts")
    List<GuardianService.AlertView> alerts(@AuthenticationPrincipal AuthUser adult, @PathVariable String id, HttpServletRequest request) {
        UUID student = guard.requireStudentOf(adult, id, request.getRemoteAddr());
        return panel.alerts(adult, student, request.getRemoteAddr());
    }
}
