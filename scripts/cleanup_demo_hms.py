from pyspark.sql import SparkSession

spark = SparkSession.builder.appName("cleanup-demo-hms").getOrCreate()

tables = spark.sql("SHOW TABLES IN hms.demo").collect()
for t in tables:
    spark.sql(f"DROP TABLE IF EXISTS hms.demo.{t.tableName} PURGE")
    print(f"  dropped hms.demo.{t.tableName}")

spark.sql("DROP NAMESPACE IF EXISTS hms.demo")
print("  dropped namespace hms.demo")

spark.stop()
