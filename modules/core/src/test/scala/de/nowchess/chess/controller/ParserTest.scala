package de.nowchess.chess.controller

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.move.PromotionPiece
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ParserTest extends AnyFunSuite with Matchers:

  test("parseMove parses valid 'e2e4'"):
    Parser.parseMove("e2e4") shouldBe Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))

  test("parseMove is case-insensitive"):
    Parser.parseMove("E2E4") shouldBe Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))

  test("parseMove trims leading and trailing whitespace"):
    Parser.parseMove("  e2e4  ") shouldBe Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))

  test("parseMove handles corner squares a1h8"):
    Parser.parseMove("a1h8") shouldBe Some((Square(File.A, Rank.R1), Square(File.H, Rank.R8), None))

  test("parseMove handles corner squares h8a1"):
    Parser.parseMove("h8a1") shouldBe Some((Square(File.H, Rank.R8), Square(File.A, Rank.R1), None))

  test("parseMove returns None for empty string"):
    Parser.parseMove("") shouldBe None

  test("parseMove returns None for input shorter than 4 chars"):
    Parser.parseMove("e2e") shouldBe None

  test("parseMove returns None for input longer than 5 chars"):
    Parser.parseMove("e2e4qq") shouldBe None

  test("parseMove returns None when from-file is out of range"):
    Parser.parseMove("z2e4") shouldBe None

  test("parseMove returns None when from-rank is out of range"):
    Parser.parseMove("e9e4") shouldBe None

  test("parseMove returns None when to-file is out of range"):
    Parser.parseMove("e2z4") shouldBe None

  test("parseMove returns None when to-rank is out of range"):
    Parser.parseMove("e2e9") shouldBe None

  test("parseMove parses queen promotion 'e7e8q'"):
    Parser.parseMove("e7e8q") shouldBe Some(
      (Square(File.E, Rank.R7), Square(File.E, Rank.R8), Some(PromotionPiece.Queen)),
    )

  test("parseMove parses rook promotion 'a7a8r'"):
    Parser.parseMove("a7a8r") shouldBe Some(
      (Square(File.A, Rank.R7), Square(File.A, Rank.R8), Some(PromotionPiece.Rook)),
    )

  test("parseMove parses bishop promotion 'e7e8b'"):
    Parser.parseMove("e7e8b") shouldBe Some(
      (Square(File.E, Rank.R7), Square(File.E, Rank.R8), Some(PromotionPiece.Bishop)),
    )

  test("parseMove parses knight promotion 'e7e8n'"):
    Parser.parseMove("e7e8n") shouldBe Some(
      (Square(File.E, Rank.R7), Square(File.E, Rank.R8), Some(PromotionPiece.Knight)),
    )

  test("parseMove returns None for 5-char input with invalid promotion char"):
    Parser.parseMove("e7e8x") shouldBe None

  test("parseMove parses black promotion 'e2e1q'"):
    Parser.parseMove("e2e1q") shouldBe Some(
      (Square(File.E, Rank.R2), Square(File.E, Rank.R1), Some(PromotionPiece.Queen)),
    )
