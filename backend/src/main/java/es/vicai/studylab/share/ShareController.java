package es.vicai.studylab.share;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
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
 * Compartir un curso por código y canjearlo (F09). Todos exigen sesión (por defecto en SecurityConfig) y CSRF en lo que cambia datos. Un curso o un código ajenos responden igual
 * que uno inexistente (404); un código que no vale responde siempre lo mismo, sin decir por qué.
 */
@RestController
public class ShareController {

    private final ShareService share;

    public ShareController(ShareService share) {
        this.share = share;
    }

    @PostMapping("/api/courses/{course}/share")
    ResponseEntity<ShareService.Created> create(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @RequestBody(required = false) Map<String, Object> body,
                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(share.create(user, course, body, request.getRemoteAddr()));
    }

    @GetMapping("/api/courses/{course}/share")
    List<ShareService.CodeView> list(@AuthenticationPrincipal AuthUser user, @PathVariable String course) {
        return share.list(user, course);
    }

    @DeleteMapping("/api/courses/{course}/share/{code}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String code, HttpServletRequest request) {
        share.revoke(user, course, code, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/courses/redeem")
    ResponseEntity<ShareService.Redeemed> redeem(@AuthenticationPrincipal AuthUser user, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(share.redeem(user, body, request.getRemoteAddr()));
    }
}
