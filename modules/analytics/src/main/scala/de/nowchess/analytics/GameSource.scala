package de.nowchess.analytics

import org.apache.spark.SparkFiles
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

/** Normalised game-record source for the batch jobs.
  *
  * Every batch job consumes the same five-column shape:
  *   - white_id, black_id : player identifiers
  *   - result             : one of "white", "black", "draw"
  *   - move_count         : number of plies
  *   - pgn                : full PGN ("[Event …]…\n\n1. e4 …"), header and movetext separated by a blank line
  *
  * Two backends, selected by the `NOWCHESS_PGN_PATH` environment variable:
  *   - unset → PostgreSQL `game_records` table (production)
  *   - set   → a Lichess PGN dump file/URL (demo). Point it at a `lichess_db_standard_rated_*.pgn[.zst]`
  *             to drive every batch job from real Lichess games.
  *
  * Lichess parsing uses only Spark SQL string functions — no UDFs — so Catalyst can push predicates,
  * matching the no-UDF approach already used in OpeningBookJob.
  */
object GameSource:

  private val PgnPathEnv = "NOWCHESS_PGN_PATH"

  /** True when a Lichess PGN dump is configured; jobs use this to skip JDBC write-back. */
  def isPgnMode: Boolean = sys.env.contains(PgnPathEnv)

  def load(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String): DataFrame =
    sys.env.get(PgnPathEnv) match
      case Some(path) => fromLichessPgn(spark, path)
      case None       => fromJdbc(spark, jdbcUrl, dbUser, dbPass)

  def fromJdbc(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String): DataFrame =
    spark.read
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", "game_records")
      .option("user", dbUser)
      .option("password", dbPass)
      .option("driver", "org.postgresql.Driver")
      .option("fetchsize", "10000")
      .load()
      .select("white_id", "black_id", "result", "move_count", "pgn")

  /** Parses a Lichess PGN dump into the normalised game shape.
    *
    * `path` may be:
    *   - an http(s)/ftp URL — fetched once via SparkContext.addFile and distributed to executors, then read
    *     from the local replica (no S3/PVC needed; handy for a staging demo)
    *   - any Hadoop-readable path (file://, hdfs://, s3a://, …)
    *
    * `.zst` dumps (Lichess' native format) are decompressed in-process via zstd-jni; `.gz`/`.bz2` are
    * handled by Spark's text reader codecs.
    *
    * Records are split on the "[Event " tag that opens every game, so each row holds one complete game
    * (the empty fragment before the first game is filtered out). Header tags are read with regexp_extract;
    * the movetext (after the blank line) is cleaned of clock/eval comments and move numbers to count plies.
    */
  def fromLichessPgn(spark: SparkSession, path: String): DataFrame =
    val resolved = resolvePath(spark, path)
    val record   = F.col("value")

    val resultTag = F.regexp_extract(record, "Result \"([^\"]*)\"", 1)
    val result = F
      .when(resultTag === "1-0", "white")
      .when(resultTag === "0-1", "black")
      .when(resultTag === "1/2-1/2", "draw")
      .otherwise(F.lit(null).cast("string"))

    val moveText  = F.coalesce(F.split(record, "\n\n").getItem(1), F.lit(""))
    val noComment = F.regexp_replace(moveText, "\\{[^}]*\\}", "")
    val noResult  = F.regexp_replace(noComment, "(1-0|0-1|1/2-1/2|\\*)", "")
    val noNumbers = F.regexp_replace(noResult, "\\d+\\.+", " ")
    val plies     = F.size(F.filter(F.split(F.trim(noNumbers), "\\s+"), tok => F.length(tok) > 0))

    spark.read
      .option("lineSep", "[Event ")
      .text(resolved)
      .filter(F.length(F.trim(record)) > 0)
      .select(
        F.regexp_extract(record, "White \"([^\"]*)\"", 1).as("white_id"),
        F.regexp_extract(record, "Black \"([^\"]*)\"", 1).as("black_id"),
        result.as("result"),
        plies.as("move_count"),
        F.concat(F.lit("[Event "), record).as("pgn"),
      )
      .filter((F.col("white_id") =!= "").and(F.col("black_id") =!= ""))

  /** Turns an http(s)/ftp URL into a cluster-local path by fetching it once with SparkContext.addFile,
    * which distributes the file to every executor. `.zst` is decompressed in-process and the plain `.pgn`
    * is redistributed. Non-URL paths are returned unchanged.
    */
  private def resolvePath(spark: SparkSession, path: String): String =
    if !path.matches("^(https?|ftp)://.*") then path
    else
      spark.sparkContext.addFile(path)
      val local = SparkFiles.get(baseName(path))
      if !local.endsWith(".zst") then "file://" + local
      else distribute(spark, decompressZstd(local))

  private def baseName(path: String): String = path.substring(path.lastIndexOf('/') + 1)

  private def distribute(spark: SparkSession, localPath: String): String =
    spark.sparkContext.addFile("file://" + localPath)
    "file://" + SparkFiles.get(baseName(localPath))

  /** Decompresses a `.zst` file to a temp `.pgn` using zstd-jni (bundled with Spark at runtime). */
  private def decompressZstd(srcPath: String): String =
    val out = java.io.File.createTempFile("lichess-", ".pgn")
    out.deleteOnExit()
    val in = com.github.luben.zstd.ZstdInputStream(
      java.io.BufferedInputStream(java.io.FileInputStream(srcPath)),
    )
    try java.nio.file.Files.copy(in, out.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    finally in.close()
    out.getAbsolutePath
