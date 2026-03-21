package de.nowchess.chess.controller

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GameControllerTest:

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /** Create a custom board from a map of squares to pieces. */
  private def boardOf(pieces: (Square, Piece)*): Board =
    Board(pieces.toMap)

  /** Shorthand for Square constructor. */
  private def sq(file: File, rank: Rank): Square = Square(file, rank)

  // ─── Tests for processMove ────────────────────────────────────────────────

  // Branch 1: "quit" → MoveResult.Quit
  @Test def processMove_quitCommand_returnsQuit(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "quit")
    assertEquals(MoveResult.Quit, result)

  // Branch 2: "q" → MoveResult.Quit
  @Test def processMove_shortQuitCommand_returnsQuit(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "q")
    assertEquals(MoveResult.Quit, result)

  // Branch 3: "  quit  " → MoveResult.Quit (trim is applied)
  @Test def processMove_quitWithWhitespace_returnsQuit(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "  quit  ")
    assertEquals(MoveResult.Quit, result)

  @Test def processMove_qWithWhitespace_returnsQuit(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "  q  ")
    assertEquals(MoveResult.Quit, result)

  // Branch 4: Unparseable input → InvalidFormat
  @Test def processMove_invalidFormat_returnsInvalidFormat(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "notavalidmove")
    assert(result.isInstanceOf[MoveResult.InvalidFormat])
    result match
      case MoveResult.InvalidFormat(raw) => assertEquals("notavalidmove", raw)
      case _ => fail("Expected InvalidFormat")

  @Test def processMove_tooShortInput_returnsInvalidFormat(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "e2")
    assert(result.isInstanceOf[MoveResult.InvalidFormat])

  @Test def processMove_tooLongInput_returnsInvalidFormat(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "e2e3e4")
    assert(result.isInstanceOf[MoveResult.InvalidFormat])

  @Test def processMove_nonAlgebraicInput_returnsInvalidFormat(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "abcd")
    assert(result.isInstanceOf[MoveResult.InvalidFormat])

  @Test def processMove_emptyInput_returnsInvalidFormat(): Unit =
    val board = Board.initial
    val result = GameController.processMove(board, Color.White, "")
    assert(result.isInstanceOf[MoveResult.InvalidFormat])

  @Test def processMove_invalidFormatPreservesInput(): Unit =
    val board = Board.initial
    val badInput = "xxx"
    val result = GameController.processMove(board, Color.White, badInput)
    result match
      case MoveResult.InvalidFormat(raw) => assertEquals(badInput, raw)
      case _ => fail("Expected InvalidFormat")

  // Branch 5: Origin square is empty → NoPiece
  @Test def processMove_noPieceOnOriginSquare_returnsNoPiece(): Unit =
    val board = boardOf(sq(File.A, Rank.R1) -> Piece.WhiteRook)
    val result = GameController.processMove(board, Color.White, "e2e3")
    assertEquals(MoveResult.NoPiece, result)

  @Test def processMove_emptyBoardNoPiece_returnsNoPiece(): Unit =
    val board = Board(Map.empty)
    val result = GameController.processMove(board, Color.White, "e2e3")
    assertEquals(MoveResult.NoPiece, result)

  // Branch 6: Origin has piece of opposite color → WrongColor
  @Test def processMove_wrongColorWhiteTurn_returnsWrongColor(): Unit =
    val board = boardOf(sq(File.E, Rank.R7) -> Piece.BlackPawn)
    val result = GameController.processMove(board, Color.White, "e7e6")
    assertEquals(MoveResult.WrongColor, result)

  @Test def processMove_wrongColorBlackTurn_returnsWrongColor(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.Black, "e2e3")
    assertEquals(MoveResult.WrongColor, result)

  @Test def processMove_wrongColorDoesNotRequireValidMove_returnsWrongColor(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    // White pawn on e2, but it's Black's turn: should return WrongColor immediately
    val result = GameController.processMove(board, Color.Black, "e2e3")
    assertEquals(MoveResult.WrongColor, result)

  // Branch 7: Valid piece, legal move, but MoveValidator returns false → IllegalMove
  @Test def processMove_illegalMoveBlocked_returnsIllegalMove(): Unit =
    // White pawn on e2, white pawn on e3: the e2 pawn cannot jump over the e3 pawn
    val board = boardOf(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R3) -> Piece.WhitePawn
    )
    val result = GameController.processMove(board, Color.White, "e2e4")
    assertEquals(MoveResult.IllegalMove, result)

  @Test def processMove_illegalMoveBishopBlocked_returnsIllegalMove(): Unit =
    // White bishop on a1, white pawn on b2: bishop cannot move diagonally to c3
    val board = boardOf(
      sq(File.A, Rank.R1) -> Piece.WhiteBishop,
      sq(File.B, Rank.R2) -> Piece.WhitePawn
    )
    val result = GameController.processMove(board, Color.White, "a1c3")
    assertEquals(MoveResult.IllegalMove, result)

  @Test def processMove_illegalMoveCaptureSelfPiece_returnsIllegalMove(): Unit =
    // White pawn on e2, white pawn on e4: cannot capture own piece
    val board = boardOf(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R4) -> Piece.WhitePawn
    )
    val result = GameController.processMove(board, Color.White, "e2e4")
    assertEquals(MoveResult.IllegalMove, result)

  @Test def processMove_illegalMoveWrongDirection_returnsIllegalMove(): Unit =
    // White pawn on e4 trying to move backward to e3
    val board = boardOf(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "e4e3")
    assertEquals(MoveResult.IllegalMove, result)

  @Test def processMove_illegalMoveKnightInvalidL_returnsIllegalMove(): Unit =
    // White knight on e4 trying to move to e6 (only one file, one rank)
    val board = boardOf(sq(File.E, Rank.R4) -> Piece.WhiteKnight)
    val result = GameController.processMove(board, Color.White, "e4e6")
    assertEquals(MoveResult.IllegalMove, result)

  // Branch 8: Valid move without capture → Moved with None
  @Test def processMove_validMoveNoPiece_returnsMoved(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "e2e3")
    assert(result.isInstanceOf[MoveResult.Moved])
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(None, captured)
        assertEquals(Color.Black, newTurn)
        assertEquals(None, newBoard.pieceAt(sq(File.E, Rank.R2)))
        assertEquals(Some(Piece.WhitePawn), newBoard.pieceAt(sq(File.E, Rank.R3)))
      case _ => fail("Expected Moved")

  @Test def processMove_validMovePawnOneStep_returnsMoved(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "e2e3")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(None, captured)
        assertEquals(Color.Black, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_validMovePawnTwoSteps_returnsMoved(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "e2e4")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(None, captured)
        assertEquals(Color.Black, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_validMoveKnight_returnsMoved(): Unit =
    val board = boardOf(sq(File.G, Rank.R1) -> Piece.WhiteKnight)
    val result = GameController.processMove(board, Color.White, "g1f3")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(None, captured)
        assertEquals(Color.Black, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_validMoveRook_returnsMoved(): Unit =
    val board = boardOf(sq(File.A, Rank.R1) -> Piece.WhiteRook)
    val result = GameController.processMove(board, Color.White, "a1a3")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(None, captured)
        assertEquals(Color.Black, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_validMoveBishop_returnsMoved(): Unit =
    val board = boardOf(sq(File.C, Rank.R1) -> Piece.WhiteBishop)
    val result = GameController.processMove(board, Color.White, "c1a3")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(None, captured)
        assertEquals(Color.Black, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_validMoveUpdatesBoard_returnsMoved(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "e2e3")
    result match
      case MoveResult.Moved(newBoard, _, _) =>
        assertEquals(None, newBoard.pieceAt(sq(File.E, Rank.R2)))
        assertEquals(Some(Piece.WhitePawn), newBoard.pieceAt(sq(File.E, Rank.R3)))
      case _ => fail("Expected Moved")

  @Test def processMove_validMoveBlackPawn_returnsMoved(): Unit =
    val board = boardOf(sq(File.E, Rank.R7) -> Piece.BlackPawn)
    val result = GameController.processMove(board, Color.Black, "e7e6")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(None, captured)
        assertEquals(Color.White, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_validMoveDoesNotMutateOriginal_returnsMoved(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "e2e3")
    result match
      case MoveResult.Moved(newBoard, _, _) =>
        assertEquals(Some(Piece.WhitePawn), board.pieceAt(sq(File.E, Rank.R2)))
        assertEquals(None, newBoard.pieceAt(sq(File.E, Rank.R2)))
      case _ => fail("Expected Moved")

  // Branch 9: Valid move with capture → Moved with Some
  @Test def processMove_validMoveWithCapture_returnsMoved(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val result = GameController.processMove(board, Color.White, "e4d5")
    assert(result.isInstanceOf[MoveResult.Moved])
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(Some(Piece.BlackPawn), captured)
        assertEquals(Color.Black, newTurn)
        assertEquals(Some(Piece.WhitePawn), newBoard.pieceAt(sq(File.D, Rank.R5)))
        assertEquals(None, newBoard.pieceAt(sq(File.E, Rank.R4)))
      case _ => fail("Expected Moved")

  @Test def processMove_captureRemovesEnemyPiece_returnsMoved(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val result = GameController.processMove(board, Color.White, "e4d5")
    result match
      case MoveResult.Moved(newBoard, _, _) =>
        // The destination should have the capturing piece (no longer the original piece)
        assertEquals(Some(Piece.WhitePawn), newBoard.pieceAt(sq(File.D, Rank.R5)))
      case _ => fail("Expected Moved")

  @Test def processMove_captureBlackPiece_returnsMoved(): Unit =
    val board = boardOf(
      sq(File.A, Rank.R4) -> Piece.WhiteRook,
      sq(File.A, Rank.R7) -> Piece.BlackRook
    )
    val result = GameController.processMove(board, Color.White, "a4a7")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(Some(Piece.BlackRook), captured)
        assertEquals(Color.Black, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_captureSwitchesTonNextTurn_returnsMoved(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val result = GameController.processMove(board, Color.White, "e4d5")
    result match
      case MoveResult.Moved(_, _, newTurn) =>
        assertEquals(Color.Black, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_captureByBlack_returnsMoved(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R5) -> Piece.BlackPawn,
      sq(File.D, Rank.R4) -> Piece.WhitePawn
    )
    val result = GameController.processMove(board, Color.Black, "e5d4")
    result match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        assertEquals(Some(Piece.WhitePawn), captured)
        assertEquals(Color.White, newTurn)
      case _ => fail("Expected Moved")

  @Test def processMove_captureDoesNotMutateOriginal_returnsMoved(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R4) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val result = GameController.processMove(board, Color.White, "e4d5")
    result match
      case MoveResult.Moved(newBoard, _, _) =>
        // Original board still has the captured piece
        assertEquals(Some(Piece.BlackPawn), board.pieceAt(sq(File.D, Rank.R5)))
        // New board has the capturing piece instead
        assertEquals(Some(Piece.WhitePawn), newBoard.pieceAt(sq(File.D, Rank.R5)))
      case _ => fail("Expected Moved")

  // ─── Additional edge cases for comprehensive coverage ────────────────────

  @Test def processMove_queenMove_returnsMoved(): Unit =
    val board = boardOf(sq(File.D, Rank.R1) -> Piece.WhiteQueen)
    val result = GameController.processMove(board, Color.White, "d1d4")
    result match
      case MoveResult.Moved(_, _, Color.Black) => // OK
      case _ => fail("Expected Moved with Black turn next")

  @Test def processMove_kingMove_returnsMoved(): Unit =
    val board = boardOf(sq(File.E, Rank.R1) -> Piece.WhiteKing)
    val result = GameController.processMove(board, Color.White, "e1e2")
    result match
      case MoveResult.Moved(_, _, Color.Black) => // OK
      case _ => fail("Expected Moved with Black turn next")

  @Test def processMove_turnAlternates_whiteThenBlack(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R7) -> Piece.BlackPawn
    )
    val result = GameController.processMove(board, Color.White, "e2e3")
    result match
      case MoveResult.Moved(_, _, turn) => assertEquals(Color.Black, turn)
      case _ => fail("Expected Moved")

  @Test def processMove_turnAlternates_blackThenWhite(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R7) -> Piece.BlackPawn,
      sq(File.E, Rank.R2) -> Piece.WhitePawn
    )
    val result = GameController.processMove(board, Color.Black, "e7e6")
    result match
      case MoveResult.Moved(_, _, turn) => assertEquals(Color.White, turn)
      case _ => fail("Expected Moved")

  @Test def processMove_caseInsensitive_lowerCase(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "e2e3")
    assert(result.isInstanceOf[MoveResult.Moved])

  @Test def processMove_caseInsensitive_upperCase(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "E2E3")
    assert(result.isInstanceOf[MoveResult.Moved])

  @Test def processMove_caseInsensitive_mixedCase(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val result = GameController.processMove(board, Color.White, "E2e3")
    assert(result.isInstanceOf[MoveResult.Moved])
