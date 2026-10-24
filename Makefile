# Envoltorio fino de docker compose. Sin make, ejecuta el comando de cada objetivo a mano.
# ENV_FILE=otro.env usa otro fichero de variables (por defecto, el .env de la raíz).
COMPOSE = docker compose $(if $(ENV_FILE),--env-file $(ENV_FILE),)
DEV     = $(COMPOSE) -f compose.yaml -f compose.dev.yaml --profile dev
TEST    = $(COMPOSE) -f compose.yaml --profile test

.PHONY: up down logs health test-backend test-frontend test-env seed reset-dev-db

up:
	$(DEV) up -d --build --wait

down:
	$(DEV) down

logs:
	$(DEV) logs -f --tail=100

health:
	@$(DEV) ps --format '{{.Service}}: {{.State}} {{.Health}}'
	@test -z "$$($(DEV) ps --format '{{.Health}}' | grep -v '^healthy$$')" && echo "Entorno sano" || { echo "Hay servicios que no estan sanos"; exit 1; }

test-backend:
	$(TEST) run --rm --build backend-test; code=$$?; $(TEST) rm -sf db-test >/dev/null 2>&1; exit $$code

test-frontend:
	$(TEST) run --rm --build frontend-test

test-env:
	sh scripts/tests/run-env-tests.sh

seed:
	sh scripts/seed.sh

# Borra SOLO los datos de la BD de desarrollo (no los modelos de Ollama). Pide confirmación.
reset-dev-db:
	@printf "Esto borra la BD de desarrollo. Escribe 'si' para continuar: " && read answer && [ "$$answer" = "si" ]
	$(DEV) rm -sf backend db
	docker volume rm studylab_db-data
	$(MAKE) up
