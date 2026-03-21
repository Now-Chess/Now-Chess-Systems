package de.nowchess.chess.logic

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.chess.logic.MoveValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MoveValidatorExtendedTest:

  private def sq(file: File, rank: Rank): Square = Square(file, rank)
  private def boardOf(pieces: (Square, Piece)*): Board = Board(pieces.toMap)

  // ── Pawn edge cases ───────────────────────────────────────────────────

  @Test def whitePawnCannotMoveTwoSquaresFromNonStartingRank(): Unit =
    val board = boardOf(sq(File.E, Rank.R3) -> Piece.WhitePawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R3), sq(File.E, Rank.R5)))

  @Test def blackPawnCannotMoveTwoSquaresFromNonStartingRank(): Unit =
    val board = boardOf(sq(File.E, Rank.R6) -> Piece.BlackPawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R6), sq(File.E, Rank.R4)))

  @Test def whitePawnBlockedByPieceBeforeDoubleMove(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R3) -> Piece.BlackPawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R2), sq(File.E, Rank.R4)))

  @Test def whitePawnBlockedByPieceAfterDoubleMove(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R4) -> Piece.BlackPawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R2), sq(File.E, Rank.R4)))

  @Test def blackPawnBlockedByPieceBeforeDoubleMove(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R7) -> Piece.BlackPawn,
      sq(File.E, Rank.R6) -> Piece.WhitePawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R7), sq(File.E, Rank.R5)))

  @Test def blackPawnBlockedByPieceAfterDoubleMove(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R7) -> Piece.BlackPawn,
      sq(File.E, Rank.R5) -> Piece.WhitePawn
    )
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R7), sq(File.E, Rank.R5)))

  @Test def whitePawnForwardTwoAfterFirstMove(): Unit =
    val board = boardOf(sq(File.D, Rank.R3) -> Piece.WhitePawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R3), sq(File.D, Rank.R5)))

  @Test def blackPawnBackwardIsIllegal(): Unit =
    val board = boardOf(sq(File.E, Rank.R5) -> Piece.BlackPawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R5), sq(File.E, Rank.R6)))

  @Test def whitePawnBackwardIsIllegal(): Unit =
    val board = boardOf(sq(File.E, Rank.R5) -> Piece.WhitePawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R5), sq(File.E, Rank.R4)))

  @Test def whitePawnSidewaysIsIllegal(): Unit =
    val board = boardOf(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R4), sq(File.D, Rank.R4)))

  @Test def blackPawnSidewaysIsIllegal(): Unit =
    val board = boardOf(sq(File.E, Rank.R5) -> Piece.BlackPawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R5), sq(File.F, Rank.R5)))

  @Test def whitePawnDiagonalTwoSquaresIsIllegal(): Unit =
    val board = boardOf(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    assertFalse(MoveValidator.isLegal(board, sq(File.E, Rank.R4), sq(File.G, Rank.R6)))

  @Test def whitePawnCapturesLeftDiagonal(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R4), sq(File.D, Rank.R5)))

  @Test def whitePawnCapturesRightDiagonal(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.F, Rank.R5) -> Piece.BlackPawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R4), sq(File.F, Rank.R5)))

  @Test def blackPawnCapturesLeftDiagonal(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R5) -> Piece.BlackPawn,
      sq(File.D, Rank.R4) -> Piece.WhitePawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R5), sq(File.D, Rank.R4)))

  @Test def blackPawnCapturesRightDiagonal(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R5) -> Piece.BlackPawn,
      sq(File.F, Rank.R4) -> Piece.WhitePawn
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.E, Rank.R5), sq(File.F, Rank.R4)))

  // ── Knight comprehensive tests ──────────────────────────────────────────

  @Test def knightCanMoveToAllEightSquares(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteKnight)
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    val expected = Set(
      sq(File.C, Rank.R6), sq(File.E, Rank.R6),
      sq(File.B, Rank.R5), sq(File.F, Rank.R5),
      sq(File.B, Rank.R3), sq(File.F, Rank.R3),
      sq(File.C, Rank.R2), sq(File.E, Rank.R2)
    )
    assertEquals(expected, targets)

  @Test def knightInCornerHasFewerMoves(): Unit =
    val board = boardOf(sq(File.A, Rank.R1) -> Piece.WhiteKnight)
    val targets = MoveValidator.legalTargets(board, sq(File.A, Rank.R1))
    assertEquals(2, targets.size)

  @Test def knightNearEdgeHasFewerMoves(): Unit =
    val board = boardOf(sq(File.A, Rank.R4) -> Piece.WhiteKnight)
    val targets = MoveValidator.legalTargets(board, sq(File.A, Rank.R4))
    assertEquals(4, targets.size)

  @Test def knightCanJumpAllAroundBoard(): Unit =
    val board = boardOf(
      sq(File.B, Rank.R1) -> Piece.WhiteKnight,
      sq(File.C, Rank.R3) -> Piece.BlackRook
    )
    assertTrue(MoveValidator.isLegal(board, sq(File.B, Rank.R1), sq(File.C, Rank.R3)))

  @Test def knightCannotMoveToNonLShapeSquare(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteKnight)
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.D, Rank.R5)))
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.E, Rank.R4)))

  @Test def knightAllTargetsExcludeOwnPieces(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteKnight,
      sq(File.C, Rank.R6) -> Piece.WhitePawn,
      sq(File.E, Rank.R6) -> Piece.WhiteRook
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertFalse(targets.contains(sq(File.C, Rank.R6)))
    assertFalse(targets.contains(sq(File.E, Rank.R6)))

  @Test def knightAllTargetsIncludeEnemyPieces(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteKnight,
      sq(File.C, Rank.R6) -> Piece.BlackPawn,
      sq(File.E, Rank.R6) -> Piece.BlackRook
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertTrue(targets.contains(sq(File.C, Rank.R6)))
    assertTrue(targets.contains(sq(File.E, Rank.R6)))

  // ── Bishop edge cases ──────────────────────────────────────────────────

  @Test def bishopCannotMoveLikeRook(): Unit =
    val board = boardOf(sq(File.D, Rank.R1) -> Piece.WhiteBishop)
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R1), sq(File.D, Rank.R8)))
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R1), sq(File.H, Rank.R1)))

  @Test def bishopAllDiagonalsBlocked(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteBishop,
      sq(File.C, Rank.R5) -> Piece.WhitePawn,
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.C, Rank.R3) -> Piece.WhitePawn,
      sq(File.E, Rank.R3) -> Piece.WhitePawn
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertEquals(0, targets.size)

  @Test def bishopAllDiagonalsFree(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteBishop)
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertTrue(targets.size > 0)
    // All targets should be on diagonals
    for target <- targets do
      val fileDiff = math.abs(target.file.ordinal - sq(File.D, Rank.R4).file.ordinal)
      val rankDiff = math.abs(target.rank.ordinal - sq(File.D, Rank.R4).rank.ordinal)
      assertEquals(fileDiff, rankDiff)

  @Test def bishopCapturesMultiplePiecesButNotBeyond(): Unit =
    val board = boardOf(
      sq(File.C, Rank.R1) -> Piece.WhiteBishop,
      sq(File.E, Rank.R3) -> Piece.BlackRook,
      sq(File.F, Rank.R4) -> Piece.BlackBishop
    )
    val targets = MoveValidator.legalTargets(board, sq(File.C, Rank.R1))
    assertTrue(targets.contains(sq(File.E, Rank.R3)))
    assertFalse(targets.contains(sq(File.F, Rank.R4)))

  // ── Rook edge cases ────────────────────────────────────────────────────

  @Test def rookCannotMoveLikeBishop(): Unit =
    val board = boardOf(sq(File.D, Rank.R1) -> Piece.WhiteRook)
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R1), sq(File.H, Rank.R5)))
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R1), sq(File.A, Rank.R4)))

  @Test def rookAllDirectionsBlocked(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteRook,
      sq(File.D, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R3) -> Piece.WhitePawn,
      sq(File.C, Rank.R4) -> Piece.WhitePawn,
      sq(File.E, Rank.R4) -> Piece.WhitePawn
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertEquals(0, targets.size)

  @Test def rookFullFileClear(): Unit =
    val board = boardOf(sq(File.A, Rank.R4) -> Piece.WhiteRook)
    val targets = MoveValidator.legalTargets(board, sq(File.A, Rank.R4))
    assertEquals(14, targets.size) // 7 squares up and down, 7 left and right, minus itself

  @Test def rookFullRankClear(): Unit =
    val board = boardOf(sq(File.D, Rank.R1) -> Piece.WhiteRook)
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R1))
    assertEquals(14, targets.size)

  @Test def rookStoppedByOwnPieceOnFile(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R1) -> Piece.WhiteRook,
      sq(File.D, Rank.R4) -> Piece.WhitePawn
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R1))
    assertFalse(targets.contains(sq(File.D, Rank.R4)))
    assertFalse(targets.contains(sq(File.D, Rank.R8)))
    assertTrue(targets.contains(sq(File.D, Rank.R3)))

  @Test def rookStoppedByOwnPieceOnRank(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R1) -> Piece.WhiteRook,
      sq(File.F, Rank.R1) -> Piece.WhitePawn
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R1))
    assertFalse(targets.contains(sq(File.F, Rank.R1)))
    assertFalse(targets.contains(sq(File.H, Rank.R1)))
    assertTrue(targets.contains(sq(File.E, Rank.R1)))

  // ── Queen comprehensive ────────────────────────────────────────────────

  @Test def queenHasMoreMovesthanBishopOrRook(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteQueen,
      sq(File.E, Rank.R4) -> Piece.BlackPawn
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertTrue(targets.size > 8) // Should have plenty of moves

  @Test def queenBlockedByOwnPieceBothWays(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteQueen,
      sq(File.D, Rank.R6) -> Piece.WhitePawn,
      sq(File.E, Rank.R5) -> Piece.WhitePawn
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertFalse(targets.contains(sq(File.D, Rank.R6)))
    assertFalse(targets.contains(sq(File.D, Rank.R8)))
    assertFalse(targets.contains(sq(File.E, Rank.R5)))
    assertFalse(targets.contains(sq(File.F, Rank.R6)))

  @Test def queenCanCaptureButNotBeyond(): Unit =
    val board = boardOf(
      sq(File.D, Rank.R4) -> Piece.WhiteQueen,
      sq(File.D, Rank.R7) -> Piece.BlackPawn,
      sq(File.D, Rank.R8) -> Piece.BlackPawn
    )
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertTrue(targets.contains(sq(File.D, Rank.R7)))
    assertFalse(targets.contains(sq(File.D, Rank.R8)))

  // ── King comprehensive ────────────────────────────────────────────────

  @Test def kingMovesOnlyOneSquare(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteKing)
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    assertEquals(8, targets.size)

  @Test def kingInCornerHasFewermoves(): Unit =
    val board = boardOf(sq(File.A, Rank.R1) -> Piece.WhiteKing)
    val targets = MoveValidator.legalTargets(board, sq(File.A, Rank.R1))
    assertEquals(3, targets.size)

  @Test def kingEdgeHasFewerMoves(): Unit =
    val board = boardOf(sq(File.A, Rank.R4) -> Piece.WhiteKing)
    val targets = MoveValidator.legalTargets(board, sq(File.A, Rank.R4))
    assertEquals(5, targets.size)

  @Test def kingCannotMoveMultipleSquares(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteKing)
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.D, Rank.R6)))
    assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R4), sq(File.F, Rank.R4)))

  // ── legalTargets returns Set ──────────────────────────────────────────

  @Test def legalTargetsReturnsSet(): Unit =
    val board = Board.initial
    val targets = MoveValidator.legalTargets(board, sq(File.E, Rank.R2))
    assertTrue(targets.isInstanceOf[Set[?]])

  @Test def legalTargetsForWhitePawnE2On32(): Unit =
    val targets = MoveValidator.legalTargets(Board.initial, sq(File.E, Rank.R2))
    assertEquals(2, targets.size) // Can move to e3 or e4

  @Test def legalTargetsForWhiteKnightG1(): Unit =
    val targets = MoveValidator.legalTargets(Board.initial, sq(File.G, Rank.R1))
    assertEquals(2, targets.size) // Can move to f3 or h3

  // ── isLegal returns consistent results ──────────────────────────────

  @Test def isLegalConsistentWithLegalTargets(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteKnight)
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    for target <- targets do
      assertTrue(MoveValidator.isLegal(board, sq(File.D, Rank.R4), target))

  @Test def isLegalReturnsFalseForNonTargetSquares(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteKnight)
    val targets = MoveValidator.legalTargets(board, sq(File.D, Rank.R4))
    val allSquares = for
      file <- File.values
      rank <- Rank.values
    yield Square(file, rank)
    for square <- allSquares do
      if !targets.contains(square) then
        assertFalse(MoveValidator.isLegal(board, sq(File.D, Rank.R4), square))
