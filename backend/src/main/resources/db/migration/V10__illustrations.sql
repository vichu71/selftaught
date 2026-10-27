-- F12: ilustraciones de portada generadas por IA (una por tema). La imagen vive en su propia tabla, fuera de course_piece: no cuenta para el tope de piezas, ni para
-- el progreso, ni toca course.status (ADR-79). Es contenido del propietario y se borra con su tema o su curso.

-- La tarea de imagen se registra como cualquier otra en ai_usage y las imágenes generadas por hora cuentan en course_action
ALTER TABLE ai_usage DROP CONSTRAINT ai_usage_task_check;
ALTER TABLE ai_usage ADD CONSTRAINT ai_usage_task_check
    CHECK (task IN ('OUTLINE', 'LESSON', 'ASSESSMENT', 'TUTOR', 'EMBEDDING', 'INTAKE', 'IMAGE'));

ALTER TABLE course_action DROP CONSTRAINT course_action_kind_check;
ALTER TABLE course_action ADD CONSTRAINT course_action_kind_check
    CHECK (kind IN ('AI_EDIT', 'AI_IMAGE', 'REDEEM_OK', 'REDEEM_FAIL'));

-- La elección del usuario al crear el curso (no cambia la ficha, ADR-79). Gobierna solo la GENERACIÓN: la copia por código nace en false (ADR-82).
ALTER TABLE course ADD COLUMN illustrations boolean NOT NULL DEFAULT false;

-- Para que una imagen no pueda apuntar a un tema de otro curso aunque sea del mismo propietario
ALTER TABLE course_topic ADD CONSTRAINT course_topic_id_course UNIQUE (id, course_id);

CREATE TABLE course_image (
    id            uuid         PRIMARY KEY,
    course_id     uuid         NOT NULL,
    owner_id      uuid         NOT NULL,
    topic_id      uuid         NOT NULL,
    status        varchar(8)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DONE', 'FAILED', 'BLOCKED', 'REMOVED')),
    error_kind    varchar(30),                                   -- tipo de error o MODERATION: nunca texto del proveedor
    attempts      smallint     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    regenerations smallint     NOT NULL DEFAULT 0 CHECK (regenerations >= 0 AND regenerations <= 20),
    alt_text      varchar(200),                                  -- determinista: «Ilustración generada por IA sobre «título»»
    mime          varchar(16),
    size_bytes    integer      CHECK (size_bytes IS NULL OR (size_bytes >= 0 AND size_bytes <= 307200)),
    content       bytea,                                         -- JPEG ya validado y re-codificado; solo con DONE
    created_at    timestamptz  NOT NULL DEFAULT now(),
    generated_at  timestamptz,
    CONSTRAINT course_image_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_image_topic_fk FOREIGN KEY (topic_id, owner_id) REFERENCES course_topic (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_image_topic_course_fk FOREIGN KEY (topic_id, course_id) REFERENCES course_topic (id, course_id) ON DELETE CASCADE,
    CONSTRAINT course_image_one_per_topic UNIQUE (topic_id, owner_id)
);
CREATE INDEX course_image_course_idx ON course_image (course_id, owner_id, status);
CREATE INDEX course_image_pending_idx ON course_image (created_at) WHERE status = 'PENDING';

GRANT SELECT, INSERT, UPDATE, DELETE ON course_image TO studylab_app;
