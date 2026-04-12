package de.nowchess.io.pgn

import de.nowchess.api.board.*
import de.nowchess.api.move.MoveType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PgnValidatorTest extends AnyFunSuite with Matchers:

  test("validatePgn accepts valid games including castling and result tokens"):
    val pgn =
      """[Event "Test"]

1. e4 e5 2. Nf3 Nc6
"""
    val valid = PgnParser.validatePgn(pgn)
    valid.isRight shouldBe true
    valid.toOption.get.moves.length shouldBe 4
    valid.toOption.get.moves.head.from shouldBe Square(File.E, Rank.R2)

    val withResult = PgnParser.validatePgn("""[Event "Test"]

1. e4 e5 1-0
""")
    withResult.map(_.moves.length) shouldBe Right(2)

    val kCastle = PgnParser.validatePgn("""[Event "Test"]

1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O
""")
    kCastle.map(_.moves.last.moveType) shouldBe Right(MoveType.CastleKingside)

    val qCastle = PgnParser.validatePgn("""[Event "Test"]

1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7 5. O-O-O
""")
    qCastle.map(_.moves.last.moveType) shouldBe Right(MoveType.CastleQueenside)

  test("validatePgn rejects impossible illegal and garbage tokens"):
    PgnParser
      .validatePgn("""[Event "Test"]

1. Qd4
""").isLeft shouldBe true

    PgnParser
      .validatePgn("""[Event "Test"]

1. O-O
""").isLeft shouldBe true

    PgnParser
      .validatePgn("""[Event "Test"]

1. e4 GARBAGE e5
""").isLeft shouldBe true

  test("validatePgn accepts empty move text and minimal valid header"):
    PgnParser.validatePgn("[Event \"Test\"]\n[White \"A\"]\n[Black \"B\"]\n").map(_.moves) shouldBe Right(List.empty)
    PgnParser.validatePgn("[Event \"T\"]\n\n1. e4").isRight shouldBe true
