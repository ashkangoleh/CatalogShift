from pyspark.sql import SparkSession

spark = SparkSession.builder.appName("seed-hms").getOrCreate()
spark.sql("CREATE NAMESPACE IF NOT EXISTS hms.demo")
spark.sql("DROP TABLE IF EXISTS hms.demo.orders")
spark.sql("DROP TABLE IF EXISTS hms.demo.customers")
spark.sql("DROP TABLE IF EXISTS hms.demo.events")
spark.sql(
    """
    CREATE TABLE hms.demo.customers (
      customer_id BIGINT NOT NULL,
      email STRING,
      country STRING,
      created_at TIMESTAMP
    ) USING iceberg
    LOCATION 's3://warehouse/migration/demo/customers'
    TBLPROPERTIES ('format-version'='2')
    """
)
spark.sql(
    """
    INSERT INTO hms.demo.customers VALUES
      (1, 'ada@example.test', 'GB', TIMESTAMP '2025-01-01 09:00:00'),
      (2, 'linus@example.test', 'FI', TIMESTAMP '2025-01-02 10:30:00'),
      (3, 'grace@example.test', 'US', TIMESTAMP '2025-01-03 13:15:00')
    """
)
spark.sql("ALTER TABLE hms.demo.customers ADD COLUMN tier STRING")
spark.sql(
    """
    INSERT INTO hms.demo.customers VALUES
      (4, 'margaret@example.test', 'US', TIMESTAMP '2025-01-04 16:00:00', 'gold')
    """
)
spark.sql(
    """
    CREATE TABLE hms.demo.orders (
      order_id BIGINT NOT NULL,
      customer_id BIGINT NOT NULL,
      amount DECIMAL(12,2),
      status STRING,
      ordered_at TIMESTAMP
    ) USING iceberg
    PARTITIONED BY (days(ordered_at))
    LOCATION 's3://warehouse/migration/demo/orders'
    TBLPROPERTIES ('format-version'='2', 'write.distribution-mode'='hash')
    """
)
spark.sql("ALTER TABLE hms.demo.orders WRITE ORDERED BY customer_id, order_id")
spark.sql(
    """
    INSERT INTO hms.demo.orders VALUES
      (101, 1, 125.50, 'paid', TIMESTAMP '2025-02-01 08:00:00'),
      (102, 2, 48.25, 'paid', TIMESTAMP '2025-02-01 09:30:00'),
      (103, 1, 9.99, 'pending', TIMESTAMP '2025-02-02 14:00:00')
    """
)
spark.sql(
    """
    INSERT INTO hms.demo.orders VALUES
      (104, 3, 500.00, 'paid', TIMESTAMP '2025-02-03 11:45:00'),
      (105, 4, 87.40, 'cancelled', TIMESTAMP '2025-02-04 18:20:00')
    """
)
spark.sql(
    """
    CREATE TABLE hms.demo.events (
      event_id STRING NOT NULL,
      customer_id BIGINT,
      event_type STRING,
      occurred_at TIMESTAMP
    ) USING iceberg
    PARTITIONED BY (bucket(4, customer_id))
    LOCATION 's3://warehouse/migration/demo/events'
    TBLPROPERTIES ('format-version'='2')
    """
)
spark.sql(
    """
    INSERT INTO hms.demo.events VALUES
      ('evt-001', 1, 'view', TIMESTAMP '2025-02-01 07:55:00'),
      ('evt-002', 1, 'purchase', TIMESTAMP '2025-02-01 08:00:00'),
      ('evt-003', 2, 'view', TIMESTAMP '2025-02-01 09:25:00'),
      ('evt-004', 3, 'refund', TIMESTAMP '2025-02-05 12:00:00')
    """
)
print("Seeded three deterministic Iceberg tables")
spark.stop()
