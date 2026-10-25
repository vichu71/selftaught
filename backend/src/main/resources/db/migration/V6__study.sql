-- F06: estudio. Lecciones hechas, intentos de ejercicio y de examen (ADR-49 a 53). El progreso no se guarda: se calcula a partir de estas tablas.
-- Las claves compuestas por propietario impiden que un usuario escriba o lea estudio de otro.

-- Para poder enlazar con clave compuesta (pieza, propietario)
ALTER TABLE course_piece ADD CONSTRAINT course_piece_id_owner UNIQUE (id, owner_id);

CREATE TABLE lesson_progress (
    owner_id     uuid        NOT NULL,
    piece_id     uuid        NOT NULL,                              -- una lección
    course_id    uuid        NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (piece_id, owner_id),
    CONSTRAINT lesson_progress_piece_fk FOREIGN KEY (piece_id, owner_id) REFERENCES course_piece (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT lesson_progress_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE
);
CREATE INDEX lesson_progress_course_idx ON lesson_progress (course_id, owner_id);

-- Un envío de UN ejercicio de una pieza de ejercicios
CREATE TABLE exercise_attempt (
    id               uuid          PRIMARY KEY,
    owner_id         uuid          NOT NULL,
    course_id        uuid          NOT NULL,
    piece_id         uuid          NOT NULL,
    item_index       smallint      NOT NULL CHECK (item_index BETWEEN 0 AND 7),
    answer           varchar(2000) NOT NULL,
    self_result      varchar(9)    CHECK (self_result IN ('CORRECT', 'PARTIAL', 'INCORRECT')),
    ai_result        varchar(9)    CHECK (ai_result IN ('CORRECT', 'PARTIAL', 'INCORRECT')),
    ai_comment       varchar(600),
    ai_requested_at  timestamptz,                                   -- cuenta las correcciones de IA del día, también las que fallan
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT exercise_attempt_piece_fk FOREIGN KEY (piece_id, owner_id) REFERENCES course_piece (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT exercise_attempt_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE
);
CREATE INDEX exercise_attempt_owner_idx ON exercise_attempt (owner_id, created_at);
CREATE INDEX exercise_attempt_piece_idx ON exercise_attempt (piece_id, owner_id, created_at);

CREATE TABLE exam_attempt (
    id           uuid         PRIMARY KEY,
    owner_id     uuid         NOT NULL,
    course_id    uuid         NOT NULL,
    piece_id     uuid         NOT NULL,                             -- un examen
    status       varchar(9)   NOT NULL CHECK (status IN ('STARTED', 'SUBMITTED')),
    answers      jsonb,                                              -- [int|null] por pregunta, solo al entregar
    correct      smallint,
    total        smallint,
    started_at   timestamptz  NOT NULL DEFAULT now(),
    submitted_at timestamptz,
    CONSTRAINT exam_attempt_score CHECK (correct IS NULL OR (total IS NOT NULL AND correct >= 0 AND correct <= total)),
    CONSTRAINT exam_attempt_piece_fk FOREIGN KEY (piece_id, owner_id) REFERENCES course_piece (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT exam_attempt_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE
);
CREATE INDEX exam_attempt_owner_idx ON exam_attempt (owner_id, submitted_at);
CREATE INDEX exam_attempt_piece_idx ON exam_attempt (piece_id, owner_id, status, submitted_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON lesson_progress, exercise_attempt, exam_attempt TO studylab_app;
