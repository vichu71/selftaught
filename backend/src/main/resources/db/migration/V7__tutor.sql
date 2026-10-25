-- F07: tutor IA por lección. El hilo de cada lección y usuario; los mensajes provisionales (PENDING y FAILED) sostienen los límites por usuario y la «respuesta a la vez» (ADR-55).
-- Las claves compuestas por propietario impiden mezclar conversaciones de usuarios distintos.

CREATE TABLE tutor_message (
    id         uuid          PRIMARY KEY,
    owner_id   uuid          NOT NULL,
    course_id  uuid          NOT NULL,
    piece_id   uuid          NOT NULL,                                  -- una lección
    role       varchar(5)    NOT NULL CHECK (role IN ('USER', 'TUTOR')),
    status     varchar(8)    NOT NULL CHECK (status IN ('PENDING', 'OK', 'FAILED', 'BLOCKED', 'FILTERED')),
    content    varchar(6000) NOT NULL,                                  -- pregunta (≤ 1 000), respuesta (≤ 4 000) o el texto fijo de un bloqueo
    sources    jsonb,                                                   -- solo respuestas con material: [{n, page}], sin documento ni nombre
    created_at timestamptz   NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT tutor_message_piece_fk FOREIGN KEY (piece_id, owner_id) REFERENCES course_piece (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT tutor_message_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE
);
-- El hilo de una lección y el recuento de mensajes por hora y por día de un usuario
CREATE INDEX tutor_message_thread_idx ON tutor_message (owner_id, piece_id, created_at);
CREATE INDEX tutor_message_owner_idx ON tutor_message (owner_id, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON tutor_message TO studylab_app;
