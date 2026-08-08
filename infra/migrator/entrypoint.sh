#!/usr/bin/env bash
set -Eeuo pipefail

action="${1:-}"
source_name="${2:-}"
target_name="${3:-}"
regex="${MIGRATION_IDENTIFIERS_REGEX:-^demo\..*}"

if [[ "$action" != "plan" && "$action" != "execute" ]]; then
  echo "Usage: plan|execute hms|polaris polaris|lakekeeper" >&2
  exit 64
fi

if [[ "$source_name:$target_name" != "hms:polaris" && "$source_name:$target_name" != "polaris:lakekeeper" ]]; then
  echo "Supported paths: hms polaris, polaris lakekeeper" >&2
  exit 64
fi

token_response="$(curl --fail --silent --show-error \
  -X POST http://polaris:8181/api/catalog/v1/oauth/tokens \
  -d grant_type=client_credentials \
  -d "client_id=${POLARIS_CLIENT_ID}" \
  -d "client_secret=${POLARIS_CLIENT_SECRET}" \
  -d scope=PRINCIPAL_ROLE:ALL)"
polaris_token="$(printf '%s' "$token_response" | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [[ -z "$polaris_token" ]]; then
  echo "Unable to obtain a Polaris token" >&2
  exit 69
fi

s3_properties="io-impl=org.apache.iceberg.aws.s3.S3FileIO,s3.endpoint=http://minio:9000,s3.path-style-access=true,s3.access-key-id=${AWS_ACCESS_KEY_ID},s3.secret-access-key=${AWS_SECRET_ACCESS_KEY},client.region=${AWS_REGION}"
hms_properties="uri=thrift://hive-metastore:9083,warehouse=file:///tmp/hive-warehouse,${s3_properties}"
polaris_properties="uri=http://polaris:8181/api/catalog,warehouse=migration,token=${polaris_token},${s3_properties}"
lakekeeper_properties="uri=http://lakekeeper:8181/catalog,warehouse=migration,${s3_properties}"

if [[ "$source_name" == "hms" ]]; then
  source_type=HIVE
  source_properties="$hms_properties"
else
  source_type=REST
  source_properties="$polaris_properties"
fi

if [[ "$target_name" == "polaris" ]]; then
  target_properties="$polaris_properties"
else
  target_properties="$lakekeeper_properties"
fi

if [[ "$action" == "plan" ]]; then
  migrator_action=register
  extra_arguments=(--dry-run)
else
  if [[ "${MIGRATION_CONFIRM:-}" != "YES" ]]; then
    echo "Set MIGRATION_CONFIRM=YES after freezing source writes and reviewing the plan" >&2
    echo "Tables will be registered in the target catalog; source catalog entries are preserved." >&2
    exit 65
  fi
  migration_mode="${MIGRATION_MODE:-register}"
  if [[ "$migration_mode" != "register" && "$migration_mode" != "migrate" ]]; then
    echo "MIGRATION_MODE must be 'register' or 'migrate', got '$migration_mode'" >&2
    exit 64
  fi
  if [[ "$migration_mode" == "migrate" && "$source_name" == "hms" ]]; then
    echo "MIGRATION_MODE=migrate is not supported for HMS source catalogs." >&2
    echo "HMS catalogs do not support table deletion via the Iceberg API. Use 'register' instead." >&2
    exit 64
  fi
  migrator_action="$migration_mode"
  extra_arguments=(--disable-safety-prompts)
fi

java \
  -cp /app/migrator.jar:/app/iceberg-aws-bundle.jar \
  org.apache.polaris.iceberg.catalog.migrator.cli.CatalogMigrationCLI \
  "$migrator_action" \
  --source-catalog-type "$source_type" \
  --source-catalog-properties "$source_properties" \
  --target-catalog-type REST \
  --target-catalog-properties "$target_properties" \
  --identifiers-regex "$regex" \
  "${extra_arguments[@]}"
