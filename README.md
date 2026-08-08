# Iceberg Catalog Migration Lab

A reproducible migration lab for moving Apache Iceberg table registrations without rewriting table data.

Supported paths:

- Hive Metastore to Apache Polaris
- Apache Polaris to Lakekeeper

The project runs each migration as a guarded sequence: freeze source writes, capture source state, dry-run, register tables in the target catalog, compare target state, and verify source registrations are intact. By default, source catalog entries are preserved so both catalogs remain queryable after migration.

## Why this repository exists

Catalog migration is a metadata operation with a high blast radius. A successful command is not enough evidence. This repository verifies schemas, locations, row fingerprints, snapshot lineage, and data-file manifests before declaring a migration successful.

The executable path uses the official Apache Polaris Iceberg Catalog Migrator instead of maintaining custom registration logic.

## Pinned stack

| Component | Version |
| --- | --- |
| Apache Polaris | 1.7.0 |
| Polaris Console | apache/polaris-tools commit e5fea020aedc |
| Lakekeeper | 0.13.1 |
| Apache Hive | 3.1.3 |
| Apache Spark | 3.5.9 |
| Apache Iceberg | 1.11.0 |
| Catalog Migrator | 1.0.0 release candidate tag |
| PostgreSQL | 17.6 Alpine |
| MinIO | RELEASE.2025-09-07T16-13-09Z |

## Requirements

- Docker Engine 27 or newer
- Docker Compose 2.30 or newer
- Git with submodule support
- GNU Make
- At least 8 GB of available memory and 15 GB of free disk space

## Clone

For a fresh clone:

```bash
git clone --recurse-submodules https://github.com/ashkangoleh/CatalogShift.git
cd CatalogShift
```

For an existing clone:

```bash
git pull
git submodule update --init --recursive
```

## Quick start

```bash
cp .env.example .env
make up
make seed
make plan-hms-to-polaris
make migrate-hms-to-polaris
make query-hms
make query-polaris
```

After HMS → Polaris, both catalogs return identical results:

```bash
make plan-polaris-to-lakekeeper
make migrate-polaris-to-lakekeeper
make query-all
```

For a destructive clean-room verification of both paths:

```bash
make e2e
```

The end-to-end job deletes this Compose project's local volumes when it exits. It does not touch external buckets or databases.

## Local consoles

Start the core stack and Polaris Console:

```bash
cp .env.example .env
make up-ui
```

`make up-ui` initializes the pinned `apache/polaris-tools` submodule, starts the core stack, builds the official Console source, and exposes it on port `3030`.

| Console | URL | Username | Password |
| --- | --- | --- | --- |
| MinIO | http://localhost:9001 | `MINIO_ROOT_USER` | `MINIO_ROOT_PASSWORD` |
| Polaris | http://localhost:3030 | `POLARIS_CLIENT_ID` | `POLARIS_CLIENT_SECRET` |

The default local values are defined in `.env.example`. Polaris Console uses realm `POLARIS` and scope `PRINCIPAL_ROLE:ALL`.

After login, the Polaris Console should show the `migration` catalog. After `make migrate-hms-to-polaris`, inspect its namespaces and tables in the Console. MinIO should show the `warehouse` bucket and the Iceberg metadata and data files under its prefixes.

Validate service state and inspect logs with:

```bash
docker compose ps
docker compose logs --follow polaris polaris-console minio
curl --fail http://localhost:8182/q/health/ready
curl --fail http://localhost:9000/minio/health/live
```

Stop all core and UI containers without deleting volumes:

```bash
make down
```

Delete the local volumes only when a clean reset is required:

```bash
docker compose --profile tools --profile ui down --volumes --remove-orphans
```

## Migration contract

`plan` uses registration dry-run and does not mutate either catalog. `execute` registers the current Iceberg metadata location in the target catalog. Source catalog entries are preserved by default (`MIGRATION_MODE=register` in `.env`). Set `MIGRATION_MODE=migrate` for Polaris → Lakekeeper to delete source entries after successful target registration. HMS sources only support `register`.

Every execution requires `MIGRATION_CONFIRM=YES`. The Make targets set it for local use, so operators must freeze writers and review the plan before invoking a migration target.

The default table selector is `^demo\..*`. Override it through `MIGRATION_IDENTIFIERS_REGEX` in the environment.

## Validation evidence

The test harness records these invariants for every table:

- Complete Spark schema JSON
- Table storage location
- Normalized create statement fingerprint
- Row count and deterministic row-hash aggregates
- Iceberg snapshot lineage fingerprint
- Iceberg data-file manifest fingerprint
- Exact namespace and table inventory

A target mismatch fails the workflow. Source registrations are verified intact after migration (not deleted).

## Local endpoints

| Service | URL |
| --- | --- |
| MinIO API | http://localhost:9000 |
| MinIO Console | http://localhost:9001 |
| Polaris API | http://localhost:8181 |
| Polaris Health | http://localhost:8182 |
| Polaris Console | http://localhost:3030 |
| Lakekeeper API | http://localhost:8183 |

The credentials in `.env.example` are intentionally local-only values. Replace them before any shared deployment.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Operator runbook](docs/RUNBOOK.md)
- [Production cutover](docs/PRODUCTION_CUTOVER.md)
- [Security policy](SECURITY.md)

## Make targets

| Target | Purpose |
|---|---|
| `make up` | Start core stack (MinIO, HMS, Polaris, Lakekeeper) |
| `make up-ui` | Start core stack + Polaris Console |
| `make seed` | Seed demo data into HMS |
| `make plan-hms-to-polaris` | Dry-run HMS → Polaris |
| `make migrate-hms-to-polaris` | Execute HMS → Polaris registration |
| `make plan-polaris-to-lakekeeper` | Dry-run Polaris → Lakekeeper |
| `make migrate-polaris-to-lakekeeper` | Execute Polaris → Lakekeeper registration |
| `make query-hms` | Run analytical queries against HMS |
| `make query-polaris` | Run analytical queries against Polaris |
| `make query-lakekeeper` | Run analytical queries against Lakekeeper |
| `make query-all` | Run queries against all three catalogs |
| `make cleanup-demo` | Purge demo data + tear down containers |
| `make e2e` | Clean-room HMS → Polaris → Lakekeeper E2E |
| `make down` | Stop containers, keep volumes |

## Project boundaries

This repository validates catalog migration. It does not operate Kafka, stream-processing jobs, BI services, or production Kubernetes manifests. Those concerns belong in independently deployable projects with separate ownership and release cycles.

MinIO is included only as an S3-compatible local test service. Production cutovers should use durable object storage, managed secrets, TLS, independent PostgreSQL backups, and catalog-specific authentication.

Polaris Console is an optional local administration UI. It is not an observability platform and is not included in the migration end-to-end test path.

## Upstream references

- [Apache Polaris releases](https://polaris.apache.org/downloads/)
- [Apache Polaris JDBC persistence](https://polaris.apache.org/guides/jdbc/)
- [Apache Polaris MinIO guide](https://polaris.apache.org/guides/minio/)
- [Apache Polaris Tools](https://github.com/apache/polaris-tools)
- [Apache Polaris Console](https://github.com/apache/polaris-tools/tree/main/console)
- [Apache Polaris Iceberg Catalog Migrator](https://github.com/apache/polaris-tools/tree/main/iceberg-catalog-migrator)
- [Apache Iceberg releases](https://iceberg.apache.org/releases/)
- [Lakekeeper releases](https://github.com/lakekeeper/lakekeeper/releases)
- [Lakekeeper storage configuration](https://docs.lakekeeper.io/docs/latest/storage/)
