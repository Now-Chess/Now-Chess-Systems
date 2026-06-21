package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

object TimeControlJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-time-control"

    val spark = SparkSession
      .builder()
      .appName("NowChess Time Control")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .select("result", "time_control")
      .filter(
        F.col("time_control").isNotNull
          .and(F.col("time_control") =!= "")
          .and(F.col("time_control") =!= "-"),
      )

    val baseSeconds = F.regexp_extract(F.col("time_control"), "^(?:\\d+/)?(\\d+)", 1).cast("int")
    val category = F
      .when(baseSeconds < 30, "UltraBullet")
      .when(baseSeconds < 180, "Bullet")
      .when(baseSeconds < 480, "Blitz")
      .when(baseSeconds < 1500, "Rapid")
      .when(baseSeconds < 86400, "Classical")
      .otherwise("Correspondence")

    val stats = games
      .withColumn("category", category)
      .groupBy("category")
      .agg(
        F.count("*").as("total_games"),
        F.sum(F.when(F.col("result") === "white", 1).otherwise(0)).as("white_wins"),
        F.sum(F.when(F.col("result") === "black", 1).otherwise(0)).as("black_wins"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
      )
      .withColumn("white_win_rate", F.round(F.col("white_wins") / F.col("total_games").cast("double"), 3))
      .withColumn("draw_rate", F.round(F.col("draws") / F.col("total_games").cast("double"), 3))
      .orderBy(F.desc("total_games"))
      .select("category", "total_games", "white_wins", "black_wins", "draws", "white_win_rate", "draw_rate")

    stats.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/time_control_stats")
