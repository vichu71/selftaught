package es.vicai.studylab.user;

import es.vicai.studylab.security.AccessGuard;
import es.vicai.studylab.security.AuthUser;
import es.vicai.studylab.user.UserRepository.StudentRow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Solo adultos (SecurityConfig exige ROLE_TUTOR); además cada acción sobre un menor pasa por AccessGuard. */
@RestController
@RequestMapping("/api/tutor/students")
public class StudentController {

    public record CreateStudentRequest(
            @NotBlank @Size(max = 50) String displayName,
            @NotBlank @Size(max = 60) String loginHint,
            @NotNull @Size(min = 10, max = 128) String password) {}

    public record ResetPasswordRequest(@NotNull @Size(min = 10, max = 128) String newPassword) {}

    public record StudentResponse(UUID id, String displayName, String loginId, UserStatus status) {
        static StudentResponse of(StudentRow row) {
            return new StudentResponse(row.id(), row.displayName(), row.loginId(), row.status());
        }
    }

    private final StudentService students;
    private final AccessGuard guard;

    public StudentController(StudentService students, AccessGuard guard) {
        this.students = students;
        this.guard = guard;
    }

    @GetMapping
    List<StudentResponse> list(@AuthenticationPrincipal AuthUser tutor) {
        return students.list(tutor).stream().map(StudentResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StudentResponse create(@AuthenticationPrincipal AuthUser tutor, @Valid @RequestBody CreateStudentRequest body,
                           HttpServletRequest request) {
        return StudentResponse.of(students.create(tutor, body.displayName(), body.loginHint(), body.password(),
                request.getRemoteAddr()));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@AuthenticationPrincipal AuthUser tutor, @PathVariable String id,
                       @Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        UUID studentId = guard.requireStudentOf(tutor, id, request.getRemoteAddr());
        students.resetPassword(tutor, studentId, body.newPassword(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@AuthenticationPrincipal AuthUser tutor, @PathVariable String id, HttpServletRequest request) {
        students.deactivate(tutor, guard.requireStudentOf(tutor, id, request.getRemoteAddr()), request.getRemoteAddr());
    }

    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@AuthenticationPrincipal AuthUser tutor, @PathVariable String id, HttpServletRequest request) {
        students.reactivate(tutor, guard.requireStudentOf(tutor, id, request.getRemoteAddr()), request.getRemoteAddr());
    }
}
