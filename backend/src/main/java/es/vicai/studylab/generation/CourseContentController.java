package es.vicai.studylab.generation;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El curso ya generado: detalle con temario y progreso, cada pieza, «Reanudar» y la exportación. Todos exigen sesión (por defecto en SecurityConfig),
 * el CSRF cubre «Reanudar» y un curso o pieza ajenos responden igual que uno inexistente (404). El tutor aún no ve el contenido de su menor (F08).
 */
@RestController
@RequestMapping("/api/courses/{id}")
public class CourseContentController {

    private final CourseContentService service;

    public CourseContentController(CourseContentService service) {
        this.service = service;
    }

    @GetMapping
    CourseContentService.CourseDetailView get(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return service.detail(user, id);
    }

    @GetMapping("/pieces/{pieceId}")
    CourseContentService.PieceView piece(@AuthenticationPrincipal AuthUser user, @PathVariable String id, @PathVariable String pieceId) {
        return service.piece(user, id, pieceId);
    }

    @PostMapping("/resume")
    ResponseEntity<CourseContentService.CourseDetailView> resume(@AuthenticationPrincipal AuthUser user, @PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resume(user, id, request.getRemoteAddr()));
    }

    @GetMapping("/export")
    ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"curso.json\"")
                .body(service.export(user, id));
    }
}
