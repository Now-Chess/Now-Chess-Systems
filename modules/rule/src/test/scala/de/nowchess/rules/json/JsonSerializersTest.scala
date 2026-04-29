package de.nowchess.rules.json

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.move.MoveType
import de.nowchess.rules.config.JacksonConfig
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonSerializersTest extends AnyFunSuite with Matchers:

  private val mapper: ObjectMapper =
    val m = new ObjectMapper()
    new JacksonConfig().customize(m)
    m

  test("customize enables Option serialization via DefaultScalaModule"):
    mapper.writeValueAsString(None) shouldBe "null"
    mapper.writeValueAsString(Some("hello")) shouldBe """"hello""""

  test("customize registers SquareSerializer"):
    mapper.writeValueAsString(Square(File.E, Rank.R4)) shouldBe """"e4""""

  test("customize registers SquareDeserializer"):
    mapper.readValue(""""e4"""", classOf[Square]) shouldBe Square(File.E, Rank.R4)

  test("customize registers MoveTypeSerializer"):
    mapper.writeValueAsString(MoveType.CastleKingside) shouldBe """{"type":"castleKingside"}"""

  test("customize registers MoveTypeDeserializer"):
    mapper.readValue("""{"type":"enPassant"}""", classOf[MoveType]) shouldBe MoveType.EnPassant
