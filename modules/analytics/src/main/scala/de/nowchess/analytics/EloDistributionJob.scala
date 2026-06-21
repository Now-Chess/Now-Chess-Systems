package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

object EloDistributionJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-elo-distribution"

    val spark = SparkSession
      .builder()
      .appName("NowChess Elo Distribution")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .filter(F.col("white_elo").isNotNull)

    val whiteElo = games.select(F.col("white_elo").as("elo"))
    val blackElo = games.select(F.col("black_elo").as("elo"))
    val allElo   = whiteElo.union(blackElo).filter(F.col("elo").isNotNull)

    val bucketMin = (F.floor(F.col("elo") / 200) * 200).cast("int")
    val bucketLabel = F.when(
      F.col("elo") >= 2800,
      F.lit("2800+"),
    ).otherwise(F.concat(bucketMin.cast("string"), F.lit("-"), (bucketMin + 199).cast("string")))

    val distribution = allElo
      .withColumn("elo_bucket", bucketLabel)
      .withColumn("bucket_order", F.when(F.col("elo") >= 2800, 2800).otherwise(bucketMin))
      .groupBy("elo_bucket", "bucket_order")
      .agg(F.count("*").as("player_count"))
      .orderBy(F.asc("bucket_order"))
      .select("elo_bucket", "player_count")

    distribution.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/elo_distribution")

    distribution.write
      .mode("overwrite")
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", "analytics_elo_distribution")
      .option("user", dbUser)
      .option("password", dbPass)
      .option("driver", "org.postgresql.Driver")
      .save()
