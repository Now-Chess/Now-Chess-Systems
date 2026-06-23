package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions as F

/** Smurf / sandbagging anomaly detection via population z-scores.
  *
  * Smurfs (strong players on fresh accounts) and sandbaggers leave a statistical signature: a win-rate, an upset-rate
  * (beating higher-rated opponents) and a self-Elo climb that sit far above the population norm. This job builds those
  * three features per player, standardises each against the whole player base, and flags the players whose combined
  * deviation is extreme.
  *
  * Features per player (from each game's own/opponent Elo):
  *   - win_rate — fraction of decisive results won
  *   - upset_rate — wins vs higher-rated opponents / games vs higher-rated opponents
  *   - elo_climb — max self-Elo − min self-Elo across their games (rapid rating gain)
  *
  * Standardisation uses a single unbounded window (`Window.partitionBy()`), i.e. mean/stddev over every qualifying
  * player, so z = (x − μ) / σ. The composite anomaly score sums the three z-scores. No UDFs — pure SQL aggregates +
  * window functions, so Catalyst plans the whole job.
  *
  * Outputs (Parquet + CSV + JDBC):
  *   - `anomaly_scores` — every qualifying player with features, z-scores and composite, ranked most-anomalous first.
  *   - `flagged_smurfs` — the suspicious subset (high composite, or the classic high-winrate / few-games / steep-climb
  *     profile).
  *
  * Meaningful only when Elo is present (Lichess dump); requires `minGames` (arg 1, default 15) to avoid small-sample
  * noise.
  */
object SmurfAnomalyJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-smurf-anomaly"
    val minGames  = if args.length > 1 then args(1).toInt else 15

    val spark = SparkSession
      .builder()
      .appName("NowChess Smurf Anomaly Detection")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir, minGames)
    spark.stop()

  def run(
      spark: SparkSession,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      outputDir: String,
      minGames: Int,
  ): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .select("white_id", "black_id", "result", "white_elo", "black_elo")
      .filter(F.col("result").isNotNull)

    val asWhite = games.select(
      F.col("white_id").as("player_id"),
      F.col("white_elo").as("self_elo"),
      F.col("black_elo").as("opp_elo"),
      F.when(F.col("result") === "white", 1).otherwise(0).as("won"),
    )
    val asBlack = games.select(
      F.col("black_id").as("player_id"),
      F.col("black_elo").as("self_elo"),
      F.col("white_elo").as("opp_elo"),
      F.when(F.col("result") === "black", 1).otherwise(0).as("won"),
    )

    val playerGames = asWhite
      .union(asBlack)
      .filter(F.col("self_elo").isNotNull.and(F.col("opp_elo").isNotNull))

    val higher = F.col("opp_elo") > F.col("self_elo")

    val features = playerGames
      .groupBy("player_id")
      .agg(
        F.count("*").as("total_games"),
        F.round(F.avg("won"), 3).as("win_rate"),
        F.round(F.avg("self_elo"), 0).as("avg_self_elo"),
        (F.max("self_elo") - F.min("self_elo")).as("elo_climb"),
        F.sum(F.when(higher, 1).otherwise(0)).as("vs_higher"),
        F.sum(F.when(higher && F.col("won") === 1, 1).otherwise(0)).as("upsets"),
      )
      .filter(F.col("total_games") >= minGames)
      .withColumn("upset_rate", F.round(F.col("upsets") / F.greatest(F.col("vs_higher"), F.lit(1)), 3))

    val all = Window.partitionBy()
    def z(col: String): org.apache.spark.sql.Column =
      val mean = F.avg(col).over(all)
      val std  = F.stddev(col).over(all)
      F.round((F.col(col) - mean) / F.when(std === 0 || std.isNull, F.lit(1.0)).otherwise(std), 2)

    val scored = features
      .withColumn("z_win_rate", z("win_rate"))
      .withColumn("z_upset_rate", z("upset_rate"))
      .withColumn("z_elo_climb", z("elo_climb"))
      .withColumn(
        "anomaly_score",
        F.round(F.col("z_win_rate") + F.col("z_upset_rate") + F.col("z_elo_climb"), 2),
      )
      .withColumn(
        "flagged",
        (F.col("anomaly_score") >= 4.0)
          .or(F.col("win_rate") >= 0.8 && F.col("total_games") < 50 && F.col("elo_climb") >= 300),
      )

    val ordered = scored
      .select(
        "player_id",
        "total_games",
        "win_rate",
        "avg_self_elo",
        "elo_climb",
        "upset_rate",
        "z_win_rate",
        "z_upset_rate",
        "z_elo_climb",
        "anomaly_score",
        "flagged",
      )
      .orderBy(F.desc("anomaly_score"))

    write(ordered, outputDir, "anomaly_scores", jdbcUrl, dbUser, dbPass, "analytics_smurf_anomaly")

    val flagged = ordered.filter(F.col("flagged") === true)
    write(flagged, outputDir, "flagged_smurfs", jdbcUrl, dbUser, dbPass, "analytics_flagged_smurfs")

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
