#!/usr/bin/env bash
# Purge the demo namespace, its S3 data, and tear down all containers.
# Safe to run repeatedly — each step is idempotent.
set -Eeuo pipefail

echo "=== Cleaning up demo namespace from all catalogs ==="

# 1.- Drop tables and namespace from HMS (if still registered)
echo ""
echo "[1/6] Dropping demo namespace from HMS..."
docker compose run --rm --no-deps spark cleanup-demo-hms 2>&1 | grep -E 'dropped|error|Error' || echo "  (HMS already clean)"

# 2.- Remove namespace from Polaris via REST API
echo ""
echo "[2/6] Removing demo namespace from Polaris..."
TOKEN=$(curl -s -X POST http://localhost:8181/api/catalog/v1/oauth/tokens \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&scope=PRINCIPAL_ROLE:ALL&client_id=root&client_secret=local_polaris_secret' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])' 2>/dev/null)

TABLES=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8181/api/catalog/v1/migration/namespaces/demo/tables" \
  | python3 -c 'import sys,json; [print(t["name"]) for t in json.load(sys.stdin).get("identifiers",[])]' 2>/dev/null)

for tbl in $TABLES; do
  curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8181/api/catalog/v1/migration/namespaces/demo/tables/$tbl" \
    -w "  DELETE $tbl → HTTP %{http_code}\n" -o /dev/null
done

curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8181/api/catalog/v1/migration/namespaces/demo" \
  -w "  DELETE namespace demo → HTTP %{http_code}\n" -o /dev/null

echo "  Polaris demo namespace removed"

# 3.- Remove namespace from Lakekeeper via REST API
echo ""
echo "[3/6] Removing demo namespace from Lakekeeper..."
WID=$(docker compose exec -T lakekeeper-db psql -U lakekeeper -d lakekeeper -t \
  -c "SELECT warehouse_id FROM warehouse LIMIT 1;" 2>/dev/null | tr -d ' ')

TABLES=$(curl -s "http://localhost:8183/catalog/v1/${WID}/namespaces/demo/tables" \
  | python3 -c 'import sys,json; [print(t["name"]) for t in json.load(sys.stdin).get("identifiers",[])]' 2>/dev/null)

for tbl in $TABLES; do
  curl -s -X DELETE \
    "http://localhost:8183/catalog/v1/${WID}/namespaces/demo/tables/${tbl}" \
    -w "  DELETE $tbl → HTTP %{http_code}\n" -o /dev/null
done

curl -s -X DELETE \
  "http://localhost:8183/catalog/v1/${WID}/namespaces/demo" \
  -w "  DELETE namespace demo → HTTP %{http_code}\n" -o /dev/null

echo "  Lakekeeper demo namespace removed"

# 4.- Purge S3 data from MinIO
echo ""
echo "[4/6] Purging s3://warehouse/migration/demo from MinIO..."
docker compose exec -T minio sh -c "
mc alias set local http://localhost:9000 \$MINIO_ROOT_USER \$MINIO_ROOT_PASSWORD 2>/dev/null
mc rb --force local/warehouse/migration/demo/ 2>/dev/null && echo '  Removed local/warehouse/migration/demo/' || echo '  Path already clean'
mc rb --force local/warehouse/migration/demo.db/ 2>/dev/null && echo '  Removed orphaned local/warehouse/migration/demo.db/' || echo '  No orphaned demo.db path'
" 2>&1 | grep -v 'Added'

# 5.- Remove state files
echo ""
echo "[5/6] Removing state files..."
rm -f .state/hms.json .state/polaris.json .state/dry_run_identifiers.txt
echo "  State files removed"

# 6.- Tear down all containers, networks, and volumes
echo ""
echo "[6/6] Tearing down all containers, networks, and volumes..."
docker compose --profile tools --profile ui down --volumes --remove-orphans 2>&1 | tail -5

echo ""
echo "=== Cleanup complete — all containers, volumes, and demo data removed ==="
