package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PieceTypeTest extends AnyFunSuite with Matchers:

  test("Pawn.label returns 'Pawn'") {
    PieceType.Pawn.label shouldBe "Pawn"
  }

  test("Knight.label returns 'Knight'") {
    PieceType.Knight.label shouldBe "Knight"
  }

  test("Bishop.label returns 'Bishop'") {
    PieceType.Bishop.label shouldBe "Bishop"
  }

  test("Rook.label returns 'Rook'") {
    PieceType.Rook.label shouldBe "Rook"
  }

  test("Queen.label returns 'Queen'") {
    PieceType.Queen.label shouldBe "Queen"
  }

  test("King.label returns 'King'") {
    PieceType.King.label shouldBe "King"
  }
