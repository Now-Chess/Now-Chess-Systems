package de.nowchess.chess.controller

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.chess.controller.Parser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ParserExtendedTest:

  // ── Valid moves ────────────────────────────────────────────────────────

  @Test def parsesAllValidFileLetters(): Unit =
    for fileChar <- 'a' to 'h' do
      val move = s"${fileChar}1${fileChar}2"
      val result = Parser.parseMove(move)
      assertTrue(result.isDefined, s"Should parse valid move $move")

  @Test def parsesAllValidRankNumbers(): Unit =
    for rank <- 1 to 8 do
      val move = s"a${rank}a${if rank < 8 then rank + 1 else rank}"
      val result = Parser.parseMove(move)
      assertTrue(result.isDefined, s"Should parse valid move $move")

  @Test def parsesCornerToCornerMove(): Unit =
    val result = Parser.parseMove("a1h8")
    assertEquals(Some((Square(File.A, Rank.R1), Square(File.H, Rank.R8))), result)

  @Test def parsesCornerToCornerOpposite(): Unit =
    val result = Parser.parseMove("h1a8")
    assertEquals(Some((Square(File.H, Rank.R1), Square(File.A, Rank.R8))), result)

  @Test def parsesSameSquareMove(): Unit =
    val result = Parser.parseMove("a1a1")
    assertEquals(Some((Square(File.A, Rank.R1), Square(File.A, Rank.R1))), result)

  // ── Whitespace handling ────────────────────────────────────────────────

  @Test def parsesWithLeadingWhitespace(): Unit =
    val result = Parser.parseMove("   e2e4")
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), result)

  @Test def parsesWithTrailingWhitespace(): Unit =
    val result = Parser.parseMove("e2e4   ")
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), result)

  @Test def parsesWithLeadingAndTrailingWhitespace(): Unit =
    val result = Parser.parseMove("  e2e4  ")
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), result)

  @Test def parsesWithInternalWhitespaceIsInvalid(): Unit =
    val result = Parser.parseMove("e2 e4")
    assertEquals(None, result)

  // ── Case sensitivity ──────────────────────────────────────────────────

  @Test def parsesUppercaseInput(): Unit =
    val result = Parser.parseMove("E2E4")
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), result)

  @Test def parsesMixedCaseInput(): Unit =
    val result = Parser.parseMove("E2e4")
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), result)

  @Test def parsesLowercaseInput(): Unit =
    val result = Parser.parseMove("e2e4")
    assertEquals(Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4))), result)

  // ── Boundary checks ───────────────────────────────────────────────────

  @Test def rejectsFileBeforeA(): Unit =
    val result = Parser.parseMove("`1a1")
    assertEquals(None, result) // backtick is before 'a'

  @Test def rejectsFileAfterH(): Unit =
    val result = Parser.parseMove("i1a1")
    assertEquals(None, result)

  @Test def rejectsRankZero(): Unit =
    val result = Parser.parseMove("a0a1")
    assertEquals(None, result)

  @Test def rejectsRankNine(): Unit =
    val result = Parser.parseMove("a9a1")
    assertEquals(None, result)

  @Test def rejectsNegativeRank(): Unit =
    val result = Parser.parseMove("a-1a1")
    assertEquals(None, result)

  @Test def acceptsRank1(): Unit =
    val result = Parser.parseMove("a1a2")
    assertEquals(Some((Square(File.A, Rank.R1), Square(File.A, Rank.R2))), result)

  @Test def acceptsRank8(): Unit =
    val result = Parser.parseMove("a8a7")
    assertEquals(Some((Square(File.A, Rank.R8), Square(File.A, Rank.R7))), result)

  // ── Length validation ─────────────────────────────────────────────────

  @Test def rejectsTooShortInput(): Unit =
    assertEquals(None, Parser.parseMove("e2e"))
    assertEquals(None, Parser.parseMove("e2"))
    assertEquals(None, Parser.parseMove("e"))

  @Test def rejectsTooLongInput(): Unit =
    assertEquals(None, Parser.parseMove("e2e4e5"))
    assertEquals(None, Parser.parseMove("e2e4x"))

  @Test def rejectsEmptyString(): Unit =
    assertEquals(None, Parser.parseMove(""))

  @Test def rejectsOnlyWhitespace(): Unit =
    assertEquals(None, Parser.parseMove("   "))

  // ── Invalid character formats ──────────────────────────────────────────

  @Test def rejectsNonAlphanumericFromSquare(): Unit =
    assertEquals(None, Parser.parseMove("!@a1"))
    assertEquals(None, Parser.parseMove("#$a1"))
    assertEquals(None, Parser.parseMove("*.a1"))

  @Test def rejectsNonAlphanumericToSquare(): Unit =
    assertEquals(None, Parser.parseMove("a1!@"))
    assertEquals(None, Parser.parseMove("a1#$"))
    assertEquals(None, Parser.parseMove("a1*."))

  @Test def rejectsNumbers(): Unit =
    assertEquals(None, Parser.parseMove("1234"))
    assertEquals(None, Parser.parseMove("5678"))

  @Test def rejectsAllLetters(): Unit =
    assertEquals(None, Parser.parseMove("abcd"))
    assertEquals(None, Parser.parseMove("hgfe"))

  // ── File and rank order ────────────────────────────────────────────────

  @Test def parsesValidFromSquareToSquareFormat(): Unit =
    val result = Parser.parseMove("a1a2")
    assertTrue(result.isDefined)
    val (from, to) = result.get
    assertEquals(Square(File.A, Rank.R1), from)
    assertEquals(Square(File.A, Rank.R2), to)

  @Test def parsesKnightMoveG1F3(): Unit =
    val result = Parser.parseMove("g1f3")
    assertEquals(Some((Square(File.G, Rank.R1), Square(File.F, Rank.R3))), result)

  @Test def parsesCastlingRookMoveH1F1(): Unit =
    val result = Parser.parseMove("h1f1")
    assertEquals(Some((Square(File.H, Rank.R1), Square(File.F, Rank.R1))), result)

  // ── Special inputs ──────────────────────────────────────────────────────

  @Test def rejectsQuitString(): Unit =
    assertEquals(None, Parser.parseMove("quit"))

  @Test def rejectsQString(): Unit =
    assertEquals(None, Parser.parseMove("q"))

  @Test def rejectsRandomText(): Unit =
    assertEquals(None, Parser.parseMove("hello"))
    assertEquals(None, Parser.parseMove("board"))

  @Test def rejectsSpecialChars(): Unit =
    assertEquals(None, Parser.parseMove("e2-e4"))
    assertEquals(None, Parser.parseMove("e2xe4"))
    assertEquals(None, Parser.parseMove("e2/e4"))

  // ── Very long strings ──────────────────────────────────────────────────

  @Test def rejectsVeryLongInput(): Unit =
    val longString = "a" * 1000 + "1a1"
    assertEquals(None, Parser.parseMove(longString))

  @Test def rejectsVeryLongOnlyAfterTrimming(): Unit =
    val longString = "   " + ("a" * 1000)
    assertEquals(None, Parser.parseMove(longString))

  // ── Exact length boundary ──────────────────────────────────────────────

  @Test def acceptsExactlyFourCharacters(): Unit =
    val result = Parser.parseMove("a1b2")
    assertTrue(result.isDefined)

  @Test def rejectsThreeCharacters(): Unit =
    val result = Parser.parseMove("a1b")
    assertEquals(None, result)

  @Test def rejectsFiveCharacters(): Unit =
    val result = Parser.parseMove("a1b2c")
    assertEquals(None, result)

  // ── Consistent parsing ────────────────────────────────────────────────

  @Test def parsesSameMoveMultipleTimes(): Unit =
    val move = "e2e4"
    val result1 = Parser.parseMove(move)
    val result2 = Parser.parseMove(move)
    assertEquals(result1, result2)

  @Test def parsesComprehensiveSquareSet(): Unit =
    val squares = for
      f <- 'a' to 'h'
      r <- '1' to '8'
    yield s"${f}${r}${f}${r}"

    for move <- squares do
      val result = Parser.parseMove(move)
      assertTrue(result.isDefined, s"Should parse $move")
