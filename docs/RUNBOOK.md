# Operator Runbook

Every step has an **automated** path (Makefile target) and a **manual** path (CLI commands). The manual path is the executable source of truth; Make targets delegate to it.

---

## Prerequisites

```bash
git submodule update --init --recursive
test -f .env || cp .env.example .env
make quality
make config
```

## Configuration

### `.env` variables

| Variable | Default | Purpose |
|---|---|---|
| `MIGRATION_MODE` | `register` | `register` keeps source tables; `migrate` deletes them after target registration |
| `MIGRATION_IDENTIFIERS_REGEX` | `^demo\..*` | Regex filter for tables to migrate |
| `MIGRATION_CONFIRM` | _(unset)_ | Must be `YES` to execute (plan/dry-run don't require it) |

`MIGRATION_MODE=migrate` is rejected for HMS source catalogs — HMS does not support Iceberg-level table deletion.

Set these in `.env` or pass inline:

```bash
MIGRATION_MODE=migrate make migrate-polaris-to-lakekeeper
```

## Build

| | Command |
|---|---|
| Automated | _none_ (builds are implicit in `up` and `up-ui`) |
| Manual | `docker compose build --pull --no-cache hive-metastore spark migrator` |
| Manual | `docker compose --profile ui build polaris-console` |

## Bring Up Infrastructure

| | Command |
|---|---|
| Automated | `make up` |
| Manual | `./scripts/up.sh` |

Starts MinIO (S3), HMS (Hive Metastore + PostgreSQL), Polaris (+ PostgreSQL), and Lakekeeper (+ PostgreSQL). All catalogs share `s3://warehouse/migration`.

Wait for all services to reach `(healthy)`:

```bash
docker compose ps
```

## Bring Up UIs

UIs must be running **before** seed and migration so you can observe table registration in real time.

| | Command |
|---|---|
| Automated | `make up-ui` |
| Manual | `docker compose --profile ui up --detach --build polaris-console` |

| Console | URL | Credentials |
|---------|-----|-------------|
| MinIO Console | http://localhost:9001 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` from `.env` |
| Polaris Console | http://localhost:3030 | Realm: `POLARIS`, Client ID: `root`, Secret: `local_polaris_secret` |

Lakekeeper has no web console — use the REST API directly (see Reference).

## Seed Data (demo only)

Use `make seed` **only** for the deterministic local demonstration. Never seed a real environment.

| | Command |
|---|---|
| Automated | `make state-dir && make seed` |
| Manual | `install -d -m 0777 .state && docker compose run --rm --no-deps spark seed` |

Creates 3 tables in `hms.demo`:

| Table | Partition | Location |
|-------|-----------|----------|
| `customers` | none | `s3://warehouse/migration/demo/customers` |
| `orders` | `days(ordered_at)` | `s3://warehouse/migration/demo/orders` |
| `events` | `bucket(4, customer_id)` | `s3://warehouse/migration/demo/events` |

**After seed**, open Polaris Console to confirm the HMS catalog shows `demo` with 3 tables, and MinIO Console to verify files landed in `warehouse/migration/demo/`.

---

## HMS → Polaris

The migrator **registers** Iceberg tables in the target catalog. By default (`MIGRATION_MODE=register`), source entries are preserved — tables exist in both HMS and Polaris and you can query either catalog. Set `MIGRATION_MODE=migrate` to delete source entries after registration (not available for HMS sources).

**Warning:** Both catalogs point to the same underlying data files (S3). Writing through two catalogs simultaneously will corrupt the table. Freeze all writers before registration and only unfreeze one catalog at a time.

### 1. Plan (dry-run)

| | Command |
|---|---|
| Automated | `make plan-hms-to-polaris` |
| Manual | `docker compose run --rm --no-deps migrator plan hms polaris` |

Review `.state/dry_run_identifiers.txt` against the approved manifest.

### 2. Capture HMS Baseline

State capture **must happen before registration** for a valid comparison afterward.

```bash
docker compose run --rm --no-deps spark state capture hms /workspace/state/hms-before.json
```

Output: `Captured 3 tables from hms`

### 3. Execute Registration

| | Command |
|---|---|
| Automated | `make migrate-hms-to-polaris` |
| Manual | `docker compose run --rm --no-deps -e MIGRATION_CONFIRM=YES migrator execute hms polaris` |

Output: `Successfully registered 3 tables from HIVE catalog to REST catalog.`

### 4. Verify

```bash
# Target matches source baseline
docker compose run --rm --no-deps spark state compare polaris /workspace/state/hms-before.json
# Expected: Catalog polaris matches /workspace/state/hms-before.json

# Source still has the tables (not deleted)
docker compose run --rm --no-deps spark state capture hms /workspace/state/hms-after.json
# Expected: Captured 3 tables from hms
```

`state compare` validates schema, partition specs, sort orders, snapshot IDs, and data-file fingerprints. Source tables are preserved — both HMS and Polaris show the same `demo` namespace.

### 5. Observe in Polaris Console

Open **http://localhost:3030**, authenticate, and verify the `migration` catalog shows `demo` → `customers`, `orders`, `events`.

Verify via API:

```bash
TOKEN=$(curl -s -X POST http://localhost:8181/api/catalog/v1/oauth/tokens \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&scope=PRINCIPAL_ROLE:ALL&client_id=root&client_secret=local_polaris_secret' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8181/api/catalog/v1/migration/namespaces/demo/tables"
# → {"identifiers":[...]}
```

### 6. Cross-Catalog Verification

Run analytical queries against both catalogs to confirm identical results:

```bash
make query-hms
make query-polaris
```

Both should return the same row counts, aggregations, and snapshots.

### Stop Here for Polaris-Only

If the target is Polaris, stop at this point. Tables are now registered in both HMS and Polaris. For production cutover: freeze HMS writers, validate Polaris, then switch clients to Polaris and unfreeze.

---

## Polaris → Lakekeeper

Continue only if migrating onward from Polaris to Lakekeeper. By default (`MIGRATION_MODE=register`), source tables are preserved — Lakekeeper gets a registration, Polaris keeps its entries. For this path, `MIGRATION_MODE=migrate` is also valid: Polaris supports Iceberg-level table deletion, so source entries will be removed after successful target registration.

Freeze all writers before proceeding. Never write through multiple catalogs simultaneously.

### 1. Plan (dry-run)

| | Command |
|---|---|
| Automated | `make plan-polaris-to-lakekeeper` |
| Manual | `docker compose run --rm --no-deps migrator plan polaris lakekeeper` |

### 2. Capture Polaris Baseline

```bash
docker compose run --rm --no-deps spark state capture polaris /workspace/state/polaris-before.json
```

Output: `Captured 3 tables from polaris`

### 3. Execute Registration

| | Command |
|---|---|
| Automated | `make migrate-polaris-to-lakekeeper` |
| Manual | `docker compose run --rm --no-deps -e MIGRATION_CONFIRM=YES migrator execute polaris lakekeeper` |

Output: `Successfully registered 3 tables from REST catalog to REST catalog.`

### 4. Verify

```bash
# Target matches source baseline
docker compose run --rm --no-deps spark state compare lakekeeper /workspace/state/polaris-before.json
# Expected: Catalog lakekeeper matches /workspace/state/polaris-before.json

# Source still has the tables (not deleted)
docker compose run --rm --no-deps spark state capture polaris /workspace/state/polaris-after.json
# Expected: Captured 3 tables from polaris
```

### 5. Verify Lakekeeper via API

```bash
WID=$(docker compose exec -T lakekeeper-db psql -U lakekeeper -d lakekeeper -t \
  -c "SELECT warehouse_id FROM warehouse LIMIT 1;" | tr -d ' ')

curl -s "http://localhost:8183/catalog/v1/${WID}/namespaces"
# → {"namespaces":[["demo"]]}

curl -s "http://localhost:8183/catalog/v1/${WID}/namespaces/demo/tables"
# → {"identifiers":[{"name":"orders"},{"name":"events"},{"name":"customers"}]}
```

### 6. Cross-Catalog Verification

```bash
make query-polaris
make query-lakekeeper
```

After the full HMS → Polaris → Lakekeeper chain, all three catalogs return identical results:

```bash
make query-all
```

---

## Selector Control

The default selector migrates all tables matching `^demo\..*`. Override with an anchored regex:

```bash
MIGRATION_IDENTIFIERS_REGEX='^analytics\.(orders|customers)$' make plan-hms-to-polaris
```

Use the identical selector for plan and execution.

---

## Analytical Queries

Three self-contained Spark scripts run analytical queries against each catalog: inventory, schemas, row counts, aggregations, joins, and snapshot metadata.

| | Command |
|---|---|
| HMS | `make query-hms` |
| Polaris | `make query-polaris` |
| Lakekeeper | `make query-lakekeeper` |
| All three | `make query-all` |

Manual equivalents:

```bash
docker compose run --rm --no-deps spark query-hms
docker compose run --rm --no-deps spark query-polaris
docker compose run --rm --no-deps spark query-lakekeeper
```

Each script prints:

| Section | Content |
|---------|---------|
| Namespace→Table tree | `demo` → `customers`, `orders`, `events` |
| Table schemas | Column names and types per table |
| Row counts | `SELECT COUNT(*)` per table |
| Customers by country | `GROUP BY country` |
| Customer tier breakdown | Tier distribution (`premium` / `standard`) |
| Order value by status | Aggregated amount per order status |
| Top customers by spend | `customers LEFT JOIN orders` ranked by `total_spend` |
| Event count by type | `GROUP BY event_type` |
| Customer journey | 3-table `LEFT JOIN` — customers, orders, events |
| Snapshots | `snapshot_id`, `operation`, `committed_at` per table |

Use these to verify data consistency after each migration stage. All three catalogs should return identical results after a successful full-chain migration.

---

## Demo Cleanup

Purge the `demo` namespace, its S3 data, state files, and tear down all containers.

| | Command |
|---|---|
| Automated | `make cleanup-demo` |
| Manual | `./scripts/cleanup-demo.sh` |

What it does:

| Step | Operation |
|------|-----------|
| 1 | Drop tables and namespace from HMS |
| 2 | Delete namespace and tables from Polaris (REST API) |
| 3 | Delete namespace and tables from Lakekeeper (REST API) |
| 4 | Purge `s3://warehouse/migration/demo/` and orphaned `demo.db/` from MinIO |
| 5 | Remove state files (`.state/hms*.json`, `.state/polaris*.json`, `dry_run_identifiers.txt`) |
| 6 | Tear down all containers, networks, and volumes |

Runs idempotently — safe to re-execute on an already-clean stack.

## Shutdown

| | Command |
|---|---|
| Containers only | `make down` |
| Containers + volumes | `docker compose --profile tools --profile ui down --volumes --remove-orphans` |

---

## Failure Handling

Do not unfreeze writers after a partial failure. Do not write through two catalogs that own the same Iceberg table — both registrations point to the same S3 metadata files.

| Observation | Action |
| --- | --- |
| Dry-run fails | Correct connectivity, credentials, or location policy and rerun dry-run |
| Target registration fails | Keep the source frozen and inspect the migrator report. Source tables are untouched. |
| State comparison fails | Keep all catalogs frozen and compare schema, snapshot, and file fingerprints |
| Dual-write detected | Immediately freeze all writers, identify which catalog has the latest correct state, drop stale registrations from the other catalog |

The migrator uses `register` by default (`MIGRATION_MODE=register`), so source catalog entries are preserved. To remove a catalog's registration, use the REST API directly, run `make cleanup-demo` for a full teardown, or re-run with `MIGRATION_MODE=migrate` for Polaris→Lakekeeper.

---

## Reference

### Full E2E (one command)

```bash
make e2e
```

Builds, brings up services, seeds, migrates HMS→Polaris→Lakekeeper, verifies each stage, and tears down.

### Endpoints

| Service | Port | Purpose |
|---------|------|---------|
| HMS (Hive Metastore) | `localhost:9083` | Thrift metastore API |
| Polaris REST API | `localhost:8181` | Iceberg REST catalog |
| Polaris Management | `localhost:8182` | Health / metrics |
| Lakekeeper REST API | `localhost:8183` | Iceberg REST catalog |
| Polaris Console | `localhost:3030` | Web UI for Polaris |
| MinIO S3 | `localhost:9000` | S3-compatible storage |
| MinIO Console | `localhost:9001` | MinIO web UI |

### Check Service Status

```bash
docker compose ps
docker compose logs --tail=100 hive-metastore
docker compose logs --tail=100 polaris
docker compose logs --tail=100 lakekeeper
```

### Browse S3 Warehouse

```bash
docker compose exec minio sh -c '
  mc alias set local http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD
  mc tree local/warehouse/migration --depth 3
'
```

Or open http://localhost:9001.

### Inspect State Files

```bash
cat .state/hms-before.json | python3 -m json.tool | head -50
cat .state/polaris-before.json | python3 -m json.tool | head -50
```

---

## Troubleshooting

### Hive Metastore won't start

```bash
docker compose logs hms-db
docker compose logs hive-metastore
docker compose restart hive-metastore
```

### "unzip: not found" during migrator build

Fixed in `infra/migrator/Dockerfile`. The build stage installs `unzip` before the Gradle step.

### NPM peer dependency errors during Polaris Console build

Fixed in `infra/polaris-console/Dockerfile` — uses `npm install --legacy-peer-deps`.

### Registry 403 on Red Hat images

The original Apache Polaris Console Dockerfile uses `registry.access.redhat.com` images (require auth). Our wrapper at `infra/polaris-console/Dockerfile` replaces them with public `node:22-alpine` and `nginx:1.27-alpine`.

### Lakekeeper returns HTTP 404

Expected when hitting `GET /catalog/v1/` without a warehouse ID. Use the full path:

```
/catalog/v1/{warehouse-id}/namespaces
/catalog/v1/{warehouse-id}/namespaces/demo/tables
```

### "demo.db" appears in S3 paths

Fixed in `scripts/seed.py` — `LOCATION` paths use `demo/`, not `demo.db/`. `make cleanup-demo` purges orphaned `demo.db/` data.
