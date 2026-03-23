package de.nowchess.chess.controller

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream

class GameControllerTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private val initial = Board.initial

  // ──── processMove ────────────────────────────────────────────────────

  test("processMove: 'quit' input returns Quit"):
    GameController.processMove(initial, Color.White, "quit") shouldBe MoveResult.Quit

  test("processMove: 'q' input returns Quit"):
    GameController.processMove(initial, Color.White, "q") shouldBe MoveResult.Quit

  test("processMove: quit with surrounding whitespace returns Quit"):
    GameController.processMove(initial, Color.White, "  quit  ") shouldBe MoveResult.Quit

  test("processMove: unparseable input returns InvalidFormat"):
    GameController.processMove(initial, Color.White, "xyz") shouldBe MoveResult.InvalidFormat("xyz")

  test("processMove: valid format but empty square returns NoPiece"):
    // E3 is empty in the initial position
    GameController.processMove(initial, Color.White, "e3e4") shouldBe MoveResult.NoPiece

  test("processMove: piece of wrong color returns WrongColor"):
    // E7 has a Black pawn; it is White's turn
    GameController.processMove(initial, Color.White, "e7e6") shouldBe MoveResult.WrongColor

  test("processMove: geometrically illegal move returns IllegalMove"):
    // White pawn at E2 cannot jump three squares to E5
    GameController.processMove(initial, Color.White, "e2e5") shouldBe MoveResult.IllegalMove

  test("processMove: legal pawn move returns Moved with updated board and flipped turn"):
    GameController.processMove(initial, Color.White, "e2e4") match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        newBoard.pieceAt(sq(File.E, Rank.R4)) shouldBe Some(Piece.WhitePawn)
        newBoard.pieceAt(sq(File.E, Rank.R2)) shouldBe None
        captured shouldBe None
        newTurn shouldBe Color.Black
      case other => fail(s"Expected Moved, got $other")

  test("processMove: legal capture returns Moved with the captured piece"):
    val captureBoard = Board(Map(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R6) -> Piece.BlackPawn,
      sq(File.H, Rank.R1) -> Piece.BlackKing,
      sq(File.H, Rank.R8) -> Piece.WhiteKing
    ))
    GameController.processMove(captureBoard, Color.White, "e5d6") match
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        captured shouldBe Some(Piece.BlackPawn)
        newBoard.pieceAt(sq(File.D, Rank.R6)) shouldBe Some(Piece.WhitePawn)
        newTurn shouldBe Color.Black
      case other => fail(s"Expected Moved, got $other")

  // ──── gameLoop ───────────────────────────────────────────────────────

  private def withInput(input: String)(block: => Unit): Unit =
    val stream = ByteArrayInputStream(input.getBytes("UTF-8"))
    scala.Console.withIn(stream)(block)

  test("gameLoop: 'quit' exits cleanly without exception"):
    withInput("quit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: EOF (null readLine) exits via quit fallback"):
    withInput(""):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: invalid format prints message and recurses until quit"):
    withInput("badmove\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: NoPiece prints message and recurses until quit"):
    // E3 is empty in the initial position
    withInput("e3e4\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: WrongColor prints message and recurses until quit"):
    // E7 has a Black pawn; it is White's turn
    withInput("e7e6\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: IllegalMove prints message and recurses until quit"):
    withInput("e2e5\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: legal non-capture move recurses with new board then quits"):
    withInput("e2e4\nquit\n"):
      GameController.gameLoop(Board.initial, Color.White)

  test("gameLoop: capture move prints capture message then recurses and quits"):
    val captureBoard = Board(Map(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R6) -> Piece.BlackPawn,
      sq(File.H, Rank.R1) -> Piece.BlackKing,
      sq(File.H, Rank.R8) -> Piece.WhiteKing
    ))
    withInput("e5d6\nquit\n"):
      GameController.gameLoop(captureBoard, Color.White)

  // ──── helpers ────────────────────────────────────────────────────────

  private def captureOutput(block: => Unit): String =
    val out = java.io.ByteArrayOutputStream()
    scala.Console.withOut(out)(block)
    out.toString("UTF-8")

  // ──── processMove: check / checkmate / stalemate ─────────────────────

  test("processMove: legal move that delivers check returns MovedInCheck"):
    // White Ra1, Ka3; Black Kh8 — White plays Ra1-Ra8, Ra8 attacks rank 8 putting Kh8 in check
    // Kh8 can escape to g7/g8/h7 so this is InCheck, not Mated
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.C, Rank.R3) -> Piece.WhiteKing,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    ))
    GameController.processMove(b, Color.White, "a1a8") match
      case MoveResult.MovedInCheck(_, _, newTurn) => newTurn shouldBe Color.Black
      case other => fail(s"Expected MovedInCheck, got $other")

  test("processMove: legal move that results in checkmate returns Checkmate"):
    // White Qa1, Ka6; Black Ka8 — White plays Qa1-Qh8 (diagonal a1→h8)
    // After Qh8: White Qh8 + Ka6 vs Black Ka8 = checkmate (spec-verified position)
    // Qa1 does NOT currently attack Ka8 — path along file A is blocked by Ka6
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    GameController.processMove(b, Color.White, "a1h8") match
      case MoveResult.Checkmate(winner) => winner shouldBe Color.White
      case other => fail(s"Expected Checkmate(White), got $other")

  test("processMove: legal move that results in stalemate returns Stalemate"):
    // White Qb1, Kc6; Black Ka8 — White plays Qb1-Qb6
    // After Qb6: White Qb6 + Kc6 vs Black Ka8 = stalemate (spec-verified position)
    val b = Board(Map(
      sq(File.B, Rank.R1) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    GameController.processMove(b, Color.White, "b1b6") match
      case MoveResult.Stalemate => succeed
      case other => fail(s"Expected Stalemate, got $other")

  // ──── gameLoop: check / checkmate / stalemate ─────────────────────────

  test("gameLoop: checkmate prints winner message and resets to new game"):
    // After Qa1-Qh8, position is checkmate; second "quit" exits the new game
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("a1h8\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("Checkmate! White wins.")

  test("gameLoop: stalemate prints draw message and resets to new game"):
    val b = Board(Map(
      sq(File.B, Rank.R1) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("b1b6\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("Stalemate! The game is a draw.")

  test("gameLoop: MovedInCheck without capture prints check message"):
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.C, Rank.R3) -> Piece.WhiteKing,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("a1a8\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("Black is in check!")

  test("gameLoop: MovedInCheck with capture prints both capture and check message"):
    // White Rook A1 captures Black Pawn on A8, Ra8 then attacks rank 8 putting Kh8 in check
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.C, Rank.R3) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackPawn,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    ))
    val output = captureOutput:
      withInput("a1a8\nquit\n"):
        GameController.gameLoop(b, Color.White)
    output should include("captures")
    output should include("Black is in check!")
