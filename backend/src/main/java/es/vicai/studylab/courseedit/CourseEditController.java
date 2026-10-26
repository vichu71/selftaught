package es.vicai.studylab.courseedit;

import es.vicai.studylab.generation.CourseContentService;
import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editar un curso propio (F09). Todos exigen sesión (por defecto en SecurityConfig) y CSRF, y devuelven el detalle del curso ya cambiado. Un curso, tema, capítulo o
 * pieza ajenos responden igual que uno inexistente (404); el adulto tutor no edita los cursos de su menor.
 */
@RestController
public class CourseEditController {

    private static final String COURSE = "/api/courses/{course}";

    private final CourseEditService edit;

    public CourseEditController(CourseEditService edit) {
        this.edit = edit;
    }

    @PutMapping(COURSE + "/topics/order")
    CourseContentService.CourseDetailView reorderTopics(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return edit.reorderTopics(user, course, body, request.getRemoteAddr());
    }

    @PutMapping(COURSE + "/topics/{topic}")
    CourseContentService.CourseDetailView renameTopic(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String topic, @RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        return edit.renameTopic(user, course, topic, body, request.getRemoteAddr());
    }

    @DeleteMapping(COURSE + "/topics/{topic}")
    CourseContentService.CourseDetailView removeTopic(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String topic, HttpServletRequest request) {
        return edit.removeTopic(user, course, topic, request.getRemoteAddr());
    }

    @PostMapping(COURSE + "/topics")
    ResponseEntity<CourseContentService.CourseDetailView> addTopic(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @RequestBody Map<String, Object> body,
                                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(edit.addTopic(user, course, body, request.getRemoteAddr()));
    }

    @PutMapping(COURSE + "/topics/{topic}/chapters/order")
    CourseContentService.CourseDetailView reorderChapters(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String topic, @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        return edit.reorderChapters(user, course, topic, body, request.getRemoteAddr());
    }

    @PostMapping(COURSE + "/topics/{topic}/chapters")
    ResponseEntity<CourseContentService.CourseDetailView> addChapter(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String topic,
                                                                     @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(edit.addChapter(user, course, topic, body, request.getRemoteAddr()));
    }

    @PutMapping(COURSE + "/chapters/{chapter}")
    CourseContentService.CourseDetailView renameChapter(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String chapter, @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        return edit.renameChapter(user, course, chapter, body, request.getRemoteAddr());
    }

    @DeleteMapping(COURSE + "/chapters/{chapter}")
    CourseContentService.CourseDetailView removeChapter(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String chapter, HttpServletRequest request) {
        return edit.removeChapter(user, course, chapter, request.getRemoteAddr());
    }

    @PutMapping(COURSE + "/pieces/{piece}/content")
    CourseContentService.CourseDetailView editLesson(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece, @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        return edit.editLesson(user, course, piece, body, request.getRemoteAddr());
    }

    @PostMapping(COURSE + "/pieces/{piece}/regenerate")
    ResponseEntity<CourseContentService.CourseDetailView> regenerate(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece, HttpServletRequest request) {
        return ResponseEntity.accepted().body(edit.regenerate(user, course, piece, request.getRemoteAddr()));
    }
}
