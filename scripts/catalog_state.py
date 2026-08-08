import argparse
import json
import sys
from pathlib import Path

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from state_model import compare_states, fingerprint


def quoted(value):
    return "`" + value.replace("`", "``") + "`"


def rows_as_dicts(rows):
    return [row.asDict(recursive=True) for row in rows]


def namespaces(spark, catalog):
    rows = spark.sql(f"SHOW NAMESPACES IN {quoted(catalog)}").collect()
    return sorted(str(next(iter(row.asDict().values()))) for row in rows)


def table_names(spark, catalog):
    result = []
    for namespace in namespaces(spark, catalog):
        rows = spark.sql(f"SHOW TABLES IN {quoted(catalog)}.{quoted(namespace)}").collect()
        for row in rows:
            values = row.asDict()
            if not values.get("isTemporary", False):
                result.append(f"{namespace}.{values['tableName']}")
    return sorted(result)


def table_state(spark, catalog, identifier):
    namespace, table = identifier.split(".", 1)
    base = f"{quoted(catalog)}.{quoted(namespace)}.{quoted(table)}"
    dataframe = spark.table(base)
    columns = [F.col(quoted(column)) for column in dataframe.columns]
    row_json = dataframe.select(
        F.to_json(F.struct(*columns), options={"ignoreNullFields": "false"}).alias("row_json")
    )
    aggregates = (
        row_json.select(F.xxhash64("row_json").alias("row_hash"))
        .agg(
            F.count("*").alias("row_count"),
            F.sum(F.col("row_hash").cast("decimal(38,0)")).alias("hash_sum"),
            F.min("row_hash").alias("hash_min"),
            F.max("row_hash").alias("hash_max"),
        )
        .first()
    )
    describe_rows = rows_as_dicts(spark.sql(f"DESCRIBE TABLE EXTENDED {base}").collect())
    location = next(
        row["data_type"] for row in describe_rows if row.get("col_name", "").strip() == "Location"
    )
    create_rows = spark.sql(f"SHOW CREATE TABLE {base}").collect()
    create_statement = "\n".join(str(next(iter(row.asDict().values()))) for row in create_rows)
    create_statement = create_statement.replace(f"{quoted(catalog)}.", "")
    create_statement = create_statement.replace(f"{catalog}.", "")
    snapshot_query = (
        "SELECT snapshot_id, parent_id, operation, manifest_list "
        f"FROM {base}.snapshots ORDER BY committed_at, snapshot_id"
    )
    snapshots = rows_as_dicts(spark.sql(snapshot_query).collect())
    file_columns = spark.sql(f"SELECT * FROM {base}.files LIMIT 0").columns
    selected_file_columns = [
        column
        for column in (
            "content",
            "file_path",
            "file_format",
            "spec_id",
            "record_count",
            "file_size_in_bytes",
            "partition",
        )
        if column in file_columns
    ]
    files_query = (
        "SELECT "
        + ", ".join(quoted(column) for column in selected_file_columns)
        + f" FROM {base}.files ORDER BY file_path"
    )
    files = rows_as_dicts(spark.sql(files_query).collect())
    return {
        "schema": dataframe.schema.json(),
        "location": location,
        "create_statement_fingerprint": fingerprint(create_statement),
        "row_count": aggregates["row_count"],
        "row_hash_sum": str(aggregates["hash_sum"]),
        "row_hash_min": aggregates["hash_min"],
        "row_hash_max": aggregates["hash_max"],
        "snapshots_fingerprint": fingerprint(snapshots),
        "files_fingerprint": fingerprint(files),
    }


def capture(spark, catalog):
    return {
        "format": 1,
        "tables": {
            identifier: table_state(spark, catalog, identifier)
            for identifier in table_names(spark, catalog)
        },
    }


parser = argparse.ArgumentParser()
subparsers = parser.add_subparsers(dest="command", required=True)
capture_parser = subparsers.add_parser("capture")
capture_parser.add_argument("catalog")
capture_parser.add_argument("output")
compare_parser = subparsers.add_parser("compare")
compare_parser.add_argument("catalog")
compare_parser.add_argument("expected")
empty_parser = subparsers.add_parser("assert-empty")
empty_parser.add_argument("catalog")
args = parser.parse_args()
spark = SparkSession.builder.appName(f"catalog-state-{args.catalog}").getOrCreate()
if args.command == "capture":
    state = capture(spark, args.catalog)
    Path(args.output).write_text(json.dumps(state, indent=2, sort_keys=True) + "\n")
    print(f"Captured {len(state['tables'])} tables from {args.catalog}")
elif args.command == "compare":
    expected = json.loads(Path(args.expected).read_text())
    actual = capture(spark, args.catalog)
    failures = compare_states(expected, actual)
    if failures:
        print(json.dumps(failures, indent=2, default=str), file=sys.stderr)
        raise SystemExit(1)
    print(f"Catalog {args.catalog} matches {args.expected}")
else:
    tables = table_names(spark, args.catalog)
    if tables:
        print(json.dumps(tables), file=sys.stderr)
        raise SystemExit(1)
    print(f"Catalog {args.catalog} contains no registered tables")
spark.stop()
