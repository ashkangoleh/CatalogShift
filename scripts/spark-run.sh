#!/usr/bin/env bash
set -Eeuo pipefail

job="${1:-}"
shift || true

common=(
  /opt/spark/bin/spark-submit
  --master 'local[2]'
  --conf spark.ui.enabled=false
  --conf spark.sql.session.timeZone=UTC
  --conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions
  --conf spark.sql.catalog.hms=org.apache.iceberg.spark.SparkCatalog
  --conf spark.sql.catalog.hms.type=hive
  --conf spark.sql.catalog.hms.uri=thrift://hive-metastore:9083
  --conf spark.sql.catalog.hms.warehouse=file:///tmp/hive-warehouse
  --conf spark.sql.catalog.hms.io-impl=org.apache.iceberg.aws.s3.S3FileIO
  --conf spark.sql.catalog.hms.s3.endpoint=http://minio:9000
  --conf spark.sql.catalog.hms.s3.path-style-access=true
  --conf "spark.sql.catalog.hms.s3.access-key-id=${MINIO_ROOT_USER}"
  --conf "spark.sql.catalog.hms.s3.secret-access-key=${MINIO_ROOT_PASSWORD}"
  --conf spark.sql.catalog.hms.client.region=us-west-2
  --conf spark.sql.catalog.polaris=org.apache.iceberg.spark.SparkCatalog
  --conf spark.sql.catalog.polaris.type=rest
  --conf spark.sql.catalog.polaris.uri=http://polaris:8181/api/catalog
  --conf spark.sql.catalog.polaris.warehouse=migration
  --conf "spark.sql.catalog.polaris.credential=${POLARIS_CLIENT_ID}:${POLARIS_CLIENT_SECRET}"
  --conf spark.sql.catalog.polaris.scope=PRINCIPAL_ROLE:ALL
  --conf spark.sql.catalog.polaris.token-refresh-enabled=false
  --conf spark.sql.catalog.polaris.io-impl=org.apache.iceberg.aws.s3.S3FileIO
  --conf spark.sql.catalog.polaris.s3.endpoint=http://minio:9000
  --conf spark.sql.catalog.polaris.s3.path-style-access=true
  --conf "spark.sql.catalog.polaris.s3.access-key-id=${MINIO_ROOT_USER}"
  --conf "spark.sql.catalog.polaris.s3.secret-access-key=${MINIO_ROOT_PASSWORD}"
  --conf spark.sql.catalog.polaris.client.region=us-west-2
  --conf spark.sql.catalog.lakekeeper=org.apache.iceberg.spark.SparkCatalog
  --conf spark.sql.catalog.lakekeeper.type=rest
  --conf spark.sql.catalog.lakekeeper.uri=http://lakekeeper:8181/catalog
  --conf spark.sql.catalog.lakekeeper.warehouse=migration
  --conf spark.sql.catalog.lakekeeper.io-impl=org.apache.iceberg.aws.s3.S3FileIO
  --conf spark.sql.catalog.lakekeeper.s3.endpoint=http://minio:9000
  --conf spark.sql.catalog.lakekeeper.s3.path-style-access=true
  --conf "spark.sql.catalog.lakekeeper.s3.access-key-id=${MINIO_ROOT_USER}"
  --conf "spark.sql.catalog.lakekeeper.s3.secret-access-key=${MINIO_ROOT_PASSWORD}"
  --conf spark.sql.catalog.lakekeeper.client.region=us-west-2
)

if [[ "$job" == "seed" ]]; then
  exec "${common[@]}" /workspace/scripts/seed.py "$@"
fi

if [[ "$job" == "state" ]]; then
  exec "${common[@]}" /workspace/scripts/catalog_state.py "$@"
fi

if [[ "$job" == "cleanup-demo-hms" ]]; then
  exec "${common[@]}" /workspace/scripts/cleanup_demo_hms.py
fi

if [[ "$job" == "query-hms" ]]; then
  exec "${common[@]}" /workspace/scripts/queries/hms_queries.py
fi

if [[ "$job" == "query-polaris" ]]; then
  exec "${common[@]}" /workspace/scripts/queries/polaris_queries.py
fi

if [[ "$job" == "query-lakekeeper" ]]; then
  exec "${common[@]}" /workspace/scripts/queries/lakekeeper_queries.py
fi

echo "Usage: seed | state capture|compare|assert-empty | cleanup-demo-hms | query-hms | query-polaris | query-lakekeeper" >&2
exit 64
