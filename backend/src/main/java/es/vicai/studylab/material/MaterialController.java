package es.vicai.studylab.material;

import es.vicai.studylab.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Material propio. Todos los endpoints exigen sesión (por defecto en SecurityConfig) y el CSRF cubre los que modifican. Un documento
 * ajeno o inexistente responde igual (404); el tutor de un menor lo ve en solo lectura pero no lo borra (403).
 */
@RestController
@RequestMapping("/api/material")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentView> upload(@AuthenticationPrincipal AuthUser user, @RequestPart("file") MultipartFile file,
                                        HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.upload(user, file, request.getRemoteAddr()));
    }

    @GetMapping
    MaterialService.Listing list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user);
    }

    @GetMapping("/{id}")
    DocumentView get(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return service.get(user, id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable String id, HttpServletRequest request) {
        service.delete(user, id, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
