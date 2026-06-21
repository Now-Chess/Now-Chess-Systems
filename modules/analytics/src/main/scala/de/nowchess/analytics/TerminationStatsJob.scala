package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

object TerminationStatsJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-termination-stats"

    val spark = SparkSession
      .builder()
      .appName("NowChess Termination Stats")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .select("result", "termination")
      .filter(F.col("termination").isNotNull.and(F.col("termination") =!= ""))

    val stats = games
      .groupBy("termination")
      .agg(
        F.count("*").as("total_games"),
        F.sum(F.when(F.col("result") === "white", 1).otherwise(0)).as("white_wins"),
        F.sum(F.when(F.col("result") === "black", 1).otherwise(0)).as("black_wins"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
      )
      .withColumn("draw_rate", F.round(F.col("draws") / F.col("total_games").cast("double"), 3))
      .withColumnRenamed("termination", "termination_type")
      .orderBy(F.desc("total_games"))
      .select("termination_type", "total_games", "white_wins", "black_wins", "draws", "draw_rate")

    stats.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/termination_stats")

    stats.write
      .mode("overwrite")
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", "analytics_termination_stats")
      .option("user", dbUser)
      .option("password", dbPass)
      .option("driver", "org.postgresql.Driver")
      .save()
