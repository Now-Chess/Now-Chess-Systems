package de.nowchess.chess.view

import de.nowchess.api.board.Piece
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PieceUnicodeTest extends AnyFunSuite with Matchers:

  test("White King maps to ♔"):
    Piece.WhiteKing.unicode shouldBe "\u2654"

  test("White Queen maps to ♕"):
    Piece.WhiteQueen.unicode shouldBe "\u2655"

  test("White Rook maps to ♖"):
    Piece.WhiteRook.unicode shouldBe "\u2656"

  test("White Bishop maps to ♗"):
    Piece.WhiteBishop.unicode shouldBe "\u2657"

  test("White Knight maps to ♘"):
    Piece.WhiteKnight.unicode shouldBe "\u2658"

  test("White Pawn maps to ♙"):
    Piece.WhitePawn.unicode shouldBe "\u2659"

  test("Black King maps to ♚"):
    Piece.BlackKing.unicode shouldBe "\u265A"

  test("Black Queen maps to ♛"):
    Piece.BlackQueen.unicode shouldBe "\u265B"

  test("Black Rook maps to ♜"):
    Piece.BlackRook.unicode shouldBe "\u265C"

  test("Black Bishop maps to ♝"):
    Piece.BlackBishop.unicode shouldBe "\u265D"

  test("Black Knight maps to ♞"):
    Piece.BlackKnight.unicode shouldBe "\u265E"

  test("Black Pawn maps to ♟"):
    Piece.BlackPawn.unicode shouldBe "\u265F"
