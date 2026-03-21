package de.nowchess.chess.model

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.chess.logic.MoveValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MoveValidatorTest:

  // ── helpers ────────────────────────────────────────────────────────────────

  private def sq(file: File, rank: Rank): Square = Square(file, rank)

  /** Build a board with exactly the given pieces. */
  private def boardOf(pieces: (Square, Piece)*): Board =
    Board(pieces.toMap)

  // ── Pawn ───────────────────────────────────────────────────────────────────

  @Test def whitePawnForwardOneSquare(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R2), sq(File.E, Rank.R3)))

  @Test def whitePawnDoublePushFromStartingRank(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R2), sq(File.E, Rank.R4)))

  @Test def whitePawnBlockedDoublePush(): Unit =
    // Piece on e3 blocks the double push
    val board = boardOf(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R3) -> Piece.BlackPawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R2), sq(File.E, Rank.R4)))

  @Test def whitePawnCannotPushForwardOntoOccupiedSquare(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R3) -> Piece.BlackPawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R2), sq(File.E, Rank.R3)))

  @Test def whitePawnDiagonalCaptureEnemy(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R4), sq(File.D, Rank.R5)))

  @Test def whitePawnCannotCaptureDiagonallyWithoutEnemy(): Unit =
    val board = boardOf(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R4), sq(File.D, Rank.R5)))

  @Test def whitePawnCannotCaptureDiagonalOwnPiece(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.WhiteKnight
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R4), sq(File.D, Rank.R5)))

  @Test def blackPawnForwardOneSquare(): Unit =
    val board = boardOf(sq(File.E, Rank.R7) -> Piece.BlackPawn)
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R7), sq(File.E, Rank.R6)))

  @Test def blackPawnDoublePushFromStartingRank(): Unit =
    val board = boardOf(sq(File.E, Rank.R7) -> Piece.BlackPawn)
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R7), sq(File.E, Rank.R5)))

  // ── Knight ─────────────────────────────────────────────────────────────────

  @Test def knightValidLShape(): Unit =
    val board = boardOf(sq(File.G, Rank.R1) -> Piece.WhiteKnight)
    assertTrue(MoveValidator.isLegal(board, sq(File.G, Rank.R1), sq(File.F, Rank.R3)))
    assertTrue(MoveValidator.isLegal(board, sq(File.G, Rank.R1), sq(File.H, Rank.R3)))

  @Test def knightJumpsOverPieces(): Unit =
    // Surround the knight with own pieces — it should still reach its L-targets
    val board = boardOf(
      sq(File.G, Rank.R1) -> Piece.WhiteKnight,
      sq(File.G, Rank.R2) -> Piece.WhitePawn,
      sq(File.F, Rank.R1) -> Piece.WhitePawn,
      sq(File.H, Rank.R1) -> Piece.WhitePawn,
      sq(File.F, Rank.R2) -> Piece.WhitePawn,
      sq(File.H, Rank.R2) -> Piece.WhitePawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.G, Rank.R1), sq(File.F, Rank.R3)))
    assertTrue(MoveValidator.isLegal(board, sq(File.G, Rank.R1), sq(File.H, Rank.R3)))

  @Test def knightCannotLandOnOwnPiece(): Unit =
    val board = boardOf(
      sq(File.G, Rank.R1) -> Piece.WhiteKnight,
      sq(File.F, Rank.R3) -> Piece.WhitePawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.G, Rank.R1), sq(File.F, Rank.R3)))

  @Test def knightCanCaptureEnemy(): Unit =
    val board = boardOf(
      sq(File.G, Rank.R1) -> Piece.WhiteKnight,
      sq(File.F, Rank.R3) -> Piece.BlackPawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.G, Rank.R1), sq(File.F, Rank.R3)))

  // ── Bishop ─────────────────────────────────────────────────────────────────

  @Test def bishopDiagonalSlide(): Unit =
    val board = boardOf(sq(File.C, Rank.R1) -> Piece.WhiteBishop)
    assertTrue(MoveValidator.isLegal(board, sq(File.C, Rank.R1), sq(File.F, Rank.R4)))
    assertTrue(MoveValidator.isLegal(board, sq(File.C, Rank.R1), sq(File.A, Rank.R3)))

  @Test def bishopBlockedByOwnPiece(): Unit =
    val board = boardOf(
      sq(File.C, Rank.R1) -> Piece.WhiteBishop,
      sq(File.E, Rank.R3) -> Piece.WhitePawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.C, Rank.R1), sq(File.F, Rank.R4)))

  @Test def bishopCapturesFirstEnemy(): Unit =
    val board = boardOf(
      sq(File.C, Rank.R1) -> Piece.WhiteBishop,
      sq(File.E, Rank.R3) -> Piece.BlackPawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.C, Rank.R1), sq(File.E, Rank.R3)))
    // Cannot reach beyond the captured piece
    assertFalse(MoveValidator.isLegal(board, sq(File.C, Rank.R1), sq(File.F, Rank.R4)))

  // ── Rook ───────────────────────────────────────────────────────────────────

  @Test def rookOrthogonalSlide(): Unit =
    val board = boardOf(sq(File.A, Rank.R1) -> Piece.WhiteRook)
    assertTrue(MoveValidator.isLegal(board, sq(File.A, Rank.R1), sq(File.A, Rank.R8)))
    assertTrue(MoveValidator.isLegal(board, sq(File.A, Rank.R1), sq(File.H, Rank.R1)))

  @Test def rookBlockedByOwnPiece(): Unit =
    val board = boardOf(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.A, Rank.R4) -> Piece.WhitePawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.A, Rank.R1), sq(File.A, Rank.R8)))
    // Can still reach squares before the blocker
    assertTrue(MoveValidator.isLegal(board, sq(File.A, Rank.R1), sq(File.A, Rank.R3)))

  @Test def rookCapturesFirstEnemy(): Unit =
    val board = boardOf(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.A, Rank.R4) -> Piece.BlackPawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.A, Rank.R1), sq(File.A, Rank.R4)))
    assertFalse(MoveValidator.isLegal(board, sq(File.A, Rank.R1), sq(File.A, Rank.R8)))

  // ── Queen ──────────────────────────────────────────────────────────────────

  @Test def queenCombinesRookAndBishop(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteQueen)
    // Orthogonal
    assertTrue(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.D, Rank.R8)))
    assertTrue(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.H, Rank.R4)))
    // Diagonal
    assertTrue(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.G, Rank.R7)))
    assertTrue(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.A, Rank.R1)))

  @Test def queenBlockedByOwnPiece(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteQueen,
      sq(File.D, Rank.R6) -> Piece.WhitePawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.D, Rank.R8)))

  // ── King ───────────────────────────────────────────────────────────────────

  @Test def kingMovesOneSquareInEachDirection(): Unit =
    val board = boardOf(sq(File.E, Rank.R4) -> Piece.WhiteKing)
    val expected = Set(
      sq(File.E, Rank.R5), sq(File.E, Rank.R3),
      sq(File.D, Rank.R4), sq(File.F, Rank.R4),
      sq(File.D, Rank.R5), sq(File.F, Rank.R5),
      sq(File.D, Rank.R3), sq(File.F, Rank.R3)
    )
    assertEquals(expected, MoveValidator.legalTargets(board, sq(File.E, Rank.R4)))

  @Test def kingCannotLandOnOwnPiece(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.E, Rank.R2) -> Piece.WhitePawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R1), sq(File.E, Rank.R2)))

  @Test def kingCanCaptureEnemy(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.E, Rank.R2) -> Piece.BlackPawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R1), sq(File.E, Rank.R2)))

  // ── Empty square ───────────────────────────────────────────────────────────

  @Test def noLegalTargetsForEmptySquare(): Unit =
    val board = boardOf(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    assertEquals(Set.empty, MoveValidator.legalTargets(board, sq(File.A, Rank.R1)))
