package de.nowchess.chess

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.chess.view.unicode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ModelTest:

  @Test def colorOpposite(): Unit =
    assertEquals(Color.Black, Color.White.opposite)
    assertEquals(Color.White, Color.Black.opposite)

  @Test def squareLabel(): Unit =
    assertEquals("a1", Square(File.A, Rank.R1).toString)
    assertEquals("e4", Square(File.E, Rank.R4).toString)
    assertEquals("h8", Square(File.H, Rank.R8).toString)

  @Test def pieceUnicode(): Unit =
    assertEquals("\u2654", Piece(Color.White, PieceType.King).unicode)
    assertEquals("\u265A", Piece(Color.Black, PieceType.King).unicode)
    assertEquals("\u2659", Piece(Color.White, PieceType.Pawn).unicode)
    assertEquals("\u265F", Piece(Color.Black, PieceType.Pawn).unicode)

  @Test def initialBoardHas32Pieces(): Unit =
    assertEquals(32, Board.initial.pieces.size)

  @Test def initialWhiteKingOnE1(): Unit =
    val e1 = Square(File.E, Rank.R1)
    assertEquals(Some(Piece(Color.White, PieceType.King)), Board.initial.pieceAt(e1))

  @Test def initialBlackQueenOnD8(): Unit =
    val d8 = Square(File.D, Rank.R8)
    assertEquals(Some(Piece(Color.Black, PieceType.Queen)), Board.initial.pieceAt(d8))

  @Test def initialWhitePawnsOnRank2(): Unit =
    for file <- File.values do
      val sq = Square(file, Rank.R2)
      assertEquals(Some(Piece(Color.White, PieceType.Pawn)), Board.initial.pieceAt(sq))

  @Test def withMoveMovesAndLeavesOriginEmpty(): Unit =
    val e2 = Square(File.E, Rank.R2)
    val e4 = Square(File.E, Rank.R4)
    val (newBoard, captured) = Board.initial.withMove(e2, e4)
    assertEquals(None, newBoard.pieceAt(e2))
    assertEquals(Some(Piece(Color.White, PieceType.Pawn)), newBoard.pieceAt(e4))
    assertEquals(None, captured)

  @Test def withMoveCaptureReturnsCapture(): Unit =
    val e2 = Square(File.E, Rank.R2)
    val e4 = Square(File.E, Rank.R4)
    val (board2, _) = Board.initial.withMove(e2, e4)
    val d7 = Square(File.D, Rank.R7)
    val d4 = Square(File.D, Rank.R4)
    val (board3, _) = board2.withMove(d7, d4)
    // White pawn on e4 captures black pawn on d4 (diagonal — no legality check)
    val (board4, cap) = board3.withMove(e4, d4)
    assertEquals(Some(Piece(Color.Black, PieceType.Pawn)), cap)
    assertEquals(Some(Piece(Color.White, PieceType.Pawn)), board4.pieceAt(d4))
