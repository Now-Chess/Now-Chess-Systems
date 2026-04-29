package de.nowchess.json

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.nowchess.api.board.{Color, File, Rank, Square}
import de.nowchess.api.game.{DrawReason, GameResult, WinReason}
import de.nowchess.api.move.{MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ChessJacksonModuleTest extends AnyFunSuite with Matchers:

  private val mapper: ObjectMapper =
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m.registerModule(new ChessJacksonModule())
    m

  private val e4 = Square(File.E, Rank.R4)

  // ── SquareSerializer ──────────────────────────────────────────────

  test("SquareSerializer writes square as string"):
    mapper.writeValueAsString(e4) shouldBe """"e4""""

  // ── SquareDeserializer ────────────────────────────────────────────

  test("SquareDeserializer reads valid square string"):
    mapper.readValue(""""e4"""", classOf[Square]) shouldBe e4

  // scalafix:off DisableSyntax.null
  test("SquareDeserializer returns null for invalid square string"):
    mapper.readValue(""""z9"""", classOf[Square]) shouldBe null
  // scalafix:on DisableSyntax.null

  // ── SquareKeySerializer/Deserializer ──────────────────────────────

  test("SquareKeySerializer writes square as map field name"):
    mapper.writeValueAsString(Map(e4 -> "piece")) shouldBe """{"e4":"piece"}"""

  // scalafix:off DisableSyntax.null
  test("SquareKeyDeserializer returns square for valid key"):
    new SquareKeyDeserializer().deserializeKey("e4", null) shouldBe e4

  test("SquareKeyDeserializer returns null for invalid key"):
    new SquareKeyDeserializer().deserializeKey("z9", null) shouldBe null
  // scalafix:on DisableSyntax.null

  test("Square round-trips as map key"):
    val original = Map(Square(File.D, Rank.R5) -> 99)
    val json     = mapper.writeValueAsString(original)
    val result   = mapper.readValue(json, new TypeReference[Map[Square, Int]] {})
    result shouldBe original

  // ── MoveTypeSerializer ────────────────────────────────────────────

  test("MoveTypeSerializer serializes Normal non-capture"):
    mapper.writeValueAsString(MoveType.Normal(false)) shouldBe """{"type":"normal","isCapture":false}"""

  test("MoveTypeSerializer serializes Normal capture"):
    mapper.writeValueAsString(MoveType.Normal(true)) shouldBe """{"type":"normal","isCapture":true}"""

  test("MoveTypeSerializer serializes CastleKingside"):
    mapper.writeValueAsString(MoveType.CastleKingside) shouldBe """{"type":"castleKingside"}"""

  test("MoveTypeSerializer serializes CastleQueenside"):
    mapper.writeValueAsString(MoveType.CastleQueenside) shouldBe """{"type":"castleQueenside"}"""

  test("MoveTypeSerializer serializes EnPassant"):
    mapper.writeValueAsString(MoveType.EnPassant) shouldBe """{"type":"enPassant"}"""

  test("MoveTypeSerializer serializes Promotion"):
    mapper.writeValueAsString(MoveType.Promotion(PromotionPiece.Queen)) shouldBe
      """{"type":"promotion","piece":"Queen"}"""

  // ── MoveTypeDeserializer ──────────────────────────────────────────

  test("MoveTypeDeserializer deserializes normal non-capture"):
    mapper.readValue("""{"type":"normal","isCapture":false}""", classOf[MoveType]) shouldBe MoveType.Normal(false)

  test("MoveTypeDeserializer deserializes normal capture"):
    mapper.readValue("""{"type":"normal","isCapture":true}""", classOf[MoveType]) shouldBe MoveType.Normal(true)

  test("MoveTypeDeserializer deserializes castleKingside"):
    mapper.readValue("""{"type":"castleKingside"}""", classOf[MoveType]) shouldBe MoveType.CastleKingside

  test("MoveTypeDeserializer deserializes castleQueenside"):
    mapper.readValue("""{"type":"castleQueenside"}""", classOf[MoveType]) shouldBe MoveType.CastleQueenside

  test("MoveTypeDeserializer deserializes enPassant"):
    mapper.readValue("""{"type":"enPassant"}""", classOf[MoveType]) shouldBe MoveType.EnPassant

  test("MoveTypeDeserializer deserializes promotion"):
    mapper.readValue("""{"type":"promotion","piece":"Rook"}""", classOf[MoveType]) shouldBe
      MoveType.Promotion(PromotionPiece.Rook)

  test("MoveTypeDeserializer throws for unknown type"):
    an[Exception] should be thrownBy
      mapper.readValue("""{"type":"unknown"}""", classOf[MoveType])

  // ── GameResultSerializer ──────────────────────────────────────────

  test("GameResultSerializer serializes Win"):
    mapper.writeValueAsString(GameResult.Win(Color.White, WinReason.Checkmate)) shouldBe
      """{"type":"win","color":"White","winReason":"Checkmate"}"""

  test("GameResultSerializer serializes Win by Resignation"):
    mapper.writeValueAsString(GameResult.Win(Color.Black, WinReason.Resignation)) shouldBe
      """{"type":"win","color":"Black","winReason":"Resignation"}"""

  test("GameResultSerializer serializes Win by TimeControl"):
    mapper.writeValueAsString(GameResult.Win(Color.White, WinReason.TimeControl)) shouldBe
      """{"type":"win","color":"White","winReason":"TimeControl"}"""

  test("GameResultSerializer serializes Draw"):
    mapper.writeValueAsString(GameResult.Draw(DrawReason.Stalemate)) shouldBe
      """{"type":"draw","reason":"Stalemate"}"""

  test("GameResultSerializer serializes Draw InsufficientMaterial"):
    mapper.writeValueAsString(GameResult.Draw(DrawReason.InsufficientMaterial)) shouldBe
      """{"type":"draw","reason":"InsufficientMaterial"}"""

  test("GameResultSerializer serializes Draw FiftyMoveRule"):
    mapper.writeValueAsString(GameResult.Draw(DrawReason.FiftyMoveRule)) shouldBe
      """{"type":"draw","reason":"FiftyMoveRule"}"""

  test("GameResultSerializer serializes Draw ThreefoldRepetition"):
    mapper.writeValueAsString(GameResult.Draw(DrawReason.ThreefoldRepetition)) shouldBe
      """{"type":"draw","reason":"ThreefoldRepetition"}"""

  test("GameResultSerializer serializes Draw Agreement"):
    mapper.writeValueAsString(GameResult.Draw(DrawReason.Agreement)) shouldBe
      """{"type":"draw","reason":"Agreement"}"""

  // ── GameResultDeserializer ────────────────────────────────────────

  test("GameResultDeserializer deserializes Win"):
    mapper.readValue("""{"type":"win","color":"White","winReason":"Checkmate"}""", classOf[GameResult]) shouldBe
      GameResult.Win(Color.White, WinReason.Checkmate)

  test("GameResultDeserializer deserializes Win Black Resignation"):
    mapper.readValue("""{"type":"win","color":"Black","winReason":"Resignation"}""", classOf[GameResult]) shouldBe
      GameResult.Win(Color.Black, WinReason.Resignation)

  test("GameResultDeserializer deserializes Draw"):
    mapper.readValue("""{"type":"draw","reason":"Stalemate"}""", classOf[GameResult]) shouldBe
      GameResult.Draw(DrawReason.Stalemate)

  test("GameResultDeserializer deserializes Draw ThreefoldRepetition"):
    mapper.readValue("""{"type":"draw","reason":"ThreefoldRepetition"}""", classOf[GameResult]) shouldBe
      GameResult.Draw(DrawReason.ThreefoldRepetition)

  test("GameResultDeserializer throws for unknown type"):
    an[Exception] should be thrownBy
      mapper.readValue("""{"type":"unknown"}""", classOf[GameResult])
