package es.vicai.studylab.user;

import es.vicai.studylab.owner.OwnerPolicy;
import es.vicai.studylab.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final OwnerPolicy ownerPolicy;

    public MeController(OwnerPolicy ownerPolicy) {
        this.ownerPolicy = ownerPolicy;
    }

    @GetMapping("/api/me")
    MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return MeResponse.of(user, ownerPolicy);
    }
}
