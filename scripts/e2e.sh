#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
install -d -m 0777 .state

cleanup() {
  if [[ "${KEEP_STACK:-0}" != "1" ]]; then
    docker compose --profile tools down --volumes --remove-orphans
  fi
}
trap cleanup EXIT

docker compose --profile tools down --volumes --remove-orphans
./scripts/up.sh
docker compose run --rm --no-deps spark seed
docker compose run --rm --no-deps spark state capture hms /workspace/state/hms.json
docker compose run --rm --no-deps migrator plan hms polaris
docker compose run --rm --no-deps -e MIGRATION_CONFIRM=YES migrator execute hms polaris
docker compose run --rm --no-deps spark state compare polaris /workspace/state/hms.json
docker compose run --rm --no-deps spark state capture hms /workspace/state/hms-after.json
docker compose run --rm --no-deps spark state capture polaris /workspace/state/polaris.json
docker compose run --rm --no-deps migrator plan polaris lakekeeper
docker compose run --rm --no-deps -e MIGRATION_CONFIRM=YES migrator execute polaris lakekeeper
docker compose run --rm --no-deps spark state compare lakekeeper /workspace/state/polaris.json
docker compose run --rm --no-deps spark state capture polaris /workspace/state/polaris-after.json
