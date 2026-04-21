package de.nowchess.io.service.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.nowchess.api.board.Square
import de.nowchess.api.game.GameContext
import de.nowchess.io.json.{SquareKeyDeserializer, SquareKeySerializer}
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@QuarkusTest
class IoResourceTest:
  private lazy val testMapper: ObjectMapper =
    val m   = new ObjectMapper()
    val mod = new SimpleModule()
    mod.addKeySerializer(classOf[Square], new SquareKeySerializer())
    mod.addKeyDeserializer(classOf[Square], new SquareKeyDeserializer())
    m.registerModule(new DefaultScalaModule())
    m.registerModule(mod)
    m

  private def contextJson(ctx: GameContext): String = testMapper.writeValueAsString(ctx)

  @Test
  def importFenReturns200(): Unit =
    val resp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}""")
      .post("/io/import/fen")
    assertEquals(200, resp.statusCode())

  @Test
  def importFenInvalidReturns400(): Unit =
    val resp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"fen":"not-a-fen"}""")
      .post("/io/import/fen")
    assertEquals(400, resp.statusCode())

  @Test
  def importPgnReturns200(): Unit =
    val resp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"pgn":"1. e4 e5"}""")
      .post("/io/import/pgn")
    assertEquals(200, resp.statusCode())

  @Test
  def importPgnInvalidReturns400(): Unit =
    val resp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body("""{"pgn":"not valid pgn !!!###"}""")
      .post("/io/import/pgn")
    assertEquals(400, resp.statusCode())

  @Test
  def exportFenReturns200WithFen(): Unit =
    val resp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body(contextJson(GameContext.initial))
      .post("/io/export/fen")
    assertEquals(200, resp.statusCode())
    assertTrue(resp.getBody.asString().contains("rnbqkbnr"))

  @Test
  def exportPgnReturns200(): Unit =
    val resp = RestAssured
      .`given`()
      .contentType(ContentType.JSON)
      .body(contextJson(GameContext.initial))
      .post("/io/export/pgn")
    assertEquals(200, resp.statusCode())
