package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

/** Aggregates per-player statistics from completed games.
  *
  * Each game contributes one row per player (as white and as black), so the dataset is first unioned into a
  * player-centric view before grouping. Output columns: player_id, total_games, wins, losses, draws, games_as_white,
  * games_as_black, avg_move_count, win_rate
  *
  * Output is written as Parquet to `outputDir/player_stats`.
  */
object PlayerStatsJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-player-stats"

    val spark = SparkSession
      .builder()
      .appName("NowChess Player Stats")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(
      spark: SparkSession,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      outputDir: String,
  ): Unit =
    val games = GameSource
      .load(spark, jdbcUrl, dbUser, dbPass)
      .select("white_id", "black_id", "result", "move_count")
      .filter(F.col("result").isNotNull)

    // Flatten each game into two rows: one per player, tagged with their side.
    val asWhite = games.select(
      F.col("white_id").as("player_id"),
      F.col("result"),
      F.col("move_count"),
      F.lit("white").as("color"),
    )
    val asBlack = games.select(
      F.col("black_id").as("player_id"),
      F.col("result"),
      F.col("move_count"),
      F.lit("black").as("color"),
    )

    val playerGames = asWhite.union(asBlack)

    val wonGame = F.col("color") === F.col("result")
    val lostGame = (F.col("color") === "white" && F.col("result") === "black")
      .or(F.col("color") === "black" && F.col("result") === "white")

    val stats = playerGames
      .groupBy("player_id")
      .agg(
        F.count("*").as("total_games"),
        F.sum(F.when(wonGame, 1).otherwise(0)).as("wins"),
        F.sum(F.when(lostGame, 1).otherwise(0)).as("losses"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
        F.sum(F.when(F.col("color") === "white", 1).otherwise(0)).as("games_as_white"),
        F.sum(F.when(F.col("color") === "black", 1).otherwise(0)).as("games_as_black"),
        F.round(F.avg(F.col("move_count")), 1).as("avg_move_count"),
      )
      .withColumn("win_rate", F.round(F.col("wins") / F.col("total_games").cast("double"), 3))
      .orderBy(F.desc("total_games"))

    stats.write
      .mode("overwrite")
      .parquet(s"$outputDir/player_stats")

    if !GameSource.isPgnMode then
      stats.write
        .mode("overwrite")
        .format("jdbc")
        .option("url", jdbcUrl)
        .option("dbtable", "analytics_player_stats")
        .option("user", dbUser)
        .option("password", dbPass)
        .option("driver", "org.postgresql.Driver")
        .save()
