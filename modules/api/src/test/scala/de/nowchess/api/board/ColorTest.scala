package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ColorTest extends AnyFunSuite with Matchers:

  test("White.opposite returns Black") {
    Color.White.opposite shouldBe Color.Black
  }

  test("Black.opposite returns White") {
    Color.Black.opposite shouldBe Color.White
  }

  test("White.label returns 'White'") {
    Color.White.label shouldBe "White"
  }

  test("Black.label returns 'Black'") {
    Color.Black.label shouldBe "Black"
  }
