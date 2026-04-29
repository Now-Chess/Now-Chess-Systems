package de.nowchess.io.json

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.io.service.config.JacksonConfig
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SquareKeySerializerTest extends AnyFunSuite with Matchers:

  private def mapper: ObjectMapper =
    val m = new ObjectMapper()
    new JacksonConfig().customize(m)
    m

  test("serializes square as algebraic notation"):
    val json = mapper.writeValueAsString(Map(Square(File.E, Rank.R4) -> 1))
    json should include("\"e4\"")

  test("serializes a1 corner"):
    val json = mapper.writeValueAsString(Map(Square(File.A, Rank.R1) -> 1))
    json should include("\"a1\"")

  test("serializes h8 corner"):
    val json = mapper.writeValueAsString(Map(Square(File.H, Rank.R8) -> 1))
    json should include("\"h8\"")

  test("round-trips with SquareKeyDeserializer"):
    val original = Map(Square(File.D, Rank.R5) -> 99)
    val json     = mapper.writeValueAsString(original)
    val result   = mapper.readValue(json, new TypeReference[Map[Square, Int]] {})
    result shouldBe original
