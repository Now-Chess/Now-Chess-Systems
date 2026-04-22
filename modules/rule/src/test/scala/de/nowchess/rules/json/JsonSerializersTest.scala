package de.nowchess.rules.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.move.{MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonSerializersTest extends AnyFunSuite with Matchers:

  private val mapper: ObjectMapper =
    val m   = new ObjectMapper()
    val mod = new SimpleModule()
    m.registerModule(DefaultScalaModule)
    mod.addKeySerializer(classOf[Square], new SquareKeySerializer())
    mod.addKeyDeserializer(classOf[Square], new SquareKeyDeserializer())
    mod.addSerializer(classOf[Square], new SquareSerializer())
    mod.addDeserializer(classOf[Square], new SquareDeserializer())
    mod.addSerializer(classOf[MoveType], new MoveTypeSerializer())
    mod.addDeserializer(classOf[MoveType], new MoveTypeDeserializer())
    m.registerModule(mod)
    m

  private val e4 = Square(File.E, Rank.R4)

  // ── SquareKeySerializer ───────────────────────────────────────────

  test("SquareKeySerializer writes square as map field name"):
    mapper.writeValueAsString(Map(e4 -> "piece")) shouldBe """{"e4":"piece"}"""

  // ── SquareKeyDeserializer ─────────────────────────────────────────

  // scalafix:off DisableSyntax.null
  test("SquareKeyDeserializer returns square for valid key"):
    new SquareKeyDeserializer().deserializeKey("e4", null) shouldBe e4

  test("SquareKeyDeserializer returns null for invalid key"):
    new SquareKeyDeserializer().deserializeKey("z9", null) shouldBe null
  // scalafix:on DisableSyntax.null

  // ── SquareSerializer/Deserializer ─────────────────────────────────

  test("SquareSerializer writes square as string"):
    mapper.writeValueAsString(e4) shouldBe """"e4""""

  test("SquareDeserializer reads valid square string"):
    mapper.readValue(""""e4"""", classOf[Square]) shouldBe e4

  // scalafix:off DisableSyntax.null
  test("SquareDeserializer returns null for invalid square string"):
    mapper.readValue(""""z9"""", classOf[Square]) shouldBe null
  // scalafix:on DisableSyntax.null

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
    mapper.readValue("""{"type":"normal","isCapture":false}""", classOf[MoveType]) shouldBe
      MoveType.Normal(false)

  test("MoveTypeDeserializer deserializes normal capture"):
    mapper.readValue("""{"type":"normal","isCapture":true}""", classOf[MoveType]) shouldBe
      MoveType.Normal(true)

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
