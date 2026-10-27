-- F14: panel del propietario (ADR-87 a 91). Solo añade: dos tablas, un índice y sus permisos; no toca ai_usage ni audit_event.

-- Tope mensual de IA fijado por el propietario. Una fila solo cuando hay excepción; si no hay fila manda el valor de la configuración (ADR-88).
CREATE TABLE user_limit_override (
    user_id   uuid         PRIMARY KEY REFERENCES app_user (id),
    limit_usd numeric(8,2) NOT NULL CHECK (limit_usd >= 0 AND limit_usd <= 500),
    set_by    uuid         NOT NULL REFERENCES app_user (id),
    set_at    timestamptz  NOT NULL DEFAULT now()
);

-- Límite de frecuencia del panel, contado en la BD y por propietario (ADR-89): 120 consultas y 60 acciones por hora
CREATE TABLE owner_action (
    id         bigserial   PRIMARY KEY,
    owner_id   uuid        NOT NULL REFERENCES app_user (id),
    kind       varchar(6)  NOT NULL CHECK (kind IN ('QUERY', 'ACTION')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX owner_action_idx ON owner_action (owner_id, kind, created_at);

-- Para contar eventos por acción y día en el rango de 90 días sin recorrer toda la auditoría
CREATE INDEX audit_event_action_idx ON audit_event (action, occurred_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_limit_override TO studylab_app;
GRANT SELECT, INSERT, DELETE ON owner_action TO studylab_app;      -- DELETE: poda de las filas de más de 2 horas
GRANT USAGE, SELECT ON SEQUENCE owner_action_id_seq TO studylab_app;
