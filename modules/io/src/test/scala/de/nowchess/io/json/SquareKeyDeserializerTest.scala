package de.nowchess.io.json

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.io.service.config.JacksonConfig
import de.nowchess.json.SquareKeyDeserializer
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SquareKeyDeserializerTest extends AnyFunSuite with Matchers:

  private def mapper: ObjectMapper =
    val m = new ObjectMapper()
    new JacksonConfig().customize(m)
    m

  private def readMap(json: String): Map[Square, Int] =
    mapper.readValue(json, new TypeReference[Map[Square, Int]] {})

  test("deserializes valid algebraic key"):
    val result = readMap("""{"e4":1}""")
    result(Square(File.E, Rank.R4)) shouldBe 1

  test("deserializes a1 corner"):
    val result = readMap("""{"a1":1}""")
    result(Square(File.A, Rank.R1)) shouldBe 1

  test("deserializes h8 corner"):
    val result = readMap("""{"h8":1}""")
    result(Square(File.H, Rank.R8)) shouldBe 1

  test("deserializes multiple squares"):
    val result = readMap("""{"a1":1,"h8":2,"e4":3}""")
    result(Square(File.A, Rank.R1)) shouldBe 1
    result(Square(File.H, Rank.R8)) shouldBe 2
    result(Square(File.E, Rank.R4)) shouldBe 3

  // scalafix:off DisableSyntax.null
  test("deserializeKey returns null for invalid square"):
    new SquareKeyDeserializer().deserializeKey("invalid", null) shouldBe null

  test("deserializeKey returns null for wrong-length key"):
    new SquareKeyDeserializer().deserializeKey("e44", null) shouldBe null

  test("deserializeKey returns null for bad file"):
    new SquareKeyDeserializer().deserializeKey("z4", null) shouldBe null

  test("deserializeKey returns null for bad rank"):
    new SquareKeyDeserializer().deserializeKey("e9", null) shouldBe null
  // scalafix:on DisableSyntax.null
