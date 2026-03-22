package de.nowchess.chess.controller

import de.nowchess.api.board.{File, Rank, Square}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ParserTest extends AnyFunSuite with Matchers:

  test("parseMove parses valid 'e2e4'"):
    Parser.parseMove("e2e4") shouldBe Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4)))

  test("parseMove is case-insensitive"):
    Parser.parseMove("E2E4") shouldBe Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4)))

  test("parseMove trims leading and trailing whitespace"):
    Parser.parseMove("  e2e4  ") shouldBe Some((Square(File.E, Rank.R2), Square(File.E, Rank.R4)))

  test("parseMove handles corner squares a1h8"):
    Parser.parseMove("a1h8") shouldBe Some((Square(File.A, Rank.R1), Square(File.H, Rank.R8)))

  test("parseMove handles corner squares h8a1"):
    Parser.parseMove("h8a1") shouldBe Some((Square(File.H, Rank.R8), Square(File.A, Rank.R1)))

  test("parseMove returns None for empty string"):
    Parser.parseMove("") shouldBe None

  test("parseMove returns None for input shorter than 4 chars"):
    Parser.parseMove("e2e") shouldBe None

  test("parseMove returns None for input longer than 4 chars"):
    Parser.parseMove("e2e44") shouldBe None

  test("parseMove returns None when from-file is out of range"):
    Parser.parseMove("z2e4") shouldBe None

  test("parseMove returns None when from-rank is out of range"):
    Parser.parseMove("e9e4") shouldBe None

  test("parseMove returns None when to-file is out of range"):
    Parser.parseMove("e2z4") shouldBe None

  test("parseMove returns None when to-rank is out of range"):
    Parser.parseMove("e2e9") shouldBe None
