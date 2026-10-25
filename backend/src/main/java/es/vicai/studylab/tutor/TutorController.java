package es.vicai.studylab.tutor;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * El tutor IA de las lecciones propias (F07). Todos los endpoints exigen sesión (por defecto en SecurityConfig) y el CSRF cubre los que modifican. Una lección ajena
 * o inexistente responde igual (404); el adulto tutor de un menor no lee ni toca su hilo por esta API (su panel es F08).
 */
@RestController
public class TutorController {

    private static final String TUTOR = "/api/courses/{course}/pieces/{piece}/tutor";

    private final TutorService tutor;

    public TutorController(TutorService tutor) {
        this.tutor = tutor;
    }

    @GetMapping(TUTOR)
    TutorService.ThreadView thread(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece) {
        return tutor.thread(user, course, piece);
    }

    @PostMapping(TUTOR)
    ResponseEntity<TutorService.Exchange> ask(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece,
                                              @RequestBody TutorService.AskRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tutor.ask(user, course, piece, body, request.getRemoteAddr()));
    }

    @DeleteMapping(TUTOR)
    ResponseEntity<Void> clear(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String piece) {
        tutor.clear(user, course, piece);
        return ResponseEntity.noContent().build();
    }
}
