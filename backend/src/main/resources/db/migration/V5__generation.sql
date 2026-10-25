-- F05: generación del curso. El temario y las piezas (lección, ejercicios, examen) son filas con estado: el progreso es una consulta, reanudar es seguir por lo que
-- falta y una pieza ya hecha no se vuelve a pedir nunca (ADR-42). Las claves compuestas por propietario impiden mezclar contenido de usuarios distintos.

-- Estado nuevo (PAUSED) y motivo de la parada
ALTER TABLE course DROP CONSTRAINT course_status_check;
ALTER TABLE course ADD CONSTRAINT course_status_check
    CHECK (status IN ('PENDING_GENERATION', 'GENERATING', 'PAUSED', 'READY', 'FAILED'));
ALTER TABLE course ADD COLUMN stop_reason  varchar(20)
    CHECK (stop_reason IN ('BUDGET', 'PROVIDER', 'CONTENT', 'TOPIC_NOT_ALLOWED', 'LIMIT'));
ALTER TABLE course ADD COLUMN resumed_at   timestamptz;
ALTER TABLE course ADD COLUMN completed_at timestamptz;

CREATE TABLE course_topic (
    id         uuid         PRIMARY KEY,
    course_id  uuid         NOT NULL,
    owner_id   uuid         NOT NULL,
    position   integer      NOT NULL CHECK (position > 0),
    title      varchar(120) NOT NULL,
    summary    varchar(300) NOT NULL,
    CONSTRAINT course_topic_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_topic_id_owner UNIQUE (id, owner_id),
    CONSTRAINT course_topic_position UNIQUE (course_id, position)
);

CREATE TABLE course_chapter (
    id         uuid         PRIMARY KEY,
    topic_id   uuid         NOT NULL,
    course_id  uuid         NOT NULL,
    owner_id   uuid         NOT NULL,
    position   integer      NOT NULL CHECK (position > 0),
    title      varchar(120) NOT NULL,
    objective  varchar(200) NOT NULL,
    CONSTRAINT course_chapter_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_chapter_topic_fk FOREIGN KEY (topic_id, owner_id) REFERENCES course_topic (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_chapter_id_owner UNIQUE (id, owner_id),
    CONSTRAINT course_chapter_position UNIQUE (topic_id, position)
);

-- Lo que hay que generar: una fila por lección, por juego de ejercicios y por examen. seq es el orden de generación dentro del curso.
CREATE TABLE course_piece (
    id           uuid        PRIMARY KEY,
    course_id    uuid        NOT NULL,
    owner_id     uuid        NOT NULL,
    topic_id     uuid        NOT NULL,
    chapter_id   uuid,                                          -- nulo en el examen del tema
    kind         varchar(10) NOT NULL CHECK (kind IN ('LESSON', 'EXERCISES', 'EXAM')),
    seq          integer     NOT NULL CHECK (seq > 0),
    status       varchar(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    error_kind   varchar(30),                                   -- INVALID_OUTPUT | CONTENT_BLOCKED: nunca el texto del modelo
    attempts     smallint    NOT NULL DEFAULT 0,
    content_md   text,                                          -- la lección, en Markdown
    content_json jsonb,                                         -- ejercicios y examen
    sources      jsonb,                                         -- lecciones con material: [{n, documentId, page}], sin clave foránea
    created_at   timestamptz NOT NULL DEFAULT now(),
    generated_at timestamptz,
    CONSTRAINT course_piece_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_piece_topic_fk FOREIGN KEY (topic_id, owner_id) REFERENCES course_topic (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_piece_chapter_fk FOREIGN KEY (chapter_id, owner_id) REFERENCES course_chapter (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_piece_seq UNIQUE (course_id, seq)
);
CREATE INDEX course_piece_pending_idx ON course_piece (course_id, status);

-- El coste de un curso se suma de ai_usage por course_id (una sola fuente de verdad)
CREATE INDEX ai_usage_course_idx ON ai_usage (course_id) WHERE course_id IS NOT NULL;

GRANT SELECT, INSERT, UPDATE ON course_topic, course_chapter, course_piece TO studylab_app;
