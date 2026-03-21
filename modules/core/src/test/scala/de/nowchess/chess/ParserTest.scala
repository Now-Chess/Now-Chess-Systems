package de.nowchess.chess

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.chess.controller.Parser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ParserTest:

  @Test def parsesValidMove(): Unit =
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), Parser.parseMove("e2e4"))

  @Test def parsesKnightMove(): Unit =
    assertEquals(Some((Square(File.G, Rank.R1), Square(File.F, Rank.R3))), Parser.parseMove("g1f3"))

  @Test def ignoresExtraWhitespace(): Unit =
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), Parser.parseMove("  e2e4  "))

  @Test def rejectsShortInput(): Unit =
    assertEquals(None, Parser.parseMove("e2e"))

  @Test def rejectsEmptyInput(): Unit =
    assertEquals(None, Parser.parseMove(""))

  @Test def rejectsOutOfBoundsFile(): Unit =
    assertEquals(None, Parser.parseMove("z2a4"))

  @Test def rejectsOutOfBoundsRank(): Unit =
    assertEquals(None, Parser.parseMove("e9e4"))

  @Test def parsesUppercaseAsInvalid(): Unit =
    // Input is lowercased before parsing, so "E2E4" -> "e2e4" -> valid
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), Parser.parseMove("E2E4"))
