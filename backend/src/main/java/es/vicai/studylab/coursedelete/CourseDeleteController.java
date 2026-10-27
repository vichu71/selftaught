package es.vicai.studylab.coursedelete;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Borrar cursos propios (F13). Exigen sesión (por defecto en SecurityConfig) y CSRF. Un curso ajeno o inexistente responde 404 igual; un menor, 403; en cola o
 * generándose, 409 {@code COURSE_BUSY}. Ni el adulto tutor borra los cursos de su menor.
 */
@RestController
public class CourseDeleteController {

    private final CourseDeleteService service;

    public CourseDeleteController(CourseDeleteService service) {
        this.service = service;
    }

    @DeleteMapping("/api/courses/{course}")
    ResponseEntity<Void> deleteOne(@AuthenticationPrincipal AuthUser user, @PathVariable String course, HttpServletRequest request) {
        service.deleteOne(user, course, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/courses/delete")
    ResponseEntity<Void> deleteMany(@AuthenticationPrincipal AuthUser user, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        service.deleteMany(user, body, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
