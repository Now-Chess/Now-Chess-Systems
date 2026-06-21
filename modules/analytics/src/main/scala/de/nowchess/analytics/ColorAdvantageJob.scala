package de.nowchess.analytics

import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType

import scala.jdk.CollectionConverters.*

object ColorAdvantageJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-color-advantage"

    val spark = SparkSession
      .builder()
      .appName("NowChess Color Advantage")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir)
    spark.stop()

  def run(spark: SparkSession, jdbcUrl: String, dbUser: String, dbPass: String, outputDir: String): Unit =
    val games = GameSource
      .load(spark, jdbcUrl, dbUser, dbPass)
      .select("result")
      .filter(F.col("result").isNotNull)

    val totalGames = games.count()
    val whiteWins  = games.filter(F.col("result") === "white").count()
    val blackWins  = games.filter(F.col("result") === "black").count()
    val draws      = games.filter(F.col("result") === "draw").count()

    val schema = StructType(
      Seq(
        StructField("color", DataTypes.StringType, false),
        StructField("total_games", DataTypes.LongType, false),
        StructField("wins", DataTypes.LongType, false),
        StructField("losses", DataTypes.LongType, false),
        StructField("draws", DataTypes.LongType, false),
      ),
    )

    val rows = List(
      Row("white", totalGames, whiteWins, blackWins, draws),
      Row("black", totalGames, blackWins, whiteWins, draws),
    )

    val stats = spark
      .createDataFrame(rows.asJava, schema)
      .withColumn("win_rate", F.round(F.col("wins") / F.col("total_games").cast("double"), 3))
      .orderBy(F.asc("color"))

    stats.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/color_advantage")
