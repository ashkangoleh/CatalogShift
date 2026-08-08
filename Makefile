SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

.PHONY: help quality config state-dir up up-ui cleanup-demo down seed plan-hms-to-polaris migrate-hms-to-polaris plan-polaris-to-lakekeeper migrate-polaris-to-lakekeeper e2e query-hms query-polaris query-lakekeeper query-all

help:
	@printf '%s\n' quality config state-dir up up-ui cleanup-demo down seed plan-hms-to-polaris migrate-hms-to-polaris plan-polaris-to-lakekeeper migrate-polaris-to-lakekeeper e2e

quality:
	python3 scripts/quality.py
	python3 -m compileall -q scripts tests infra
	python3 -m unittest discover -s tests -v
	@for file in scripts/*.sh infra/migrator/*.sh; do bash -n "$$file"; done
	ruff check .
	ruff format --check .

config:
	docker compose --profile tools --profile ui config --quiet

state-dir:
	install -d -m 0777 .state

up:
	./scripts/up.sh

up-ui: up
	git submodule update --init --recursive
	docker compose --profile ui up --detach --build polaris-console

cleanup-demo:
	./scripts/cleanup-demo.sh

down:
	docker compose --profile tools --profile ui down --remove-orphans

seed:
	docker compose run --rm --no-deps spark seed

plan-hms-to-polaris:
	docker compose run --rm --no-deps migrator plan hms polaris

migrate-hms-to-polaris:
	docker compose run --rm --no-deps -e MIGRATION_CONFIRM=YES migrator execute hms polaris

plan-polaris-to-lakekeeper:
	docker compose run --rm --no-deps migrator plan polaris lakekeeper

migrate-polaris-to-lakekeeper:
	docker compose run --rm --no-deps -e MIGRATION_CONFIRM=YES migrator execute polaris lakekeeper

e2e:
	./scripts/e2e.sh

query-hms:
	docker compose run --rm --no-deps spark query-hms

query-polaris:
	docker compose run --rm --no-deps spark query-polaris

query-lakekeeper:
	docker compose run --rm --no-deps spark query-lakekeeper

query-all: query-hms query-polaris query-lakekeeper
