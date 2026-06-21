package de.nowchess.analytics

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

object DailyActivityJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-daily-activity"

    val spark = SparkSession
      .builder()
      .appName("NowChess Daily Activity")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .loadExtended(spark, jdbcUrl, dbUser, dbPass)
      .select("result", "utc_date", "utc_time")
      .filter(F.col("utc_time").isNotNull.and(F.col("utc_date").isNotNull))

    val hourOfDay = F.regexp_extract(F.col("utc_time"), "^(\\d{2})", 1).cast("int")
    val dow       = F.dayofweek(F.to_date(F.col("utc_date"), "yyyy.MM.dd"))

    val tagged = games
      .withColumn("hour_of_day", hourOfDay)
      .withColumn("dow", dow)

    val hourly = tagged
      .groupBy("hour_of_day")
      .agg(
        F.count("*").as("total_games"),
        F.sum(F.when(F.col("result") === "white", 1).otherwise(0)).as("white_wins"),
        F.sum(F.when(F.col("result") === "black", 1).otherwise(0)).as("black_wins"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
      )
      .withColumn("white_win_rate", F.round(F.col("white_wins") / F.col("total_games").cast("double"), 3))
      .orderBy(F.asc("hour_of_day"))
      .select("hour_of_day", "total_games", "white_wins", "black_wins", "draws", "white_win_rate")

    hourly.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/hourly_activity")

    hourly.write
      .mode("overwrite")
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", "analytics_hourly_activity")
      .option("user", dbUser)
      .option("password", dbPass)
      .option("driver", "org.postgresql.Driver")
      .save()

    val dayName = F
      .when(F.col("dow") === 1, "Sunday")
      .when(F.col("dow") === 2, "Monday")
      .when(F.col("dow") === 3, "Tuesday")
      .when(F.col("dow") === 4, "Wednesday")
      .when(F.col("dow") === 5, "Thursday")
      .when(F.col("dow") === 6, "Friday")
      .otherwise("Saturday")

    val weekly = tagged
      .withColumn("day_of_week", dayName)
      .withColumn("day_order", F.col("dow"))
      .groupBy("day_of_week", "day_order")
      .agg(
        F.count("*").as("total_games"),
        F.sum(F.when(F.col("result") === "white", 1).otherwise(0)).as("white_wins"),
        F.sum(F.when(F.col("result") === "black", 1).otherwise(0)).as("black_wins"),
        F.sum(F.when(F.col("result") === "draw", 1).otherwise(0)).as("draws"),
      )
      .withColumn("white_win_rate", F.round(F.col("white_wins") / F.col("total_games").cast("double"), 3))
      .orderBy(F.asc("day_order"))
      .drop("day_order")
      .select("day_of_week", "total_games", "white_wins", "black_wins", "draws", "white_win_rate")

    weekly.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/weekly_activity")

    weekly.write
      .mode("overwrite")
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", "analytics_weekly_activity")
      .option("user", dbUser)
      .option("password", dbPass)
      .option("driver", "org.postgresql.Driver")
      .save()
