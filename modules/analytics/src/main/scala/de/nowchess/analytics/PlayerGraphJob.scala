package de.nowchess.analytics

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions as F
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType

/** Models the NowChess player network as a directed graph and runs GraphX analytics.
  *
  * Spark / GraphX concepts shown:
  *   - Building a Graph from RDDs derived from a JDBC DataFrame
  *   - PageRank — measures a player's "influence"; high score = many games against other high-ranked players (analogous
  *     to web link authority)
  *   - Connected Components — finds isolated player communities; players who have never played anyone from another
  *     component cannot be linked
  *   - Converting GraphX results back to DataFrames for SQL-style joins and output
  *
  * Graph model: Vertices: one per unique player (vertex ID = hashCode of player UUID string) Edges: one per completed
  * game (white → black), attributed with result
  *
  * Note: hashCode gives a 32-bit → 64-bit vertex ID; collision probability is negligible for typical player counts. For
  * millions of players, replace with MLlib StringIndexer to generate collision-free Long IDs.
  */
object PlayerGraphJob:

  def main(args: Array[String]): Unit =
    val jdbcUrl   = sys.env.getOrElse("NOWCHESS_JDBC_URL", "jdbc:postgresql://localhost:5432/nowchess")
    val dbUser    = sys.env.getOrElse("NOWCHESS_DB_USER", "nowchess")
    val dbPass    = sys.env.getOrElse("NOWCHESS_DB_PASS", "nowchess")
    val outputDir = if args.length > 0 then args(0) else "/tmp/nowchess-player-graph"

    val spark = SparkSession
      .builder()
      .appName("NowChess Player Graph Analytics")
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
    val gamesRdd: RDD[Row] = GameSource
      .load(spark, jdbcUrl, dbUser, dbPass)
      .select("white_id", "black_id", "result")
      .filter(F.col("result").isNotNull)
      .rdd

    val toVid: String => VertexId = s => s.hashCode.toLong

    // Each row contributes two vertex entries (white and black player).
    val vertices: RDD[(VertexId, String)] = gamesRdd
      .flatMap { row =>
        Seq(
          (toVid(row.getString(0)), row.getString(0)),
          (toVid(row.getString(1)), row.getString(1)),
        )
      }
      .distinct()

    // Directed edge white → black, labelled with the game result.
    val edges: RDD[Edge[String]] = gamesRdd.map { row =>
      Edge(toVid(row.getString(0)), toVid(row.getString(1)), row.getString(2))
    }

    val graph: Graph[String, String] = Graph(vertices, edges)

    println(s"[Graph] vertices=${graph.numVertices}  edges=${graph.numEdges}")

    // ── PageRank ────────────────────────────────────────────────────────────
    // Convergence tolerance 0.01 — lower = more iterations = more accurate.
    // Returns Graph[Double, Double]; vertex attribute = PageRank score.
    val pageRanks: RDD[(VertexId, Double)] = graph.pageRank(0.01).vertices

    // ── Connected Components ────────────────────────────────────────────────
    // Returns Graph[VertexId, ED]; vertex attribute = minimum vertex ID in
    // the component (serves as a stable component label).
    val components: RDD[(VertexId, VertexId)] = graph.connectedComponents().vertices

    // Convert each RDD result to a DataFrame so we can join with SQL semantics.
    val vertexDf    = rddToFrame(spark, vertices, "player_id", StringType)
    val pageRankDf  = rddToFrame(spark, pageRanks, "page_rank", DoubleType)
    val componentDf = rddToFrame(spark, components, "component_id", LongType)

    val result = vertexDf
      .join(pageRankDf, "vertex_id")
      .join(componentDf, "vertex_id")
      .drop("vertex_id")
      .withColumn("page_rank", F.round(F.col("page_rank"), 4))
      .orderBy(F.desc("page_rank"))

    println("[Graph] Top 20 players by PageRank:")
    result.show(20, false)

    result.write
      .mode("overwrite")
      .parquet(s"$outputDir/player_graph")

    if !GameSource.isPgnMode then
      result.write
        .mode("overwrite")
        .format("jdbc")
        .option("url", jdbcUrl)
        .option("dbtable", "analytics_player_graph")
        .option("user", dbUser)
        .option("password", dbPass)
        .option("driver", "org.postgresql.Driver")
        .save()

    // How many players belong to each connected component?
    // A large dominant component + many singletons is the expected shape.
    val componentSizes = result
      .groupBy("component_id")
      .agg(F.count("*").as("player_count"))
      .orderBy(F.desc("player_count"))

    println("[Graph] Connected component sizes:")
    componentSizes.show(10, false)

    componentSizes.write
      .mode("overwrite")
      .option("header", "true")
      .csv(s"$outputDir/component_sizes")

  // Build a two-column DataFrame (vertex_id: Long, valueCol: valueType) from an RDD.
  // Used to bridge GraphX RDD results into the DataFrame API without implicits.
  private def rddToFrame[T](
      spark: SparkSession,
      rdd: RDD[(VertexId, T)],
      valueCol: String,
      valueType: DataType,
  ): org.apache.spark.sql.DataFrame =
    val schema = StructType(
      List(
        StructField("vertex_id", LongType, nullable = false),
        StructField(valueCol, valueType, nullable = false),
      ),
    )
    spark.createDataFrame(
      rdd.map { case (vid, v) => Row.fromSeq(Seq[Any](vid, v)) },
      schema,
    )
