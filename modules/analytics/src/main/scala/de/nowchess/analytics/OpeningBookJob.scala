package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

/** Reads completed games from the game_records table and produces an opening-book statistics table: for each unique
  * opening (first N plies), it reports total games played and win/draw/loss rates from each side.
  *
  * Output is written as Parquet to `outputDir/opening_book` and a human-readable CSV summary (top-1000 openings by
  * popularity) to `outputDir/opening_book_top1000`.
  *
  * PGN parsing is done entirely with Spark SQL string functions — no UDFs — so the Catalyst optimizer can push
  * predicates and the job scales to any cluster size.
  */
object OpeningBookJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-opening-book"
    val maxPlies  = if args.length > 1 then args(1).toInt else 10

    val spark = SparkSession
      .builder()
      .appName("NowChess Opening Book Generator")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir, maxPlies)
    spark.stop()

  def run(
      spark: SparkSession,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      outputDir: String,
      maxPlies: Int,
  ): Unit =
    val games = spark.read
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", "game_records")
      .option("user", dbUser)
      .option("password", dbPass)
      .option("driver", "org.postgresql.Driver")
      .option("fetchsize", "10000")
      .load()
      .select("pgn", "result")
      .filter(F.col("result").isNotNull.and(F.col("pgn").isNotNull))

    val openingCol = extractOpening(F.col("pgn"), maxPlies)

    val withOpening = games
      .withColumn("opening", openingCol)
      .filter(F.col("opening").isNotNull.and(F.length(F.col("opening")) > 0))

    val stats = withOpening
      .groupBy("opening")
      .agg(
        F.count("*").as("total"),
        F.sum(F.when(F.col("result") === "white", 1).otherwise(0)).as("white_wins"),
        F.sum(F.when(F.col("result") === "black", 1).otherwise(0)).as("black_wins"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
      )
      .withColumn("white_win_rate", F.round(F.col("white_wins") / F.col("total").cast("double"), 3))
      .withColumn("black_win_rate", F.round(F.col("black_wins") / F.col("total").cast("double"), 3))
      .withColumn("draw_rate", F.round(F.col("draws") / F.col("total").cast("double"), 3))
      .orderBy(F.desc("total"))

    stats.write
      .mode("overwrite")
      .parquet(s"$outputDir/opening_book")

    val top1000 = stats.limit(1000)

    top1000.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/opening_book_top1000")

    top1000.write
      .mode("overwrite")
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", "analytics_opening_stats")
      .option("user", dbUser)
      .option("password", dbPass)
      .option("driver", "org.postgresql.Driver")
      .save()

  /** Extracts the first `maxPlies` moves from a PGN column as a space-separated string.
    *
    * PGN format produced by PgnExporter: [Event "?"]\n[White "?"]\n...\n\n1. e4 e5 2. Nf3 Nc6 *
    *
    * Steps:
    *   1. Split on double-newline; take the moves section (index 1). 2. Strip the terminal result token (*, 1-0, 0-1,
    *      1/2-1/2). 3. Strip move numbers (e.g., "1. ", "12. "). 4. Strip check/checkmate suffixes (+ #) for
    *      position-independent lookup. 5. Tokenize on whitespace, take first maxPlies tokens, rejoin with spaces.
    */
  private def extractOpening(pgnCol: org.apache.spark.sql.Column, maxPlies: Int): org.apache.spark.sql.Column =
    val moveSection   = F.coalesce(F.split(pgnCol, "\n\n").getItem(1), pgnCol)
    val noResult      = F.regexp_replace(moveSection, "(1-0|0-1|1/2-1/2|\\*)\\s*$", "")
    val noMoveNumbers = F.regexp_replace(noResult, "\\d+\\.+\\s*", " ")
    val noAnnotations = F.regexp_replace(noMoveNumbers, "[+#]", "")
    val moveArray     = F.split(F.trim(noAnnotations), "\\s+")
    F.array_join(F.slice(moveArray, 1, maxPlies), " ")
