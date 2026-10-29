-- F19: fotos de apuntes. Tipo IMAGE para el material, tarea OCR para la lectura de pago, ajuste por menor y contador diario de fotos (ADR-100 a 105).

ALTER TABLE source_document DROP CONSTRAINT source_document_media_type_check;
ALTER TABLE source_document ADD CONSTRAINT source_document_media_type_check
    CHECK (media_type IN ('PDF', 'DOCX', 'TEXT', 'IMAGE'));

ALTER TABLE ai_usage DROP CONSTRAINT ai_usage_task_check;
ALTER TABLE ai_usage ADD CONSTRAINT ai_usage_task_check
    CHECK (task IN ('OUTLINE', 'LESSON', 'ASSESSMENT', 'TUTOR', 'EMBEDDING', 'INTAKE', 'IMAGE', 'OCR'));

-- Lo que el tutor permite a un menor. Sin fila = desactivado. Desactivar no borra lo ya subido.
CREATE TABLE child_setting (
    child_id       uuid        PRIMARY KEY REFERENCES app_user (id),
    photo_material boolean     NOT NULL DEFAULT false,
    updated_by     uuid        NOT NULL REFERENCES app_user (id),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

-- Una fila por foto aceptada: el límite diario se cuenta al subir y sobrevive al borrado del documento (ADR-105)
CREATE TABLE material_action (
    id         bigserial   PRIMARY KEY,
    owner_id   uuid        NOT NULL REFERENCES app_user (id),
    kind       varchar(16) NOT NULL CHECK (kind IN ('IMAGE_UPLOAD')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX material_action_owner_idx ON material_action (owner_id, kind, created_at);

GRANT SELECT, INSERT, UPDATE ON child_setting TO studylab_app;
GRANT SELECT, INSERT ON material_action TO studylab_app;
GRANT USAGE ON SEQUENCE material_action_id_seq TO studylab_app;
