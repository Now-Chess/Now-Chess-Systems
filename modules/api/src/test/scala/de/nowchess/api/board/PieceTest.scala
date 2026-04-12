package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PieceTest extends AnyFunSuite with Matchers:

  test("Piece holds color and pieceType") {
    val p = Piece(Color.White, PieceType.Queen)
    p.color shouldBe Color.White
    p.pieceType shouldBe PieceType.Queen
  }

  test("all convenience constants map to expected color and piece type") {
    val expected = List(
      Piece.WhitePawn   -> Piece(Color.White, PieceType.Pawn),
      Piece.WhiteKnight -> Piece(Color.White, PieceType.Knight),
      Piece.WhiteBishop -> Piece(Color.White, PieceType.Bishop),
      Piece.WhiteRook   -> Piece(Color.White, PieceType.Rook),
      Piece.WhiteQueen  -> Piece(Color.White, PieceType.Queen),
      Piece.WhiteKing   -> Piece(Color.White, PieceType.King),
      Piece.BlackPawn   -> Piece(Color.Black, PieceType.Pawn),
      Piece.BlackKnight -> Piece(Color.Black, PieceType.Knight),
      Piece.BlackBishop -> Piece(Color.Black, PieceType.Bishop),
      Piece.BlackRook   -> Piece(Color.Black, PieceType.Rook),
      Piece.BlackQueen  -> Piece(Color.Black, PieceType.Queen),
      Piece.BlackKing   -> Piece(Color.Black, PieceType.King),
    )

    expected.foreach { case (actual, wanted) =>
      actual shouldBe wanted
    }
  }
