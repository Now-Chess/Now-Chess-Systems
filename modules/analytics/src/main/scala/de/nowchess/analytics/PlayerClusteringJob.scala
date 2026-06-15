package de.nowchess.analytics

import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.clustering.KMeans
import org.apache.spark.ml.evaluation.ClusteringEvaluator
import org.apache.spark.ml.feature.StandardScaler
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F

/** Clusters NowChess players into skill tiers using K-Means via MLlib.
  *
  * Spark / MLlib concepts shown:
  *   - Feature engineering from raw relational data (JDBC → DataFrame)
  *   - VectorAssembler — combine scalar columns into a dense feature vector
  *   - StandardScaler — zero-mean / unit-variance normalisation so that total_games (can be 1000+) does not dominate
  *     win_rate (0–1)
  *   - KMeans clustering — unsupervised partitioning into k skill tiers
  *   - Pipeline — compose transformers + estimator into a single reusable object
  *   - ClusteringEvaluator — silhouette score to assess cluster quality
  *
  * Features per player (all derived from game_records): total_games — how active the player is win_rate — overall
  * strength avg_move_count — game-length preference (tactical vs positional) games_as_white_ratio — colour bias
  *
  * Output: Parquet: player_id + cluster (0..k-1) + feature values CSV: per-cluster archetype averages (interpret what
  * each tier means)
  */
object PlayerClusteringJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-player-clusters"
    val k         = if args.length > 1 then args(1).toInt else 4

    val spark = SparkSession
      .builder()
      .appName("NowChess Player Clustering")
      .getOrCreate()

    run(spark, jdbcUrl, dbUser, dbPass, outputDir, k)
    spark.stop()

  def run(
      spark: SparkSession,
      jdbcUrl: String,
      dbUser: String,
      dbPass: String,
      outputDir: String,
      k: Int,
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
      .select("white_id", "black_id", "result", "move_count")
      .filter(F.col("result").isNotNull)

    val playerStats = buildPlayerStats(games)
      .filter(F.col("total_games") >= 5)

    val featureCols = Array("total_games", "win_rate", "avg_move_count", "games_as_white_ratio")

    val assembler = new VectorAssembler()
      .setInputCols(featureCols)
      .setOutputCol("raw_features")
      .setHandleInvalid("skip")

    val scaler = new StandardScaler()
      .setInputCol("raw_features")
      .setOutputCol("features")
      .setWithStd(true)
      .setWithMean(true)

    val kmeans = new KMeans()
      .setK(k)
      .setSeed(42L)
      .setFeaturesCol("features")
      .setPredictionCol("cluster")

    val pipeline = new Pipeline().setStages(Array(assembler, scaler, kmeans))

    val model       = pipeline.fit(playerStats)
    val predictions = model.transform(playerStats)

    val silhouette = new ClusteringEvaluator()
      .setFeaturesCol("features")
      .setPredictionCol("cluster")
      .evaluate(predictions)

    println(s"[Clustering] k=$k  silhouette=$silhouette")

    // Average feature values per cluster reveal what each tier represents.
    // Example interpretation for k=4:
    //   Cluster 0: high total_games + high win_rate  → experienced strong players
    //   Cluster 1: low total_games + low win_rate    → beginners / casual
    //   Cluster 2: high total_games + mid win_rate   → active intermediate
    //   Cluster 3: low total_games + high win_rate   → strong but infrequent
    val archetypes = predictions
      .groupBy("cluster")
      .agg(
        F.count("*").as("player_count"),
        F.round(F.avg("total_games"), 1).as("avg_total_games"),
        F.round(F.avg("win_rate"), 3).as("avg_win_rate"),
        F.round(F.avg("avg_move_count"), 1).as("avg_move_count"),
        F.round(F.avg("games_as_white_ratio"), 3).as("avg_white_ratio"),
      )
      .orderBy("cluster")

    archetypes.show(20, false)

    predictions
      .select("player_id", "total_games", "win_rate", "avg_move_count", "cluster")
      .write
      .mode("overwrite")
      .parquet(s"$outputDir/player_clusters")

    archetypes.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/cluster_archetypes")

  private def buildPlayerStats(games: org.apache.spark.sql.DataFrame): org.apache.spark.sql.DataFrame =
    val asWhite = games.select(
      F.col("white_id").as("player_id"),
      F.col("result"),
      F.col("move_count"),
      F.lit(1).as("is_white"),
    )
    val asBlack = games.select(
      F.col("black_id").as("player_id"),
      F.col("result"),
      F.col("move_count"),
      F.lit(0).as("is_white"),
    )

    val won = (F.col("is_white") === 1 && F.col("result") === "white")
      .or(F.col("is_white") === 0 && F.col("result") === "black")

    asWhite
      .union(asBlack)
      .groupBy("player_id")
      .agg(
        F.count("*").as("total_games"),
        F.round(F.sum(F.when(won, 1.0).otherwise(0.0)) / F.count("*"), 3).as("win_rate"),
        F.round(F.avg(F.col("move_count")), 1).as("avg_move_count"),
        F.round(F.avg(F.col("is_white").cast("double")), 3).as("games_as_white_ratio"),
      )
