#!/bin/sh
# Se ejecuta una sola vez, al crear la BD, como superusuario. Separa los roles:
#   studylab_owner  dueño del esquema; solo lo usa Flyway para migrar
#   studylab_app    rol de la aplicación: sin DDL; los permisos por tabla los da la migración V1
# Las extensiones las instala aquí el superusuario, así el rol dueño no necesita serlo.
set -e

psql -v ON_ERROR_STOP=1 -v owner_pw="$DB_OWNER_PASSWORD" -v app_pw="$APP_DB_PASSWORD" \
  --username postgres --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE studylab_owner LOGIN PASSWORD :'owner_pw';
CREATE ROLE studylab_app LOGIN PASSWORD :'app_pw';

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;

ALTER DATABASE studylab OWNER TO studylab_owner;
ALTER SCHEMA public OWNER TO studylab_owner;
REVOKE ALL ON DATABASE studylab FROM PUBLIC;
GRANT CONNECT ON DATABASE studylab TO studylab_owner, studylab_app;
GRANT USAGE ON SCHEMA public TO studylab_app;
SQL
