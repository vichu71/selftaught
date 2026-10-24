#!/usr/bin/env sh
# Ejecuta los tests de política del entorno (AC-12 a AC-15).
# Solo hace falta Docker: Compose resuelve la configuración y Node corre los tests en un contenedor.
set -eu

cd "$(dirname "$0")/../.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

# --no-interpolate conserva ${VAR:?...}: así se comprueba que los secretos son obligatorios
docker compose -f compose.yaml --profile prod config --no-interpolate --format json > "$out/base.json"
docker compose -f compose.yaml -f compose.dev.yaml --profile dev config --no-interpolate --format json > "$out/dev.json"
docker compose -f compose.yaml --profile test config --no-interpolate --format json > "$out/test.json"

docker run --rm \
  -v "$PWD:/repo:ro" -v "$out:/out:ro" \
  -e REPO_DIR=/repo -e COMPOSE_OUT=/out \
  node:22-alpine node --test /repo/scripts/tests/environment.test.mjs
