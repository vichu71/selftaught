package es.vicai.studylab.generation;

import es.vicai.studylab.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resultados de los cursos de quien llama: piezas hechas, falladas y pendientes, y su coste. Solo lectura y solo lo propio. */
@RestController
public class ResultsController {

    private final CourseContentService service;

    public ResultsController(CourseContentService service) {
        this.service = service;
    }

    @GetMapping("/api/results")
    CourseContentService.Results results(@AuthenticationPrincipal AuthUser user) {
        return service.results(user);
    }
}
