-- F09: edición de temario y compartir por código (ADR-63 a 66).
-- El curso lleva versión y, si es una copia, su origen; una pieza puede quedar «desactualizada» cuando cambia lo que dependía de ella.
ALTER TABLE course ADD COLUMN version        integer NOT NULL DEFAULT 1 CHECK (version >= 1);
ALTER TABLE course ADD COLUMN origin_id      uuid REFERENCES course (id) ON DELETE SET NULL;   -- la copia sobrevive a su original
ALTER TABLE course ADD COLUMN origin_version integer;
ALTER TABLE course_piece ADD COLUMN outdated boolean NOT NULL DEFAULT false;

-- Códigos de compartir: el código NO se guarda, solo su SHA-256. La clave compuesta impide un código de un curso que no es de su propietario.
CREATE TABLE share_code (
    id          uuid        PRIMARY KEY,
    course_id   uuid        NOT NULL,
    owner_id    uuid        NOT NULL,
    code_hash   char(64)    NOT NULL UNIQUE CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    uses        integer     NOT NULL DEFAULT 0 CHECK (uses >= 0),
    CONSTRAINT share_code_course_fk FOREIGN KEY (course_id, owner_id) REFERENCES course (id, owner_id) ON DELETE CASCADE
);
CREATE INDEX share_code_course_idx ON share_code (course_id, owner_id);

-- Contador por usuario de ediciones con IA, canjes con éxito y canjes fallidos (límites por hora en la BD, no por IP)
CREATE TABLE course_action (
    id         bigserial   PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    kind       varchar(11) NOT NULL CHECK (kind IN ('AI_EDIT', 'REDEEM_OK', 'REDEEM_FAIL')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX course_action_user_idx ON course_action (user_id, kind, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON share_code, course_action TO studylab_app;
GRANT USAGE, SELECT ON SEQUENCE course_action_id_seq TO studylab_app;
-- Quitar un tema o un capítulo (F09)
GRANT DELETE ON course_topic, course_chapter TO studylab_app;
