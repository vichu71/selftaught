package es.vicai.studylab.course;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistente de creación de curso y cursos del usuario. Todos los endpoints exigen sesión (por defecto en SecurityConfig) y el CSRF cubre los que
 * modifican. Un curso o una consulta ajenos responden igual que uno inexistente (404).
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @PostMapping("/intake")
    ResponseEntity<CourseService.IntakeView> intake(@AuthenticationPrincipal AuthUser user, @RequestBody CourseService.IntakeRequest body,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.intake(user, body, request.getRemoteAddr()));
    }

    @PostMapping
    ResponseEntity<CourseService.CourseView> create(@AuthenticationPrincipal AuthUser user, @RequestBody CourseService.CreateRequest body,
                                                    HttpServletRequest request) {
        CourseService.Created result = service.create(user, body, request.getRemoteAddr());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.course());
    }

    @GetMapping
    List<CourseService.CourseView> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user);
    }

    /** F18: en qué cursos del que llama se usa cada documento suyo (solo lectura, sin parámetros). La ruta literal gana a /{id}. */
    @GetMapping("/material-usage")
    CourseService.MaterialUsage materialUsage(@AuthenticationPrincipal AuthUser user) {
        return service.materialUsage(user);
    }

    // GET /api/courses/{id} (detalle con temario y progreso) lo sirve generation.CourseContentController desde F05
}
