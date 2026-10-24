-- F02: registro de uso de IA, avisos de gasto y cerrojo de generación. Sin ninguna columna de contenido (ADR-22).

CREATE TABLE ai_usage (
    id               bigserial     PRIMARY KEY,
    request_id       uuid          NOT NULL,                  -- agrupa los intentos de una misma petición
    attempt          smallint      NOT NULL CHECK (attempt >= 1),
    occurred_at      timestamptz   NOT NULL DEFAULT now(),
    user_id          uuid,                                    -- nulo en el benchmark
    course_id        uuid,                                    -- aún no existe la tabla de cursos (F05)
    source           varchar(10)   NOT NULL CHECK (source IN ('APP', 'BENCHMARK')),
    task             varchar(12)   NOT NULL CHECK (task IN ('OUTLINE', 'LESSON', 'ASSESSMENT', 'TUTOR', 'EMBEDDING')),
    provider         varchar(20)   NOT NULL,
    model            varchar(80)   NOT NULL,
    input_tokens     integer       NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    output_tokens    integer       NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    tokens_estimated boolean       NOT NULL DEFAULT false,
    cost_usd         numeric(12,6) NOT NULL DEFAULT 0 CHECK (cost_usd >= 0),
    status           varchar(10)   NOT NULL CHECK (status IN ('OK', 'ERROR', 'BLOCKED')),
    error_kind       varchar(30),
    finish_reason    varchar(10),
    duration_ms      integer       NOT NULL DEFAULT 0 CHECK (duration_ms >= 0)
);
CREATE INDEX ai_usage_occurred_idx ON ai_usage (occurred_at);
CREATE INDEX ai_usage_user_idx ON ai_usage (user_id, occurred_at);

-- Es contabilidad: igual que la auditoría, solo se añade (ni siquiera el propietario puede modificarla)
CREATE FUNCTION ai_usage_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ai_usage es de solo añadir';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ai_usage_no_change BEFORE UPDATE OR DELETE ON ai_usage
    FOR EACH ROW EXECUTE FUNCTION ai_usage_append_only();

-- Un aviso de gasto por mes (hora de Madrid, formato aaaa-mm) y umbral
CREATE TABLE ai_budget_alert (
    month     varchar(7)  NOT NULL,
    threshold smallint    NOT NULL CHECK (threshold BETWEEN 1 AND 100),
    raised_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (month, threshold)
);

-- Una sola generación concurrente por usuario; caduca sola si el backend se cae
CREATE TABLE ai_generation_lock (
    user_id     uuid        PRIMARY KEY REFERENCES app_user (id),
    owner       uuid        NOT NULL,
    acquired_at timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL
);

GRANT SELECT, INSERT ON ai_usage, ai_budget_alert TO studylab_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ai_generation_lock TO studylab_app;
GRANT USAGE ON SEQUENCE ai_usage_id_seq TO studylab_app;
