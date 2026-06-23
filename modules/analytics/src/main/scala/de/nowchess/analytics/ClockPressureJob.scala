package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions as F

/** Time-management & clock-pressure analysis mined from Lichess `[%clk ...]` move annotations.
  *
  * Lichess records each player's remaining clock after every move (`{ [%clk 0:02:31] }`). This job reconstructs
  * per-move thinking time and remaining-time from those stamps to answer questions the existing time-control summary
  * cannot: how long do players actually think, how often do they fall into time scrambles (<10 s left), how often do
  * they flag (lose on time), and does burning the clock correlate with winning?
  *
  * Pipeline (Spark SQL string/array funcs + window funcs — no UDFs):
  *   1. `regexp_extract_all` pulls every `h:mm:ss` clock in ply order, converted to seconds.
  *   2. `posexplode` → one row per ply; even plies are White's clock, odd plies Black's.
  *   3. A per-(game,side) window `lag` gives the same side's previous clock; the difference is that move's thinking time.
  *      Remaining clock <10 s marks a time-scramble move.
  *   4. Roll up to (game, side): avg move time, scramble fraction, min clock, Elo, win flag, and whether the side lost on
  *      time (`Termination "Time forfeit"`).
  *
  * Outputs (Parquet + CSV + JDBC):
  *   - `clock_by_rating` — avg move time, scramble fraction, flag-loss rate and win-rate per Elo band.
  *   - `scramble_outcome` — win-rate bucketed by how much of the game was played in time-scramble. Quantifies the cost of
  *     time trouble.
  *
  * Requires a clock-annotated Lichess dump (`NOWCHESS_PGN_PATH`).
  */
object ClockPressureJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-clock-pressure"

    val spark = SparkSession
      .builder()
      .appName("NowChess Clock Pressure")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .select("pgn", "result", "white_elo", "black_elo", "termination")
      .filter(F.col("result").isNotNull.and(F.col("pgn").contains("[%clk")))
      .withColumn("game_id", F.monotonically_increasing_id())

    val clkStrs = F.expr("""regexp_extract_all(pgn, '\\[%clk ([^\\]]+)\\]', 1)""")
    // "h:mm:ss" → seconds.
    val clkSecs = F.expr(
      "transform(clk_strs, x -> " +
        "cast(split(x, ':')[0] as double) * 3600 + " +
        "cast(split(x, ':')[1] as double) * 60 + " +
        "cast(split(x, ':')[2] as double))",
    )

    val withClk = games
      .withColumn("clk_strs", clkStrs)
      .withColumn("clk_sec", clkSecs)
      .filter(F.size(F.col("clk_sec")) >= 4)

    val plies = withClk.select(
      F.col("game_id"),
      F.col("result"),
      F.col("white_elo"),
      F.col("black_elo"),
      F.col("termination"),
      F.posexplode(F.col("clk_sec")).as(Seq("ply", "clk_after")),
    )

    val mover    = F.when(F.col("ply") % 2 === 0, "white").otherwise("black")
    val bySide   = Window.partitionBy("game_id", "mover").orderBy("ply")
    val moveTime = F.lag("clk_after", 1).over(bySide) - F.col("clk_after")

    val moves = plies
      .withColumn("mover", mover)
      .withColumn("move_time", moveTime)

    val perSide = moves
      .groupBy("game_id", "mover", "result", "white_elo", "black_elo", "termination")
      .agg(
        F.round(F.avg("move_time"), 1).as("avg_move_time"),
        F.count("*").as("moves"),
        F.round(F.min("clk_after"), 1).as("min_clk"),
        F.sum(F.when(F.col("clk_after") < 10, 1).otherwise(0)).as("scramble_moves"),
      )
      .withColumn("scramble_fraction", F.round(F.col("scramble_moves") / F.col("moves"), 3))
      .withColumn(
        "self_elo",
        F.when(F.col("mover") === "white", F.col("white_elo")).otherwise(F.col("black_elo")),
      )
      .withColumn("won", F.when(F.col("mover") === F.col("result"), 1).otherwise(0))
      .withColumn(
        "flag_loss",
        F.when(
          F.coalesce(F.col("termination"), F.lit("")).contains("Time forfeit") && F.col("won") === 0,
          1,
        ).otherwise(0),
      )

    writeClockByRating(perSide, jdbcUrl, dbUser, dbPass, outputDir)
    writeScrambleOutcome(perSide, jdbcUrl, dbUser, dbPass, outputDir)

  private def writeClockByRating(
      perSide: org.apache.spark.sql.DataFrame,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      outputDir: String,
  ): Unit =
    val elo = F.col("self_elo")
    val band = F
      .when(elo < 1200, "<1200")
      .when(elo < 1500, "1200–1499")
      .when(elo < 1800, "1500–1799")
      .when(elo < 2100, "1800–2099")
      .otherwise("2100+")
    val bandOrder = F
      .when(elo < 1200, 1)
      .when(elo < 1500, 2)
      .when(elo < 1800, 3)
      .when(elo < 2100, 4)
      .otherwise(5)

    val stats = perSide
      .filter(elo.isNotNull)
      .withColumn("band", band)
      .withColumn("band_order", bandOrder)
      .groupBy("band", "band_order")
      .agg(
        F.count("*").as("player_games"),
        F.round(F.avg("avg_move_time"), 1).as("avg_move_time_s"),
        F.round(F.avg("scramble_fraction"), 3).as("avg_scramble_fraction"),
        F.round(F.avg("flag_loss"), 3).as("flag_loss_rate"),
        F.round(F.avg("won"), 3).as("win_rate"),
      )
      .orderBy(F.asc("band_order"))
      .drop("band_order")

    write(stats, outputDir, "clock_by_rating", jdbcUrl, dbUser, dbPass, "analytics_clock_by_rating")

  private def writeScrambleOutcome(
      perSide: org.apache.spark.sql.DataFrame,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      outputDir: String,
  ): Unit =
    val sf = F.col("scramble_fraction")
    val bucket = F
      .when(sf === 0, "none")
      .when(sf < 0.05, "<5%")
      .when(sf < 0.20, "5–20%")
      .otherwise(">20%")
    val order = F
      .when(sf === 0, 0)
      .when(sf < 0.05, 1)
      .when(sf < 0.20, 2)
      .otherwise(3)

    val stats = perSide
      .withColumn("scramble_bucket", bucket)
      .withColumn("bucket_order", order)
      .groupBy("scramble_bucket", "bucket_order")
      .agg(
        F.count("*").as("player_games"),
        F.round(F.avg("won"), 3).as("win_rate"),
        F.round(F.avg("flag_loss"), 3).as("flag_loss_rate"),
      )
      .orderBy(F.asc("bucket_order"))
      .drop("bucket_order")

    write(stats, outputDir, "scramble_outcome", jdbcUrl, dbUser, dbPass, "analytics_scramble_outcome")

  private def write(
      df: org.apache.spark.sql.DataFrame,
      outputDir: String,
      name: String,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      table: String,
  ): Unit =
    df.write.mode("overwrite").parquet(s"$outputDir/$name")
    df.write.mode("overwrite").option("header", "true").csv(s"$outputDir/${name}_csv")
    if !GameSource.isPgnMode then
      df.write
        .mode("overwrite")
        .format("jdbc")
        .option("url", jdbcUrl)
        .option("dbtable", table)
        .option("user", dbUser)
        .option("password", dbPass)
        .option("driver", "org.postgresql.Driver")
        .save()
