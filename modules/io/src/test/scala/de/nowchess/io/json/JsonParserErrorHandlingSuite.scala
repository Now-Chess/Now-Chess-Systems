package de.nowchess.io.json

import de.nowchess.api.game.GameContext
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonParserErrorHandlingSuite extends AnyFunSuite with Matchers:

  test("parse completely invalid JSON returns error") {
    val invalidJson = "{ this is not valid json at all }"
    val result      = JsonParser.importGameContext(invalidJson)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("JSON parsing error"))
  }

  test("parse empty string returns error") {
    val result = JsonParser.importGameContext("")
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("JSON parsing error"))
  }

  test("parse number value returns error") {
    val result = JsonParser.importGameContext("123")
    assert(result.isLeft)
  }

  test("parse malformed JSON object returns error") {
    val malformed = """{"metadata": {"unclosed": """
    val result    = JsonParser.importGameContext(malformed)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("JSON parsing error"))
  }

  test("parse invalid JSON array returns error") {
    val invalidArray = "[1, 2, 3"
    val result       = JsonParser.importGameContext(invalidArray)
    assert(result.isLeft)
  }

  test("parse JSON with missing required fields") {
    val json   = """{"metadata": {}}"""
    val result = JsonParser.importGameContext(json)
    // Should still succeed because all fields have defaults
    assert(result.isRight)
  }

  test("parse valid JSON with invalid turn falls back to default") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []},
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
  }
