package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ColorTest extends AnyFunSuite with Matchers:

  test("Color values expose opposite and label consistently"):
    val cases = List(
      (Color.White, Color.Black, "White"),
      (Color.Black, Color.White, "Black"),
    )

    cases.foreach { (color, opposite, label) =>
      color.opposite shouldBe opposite
      color.label shouldBe label
    }
