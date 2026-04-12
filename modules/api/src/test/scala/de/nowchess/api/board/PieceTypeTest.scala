package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PieceTypeTest extends AnyFunSuite with Matchers:

  test("PieceType values expose the expected labels"):
    val expectedLabels = List(
      PieceType.Pawn   -> "Pawn",
      PieceType.Knight -> "Knight",
      PieceType.Bishop -> "Bishop",
      PieceType.Rook   -> "Rook",
      PieceType.Queen  -> "Queen",
      PieceType.King   -> "King",
    )

    expectedLabels.foreach { (pieceType, expectedLabel) =>
      pieceType.label shouldBe expectedLabel
    }
