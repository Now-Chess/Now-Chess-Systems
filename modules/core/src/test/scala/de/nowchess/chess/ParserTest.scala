package de.nowchess.chess

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ParserTest:

  @Test def parsesValidMove(): Unit =
    assertEquals(Some((Square(4, 1), Square(4, 3))), Parser.parseMove("e2e4"))

  @Test def parsesKnightMove(): Unit =
    assertEquals(Some((Square(6, 0), Square(5, 2))), Parser.parseMove("g1f3"))

  @Test def ignoresExtraWhitespace(): Unit =
    assertEquals(Some((Square(4, 1), Square(4, 3))), Parser.parseMove("  e2e4  "))

  @Test def rejectsShortInput(): Unit =
    assertEquals(None, Parser.parseMove("e2e"))

  @Test def rejectsEmptyInput(): Unit =
    assertEquals(None, Parser.parseMove(""))

  @Test def rejectsOutOfBoundsFile(): Unit =
    assertEquals(None, Parser.parseMove("z2a4"))

  @Test def rejectsOutOfBoundsRank(): Unit =
    assertEquals(None, Parser.parseMove("e9e4"))

  @Test def parsesUppercaseAsInvalid(): Unit =
    // uppercase files are out of range after toLowerCase — stays lowercase internally
    assertEquals(Some((Square(4, 1), Square(4, 3))), Parser.parseMove("E2E4"))
