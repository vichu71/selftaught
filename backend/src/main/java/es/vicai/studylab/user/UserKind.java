package es.vicai.studylab.user;

import java.util.List;

public enum UserKind {
    ADULT,
    MINOR;

    /** Un adulto es alumno y tutor con la misma cuenta; un menor solo es alumno. */
    public List<String> roles() {
        return this == ADULT ? List.of("STUDENT", "TUTOR") : List.of("STUDENT");
    }
}
