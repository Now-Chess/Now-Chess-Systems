package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions as F

/** Per-move accuracy & blunder analysis mined from Lichess `[%eval ...]` move annotations.
  *
  * Unlike the flat single-`groupBy` summaries (opening rates, colour advantage), this job reconstructs the *quality of
  * every move* from the engine evaluations Lichess embeds in the movetext (`{ [%eval 0.24] }`, mate scores `[%eval
  * #-3]`) and turns them into the same accuracy signals lichess.com surfaces: average centipawn loss (ACPL), and counts
  * of inaccuracies / mistakes / blunders.
  *
  * Pipeline (all Spark SQL string/array functions + window funcs — no UDFs, Catalyst-friendly):
  *   1. Keep only games carrying `[%eval` comments.
  *   2. `regexp_extract_all` pulls every eval in ply order; mate scores collapse to ±10 pawns, normal evals are clamped
  *      to ±10 so a single huge swing cannot dominate the mean. All evals are White-POV pawns.
  *   3. `posexplode` → one row per ply; a per-game window `lag` gives the eval *before* the move.
  *   4. Centipawn loss for the side that moved = how much the eval moved against them (white wants it up, black down),
  *      floored at 0 and scaled to centipawns.
  *   5. Roll up to (game, side): ACPL + inaccuracy(≥50cp) / mistake(≥100cp) / blunder(≥200cp) counts, tagged with that
  *      side's Elo and whether they won.
  *
  * Outputs (Parquet + CSV + JDBC):
  *   - `accuracy_by_rating` — ACPL, avg blunders/mistakes/inaccuracies per game and win-rate, per Elo band. Shows how
  *     move quality scales with rating.
  *   - `blunder_outcome` — win-rate bucketed by number of blunders in the game. Quantifies "one blunder costs you the
  *     game".
  *
  * Requires the eval-annotated Lichess dump (`NOWCHESS_PGN_PATH` → an evals dump); JDBC games carry no per-move evals.
  */
object AccuracyBlunderJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-accuracy"

    val spark = SparkSession
      .builder()
      .appName("NowChess Accuracy & Blunders")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .select("pgn", "result", "white_elo", "black_elo")
      .filter(F.col("result").isNotNull.and(F.col("pgn").contains("[%eval")))
      .withColumn("game_id", F.monotonically_increasing_id())

    // White-POV pawn evals in ply order; mate → ±10, normal evals clamped to ±10.
    val evalStrs = F.expr("""regexp_extract_all(pgn, '\\[%eval ([^\\]]+)\\]', 1)""")
    val evalCps = F.expr(
      "transform(eval_strs, x -> CASE " +
        "WHEN x LIKE '#-%' THEN -10.0 " +
        "WHEN x LIKE '#%' THEN 10.0 " +
        "ELSE greatest(-10.0, least(10.0, cast(x as double))) END)",
    )

    val withEvals = games
      .withColumn("eval_strs", evalStrs)
      .withColumn("eval_cp", evalCps)
      .filter(F.size(F.col("eval_cp")) >= 2)

    val plies = withEvals.select(
      F.col("game_id"),
      F.col("result"),
      F.col("white_elo"),
      F.col("black_elo"),
      F.posexplode(F.col("eval_cp")).as(Seq("ply", "eval_after")),
    )

    val byGame     = Window.partitionBy("game_id").orderBy("ply")
    val mover      = F.when(F.col("ply") % 2 === 0, "white").otherwise("black")
    val evalBefore = F.coalesce(F.lag("eval_after", 1).over(byGame), F.lit(0.15))
    val cpl = F.greatest(
      F.lit(0.0),
      F.when(F.col("mover") === "white", evalBefore - F.col("eval_after"))
        .otherwise(F.col("eval_after") - evalBefore),
    ) * 100

    val moves = plies
      .withColumn("mover", mover)
      .withColumn("cpl", cpl)

    val perSide = moves
      .groupBy("game_id", "mover", "result", "white_elo", "black_elo")
      .agg(
        F.round(F.avg("cpl"), 1).as("acpl"),
        F.sum(F.when(F.col("cpl") >= 200, 1).otherwise(0)).as("blunders"),
        F.sum(F.when(F.col("cpl") >= 100 && F.col("cpl") < 200, 1).otherwise(0)).as("mistakes"),
        F.sum(F.when(F.col("cpl") >= 50 && F.col("cpl") < 100, 1).otherwise(0)).as("inaccuracies"),
      )
      .withColumn(
        "self_elo",
        F.when(F.col("mover") === "white", F.col("white_elo")).otherwise(F.col("black_elo")),
      )
      .withColumn("won", F.when(F.col("mover") === F.col("result"), 1).otherwise(0))

    writeAccuracyByRating(perSide, jdbcUrl, dbUser, dbPass, outputDir)
    writeBlunderOutcome(perSide, jdbcUrl, dbUser, dbPass, outputDir)

  private def writeAccuracyByRating(
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
        F.round(F.avg("acpl"), 1).as("avg_acpl"),
        F.round(F.avg("blunders"), 2).as("avg_blunders"),
        F.round(F.avg("mistakes"), 2).as("avg_mistakes"),
        F.round(F.avg("inaccuracies"), 2).as("avg_inaccuracies"),
        F.round(F.avg("won"), 3).as("win_rate"),
      )
      .orderBy(F.asc("band_order"))
      .drop("band_order")

    write(stats, outputDir, "accuracy_by_rating", jdbcUrl, dbUser, dbPass, "analytics_accuracy_by_rating")

  private def writeBlunderOutcome(
      perSide: org.apache.spark.sql.DataFrame,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      outputDir: String,
  ): Unit =
    val b      = F.col("blunders")
    val bucket = F.when(b === 0, "0").when(b === 1, "1").when(b === 2, "2").otherwise("3+")
    val order  = F.when(b === 0, 0).when(b === 1, 1).when(b === 2, 2).otherwise(3)

    val stats = perSide
      .withColumn("blunder_bucket", bucket)
      .withColumn("bucket_order", order)
      .groupBy("blunder_bucket", "bucket_order")
      .agg(
        F.count("*").as("player_games"),
        F.round(F.avg("won"), 3).as("win_rate"),
        F.round(F.avg("acpl"), 1).as("avg_acpl"),
      )
      .orderBy(F.asc("bucket_order"))
      .drop("bucket_order")

    write(stats, outputDir, "blunder_outcome", jdbcUrl, dbUser, dbPass, "analytics_blunder_outcome")

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
