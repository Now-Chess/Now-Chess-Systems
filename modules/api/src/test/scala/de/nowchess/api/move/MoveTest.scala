package de.nowchess.api.move

import de.nowchess.api.board.{File, Rank, Square}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoveTest extends AnyFunSuite with Matchers:

  private val e2 = Square(File.E, Rank.R2)
  private val e4 = Square(File.E, Rank.R4)

  test("Move defaults to Normal and keeps from/to squares") {
    val m = Move(e2, e4)
    m.from shouldBe e2
    m.to shouldBe e4
    m.moveType shouldBe MoveType.Normal()
  }

  test("Move accepts all supported move types") {
    val moveTypes = List(
      MoveType.Normal(isCapture = true),
      MoveType.CastleKingside,
      MoveType.CastleQueenside,
      MoveType.EnPassant,
      MoveType.Promotion(PromotionPiece.Queen),
      MoveType.Promotion(PromotionPiece.Rook),
      MoveType.Promotion(PromotionPiece.Bishop),
      MoveType.Promotion(PromotionPiece.Knight),
    )

    moveTypes.foreach { moveType =>
      Move(e2, e4, moveType).moveType shouldBe moveType
    }
  }
