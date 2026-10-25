-- F03: material propio del usuario (biblioteca privada) y sus fragmentos con embedding para la búsqueda (ADR-30 a 35).
-- La extensión pgvector la crea db/init/01-roles.sh y la comprueba V1.

CREATE TABLE source_document (
    id                    uuid         PRIMARY KEY,
    owner_id              uuid         NOT NULL REFERENCES app_user (id),
    display_name          varchar(120) NOT NULL CHECK (length(btrim(display_name)) > 0),   -- nombre saneado, solo para mostrar
    media_type            varchar(5)   NOT NULL CHECK (media_type IN ('PDF', 'DOCX', 'TEXT')),
    size_bytes            integer      NOT NULL CHECK (size_bytes > 0),
    content_sha256        char(64)     NOT NULL,                                           -- detecta duplicados del mismo usuario
    status                varchar(12)  NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'ERROR')),
    error_kind            varchar(30),                                                     -- motivo genérico, nunca el mensaje de una librería
    attempts              smallint     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    page_count            integer,
    chunk_count           integer      NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
    content               bytea,                                                           -- el original, SOLO hasta procesarlo (ADR-32)
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    processing_started_at timestamptz,
    CONSTRAINT source_document_owner_hash UNIQUE (owner_id, content_sha256),
    -- Necesaria para la clave foránea compuesta de source_chunk: un fragmento nunca puede tener otro propietario que su documento
    CONSTRAINT source_document_id_owner UNIQUE (id, owner_id)
);
-- La cola de procesado solo mira los pendientes
CREATE INDEX source_document_queue_idx ON source_document (created_at, id) WHERE status = 'UPLOADED';
CREATE INDEX source_document_owner_idx ON source_document (owner_id, created_at);

CREATE TABLE source_chunk (
    id              bigserial   PRIMARY KEY,
    document_id     uuid        NOT NULL,
    owner_id        uuid        NOT NULL,
    ordinal         integer     NOT NULL CHECK (ordinal >= 0),
    page            integer     CHECK (page >= 1),                                          -- solo PDF
    start_offset    integer     NOT NULL CHECK (start_offset >= 0),                        -- posición en el texto extraído, para citar
    end_offset      integer     NOT NULL CHECK (end_offset >= start_offset),
    text            text        NOT NULL,
    embedding       vector      NOT NULL,                                                   -- sin dimensión fija (ADR-33)
    embedding_model varchar(80) NOT NULL,                                                   -- con qué modelo se generó
    CONSTRAINT source_chunk_document_fk FOREIGN KEY (document_id, owner_id)
        REFERENCES source_document (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT source_chunk_ordinal UNIQUE (document_id, ordinal)
);
CREATE INDEX source_chunk_owner_idx ON source_chunk (owner_id);

-- La aplicación lee, escribe y borra (el borrado de material es real), pero nunca cambia el esquema
GRANT SELECT, INSERT, UPDATE, DELETE ON source_document TO studylab_app;
GRANT SELECT, INSERT, DELETE ON source_chunk TO studylab_app;
GRANT USAGE ON SEQUENCE source_chunk_id_seq TO studylab_app;
