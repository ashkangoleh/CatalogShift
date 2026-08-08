#!/usr/bin/env bash
set -Eeuo pipefail

services=(
  minio
  minio-init
  hms-db
  hive-metastore
  polaris-db
  polaris-bootstrap
  polaris
  polaris-setup
  lakekeeper-db
  lakekeeper-migrate
  lakekeeper
  lakekeeper-setup
)

docker compose up --detach --build "${services[@]}"
docker compose wait polaris-setup lakekeeper-setup

for service in minio hms-db hive-metastore polaris-db polaris lakekeeper-db lakekeeper; do
  container="$(docker compose ps --all --quiet "$service")"
  for attempt in $(seq 1 90); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      break
    fi
    if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
      echo "Service $service entered state $status" >&2
      exit 1
    fi
    if [[ "$attempt" == "90" ]]; then
      echo "Timed out waiting for service $service" >&2
      exit 1
    fi
    sleep 2
  done
done
