package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

object RatingMismatchJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-rating-mismatch"

    val spark = SparkSession
      .builder()
      .appName("NowChess Rating Mismatch")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .select("result", "white_elo", "black_elo")
      .filter(F.col("white_elo").isNotNull.and(F.col("black_elo").isNotNull))

    val eloDiff = F.col("white_elo") - F.col("black_elo")
    val bracket = F
      .when(eloDiff < -200, "Black +200")
      .when(eloDiff < -100, "Black +100–200")
      .when(eloDiff < -50, "Black +50–100")
      .when(eloDiff <= 50, "Even (±50)")
      .when(eloDiff <= 100, "White +50–100")
      .when(eloDiff <= 200, "White +100–200")
      .otherwise("White +200")
    val bracketOrder = F
      .when(eloDiff < -200, 1)
      .when(eloDiff < -100, 2)
      .when(eloDiff < -50, 3)
      .when(eloDiff <= 50, 4)
      .when(eloDiff <= 100, 5)
      .when(eloDiff <= 200, 6)
      .otherwise(7)

    val stats = games
      .withColumn("elo_diff", eloDiff)
      .withColumn("bracket", bracket)
      .withColumn("bracket_order", bracketOrder)
      .groupBy("bracket", "bracket_order")
      .agg(
        F.count("*").as("total_games"),
        F.sum(F.when(F.col("result") === "white", 1).otherwise(0)).as("white_wins"),
        F.sum(F.when(F.col("result") === "black", 1).otherwise(0)).as("black_wins"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
      )
      .withColumn("white_win_rate", F.round(F.col("white_wins") / F.col("total_games").cast("double"), 3))
      .orderBy(F.asc("bracket_order"))
      .drop("bracket_order")
      .select("bracket", "total_games", "white_wins", "black_wins", "draws", "white_win_rate")

    stats.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/rating_mismatch")
