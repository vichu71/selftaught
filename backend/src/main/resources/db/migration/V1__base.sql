-- F01: cuentas, tutela, tokens de correo, sesiones, bloqueo de login y auditoría.
-- Las extensiones y los roles los crea db/init/01-roles.sh (superusuario); aquí solo se comprueban.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE app_user (
    id            uuid         PRIMARY KEY,
    kind          varchar(10)  NOT NULL CHECK (kind IN ('ADULT', 'MINOR')),
    display_name  varchar(50)  NOT NULL CHECK (length(btrim(display_name)) > 0),
    email         citext       UNIQUE,
    login_id      citext       UNIQUE,
    password_hash varchar(255) NOT NULL,
    status        varchar(25)  NOT NULL CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DEACTIVATED')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    -- Un adulto se identifica por correo; un menor, por un identificador y nunca tiene correo
    CONSTRAINT app_user_identity CHECK (
        (kind = 'ADULT' AND email IS NOT NULL AND login_id IS NULL) OR
        (kind = 'MINOR' AND login_id IS NOT NULL AND email IS NULL))
);

CREATE TABLE guardianship (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id   uuid        NOT NULL REFERENCES app_user (id),
    -- UNIQUE: un tutor por menor en v1
    student_id uuid        NOT NULL UNIQUE REFERENCES app_user (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (tutor_id <> student_id)
);
CREATE INDEX guardianship_tutor_idx ON guardianship (tutor_id);

-- El tutor debe ser adulto y el alumno, menor (no se puede expresar con un CHECK entre tablas)
CREATE FUNCTION guardianship_check_kinds() RETURNS trigger AS $$
BEGIN
    IF (SELECT kind FROM app_user WHERE id = NEW.tutor_id) <> 'ADULT' THEN
        RAISE EXCEPTION 'El tutor debe ser un adulto';
    END IF;
    IF (SELECT kind FROM app_user WHERE id = NEW.student_id) <> 'MINOR' THEN
        RAISE EXCEPTION 'El alumno tutelado debe ser un menor';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER guardianship_kinds BEFORE INSERT OR UPDATE ON guardianship
    FOR EACH ROW EXECUTE FUNCTION guardianship_check_kinds();

CREATE TABLE email_token (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    purpose    varchar(20) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE auth_session (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    token_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz
);
CREATE INDEX auth_session_user_idx ON auth_session (user_id);

-- Intentos de login (kind LOGIN) y envíos de correo por IP (kind MAIL). El identificador va hasheado.
CREATE TABLE login_attempt (
    id              bigserial   PRIMARY KEY,
    kind            varchar(10) NOT NULL DEFAULT 'LOGIN' CHECK (kind IN ('LOGIN', 'MAIL')),
    identifier_hash varchar(64) NOT NULL,
    ip              varchar(64),
    succeeded       boolean     NOT NULL,
    at              timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX login_attempt_identifier_idx ON login_attempt (identifier_hash, at);
CREATE INDEX login_attempt_ip_idx ON login_attempt (ip, at);

-- Registro de auditoría: solo se añade (sin FK para que sobreviva a cualquier cambio de usuarios)
CREATE TABLE audit_event (
    id              bigserial   PRIMARY KEY,
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    actor_user_id   uuid,
    subject_user_id uuid,
    action          varchar(50) NOT NULL,
    outcome         varchar(10) NOT NULL CHECK (outcome IN ('OK', 'DENIED', 'FAILED')),
    ip              varchar(64),
    details         jsonb
);
CREATE INDEX audit_event_subject_idx ON audit_event (subject_user_id, occurred_at);

CREATE FUNCTION audit_event_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event es de solo añadir';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_change BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();

-- Mínimos privilegios para el rol de la aplicación (sin DDL; los usuarios nunca se borran)
GRANT SELECT, INSERT, UPDATE ON app_user, email_token, auth_session TO studylab_app;
GRANT SELECT, INSERT ON guardianship, audit_event TO studylab_app;
GRANT SELECT, INSERT, DELETE ON login_attempt TO studylab_app;
GRANT USAGE ON SEQUENCE audit_event_id_seq, login_attempt_id_seq TO studylab_app;
