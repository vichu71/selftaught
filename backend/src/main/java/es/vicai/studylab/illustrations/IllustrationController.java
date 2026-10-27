package es.vicai.studylab.illustrations;

import es.vicai.studylab.generation.CourseContentService;
import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las ilustraciones de un curso (F12). Todos exigen sesión (por defecto en SecurityConfig) y los que cambian algo, CSRF. Un curso, tema o imagen ajenos o inexistentes
 * responden lo mismo (404). La imagen se entrega solo a su propietario, nunca como caché pública, y con {@code nosniff}: es contenido del usuario.
 */
@RestController
public class IllustrationController {

    private static final String IMAGE = "/api/courses/{course}/topics/{topic}/image";

    private final IllustrationService service;

    public IllustrationController(IllustrationService service) {
        this.service = service;
    }

    @GetMapping("/api/illustrations/offer")
    IllustrationService.Offer offer(@AuthenticationPrincipal AuthUser user) {
        return service.offer(user);
    }

    @GetMapping(IMAGE)
    ResponseEntity<byte[]> image(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String topic,
                                 @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Optional<ImageRepository.Content> found = service.image(user, course, topic);
        if (found.isEmpty()) throw es.vicai.studylab.web.ApiException.notFound();
        ImageRepository.Content image = found.get();
        String etag = "\"" + image.id() + "-" + image.generatedAt().toEpochMilli() + "\"";
        ResponseEntity.BodyBuilder response = ResponseEntity.status(etag.equals(ifNoneMatch) ? HttpStatus.NOT_MODIFIED : HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache").header(HttpHeaders.ETAG, etag).header("X-Content-Type-Options", "nosniff");
        if (etag.equals(ifNoneMatch)) return response.build();
        return response.contentType(MediaType.parseMediaType(image.mime())).body(image.bytes());
    }

    @PostMapping(IMAGE + "/regenerate")
    ResponseEntity<CourseContentService.CourseDetailView> regenerate(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String topic,
                                                                     HttpServletRequest request) {
        return ResponseEntity.accepted().body(service.regenerate(user, course, topic, request.getRemoteAddr()));
    }

    @DeleteMapping(IMAGE)
    CourseContentService.CourseDetailView remove(@AuthenticationPrincipal AuthUser user, @PathVariable String course, @PathVariable String topic, HttpServletRequest request) {
        return service.remove(user, course, topic, request.getRemoteAddr());
    }
}
