package de.nowchess.ui.utils

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RendererAndUnicodeTest extends AnyFunSuite with Matchers:

  test("unicode returns correct unicode character for all piece types"):
    val pieces = Seq(
      (Piece(Color.White, PieceType.King), "\u2654"),
      (Piece(Color.White, PieceType.Queen), "\u2655"),
      (Piece(Color.White, PieceType.Rook), "\u2656"),
      (Piece(Color.White, PieceType.Bishop), "\u2657"),
      (Piece(Color.White, PieceType.Knight), "\u2658"),
      (Piece(Color.White, PieceType.Pawn), "\u2659"),
      (Piece(Color.Black, PieceType.King), "\u265A"),
      (Piece(Color.Black, PieceType.Queen), "\u265B"),
      (Piece(Color.Black, PieceType.Rook), "\u265C"),
      (Piece(Color.Black, PieceType.Bishop), "\u265D"),
      (Piece(Color.Black, PieceType.Knight), "\u265E"),
      (Piece(Color.Black, PieceType.Pawn), "\u265F"),
    )
    pieces.foreach { (piece, expected) =>
      piece.unicode shouldBe expected
    }

  test("render outputs coordinates ranks ansi escapes and piece glyphs"):
    val board    = Board(Map(Square(File.E, Rank.R4) -> Piece(Color.White, PieceType.Queen)))
    val rendered = Renderer.render(Board(Map.empty))
    val lines    = rendered.trim.split("\\n").toList.map(_.trim)

    lines.head shouldBe "a  b  c  d  e  f  g  h"
    lines.last shouldBe "a  b  c  d  e  f  g  h"
    rendered should include("8")
    rendered should include("1")
    Renderer.render(board) should include("\u2655")
    Renderer.render(board) should include("\u001b[")

  test("render applies black piece color for black pieces"):
    val board    = Board(Map(Square(File.A, Rank.R1) -> Piece(Color.Black, PieceType.King)))
    val rendered = Renderer.render(board)
    rendered should include("\u265A")     // Black king unicode
    rendered should include("\u001b[30m") // ANSI black text color
