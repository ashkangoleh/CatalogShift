"""Analytical queries against the HMS (Hive Metastore) catalog."""

from pyspark.sql import SparkSession, functions as F

spark = SparkSession.builder.appName("queries-hms").getOrCreate()

catalog = "hms"

print("=" * 60)
print(f"  Catalog: {catalog} (Hive Metastore)")
print("=" * 60)

# -- Inventory --------------------------------------------------
print("\n── Namespaces ──")
ns = spark.sql(f"SHOW NAMESPACES IN {catalog}").collect()
for row in ns:
    name = next(iter(row.asDict().values()))
    print(f"  {name}")
    tables = spark.sql(f"SHOW TABLES IN {catalog}.`{name}`").collect()
    for t in tables:
        print(f"    ├─ {t.tableName}")
    if not tables:
        print("    └─ (empty)")

if not ns:
    print("  (no namespaces)")

# -- Table schemas ----------------------------------------------
print("\n── Table Schemas ──")
all_tables = []
for ns_row in ns:
    ns_name = next(iter(ns_row.asDict().values()))
    tables = spark.sql(f"SHOW TABLES IN {catalog}.`{ns_name}`").collect()
    for t in tables:
        all_tables.append((ns_name, t.tableName))

if not all_tables:
    print("  (no tables to query)")
    spark.stop()
    raise SystemExit(0)

for ns_name, tbl in all_tables:
    print(f"\n  {ns_name}.{tbl}")
    for field in spark.table(f"{catalog}.`{ns_name}`.`{tbl}`").schema:
        print(f"    {field.name}: {field.dataType.simpleString()}")

# -- Row counts -------------------------------------------------
print("\n── Row Counts ──")
for ns_name, tbl in all_tables:
    count = spark.sql(f"SELECT COUNT(*) AS n FROM {catalog}.`{ns_name}`.`{tbl}`").first()["n"]
    print(f"  {ns_name}.{tbl}: {count} rows")

# -- Analytical queries -----------------------------------------
print("\n── Customers by Country ──")
df = spark.sql(f"""
    SELECT country, COUNT(*) AS customer_count
    FROM {catalog}.demo.customers
    GROUP BY country ORDER BY customer_count DESC
""")
df.show(truncate=False)

print("\n── Customer Tier Breakdown ──")
df = spark.sql(f"""
    SELECT COALESCE(tier, 'standard') AS tier_label, COUNT(*) AS cnt
    FROM {catalog}.demo.customers
    GROUP BY tier ORDER BY cnt DESC
""")
df.show(truncate=False)

print("\n── Order Value by Status ──")
df = spark.sql(f"""
    SELECT status, COUNT(*) AS order_count, ROUND(SUM(amount), 2) AS total_value
    FROM {catalog}.demo.orders
    GROUP BY status ORDER BY total_value DESC
""")
df.show(truncate=False)

print("\n── Top Customers by Total Spend ──")
df = spark.sql(f"""
    SELECT
        c.customer_id,
        c.email,
        COALESCE(c.tier, 'standard') AS tier,
        COUNT(o.order_id) AS order_count,
        ROUND(COALESCE(SUM(o.amount), 0), 2) AS total_spend
    FROM {catalog}.demo.customers c
    LEFT JOIN {catalog}.demo.orders o ON c.customer_id = o.customer_id
    GROUP BY c.customer_id, c.email, c.tier
    ORDER BY total_spend DESC
""")
df.show(truncate=False)

print("\n── Event Count by Type ──")
df = spark.sql(f"""
    SELECT event_type, COUNT(*) AS event_count
    FROM {catalog}.demo.events
    GROUP BY event_type ORDER BY event_count DESC
""")
df.show(truncate=False)

print("\n── Customer Journey (events + orders per customer) ──")
df = spark.sql(f"""
    SELECT
        c.customer_id,
        c.email,
        COALESCE(c.country, '?') AS country,
        COALESCE(SUM(o.amount), 0) AS total_spend,
        COUNT(DISTINCT o.order_id) AS order_count,
        COUNT(DISTINCT e.event_id) AS event_count
    FROM {catalog}.demo.customers c
    LEFT JOIN {catalog}.demo.orders o   ON c.customer_id = o.customer_id
    LEFT JOIN {catalog}.demo.events e   ON c.customer_id = e.customer_id
    GROUP BY c.customer_id, c.email, c.country
    ORDER BY total_spend DESC
""")
df.show(truncate=False)

# -- Snapshots --------------------------------------------------
print("\n── Snapshots ──")
for ns_name, tbl in all_tables:
    try:
        snaps = spark.sql(
            f"SELECT snapshot_id, operation, committed_at, manifest_list "
            f"FROM {catalog}.`{ns_name}`.`{tbl}`.snapshots ORDER BY committed_at"
        ).collect()
        print(f"  {ns_name}.{tbl}: {len(snaps)} snapshot(s)")
        for s in snaps:
            print(f"    snapshot_id={s.snapshot_id}  operation={s.operation}  committed_at={s.committed_at}")
    except Exception as exc:
        print(f"  {ns_name}.{tbl}: could not read snapshots — {exc}")

print("\nDone — HMS queries complete.")
spark.stop()
