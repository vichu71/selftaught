-- F04: asistente de creación de curso. Consultas al asistente (también las bloqueadas, que verá el tutor), cursos con su ficha y su enlace a documentos (ADR-37, 40, 41).

-- La tarea nueva del asistente se registra como cualquier otra en ai_usage
ALTER TABLE ai_usage DROP CONSTRAINT ai_usage_task_check;
ALTER TABLE ai_usage ADD CONSTRAINT ai_usage_task_check
    CHECK (task IN ('OUTLINE', 'LESSON', 'ASSESSMENT', 'TUTOR', 'EMBEDDING', 'INTAKE'));

CREATE TABLE course_intake (
    id               uuid         PRIMARY KEY,
    owner_id         uuid         NOT NULL REFERENCES app_user (id),
    topic            varchar(200) NOT NULL,                               -- el tema tal como lo escribió: el tutor lo verá si se bloquea (F08)
    document_ids     jsonb        NOT NULL DEFAULT '[]',                  -- documentos elegidos; se revalidan al aceptar
    status           varchar(10)  NOT NULL CHECK (status IN ('OPEN', 'CONSUMED', 'BLOCKED')),
    questions        jsonb,                                               -- preguntas validadas, guardadas por el SERVIDOR; nulo si se bloqueó
    blocked_category varchar(20)  CHECK (blocked_category IN ('EXPLICIT', 'VIOLENCE', 'DRUGS', 'SELF_HARM', 'OTHER')),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    expires_at       timestamptz  NOT NULL
);
-- Cuenta las consultas de la última hora de un usuario (límite de frecuencia, A2)
CREATE INDEX course_intake_owner_idx ON course_intake (owner_id, created_at);

CREATE TABLE course (
    id         uuid         PRIMARY KEY,
    owner_id   uuid         NOT NULL REFERENCES app_user (id),
    intake_id  uuid         NOT NULL UNIQUE REFERENCES course_intake (id),   -- un curso por consulta: «Aceptar» es idempotente
    title      varchar(200) NOT NULL,
    status     varchar(20)  NOT NULL CHECK (status IN ('PENDING_GENERATION', 'GENERATING', 'READY', 'FAILED')),
    sheet      jsonb        NOT NULL,                                        -- la ficha (schemaVersion 1): contrato con F05
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT course_id_owner UNIQUE (id, owner_id)
);
CREATE INDEX course_owner_idx ON course (owner_id, created_at);

-- Qué documentos de «Mi material» usa cada curso. Las claves compuestas impiden usar el documento de otro usuario.
CREATE TABLE course_document (
    course_id   uuid NOT NULL,
    document_id uuid NOT NULL,
    owner_id    uuid NOT NULL,
    PRIMARY KEY (course_id, document_id),
    CONSTRAINT course_document_course_fk FOREIGN KEY (course_id, owner_id)
        REFERENCES course (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT course_document_document_fk FOREIGN KEY (document_id, owner_id)
        REFERENCES source_document (id, owner_id) ON DELETE CASCADE
);

GRANT SELECT, INSERT, UPDATE ON course_intake, course TO studylab_app;
GRANT SELECT, INSERT, DELETE ON course_document TO studylab_app;
