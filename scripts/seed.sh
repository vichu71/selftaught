#!/usr/bin/env sh
# Carga datos de ejemplo en el entorno de DESARROLLO: un adulto verificado y dos menores.
# Usa la propia API (registro + el correo de verificación que captura Mailpit), así que no hay hashes ni
# contraseñas en el repositorio. La contraseña la eliges tú:
#   SEED_PASSWORD='una-clave-de-10-o-mas' make seed
# Es idempotente: si el adulto ya existe, entra con esa misma contraseña y completa lo que falte.
set -eu

if [ -z "${SEED_PASSWORD:-}" ] || [ "${#SEED_PASSWORD}" -lt 10 ]; then
  echo "Define SEED_PASSWORD con al menos 10 caracteres (por ejemplo: SEED_PASSWORD='mi-clave-demo-1' make seed)" >&2
  exit 1
fi

cd "$(dirname "$0")/.."
COMPOSE="docker compose ${ENV_FILE:+--env-file $ENV_FILE} -f compose.yaml -f compose.dev.yaml --profile dev"

# Todo se ejecuta dentro del contenedor del backend, que ya ve al frontend y a Mailpit por la red de Compose
$COMPOSE exec -T \
  -e SEED_PASSWORD="$SEED_PASSWORD" -e SEED_EMAIL="${SEED_EMAIL:-demo@example.com}" \
  backend sh -s <<'INNER'
set -eu
API=http://127.0.0.1:8080
MAILPIT=http://mailpit:8025
JSON='Content-Type: application/json'

post() { # post <ruta> <json> [cabeceras extra...] -> cuerpo; falla si la API responde con error
  path=$1; body=$2; shift 2
  set -- "$@"
  wget -q -O- --header="$JSON" "$@" --post-data="$body" "$API$path"
}

login_headers() {
  wget -S -q -O /dev/null --header="$JSON" \
    --post-data="{\"identifier\":\"$SEED_EMAIL\",\"password\":\"$SEED_PASSWORD\"}" "$API/api/auth/login" 2>&1 || true
}

headers=$(login_headers)
if ! echo "$headers" | grep -q "HTTP/1.1 200"; then
  echo "Registrando a $SEED_EMAIL ..."
  post /api/auth/register "{\"email\":\"$SEED_EMAIL\",\"password\":\"$SEED_PASSWORD\",\"displayName\":\"Demo\"}" >/dev/null
  sleep 2
  id=$(wget -q -O- "$MAILPIT/api/v1/search?query=to:$SEED_EMAIL" | sed -n 's/.*"ID":"\([^"]*\)".*/\1/p' | head -n1)
  token=$(wget -q -O- "$MAILPIT/api/v1/message/$id" | sed -n 's/.*token=\([A-Za-z0-9_-]*\).*/\1/p' | head -n1)
  [ -n "$token" ] || { echo "No se encontró el enlace de verificación (¿existe ya la cuenta con otra contraseña?)" >&2; exit 1; }
  post /api/auth/verify-email "{\"token\":\"$token\"}" >/dev/null
  headers=$(login_headers)
  echo "$headers" | grep -q "HTTP/1.1 200" || { echo "No se pudo iniciar sesión tras verificar" >&2; exit 1; }
fi

session=$(echo "$headers" | sed -n 's/.*Set-Cookie: \(STUDYLAB_SESSION=[^;]*\).*/\1/p' | head -n1)
csrf=$(echo "$headers" | sed -n 's/.*Set-Cookie: XSRF-TOKEN=\([^;]*\).*/\1/p' | head -n1)

for pair in "Hugo:hugo" "Lola:lola"; do
  name=${pair%%:*}; hint=${pair##*:}
  if post /api/tutor/students "{\"displayName\":\"$name\",\"loginHint\":\"$hint\",\"password\":\"$SEED_PASSWORD\"}" \
       --header="Cookie: $session; XSRF-TOKEN=$csrf" --header="X-XSRF-TOKEN: $csrf" >/tmp/student.json 2>/dev/null; then
    echo "Menor creado: $(sed -n 's/.*"loginId":"\([^"]*\)".*/\1/p' /tmp/student.json) ($name)"
  else
    echo "Menor $name: ya existía, se deja como está"
  fi
done

echo
echo "Listo. Adulto: $SEED_EMAIL  |  menores: usuario generado + la misma contraseña"
INNER
