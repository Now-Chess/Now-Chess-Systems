package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

object GameLengthJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-game-length"

    val spark = SparkSession
      .builder()
      .appName("NowChess Game Length")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .load(spark, jdbcUrl, dbUser, dbPass)
      .select("result", "move_count")
      .filter(F.col("result").isNotNull.and(F.col("move_count").isNotNull))

    val moves = F.col("move_count")
    val bucket = F
      .when(moves <= 10, "1-10")
      .when(moves <= 20, "11-20")
      .when(moves <= 30, "21-30")
      .when(moves <= 40, "31-40")
      .when(moves <= 60, "41-60")
      .when(moves <= 100, "61-100")
      .otherwise("101+")
    val bucketOrder = F
      .when(moves <= 10, 1)
      .when(moves <= 20, 2)
      .when(moves <= 30, 3)
      .when(moves <= 40, 4)
      .when(moves <= 60, 5)
      .when(moves <= 100, 6)
      .otherwise(7)

    val tagged = games
      .withColumn("move_bucket", bucket)
      .withColumn("bucket_order", bucketOrder)

    val distribution = tagged
      .groupBy("move_bucket", "bucket_order")
      .agg(
        F.count("*").as("total_games"),
        F.sum(F.when(F.col("result") === "white", 1).otherwise(0)).as("white_wins"),
        F.sum(F.when(F.col("result") === "black", 1).otherwise(0)).as("black_wins"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
      )
      .withColumn("white_win_rate", F.round(F.col("white_wins") / F.col("total_games").cast("double"), 3))
      .withColumn("black_win_rate", F.round(F.col("black_wins") / F.col("total_games").cast("double"), 3))
      .withColumn("draw_rate", F.round(F.col("draws") / F.col("total_games").cast("double"), 3))
      .orderBy(F.asc("bucket_order"))
      .drop("bucket_order")
      .select(
        "move_bucket",
        "total_games",
        "white_wins",
        "black_wins",
        "draws",
        "white_win_rate",
        "black_win_rate",
        "draw_rate",
      )

    distribution.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/game_length_distribution")

    val byResult = games
      .groupBy("result")
      .agg(
        F.round(F.avg("move_count"), 1).as("avg_move_count"),
        F.min("move_count").as("min_moves"),
        F.max("move_count").as("max_moves"),
      )
      .orderBy(F.asc("result"))

    byResult.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/game_length_by_result")
