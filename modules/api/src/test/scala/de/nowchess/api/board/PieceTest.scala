package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PieceTest extends AnyFunSuite with Matchers:

  test("Piece holds color and pieceType") {
    val p = Piece(Color.White, PieceType.Queen)
    p.color    shouldBe Color.White
    p.pieceType shouldBe PieceType.Queen
  }

  test("WhitePawn convenience constant") {
    Piece.WhitePawn shouldBe Piece(Color.White, PieceType.Pawn)
  }

  test("WhiteKnight convenience constant") {
    Piece.WhiteKnight shouldBe Piece(Color.White, PieceType.Knight)
  }

  test("WhiteBishop convenience constant") {
    Piece.WhiteBishop shouldBe Piece(Color.White, PieceType.Bishop)
  }

  test("WhiteRook convenience constant") {
    Piece.WhiteRook shouldBe Piece(Color.White, PieceType.Rook)
  }

  test("WhiteQueen convenience constant") {
    Piece.WhiteQueen shouldBe Piece(Color.White, PieceType.Queen)
  }

  test("WhiteKing convenience constant") {
    Piece.WhiteKing shouldBe Piece(Color.White, PieceType.King)
  }

  test("BlackPawn convenience constant") {
    Piece.BlackPawn shouldBe Piece(Color.Black, PieceType.Pawn)
  }

  test("BlackKnight convenience constant") {
    Piece.BlackKnight shouldBe Piece(Color.Black, PieceType.Knight)
  }

  test("BlackBishop convenience constant") {
    Piece.BlackBishop shouldBe Piece(Color.Black, PieceType.Bishop)
  }

  test("BlackRook convenience constant") {
    Piece.BlackRook shouldBe Piece(Color.Black, PieceType.Rook)
  }

  test("BlackQueen convenience constant") {
    Piece.BlackQueen shouldBe Piece(Color.Black, PieceType.Queen)
  }

  test("BlackKing convenience constant") {
    Piece.BlackKing shouldBe Piece(Color.Black, PieceType.King)
  }
