package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SquareTest extends AnyFunSuite with Matchers:

  test("Square.toString produces lowercase file and rank number") {
    Square(File.E, Rank.R4).toString shouldBe "e4"
  }

  test("Square.toString for a1") {
    Square(File.A, Rank.R1).toString shouldBe "a1"
  }

  test("Square.toString for h8") {
    Square(File.H, Rank.R8).toString shouldBe "h8"
  }

  test("fromAlgebraic parses valid square e4") {
    Square.fromAlgebraic("e4") shouldBe Some(Square(File.E, Rank.R4))
  }

  test("fromAlgebraic parses valid square a1") {
    Square.fromAlgebraic("a1") shouldBe Some(Square(File.A, Rank.R1))
  }

  test("fromAlgebraic parses valid square h8") {
    Square.fromAlgebraic("h8") shouldBe Some(Square(File.H, Rank.R8))
  }

  test("fromAlgebraic is case-insensitive for file") {
    Square.fromAlgebraic("E4") shouldBe Some(Square(File.E, Rank.R4))
  }

  test("fromAlgebraic returns None for empty string") {
    Square.fromAlgebraic("") shouldBe None
  }

  test("fromAlgebraic returns None for string too short") {
    Square.fromAlgebraic("e") shouldBe None
  }

  test("fromAlgebraic returns None for string too long") {
    Square.fromAlgebraic("e42") shouldBe None
  }

  test("fromAlgebraic returns None for invalid file character") {
    Square.fromAlgebraic("z4") shouldBe None
  }

  test("fromAlgebraic returns None for non-digit rank") {
    Square.fromAlgebraic("ex") shouldBe None
  }

  test("fromAlgebraic returns None for rank 0") {
    Square.fromAlgebraic("e0") shouldBe None
  }

  test("fromAlgebraic returns None for rank 9") {
    Square.fromAlgebraic("e9") shouldBe None
  }
