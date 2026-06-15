package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F
import org.apache.spark.sql.streaming.Trigger

/** Demonstrates Spark Structured Streaming on NowChess game-over events.
  *
  * Spark concepts shown:
  *   - Continuous micro-batch processing (`readStream`)
  *   - Watermarking for late-data tolerance (events up to 45 s late are accepted)
  *   - Tumbling window aggregations — fixed 1-minute buckets, zero overlap
  *   - Sliding window aggregations — 5-minute window, 1-minute slide
  *   - Append vs Update output modes and when each is valid
  *   - Exactly-once fault tolerance via checkpointing
  *   - Multiple concurrent streaming queries on the same session
  *
  * Production wiring: NowChess already publishes game-over events to a Redis Stream (`nowchess:game-over`, see
  * GameRedisPublisher). Swap the `rate` source below for one of the production sources shown in the comment block.
  */
object LiveDashboardJob:

  def main(args: Array[String]): Unit =
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-live-dashboard"

    val spark = SparkSession
      .builder()
      .appName("NowChess Live Dashboard")
      .getOrCreate()

    run(spark, outputDir)

  def run(spark: SparkSession, outputDir: String): Unit =

    // ── Production sources (replace rate source with one of these) ─────────
    //
    // Kafka (via a Redis → Kafka bridge):
    //   spark.readStream
    //     .format("kafka")
    //     .option("kafka.bootstrap.servers", sys.env("KAFKA_BROKERS"))
    //     .option("subscribe", "nowchess.game-over")
    //     .load()
    //     .select(F.from_json(F.col("value").cast("string"), gameOverSchema).as("e"))
    //     .select("e.*")
    //
    // spark-redis (com.redislabs:spark-redis:3.1.0):
    //   spark.readStream
    //     .format("redis")
    //     .option("stream.keys", "nowchess:game-over")
    //     .schema(gameOverSchema)
    //     .load()
    // ─────────────────────────────────────────────────────────────────────

    // Simulated stream: 10 game-over events / second.
    // `rate` source emits (timestamp: Timestamp, value: Long) — Spark built-in, no deps.
    val rawStream = spark.readStream
      .format("rate")
      .option("rowsPerSecond", "10")
      .load()

    // Derive game-outcome columns from the monotonic counter.
    // In production these come directly from the event payload.
    val events = rawStream
      .withColumn(
        "result",
        F.when(F.col("value") % 3 === 0L, "white")
          .when(F.col("value") % 3 === 1L, "black")
          .otherwise("draw"),
      )
      .withColumn(
        "termination",
        F.when(F.col("value") % 4 === 0L, "checkmate")
          .when(F.col("value") % 4 === 1L, "resignation")
          .when(F.col("value") % 4 === 2L, "timeout")
          .otherwise("agreement"),
      )
      // Watermark: accept events up to 45 seconds late.
      // Spark will not emit a window result until the watermark passes its end time.
      .withWatermark("timestamp", "45 seconds")

    // ── Query 1: tumbling 1-minute windows ────────────────────────────────
    // Each window is a non-overlapping 60-second bucket.
    // outputMode("append") only emits a window after the watermark seals it —
    // guarantees that late arrivals were already counted before output.
    val gamesByWindow = events
      .groupBy(F.window(F.col("timestamp"), "1 minute"), F.col("result"))
      .agg(F.count("*").as("games"))
      .select(
        F.col("window.start").as("window_start"),
        F.col("window.end").as("window_end"),
        F.col("result"),
        F.col("games"),
      )

    // ── Query 2: sliding 5-minute / 1-minute windows ──────────────────────
    // Each window covers 5 minutes of data, and a new window opens every minute.
    // outputMode("update") emits a row whenever an existing window changes —
    // gives a live rolling view of termination patterns.
    val terminationTrend = events
      .groupBy(F.window(F.col("timestamp"), "5 minutes", "1 minute"))
      .agg(
        F.count("*").as("total"),
        F.sum(F.when(F.col("termination") === "checkmate", 1).otherwise(0)).as("checkmates"),
        F.sum(F.when(F.col("termination") === "resignation", 1).otherwise(0)).as("resignations"),
        F.sum(F.when(F.col("termination") === "timeout", 1).otherwise(0)).as("timeouts"),
      )
      .withColumn(
        "checkmate_pct",
        F.round(F.col("checkmates") / F.col("total").cast("double") * 100, 1),
      )
      .select(
        F.col("window.start").as("window_start"),
        F.col("total"),
        F.col("checkmate_pct"),
        F.col("resignations"),
        F.col("timeouts"),
      )

    // Write sealed windows to Parquet — safe to query with any SQL engine.
    gamesByWindow.writeStream
      .outputMode("append")
      .format("parquet")
      .option("path", s"$outputDir/game_counts_by_window")
      .option("checkpointLocation", s"$outputDir/_checkpoints/game_counts")
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .start()

    // Print live rolling stats to the console every 10 seconds.
    terminationTrend.writeStream
      .outputMode("update")
      .format("console")
      .option("truncate", "false")
      .option("numRows", "10")
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .start()

    // Block until any query fails or the process is killed.
    spark.streams.awaitAnyTermination()
