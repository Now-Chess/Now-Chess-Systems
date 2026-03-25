package de.nowchess.api.move

import de.nowchess.api.board.{File, Rank, Square}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoveTest extends AnyFunSuite with Matchers:

  private val e2 = Square(File.E, Rank.R2)
  private val e4 = Square(File.E, Rank.R4)

  test("Move defaults moveType to Normal") {
    val m = Move(e2, e4)
    m.moveType shouldBe MoveType.Normal
  }

  test("Move stores from and to squares") {
    val m = Move(e2, e4)
    m.from shouldBe e2
    m.to   shouldBe e4
  }

  test("Move with CastleKingside moveType") {
    val m = Move(e2, e4, MoveType.CastleKingside)
    m.moveType shouldBe MoveType.CastleKingside
  }

  test("Move with CastleQueenside moveType") {
    val m = Move(e2, e4, MoveType.CastleQueenside)
    m.moveType shouldBe MoveType.CastleQueenside
  }

  test("Move with EnPassant moveType") {
    val m = Move(e2, e4, MoveType.EnPassant)
    m.moveType shouldBe MoveType.EnPassant
  }

  test("Move with Promotion to Queen") {
    val m = Move(e2, e4, MoveType.Promotion(PromotionPiece.Queen))
    m.moveType shouldBe MoveType.Promotion(PromotionPiece.Queen)
  }

  test("Move with Promotion to Knight") {
    val m = Move(e2, e4, MoveType.Promotion(PromotionPiece.Knight))
    m.moveType shouldBe MoveType.Promotion(PromotionPiece.Knight)
  }

  test("Move with Promotion to Bishop") {
    val m = Move(e2, e4, MoveType.Promotion(PromotionPiece.Bishop))
    m.moveType shouldBe MoveType.Promotion(PromotionPiece.Bishop)
  }

  test("Move with Promotion to Rook") {
    val m = Move(e2, e4, MoveType.Promotion(PromotionPiece.Rook))
    m.moveType shouldBe MoveType.Promotion(PromotionPiece.Rook)
  }
