package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SquareTest extends AnyFunSuite with Matchers:

  test("toString renders algebraic notation for edge and middle squares") {
    Square(File.A, Rank.R1).toString shouldBe "a1"
    Square(File.E, Rank.R4).toString shouldBe "e4"
    Square(File.H, Rank.R8).toString shouldBe "h8"
  }

  test("fromAlgebraic parses valid coordinates including case-insensitive files") {
    val expected = List(
      "a1" -> Square(File.A, Rank.R1),
      "e4" -> Square(File.E, Rank.R4),
      "h8" -> Square(File.H, Rank.R8),
      "E4" -> Square(File.E, Rank.R4)
    )
    expected.foreach { case (raw, sq) =>
      Square.fromAlgebraic(raw) shouldBe Some(sq)
    }
  }

  test("fromAlgebraic rejects malformed coordinates") {
    List("", "e", "e42", "z4", "ex", "e0", "e9").foreach { raw =>
      Square.fromAlgebraic(raw) shouldBe None
    }
  }

  test("offset returns Some in-bounds and None out-of-bounds") {
    Square(File.E, Rank.R4).offset(1, 2) shouldBe Some(Square(File.F, Rank.R6))
    Square(File.A, Rank.R1).offset(-1, 0) shouldBe None
    Square(File.H, Rank.R8).offset(0, 1) shouldBe None
  }

